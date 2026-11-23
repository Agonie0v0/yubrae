package com.kairoledger.app.sync

import com.kairoledger.app.data.db.AccountAliasEntity
import com.kairoledger.app.data.db.AccountEntity
import com.kairoledger.app.data.db.AccountGroup
import com.kairoledger.app.data.db.AliasMatchType
import com.kairoledger.app.data.db.BudgetEntity
import com.kairoledger.app.data.db.CaptureChannel
import com.kairoledger.app.data.db.CategoryEntity
import com.kairoledger.app.data.db.ExchangeRateEntity
import com.kairoledger.app.data.db.ParseRuleEntity
import com.kairoledger.app.data.db.ParseStatus
import com.kairoledger.app.data.db.RawCaptureLogEntity
import com.kairoledger.app.data.db.RecurringRuleEntity
import com.kairoledger.app.data.db.ReimburseStatus
import com.kairoledger.app.data.db.TagEntity
import com.kairoledger.app.data.db.TransactionEntity
import com.kairoledger.app.data.db.TransactionStatus
import com.kairoledger.app.data.db.TransactionType
import com.kairoledger.app.data.prefs.AppSettings
import com.kairoledger.app.data.prefs.ThemeMode
import com.kairoledger.app.data.prefs.ThemeStyle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class CryptoAndBackupTest {

    @Test
    fun encryptAndDecrypt_roundTripsAndUsesKlbk1HeaderWithRandomSaltAndIv() {
        val plaintext = """{"ledger":"棱账 KairoLedger","amountMicros":1234500,"currency":"USD"}"""
        val passphrase = "KairoStrongPassword#2026"

        val cipher1 = CryptoHelper.encryptString(plaintext, passphrase)
        val cipher2 = CryptoHelper.encryptString(plaintext, passphrase)

        // Check KLBK1 magic header (5 bytes) + 16B salt + 12B IV + 16B GCM tag
        val header1 = String(cipher1.copyOfRange(0, 5), Charsets.US_ASCII)
        val header2 = String(cipher2.copyOfRange(0, 5), Charsets.US_ASCII)
        assertEquals("KLBK1", header1)
        assertEquals("KLBK1", header2)
        assertTrue(cipher1.size >= 5 + 16 + 12 + 16)
        assertFalse(cipher1.contentEquals(cipher2))

        assertEquals(plaintext, CryptoHelper.decryptToString(cipher1, passphrase))
        assertEquals(plaintext, CryptoHelper.decryptToString(cipher2, passphrase))
    }

    @Test
    fun decrypt_rejectsWrongPassphraseInvalidHeaderTamperedBytesAndTruncatedPayload() {
        val original = "敏感账本快照数据".toByteArray(Charsets.UTF_8)
        val encrypted = CryptoHelper.encrypt(original, "CorrectPass123")

        // 1. Wrong passphrase
        try {
            CryptoHelper.decrypt(encrypted, "WrongPass456")
            fail("Expected IllegalArgumentException for wrong passphrase")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("口令"))
        }

        // 2. Tampered ciphertext byte
        val tampered = encrypted.clone()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0xFF).toByte()
        try {
            CryptoHelper.decrypt(tampered, "CorrectPass123")
            fail("Expected IllegalArgumentException for tampered ciphertext")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("解密失败"))
        }

        // 3. Invalid magic header
        val badHeader = encrypted.clone()
        badHeader[0] = 'X'.code.toByte()
        try {
            CryptoHelper.decrypt(badHeader, "CorrectPass123")
            fail("Expected IllegalArgumentException for wrong header")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("KLBK1"))
        }

        // 4. Truncated payload
        val truncated = encrypted.copyOfRange(0, 20)
        try {
            CryptoHelper.decrypt(truncated, "CorrectPass123")
            fail("Expected IllegalArgumentException for truncated payload")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不完整"))
        }

        // 5. Empty passphrase
        try {
            CryptoHelper.encrypt(original, "   ")
            fail("Expected IllegalArgumentException for blank passphrase")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("不能为空"))
        }
    }

    @Test
    fun backupSerializer_roundTripsAll9RoomTablesAndKeySettings() = runTest {
        val db = InMemorySyncDatabase()

        val acc1 = AccountEntity(
            id = 10L,
            name = "美元信用卡",
            groupType = AccountGroup.CREDIT,
            currencyCode = "USD",
            balanceMicros = -1_500_000L,
            creditLimitMicros = 50_000_000L,
            billingDay = 5,
            repaymentDay = 23
        )
        val acc2 = AccountEntity(
            id = 20L,
            name = "微信零钱",
            groupType = AccountGroup.WALLET,
            currencyCode = "CNY",
            balanceMicros = 8_000_000L
        )
        db.accountDao.insertAll(listOf(acc1, acc2))

        val catParent = CategoryEntity(id = 100L, name = "数字订阅", type = TransactionType.EXPENSE)
        val catChild = CategoryEntity(id = 101L, parentId = 100L, name = "AI 服务", type = TransactionType.EXPENSE, isSystem = false)
        db.categoryDao.insertAll(listOf(catParent, catChild))

        val rate = ExchangeRateEntity(
            id = 1L,
            baseCurrency = "USD",
            targetCurrency = "CNY",
            rateMicros = 7_245_000L,
            date = "2026-11-22",
            source = "FRANKFURTER"
        )
        db.exchangeRateDao.upsert(rate)

        val alias = AccountAliasEntity(id = 2L, accountId = 10L, matchType = AliasMatchType.CARD_LAST4, pattern = "8899", priority = 90)
        db.accountAliasDao.insert(alias)

        val rule = ParseRuleEntity(
            id = 3L,
            name = "OpenAI 扣款",
            sourcePackage = "*",
            regexPattern = "OpenAI.*?\\$([0-9.]+)",
            transactionType = TransactionType.EXPENSE,
            amountGroup = 1,
            defaultCurrency = "USD"
        )
        db.parseRuleDao.insert(rule)

        val log = RawCaptureLogEntity(
            id = 4L,
            channel = CaptureChannel.NOTIFICATION,
            sourcePackage = "com.bank",
            title = "信用卡消费",
            content = "尾号8899消费USD 20.00",
            capturedAt = 1795250000000L,
            parseStatus = ParseStatus.MATCHED_LOCAL,
            parserUsed = "LOCAL_RULE",
            linkedTransactionId = 50L
        )
        db.rawCaptureLogDao.insert(log)

        val tx = TransactionEntity(
            id = 50L,
            type = TransactionType.EXPENSE,
            timestamp = 1795250000000L,
            origAmountMicros = 200_000L,
            origCurrency = "USD",
            accountId = 10L,
            accountAmountMicros = 200_000L,
            accountCurrency = "USD",
            rateToAccountMicros = 1_000_000L,
            rateToBaseMicros = 7_245_000L,
            baseCurrency = "CNY",
            categoryId = 101L,
            merchant = "OpenAI",
            note = "API 账单",
            tags = "AI,工作",
            reimburseStatus = ReimburseStatus.PENDING,
            sourceType = CaptureChannel.NOTIFICATION,
            rawEventId = 4L,
            status = TransactionStatus.CONFIRMED
        )
        db.transactionDao.insert(tx)

        val budget = BudgetEntity(id = 6L, name = "AI 预算", categoryId = 100L, limitAmountMicros = 5_000_000L, currencyCode = "CNY")
        db.budgetDao.insert(budget)

        val recurring = RecurringRuleEntity(
            id = 7L,
            name = "ChatGPT Plus",
            type = TransactionType.EXPENSE,
            amountMicros = 200_000L,
            currencyCode = "USD",
            accountId = 10L,
            categoryId = 101L,
            merchant = "OpenAI",
            cycleType = "MONTHLY",
            dayOfCycle = 15,
            nextRunEpochMillis = 1797300000000L
        )
        db.recurringRuleDao.insert(recurring)

        db.settings = AppSettings(
            baseCurrency = "USD",
            pinnedCurrencies = listOf("USD", "CNY", "EUR"),
            themeMode = ThemeMode.DARK,
            dynamicColor = false,
            llmEnabled = true,
            llmBaseUrl = "https://api.deepseek.com/v1",
            llmModel = "deepseek-chat",
            autoBackupEnabled = true
        )

        val serializer = db.createSerializer()
        val json = serializer.exportToJson()

        // Wipe DB and configure device-local secrets that must NOT be wiped by restore
        db.clearAllTables()
        db.settings = AppSettings(
            baseCurrency = "CNY",
            themeMode = ThemeMode.LIGHT,
            webDavUrl = "https://dav.jianguoyun.com/dav",
            webDavUsername = "alice@example.com",
            webDavPassword = "app-secret-password",
            backupPassphrase = "local-backup-passphrase",
            llmApiKey = "sk-local-secret"
        )

        val summary = serializer.restoreFromJson(json)
        assertEquals(2, summary.accountsCount)
        assertEquals(1, summary.transactionsCount)
        assertEquals(2, summary.categoriesCount)
        assertEquals(1, summary.exchangeRatesCount)
        assertEquals(2, summary.rulesAndAliasesCount)
        assertEquals(2, summary.budgetsAndRecurringCount)
        assertEquals(1, summary.captureLogsCount)

        // Verify all 9 tables restored with original IDs & values
        assertEquals(listOf(acc1, acc2), db.accountDao.getAll())
        assertEquals(listOf(catParent, catChild), db.categoryDao.getAll())
        assertEquals(listOf(rate), db.exchangeRateDao.getAll())
        assertEquals(listOf(alias), db.accountAliasDao.getAll())
        assertEquals(listOf(rule), db.parseRuleDao.getAll())
        assertEquals(listOf(log), db.rawCaptureLogDao.getAll())
        assertEquals(listOf(tx), db.transactionDao.getAll())
        assertEquals(listOf(budget), db.budgetDao.getAll())
        assertEquals(listOf(recurring), db.recurringRuleDao.getAll())

        // Verify non-secret preferences restored while device-local secrets preserved
        assertEquals("USD", db.settings.baseCurrency)
        assertEquals(listOf("USD", "CNY", "EUR"), db.settings.pinnedCurrencies)
        assertEquals(ThemeMode.DARK, db.settings.themeMode)
        assertFalse(db.settings.dynamicColor)
        assertTrue(db.settings.llmEnabled)
        assertEquals("deepseek-chat", db.settings.llmModel)
        assertTrue(db.settings.autoBackupEnabled)
        assertEquals("https://dav.jianguoyun.com/dav", db.settings.webDavUrl)
        assertEquals("app-secret-password", db.settings.webDavPassword)
        assertEquals("local-backup-passphrase", db.settings.backupPassphrase)
        assertEquals("sk-local-secret", db.settings.llmApiKey)
    }

    @Test
    fun backupSerializer_invalidSnapshotDoesNotModifyExistingDatabase() = runTest {
        val db = InMemorySyncDatabase()
        val existing = AccountEntity(id = 1L, name = "保留账户", groupType = AccountGroup.WALLET, currencyCode = "CNY")
        db.accountDao.insert(existing)
        val serializer = db.createSerializer()

        try {
            serializer.restoreFromJson("""{"formatVersion":99,"appName":"KairoLedger"}""")
            fail("Expected IllegalArgumentException for unsupported formatVersion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("版本"))
        }

        try {
            serializer.restoreFromJson("{not-valid-json")
            fail("Expected IllegalArgumentException for malformed JSON")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("JSON"))
        }

        assertEquals(listOf(existing), db.accountDao.getAll())
    }

    @Test
    fun backupSerializer_roundTripsV3TagsAndDisplayAccessibilitySettings() = runTest {
        val db = InMemorySyncDatabase()
        val tag1 = TagEntity(id = 1L, name = "外卖", groupName = "渠道平台", colorHex = 0xFFEA580CL, sortOrder = 10, isSystem = true)
        val tag2 = TagEntity(id = 2L, name = "咖啡打卡", groupName = "自定义", colorHex = 0xFF7C3AEDL, sortOrder = 20, isSystem = false)
        db.tagDao.insertAll(listOf(tag1, tag2))

        db.settings = AppSettings(
            themeStyle = ThemeStyle.OBSIDIAN_MONO,
            oledPureBlack = true,
            greenExpenseMode = true,
            accessibilityOrderDetailEnabled = false
        )

        val serializer = db.createSerializer()
        val json = serializer.exportToJson()
        val decoded = serializer.decodeFromJson(json)
        assertEquals(KairoBackupSnapshot.CURRENT_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(2, decoded.tags.size)

        db.clearAllTables()
        db.settings = AppSettings(
            themeStyle = ThemeStyle.YIMU_FRESH,
            oledPureBlack = false,
            greenExpenseMode = false,
            accessibilityOrderDetailEnabled = true
        )

        val summary = serializer.restoreFromJson(json)
        assertEquals(2, summary.tagsCount)
        assertEquals(listOf(tag1, tag2), db.tagDao.getAll())
        assertEquals(ThemeStyle.OBSIDIAN_MONO, db.settings.themeStyle)
        assertTrue(db.settings.oledPureBlack)
        assertTrue(db.settings.greenExpenseMode)
        assertFalse(db.settings.accessibilityOrderDetailEnabled)
    }

    @Test
    fun backupSerializer_backwardCompatibleWithV1AndV2Snapshots() = runTest {
        val db = InMemorySyncDatabase()
        val existingTag = TagEntity(id = 9L, name = "保留系统标签", groupName = "渠道平台", colorHex = 0xFF2AA876L, sortOrder = 1, isSystem = true)
        db.tagDao.insert(existingTag)

        val serializer = db.createSerializer()

        val v1Json = """
            {
              "formatVersion": 1,
              "exportedAtMillis": 1795250000000,
              "appName": "KairoLedger",
              "settings": {
                "baseCurrency": "EUR",
                "themeMode": "DARK",
                "dynamicColor": false
              },
              "accounts": [
                {
                  "id": 1,
                  "name": "v1账户",
                  "groupType": "WALLET",
                  "currencyCode": "EUR"
                }
              ]
            }
        """.trimIndent()

        val v1Summary = serializer.restoreFromJson(v1Json)
        assertEquals(1, v1Summary.accountsCount)
        assertEquals(0, v1Summary.tagsCount)
        assertEquals("EUR", db.settings.baseCurrency)
        assertFalse(db.settings.oledPureBlack)
        assertFalse(db.settings.greenExpenseMode)
        assertTrue(db.settings.accessibilityOrderDetailEnabled)
        assertEquals(listOf(existingTag), db.tagDao.getAll())

        val v2Json = """
            {
              "formatVersion": 2,
              "exportedAtMillis": 1795260000000,
              "appName": "KairoLedger",
              "settings": {
                "baseCurrency": "USD",
                "themeStyle": "PRISM_TEAL"
              },
              "accounts": [
                {
                  "id": 2,
                  "name": "v2账户",
                  "groupType": "DEBIT",
                  "currencyCode": "USD"
                }
              ]
            }
        """.trimIndent()

        val v2Summary = serializer.restoreFromJson(v2Json)
        assertEquals(1, v2Summary.accountsCount)
        assertEquals(0, v2Summary.tagsCount)
        assertEquals("USD", db.settings.baseCurrency)
        assertEquals(ThemeStyle.PRISM_TEAL, db.settings.themeStyle)
        assertEquals(listOf(existingTag), db.tagDao.getAll())
    }
}
