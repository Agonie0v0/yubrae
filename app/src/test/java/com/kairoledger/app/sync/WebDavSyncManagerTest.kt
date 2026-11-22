package com.kairoledger.app.sync

import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.testutil.TestHttpExchange
import com.kairoledger.app.testutil.TestHttpServer
import kotlinx.coroutines.test.runTest
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.InetAddress
import java.net.InetSocketAddress
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class WebDavSyncManagerTest {

    private lateinit var server: TestHttpServer
    private lateinit var okHttpClient: OkHttpClient
    private lateinit var baseUrl: String

    private val collections = CopyOnWriteArraySet<String>()
    private val storedFiles = ConcurrentHashMap<String, StoredFile>()
    private val recordedMethods = CopyOnWriteArrayList<String>()

    private data class StoredFile(
        val bytes: ByteArray,
        val modifiedAt: ZonedDateTime
    )

    @Before
    fun setUp() {
        collections.clear()
        collections.add("/")
        collections.add("/dav/")
        storedFiles.clear()
        recordedMethods.clear()

        server = TestHttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
        server.executor = Executors.newCachedThreadPool()
        server.createContext("/") { exchange ->
            handleWebDavRequest(exchange)
        }
        server.start()
        baseUrl = "http://127.0.0.1:${server.address.port}/dav"

        okHttpClient = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    @After
    fun tearDown() {
        runCatching { server.stop(0) }
        runCatching {
            okHttpClient.dispatcher.executorService.shutdownNow()
            okHttpClient.connectionPool.evictAll()
        }
    }

    @Test
    fun testWebDavConnection_createsNestedDirAndDetectsAuthFailure() = runTest {
        val db = InMemorySyncDatabase()
        db.settings = AppSettings(
            webDavUrl = baseUrl,
            webDavUsername = "kairo",
            webDavPassword = "secret",
            webDavRemoteDir = "/Apps/KairoLedger"
        )
        val manager = WebDavSyncManager(
            backupSerializer = db.createSerializer(),
            okHttpClient = okHttpClient,
            readSettings = { db.settings },
            markBackupSynced = {}
        )

        val okResult = manager.testWebDavConnection()
        assertTrue(okResult.isSuccess)
        assertTrue("/dav/Apps/" in collections)
        assertTrue("/dav/Apps/KairoLedger/" in collections)
        assertTrue("MKCOL" in recordedMethods)

        // Wrong password should fail with HTTP 401
        db.settings = db.settings.copy(webDavPassword = "wrong")
        val failResult = manager.testWebDavConnection()
        assertTrue(failResult.isFailure)
        assertTrue(failResult.exceptionOrNull()!!.message!!.contains("401"))
    }

    @Test
    fun uploadListAndRestoreEncryptedBackup_endToEndAgainstLocalHttpServer() = runTest {
        val db = InMemorySyncDatabase()
        db.accountDao.insert(
            AccountEntity(
                id = 1L,
                name = "英镑储蓄卡",
                groupType = AccountGroup.FOREIGN,
                currencyCode = "GBP",
                balanceMicros = 5_000_000L
            )
        )
        db.categoryDao.insert(
            CategoryEntity(id = 2L, name = "差旅住宿", type = TransactionType.EXPENSE)
        )
        db.transactionDao.insert(
            TransactionEntity(
                id = 3L,
                type = TransactionType.EXPENSE,
                timestamp = 1795250000000L,
                origAmountMicros = 450_000L,
                origCurrency = "GBP",
                accountId = 1L,
                accountAmountMicros = 450_000L,
                accountCurrency = "GBP",
                rateToAccountMicros = 1_000_000L,
                rateToBaseMicros = 9_150_000L,
                baseCurrency = "CNY",
                categoryId = 2L,
                merchant = "Premier Inn"
            )
        )
        db.settings = AppSettings(
            baseCurrency = "CNY",
            webDavUrl = baseUrl,
            webDavUsername = "kairo",
            webDavPassword = "secret",
            webDavRemoteDir = "/KairoLedger",
            backupPassphrase = "SyncPassphrase#2026"
        )

        var syncedMillis = 0L
        val manager = WebDavSyncManager(
            backupSerializer = db.createSerializer(),
            okHttpClient = okHttpClient,
            readSettings = { db.settings },
            markBackupSynced = { syncedMillis = it }
        )

        val uploadResult = manager.uploadEncryptedBackup()
        assertTrue(uploadResult.isSuccess)
        val uploaded = uploadResult.getOrThrow()
        assertTrue(uploaded.name.endsWith(".klbk"))
        assertTrue(syncedMillis > 0L)

        // Verify raw bytes on WebDAV server are encrypted with KLBK1 header
        val rawStored = storedFiles["/dav/KairoLedger/${uploaded.name}"]!!
        assertEquals("KLBK1", String(rawStored.bytes.copyOfRange(0, 5), Charsets.US_ASCII))
        assertEquals(rawStored.bytes.size.toLong(), uploaded.sizeBytes)

        // List backups via PROPFIND Depth: 1
        val listed = manager.listRemoteBackups().getOrThrow()
        assertEquals(1, listed.size)
        assertEquals(uploaded.name, listed[0].name)
        assertEquals("/dav/KairoLedger/${uploaded.name}", listed[0].path)

        // Clear local DB completely and restore from cloud
        db.clearAllTables()
        assertTrue(db.accountDao.getAll().isEmpty())
        assertTrue(db.transactionDao.getAll().isEmpty())

        val restoreMsg = manager.restoreEncryptedBackup(listed[0].path).getOrThrow()
        assertTrue(restoreMsg.contains("1 个账户"))
        assertTrue(restoreMsg.contains("1 笔交易"))

        assertEquals(1, db.accountDao.getAll().size)
        assertEquals("英镑储蓄卡", db.accountDao.getAll()[0].name)
        assertEquals(1, db.transactionDao.getAll().size)
        assertEquals("Premier Inn", db.transactionDao.getAll()[0].merchant)
        assertEquals(9_150_000L, db.transactionDao.getAll()[0].rateToBaseMicros)
    }

    @Test
    fun parseMultiStatusXml_handlesUppercaseLowercaseAndUnprefixedNamespaces() {
        val db = InMemorySyncDatabase()
        val manager = WebDavSyncManager(
            backupSerializer = db.createSerializer(),
            okHttpClient = okHttpClient,
            readSettings = { db.settings },
            markBackupSynced = {}
        )
        val dirUrl = "https://dav.example.com/dav/KairoLedger/".toHttpUrl()

        // 1. Uppercase D: prefix (Jianguoyun / Apache mod_dav style)
        val xmlUpperD = """
            <?xml version="1.0" encoding="utf-8"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response>
                <D:href>/dav/KairoLedger/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/KairoLedger/subfolder/</D:href>
                <D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/KairoLedger/readme.txt</D:href>
                <D:propstat><D:prop><D:getcontentlength>12</D:getcontentlength></D:prop></D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/KairoLedger/kairo_backup_older.klbk</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>1024</D:getcontentlength>
                    <D:getlastmodified>Sat, 21 Nov 2026 08:00:00 GMT</D:getlastmodified>
                    <D:resourcetype/>
                  </D:prop>
                </D:propstat>
              </D:response>
              <D:response>
                <D:href>/dav/KairoLedger/kairo_backup_newer.klbk</D:href>
                <D:propstat>
                  <D:prop>
                    <D:getcontentlength>2048</D:getcontentlength>
                    <D:getlastmodified>Sun, 22 Nov 2026 09:30:00 GMT</D:getlastmodified>
                    <D:resourcetype/>
                  </D:prop>
                </D:propstat>
              </D:response>
            </D:multistatus>
        """.trimIndent()

        val list1 = manager.parseMultiStatusXml(xmlUpperD, dirUrl)
        assertEquals(2, list1.size)
        assertEquals("kairo_backup_newer.klbk", list1[0].name)
        assertEquals(2048L, list1[0].sizeBytes)
        assertEquals("kairo_backup_older.klbk", list1[1].name)

        // 2. Lowercase d: prefix (Nextcloud / SabreDAV style)
        val xmlLowerD = """
            <?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response>
                <d:href>/dav/KairoLedger/kairo%20space.klbk</d:href>
                <d:propstat>
                  <d:prop>
                    <d:getcontentlength>512</d:getcontentlength>
                    <d:getlastmodified>2026-11-22T10:00:00Z</d:getlastmodified>
                  </d:prop>
                  <d:status>HTTP/1.1 200 OK</d:status>
                </d:propstat>
              </d:response>
            </d:multistatus>
        """.trimIndent()

        val list2 = manager.parseMultiStatusXml(xmlLowerD, dirUrl)
        assertEquals(1, list2.size)
        assertEquals("kairo space.klbk", list2[0].name)
        assertEquals(512L, list2[0].sizeBytes)
        assertTrue(list2[0].modifiedAtMillis > 0L)

        // 3. Unprefixed default namespace (Nginx / NAS style)
        val xmlNoPrefix = """
            <?xml version="1.0" encoding="utf-8"?>
            <multistatus xmlns="DAV:">
              <response>
                <href>https://dav.example.com/dav/KairoLedger/nas_snapshot.klbk</href>
                <propstat>
                  <prop>
                    <getcontentlength>4096</getcontentlength>
                    <getlastmodified>Sun, 22 Nov 2026 11:00:00 GMT</getlastmodified>
                  </prop>
                </propstat>
              </response>
            </multistatus>
        """.trimIndent()

        val list3 = manager.parseMultiStatusXml(xmlNoPrefix, dirUrl)
        assertEquals(1, list3.size)
        assertEquals("nas_snapshot.klbk", list3[0].name)
        assertEquals("https://dav.example.com/dav/KairoLedger/nas_snapshot.klbk", list3[0].path)
    }

    @Test
    fun exportAndImportLocalEncryptedBytes_roundTripsAndRejectsWrongPassphrase() = runTest {
        val db = InMemorySyncDatabase()
        db.accountDao.insert(
            AccountEntity(id = 7L, name = "欧元账户", groupType = AccountGroup.FOREIGN, currencyCode = "EUR")
        )
        val manager = WebDavSyncManager(
            backupSerializer = db.createSerializer(),
            okHttpClient = okHttpClient,
            readSettings = { db.settings },
            markBackupSynced = {}
        )

        val encryptedBytes = manager.exportLocalEncryptedBytes("LocalSecret!").getOrThrow()
        assertEquals("KLBK1", String(encryptedBytes.copyOfRange(0, 5), Charsets.US_ASCII))

        db.clearAllTables()

        val badImport = manager.importLocalEncryptedBytes(encryptedBytes, "WrongPass")
        assertTrue(badImport.isFailure)
        assertTrue(db.accountDao.getAll().isEmpty())

        val goodImport = manager.importLocalEncryptedBytes(encryptedBytes, "LocalSecret!")
        assertTrue(goodImport.isSuccess)
        assertEquals(1, db.accountDao.getAll().size)
        assertEquals("欧元账户", db.accountDao.getAll()[0].name)
    }

    private fun handleWebDavRequest(exchange: TestHttpExchange) {
        try {
            val expectedAuth = "Basic " + Base64.getEncoder()
                .encodeToString("kairo:secret".toByteArray(Charsets.UTF_8))
            val auth = exchange.requestHeaders.getFirst("Authorization")
            if (auth != expectedAuth) {
                exchange.sendResponseHeaders(401, -1)
                return
            }

            val method = exchange.requestMethod.uppercase()
            recordedMethods.add(method)
            val path = exchange.requestURI.path

            when (method) {
                "MKCOL" -> {
                    val dirPath = path.trimEnd('/') + "/"
                    if (dirPath in collections) {
                        exchange.sendResponseHeaders(405, -1)
                    } else {
                        collections.add(dirPath)
                        exchange.sendResponseHeaders(201, -1)
                    }
                }
                "PROPFIND" -> {
                    val depth = exchange.requestHeaders.getFirst("Depth") ?: "0"
                    val dirPath = path.trimEnd('/') + "/"
                    if (dirPath !in collections && !storedFiles.containsKey(path)) {
                        exchange.sendResponseHeaders(404, -1)
                        return
                    }
                    val xml = buildPropfindXml(dirPath, depth)
                    val xmlBytes = xml.toByteArray(Charsets.UTF_8)
                    exchange.responseHeaders.add("Content-Type", "application/xml; charset=utf-8")
                    exchange.sendResponseHeaders(207, xmlBytes.size.toLong())
                    exchange.responseBody.use { it.write(xmlBytes) }
                }
                "PUT" -> {
                    val body = exchange.requestBody.readBytes()
                    storedFiles[path] = StoredFile(body, ZonedDateTime.now(ZoneOffset.UTC))
                    exchange.sendResponseHeaders(201, -1)
                }
                "GET" -> {
                    val file = storedFiles[path]
                    if (file == null) {
                        exchange.sendResponseHeaders(404, -1)
                    } else {
                        exchange.responseHeaders.add("Content-Type", "application/octet-stream")
                        exchange.sendResponseHeaders(200, file.bytes.size.toLong())
                        exchange.responseBody.use { it.write(file.bytes) }
                    }
                }
                else -> {
                    exchange.sendResponseHeaders(405, -1)
                }
            }
        } finally {
            exchange.close()
        }
    }

    private fun buildPropfindXml(dirPath: String, depth: String): String {
        val sb = StringBuilder()
        sb.append("""<?xml version="1.0" encoding="utf-8"?>""")
        sb.append("""<D:multistatus xmlns:D="DAV:">""")
        sb.append(
            """
            <D:response>
              <D:href>$dirPath</D:href>
              <D:propstat>
                <D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop>
                <D:status>HTTP/1.1 200 OK</D:status>
              </D:propstat>
            </D:response>
            """.trimIndent()
        )
        if (depth == "1") {
            for ((filePath, stored) in storedFiles) {
                if (filePath.startsWith(dirPath) && !filePath.removePrefix(dirPath).contains('/')) {
                    val rfc1123 = DateTimeFormatter.RFC_1123_DATE_TIME.format(stored.modifiedAt)
                    sb.append(
                        """
                        <D:response>
                          <D:href>$filePath</D:href>
                          <D:propstat>
                            <D:prop>
                              <D:getcontentlength>${stored.bytes.size}</D:getcontentlength>
                              <D:getlastmodified>$rfc1123</D:getlastmodified>
                              <D:resourcetype/>
                            </D:prop>
                            <D:status>HTTP/1.1 200 OK</D:status>
                          </D:propstat>
                        </D:response>
                        """.trimIndent()
                    )
                }
            }
        }
        sb.append("</D:multistatus>")
        return sb.toString()
    }
}
