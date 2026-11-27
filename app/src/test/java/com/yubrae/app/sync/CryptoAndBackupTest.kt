package com.yubrae.app.sync

import com.yubrae.app.data.db.AccountAliasEntity
import com.yubrae.app.data.db.AccountEntity
import com.yubrae.app.data.db.AccountGroup
import com.yubrae.app.data.db.AliasMatchType
import com.yubrae.app.data.db.BudgetEntity
import com.yubrae.app.data.db.CaptureChannel
import com.yubrae.app.data.db.CategoryEntity
import com.yubrae.app.data.db.DebtDirection
import com.yubrae.app.data.db.DebtRecordEntity
import com.yubrae.app.data.db.DebtStatus
import com.yubrae.app.data.db.ExchangeRateEntity
import com.yubrae.app.data.db.ParseRuleEntity
import com.yubrae.app.data.db.ParseStatus
import com.yubrae.app.data.db.RawCaptureLogEntity
import com.yubrae.app.data.db.RecurringRuleEntity
import com.yubrae.app.data.db.ReimburseStatus
import com.yubrae.app.data.db.TagEntity
import com.yubrae.app.data.db.TransactionEntity
import com.yubrae.app.data.db.TransactionStatus
import com.yubrae.app.data.db.TransactionType
import com.yubrae.app.data.prefs.AppSettings
import com.yubrae.app.data.prefs.ThemeMode
import com.yubrae.app.data.prefs.ThemeStyle
import com.yubrae.app.domain.FakeDebtRecordDao
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
        val exported = serializer.decodeFromJson(json)
        assertEquals("Yubrae", KairoBackupSnapshot.APP_NAME)
        assertEquals("KairoLedger", KairoBackupSnapshot.LEGACY_APP_NAME)
        assertEquals("Yubrae", exported.appName)

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
            serializer.restoreFromJson("""{"formatVersion":99,"appName":"Yubrae"}""")
            fail("Expected IllegalArgumentException for unsupported formatVersion")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("版本"))
        }

        try {
            serializer.restoreFromJson("""{"formatVersion":3,"appName":"OtherLedger"}""")
            fail("Expected IllegalArgumentException for unsupported appName")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("来源"))
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
        val tag1 = TagEntity(
            id = 1L,
            name = "外卖",
            groupName = "渠道平台",
            colorHex = 0xFFEA580CL,
            sortOrder = 10,
            isSystem = true,
            icon = "delivery_dining"
        )
        val tag2 = TagEntity(
            id = 2L,
            name = "咖啡打卡",
            groupName = "自定义",
            colorHex = 0xFF7C3AEDL,
            sortOrder = 20,
            isSystem = false,
            icon = "fastfood"
        )
        db.tagDao.insertAll(listOf(tag1, tag2))

        db.settings = AppSettings(
            themeStyle = ThemeStyle.OBSIDIAN_MONO,
            oledPureBlack = true,
            greenExpenseMode = true,
            accessibilityOrderDetailEnabled = false,
            hiddenAssetAccountIds = setOf(10L, 20L),
            hiddenAssetModules = setOf("REIMBURSE", "INVEST")
        )

        val serializer = db.createSerializer()
        val json = serializer.exportToJson()
        val decoded = serializer.decodeFromJson(json)
        assertEquals("Yubrae", decoded.appName)
        assertEquals(KairoBackupSnapshot.CURRENT_FORMAT_VERSION, decoded.formatVersion)
        assertEquals(2, decoded.tags.size)

        db.clearAllTables()
        db.settings = AppSettings(
            themeStyle = ThemeStyle.YIMU_FRESH,
            oledPureBlack = false,
            greenExpenseMode = false,
            accessibilityOrderDetailEnabled = true,
            hiddenAssetAccountIds = emptySet(),
            hiddenAssetModules = emptySet()
        )

        val summary = serializer.restoreFromJson(json)
        assertEquals(2, summary.tagsCount)
        assertEquals(listOf(tag1, tag2), db.tagDao.getAll())
        assertEquals(ThemeStyle.OBSIDIAN_MONO, db.settings.themeStyle)
        assertTrue(db.settings.oledPureBlack)
        assertTrue(db.settings.greenExpenseMode)
        assertFalse(db.settings.accessibilityOrderDetailEnabled)
        assertEquals(setOf(10L, 20L), db.settings.hiddenAssetAccountIds)
        assertEquals(setOf("REIMBURSE", "INVEST"), db.settings.hiddenAssetModules)
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

        val legacyV3Json = """
            {
              "formatVersion": 3,
              "exportedAtMillis": 1795270000000,
              "appName": "KairoLedger",
              "settings": {
                "baseCurrency": "JPY",
                "themeStyle": "FOREST_MATCHA"
              },
              "accounts": [
                {
                  "id": 3,
                  "name": "旧包v3账户",
                  "groupType": "DEBIT",
                  "currencyCode": "JPY"
                }
              ],
              "tags": [
                {
                  "id": 11,
                  "name": "旅行",
                  "groupName": "场景行程",
                  "colorHex": 4280985718,
                  "sortOrder": 1,
                  "isSystem": true
                }
              ]
            }
        """.trimIndent()

        val legacyV3Summary = serializer.restoreFromJson(legacyV3Json)
        assertEquals(1, legacyV3Summary.accountsCount)
        assertEquals(1, legacyV3Summary.tagsCount)
        assertEquals("JPY", db.settings.baseCurrency)
        assertEquals(ThemeStyle.FOREST_MATCHA, db.settings.themeStyle)
        val restoredTag = db.tagDao.getAll().single()
        assertEquals("旅行", restoredTag.name)
        assertEquals("", restoredTag.icon)
    }

    @Test
    fun backupSerializer_encryptedBackupRoundTripsHideRecentsPreviewEnabled() = runTest {
        val db = InMemorySyncDatabase()
        db.settings = AppSettings(
            hideRecentsPreviewEnabled = true,
            biometricLockEnabled = true
        )
        val serializer = db.createSerializer()
        val json = serializer.exportToJson()
        val decoded = serializer.decodeFromJson(json)
        assertTrue(decoded.settings.hideRecentsPreviewEnabled)

        val passphrase = "RecentsPrivacyBackup#2026"
        val encrypted = CryptoHelper.encryptString(json, passphrase)

        db.clearAllTables()
        db.settings = AppSettings(
            hideRecentsPreviewEnabled = false,
            biometricLockEnabled = false
        )

        val decryptedJson = CryptoHelper.decryptToString(encrypted, passphrase)
        serializer.restoreFromJson(decryptedJson)

        assertTrue(db.settings.hideRecentsPreviewEnabled)
        assertTrue(db.settings.biometricLockEnabled)
    }

    @Test
    fun backupSerializer_legacySnapshotWithoutHideRecentsPreviewDefaultsToFalse() = runTest {
        val db = InMemorySyncDatabase()
        db.settings = AppSettings(hideRecentsPreviewEnabled = true)
        val serializer = db.createSerializer()

        val legacyJson = """
            {
              "formatVersion": 3,
              "exportedAtMillis": 1795280000000,
              "appName": "Yubrae",
              "settings": {
                "baseCurrency": "CNY",
                "biometricLockEnabled": true
              }
            }
        """.trimIndent()

        val decoded = serializer.decodeFromJson(legacyJson)
        assertFalse(decoded.settings.hideRecentsPreviewEnabled)
        assertFalse(
            decoded.settings.applyOnto(AppSettings(hideRecentsPreviewEnabled = true)).hideRecentsPreviewEnabled
        )

        serializer.restoreFromJson(legacyJson)
        assertFalse(db.settings.hideRecentsPreviewEnabled)
        assertTrue(db.settings.biometricLockEnabled)
    }

    @Test
    fun backupSerializer_roundTripsAccountNoteAndAssetGroupPreferences() = runTest {
        val db = InMemorySyncDatabase()
        val accWithNote = AccountEntity(
            id = 101L,
            name = "招行信用卡",
            groupType = AccountGroup.CREDIT,
            currencyCode = "CNY",
            balanceMicros = -1_250_000L,
            creditLimitMicros = 50_000_000L,
            billingDay = 8,
            repaymentDay = 26,
            sortOrder = 10,
            note = "尾号 8821 · 年消费满 6 笔免年费"
        )
        db.accountDao.insert(accWithNote)
        db.settings = AppSettings(
            assetGroupOrder = listOf(AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET),
            collapsedAssetGroups = setOf(AccountGroup.INVEST, AccountGroup.DEBT)
        )

        val serializer = db.createSerializer()
        val json = serializer.exportToJson()
        val decoded = serializer.decodeFromJson(json)
        assertEquals("尾号 8821 · 年消费满 6 笔免年费", decoded.accounts.single().note)
        assertEquals(listOf(AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET), decoded.settings.assetGroupOrder)
        assertEquals(listOf(AccountGroup.DEBT, AccountGroup.INVEST), decoded.settings.collapsedAssetGroups)

        db.clearAllTables()
        db.settings = AppSettings(
            assetGroupOrder = emptyList(),
            collapsedAssetGroups = emptySet()
        )

        serializer.restoreFromJson(json)
        val restoredAcc = db.accountDao.getAll().single()
        assertEquals(accWithNote, restoredAcc)
        assertEquals("尾号 8821 · 年消费满 6 笔免年费", restoredAcc.note)
        assertEquals(listOf(AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET), db.settings.assetGroupOrder)
        assertEquals(setOf(AccountGroup.INVEST, AccountGroup.DEBT), db.settings.collapsedAssetGroups)
    }

    @Test
    fun backupSerializer_legacySnapshotWithoutAccountNoteAndAssetGroupPrefsDefaultsCleanly() = runTest {
        val db = InMemorySyncDatabase()
        db.settings = AppSettings(
            assetGroupOrder = listOf(AccountGroup.CREDIT, AccountGroup.WALLET),
            collapsedAssetGroups = setOf(AccountGroup.INVEST)
        )
        val serializer = db.createSerializer()

        val legacyJson = """
            {
              "formatVersion": 3,
              "exportedAtMillis": 1795290000000,
              "appName": "Yubrae",
              "settings": {
                "baseCurrency": "CNY"
              },
              "accounts": [
                {
                  "id": 7,
                  "name": "旧版储蓄卡",
                  "groupType": "DEBIT",
                  "currencyCode": "CNY",
                  "balanceMicros": 500000
                }
              ]
            }
        """.trimIndent()

        val decoded = serializer.decodeFromJson(legacyJson)
        assertEquals("", decoded.accounts.single().note)
        assertTrue(decoded.settings.assetGroupOrder.isEmpty())
        assertTrue(decoded.settings.collapsedAssetGroups.isEmpty())

        serializer.restoreFromJson(legacyJson)
        val restoredAcc = db.accountDao.getAll().single()
        assertEquals("旧版储蓄卡", restoredAcc.name)
        assertEquals("", restoredAcc.note)
        assertTrue(db.settings.assetGroupOrder.isEmpty())
        assertTrue(db.settings.collapsedAssetGroups.isEmpty())
    }

    @Test
    fun backupSerializer_roundTripsDebtIncludeInNetWorthAndLegacyDefaultsToFalse() = runTest {
        val db = InMemorySyncDatabase()
        val debtDao = FakeDebtRecordDao()
        val serializer = BackupSerializer(
            accountDao = db.accountDao,
            transactionDao = db.transactionDao,
            categoryDao = db.categoryDao,
            exchangeRateDao = db.exchangeRateDao,
            accountAliasDao = db.accountAliasDao,
            parseRuleDao = db.parseRuleDao,
            rawCaptureLogDao = db.rawCaptureLogDao,
            budgetDao = db.budgetDao,
            recurringRuleDao = db.recurringRuleDao,
            readSettings = { db.settings },
            writeSettings = { db.settings = it },
            runInTransaction = { block -> block() },
            debtRecordDao = debtDao,
            tagDao = db.tagDao
        )

        val includedDebt = DebtRecordEntity(
            id = 31L,
            direction = DebtDirection.LEND,
            personName = "Alice",
            amountMicros = 5_000_000L,
            settledAmountMicros = 1_000_000L,
            currencyCode = "USD",
            dueDateMillis = 1796000000000L,
            note = "垫付机票",
            status = DebtStatus.OPEN,
            createdAt = 1795000000000L,
            updatedAt = 1795200000000L,
            includeInNetWorth = true
        )
        val excludedDebt = DebtRecordEntity(
            id = 32L,
            direction = DebtDirection.BORROW,
            personName = "Bob",
            amountMicros = 2_000_000L,
            settledAmountMicros = 0L,
            currencyCode = "CNY",
            note = "不计入净资产",
            status = DebtStatus.OPEN,
            createdAt = 1795000000000L,
            updatedAt = 1795100000000L,
            includeInNetWorth = false
        )
        debtDao.insertAll(listOf(includedDebt, excludedDebt))

        val exportedJson = serializer.exportToJson()
        val decoded = serializer.decodeFromJson(exportedJson)
        assertEquals(2, decoded.debts.size)
        assertTrue(decoded.debts.first { it.id == 31L }.includeInNetWorth)
        assertFalse(decoded.debts.first { it.id == 32L }.includeInNetWorth)

        db.clearAllTables()
        debtDao.clearAll()

        serializer.restoreFromJson(exportedJson)
        val restoredById = debtDao.getAll().associateBy { it.id }
        assertEquals(includedDebt, restoredById[31L])
        assertEquals(excludedDebt, restoredById[32L])
        assertTrue(restoredById[31L]!!.includeInNetWorth)
        assertFalse(restoredById[32L]!!.includeInNetWorth)

        // 旧版备份缺少 includeInNetWorth 字段时必须平滑解码并默认 false
        val legacyDebtJson = """
            {
              "formatVersion": 3,
              "exportedAtMillis": 1795300000000,
              "appName": "Yubrae",
              "debts": [
                {
                  "id": 99,
                  "direction": "LEND",
                  "personName": "旧备份联系人",
                  "amountMicros": 3000000,
                  "settledAmountMicros": 500000,
                  "currencyCode": "CNY",
                  "note": "历史借出",
                  "status": "OPEN",
                  "createdAt": 1795000000000,
                  "updatedAt": 1795000000000
                }
              ]
            }
        """.trimIndent()

        val legacyDecoded = serializer.decodeFromJson(legacyDebtJson)
        assertFalse(legacyDecoded.debts.single().includeInNetWorth)

        serializer.restoreFromJson(legacyDebtJson)
        val restoredLegacyDebt = debtDao.getAll().single()
        assertEquals(99L, restoredLegacyDebt.id)
        assertEquals("旧备份联系人", restoredLegacyDebt.personName)
        assertFalse(restoredLegacyDebt.includeInNetWorth)
    }

    @Test
    fun backupSerializer_roundTripsTransactionDiscountAndFeeAndLegacyDefaultsToZero() = runTest {
        val db = InMemorySyncDatabase()
        val acc1 = AccountEntity(
            id = 1L,
            name = "储蓄卡",
            groupType = AccountGroup.DEBIT,
            currencyCode = "CNY",
            balanceMicros = 10_000_000L
        )
        val acc2 = AccountEntity(
            id = 2L,
            name = "交通卡",
            groupType = AccountGroup.WALLET,
            currencyCode = "CNY",
            balanceMicros = 500_000L
        )
        db.accountDao.insertAll(listOf(acc1, acc2))

        val transferWithDiscount = TransactionEntity(
            id = 101L,
            type = TransactionType.TRANSFER,
            timestamp = 1795300000000L,
            origAmountMicros = 480_000L,
            origCurrency = "CNY",
            accountId = 1L,
            accountAmountMicros = 480_000L,
            accountCurrency = "CNY",
            toAccountId = 2L,
            toAmountMicros = 500_000L,
            toCurrency = "CNY",
            merchant = "公交卡充值",
            note = "满50减2",
            createdAt = 1795300000000L,
            updatedAt = 1795300000000L,
            discountAmountMicros = 20_000L,
            feeAmountMicros = 0L
        )
        val transferWithFee = TransactionEntity(
            id = 102L,
            type = TransactionType.TRANSFER,
            timestamp = 1795303600000L,
            origAmountMicros = 1_001_000L,
            origCurrency = "CNY",
            accountId = 2L,
            accountAmountMicros = 1_001_000L,
            accountCurrency = "CNY",
            toAccountId = 1L,
            toAmountMicros = 1_000_000L,
            toCurrency = "CNY",
            merchant = "余额提现",
            createdAt = 1795303600000L,
            updatedAt = 1795303600000L,
            discountAmountMicros = 0L,
            feeAmountMicros = 1_000L
        )
        val expenseWithBoth = TransactionEntity(
            id = 103L,
            type = TransactionType.EXPENSE,
            timestamp = 1795307200000L,
            origAmountMicros = 485_000L,
            origCurrency = "CNY",
            accountId = 1L,
            accountAmountMicros = 485_000L,
            accountCurrency = "CNY",
            merchant = "外卖点单",
            createdAt = 1795307200000L,
            updatedAt = 1795307200000L,
            discountAmountMicros = 20_000L,
            feeAmountMicros = 5_000L
        )
        db.transactionDao.insertAll(listOf(transferWithDiscount, transferWithFee, expenseWithBoth))

        val serializer = db.createSerializer()
        val exportedJson = serializer.exportToJson()
        assertTrue(exportedJson.contains("\"discountAmountMicros\":20000"))
        assertTrue(exportedJson.contains("\"feeAmountMicros\":1000"))

        val decoded = serializer.decodeFromJson(exportedJson)
        val dtoById = decoded.transactions.associateBy { it.id }
        assertEquals(20_000L, dtoById[101L]!!.discountAmountMicros)
        assertEquals(0L, dtoById[101L]!!.feeAmountMicros)
        assertEquals(0L, dtoById[102L]!!.discountAmountMicros)
        assertEquals(1_000L, dtoById[102L]!!.feeAmountMicros)
        assertEquals(20_000L, dtoById[103L]!!.discountAmountMicros)
        assertEquals(5_000L, dtoById[103L]!!.feeAmountMicros)

        db.clearAllTables()
        serializer.restoreFromJson(exportedJson)
        val restoredById = db.transactionDao.getAll().associateBy { it.id }
        assertEquals(transferWithDiscount, restoredById[101L])
        assertEquals(transferWithFee, restoredById[102L])
        assertEquals(expenseWithBoth, restoredById[103L])

        // 旧版备份 JSON 缺失 discountAmountMicros / feeAmountMicros 字段时默认回退为 0L
        val legacyTxJson = """
            {
              "formatVersion": 3,
              "exportedAtMillis": 1795310000000,
              "appName": "Yubrae",
              "accounts": [
                {
                  "id": 1,
                  "name": "旧账户",
                  "groupType": "WALLET",
                  "currencyCode": "CNY"
                }
              ],
              "transactions": [
                {
                  "id": 201,
                  "type": "EXPENSE",
                  "timestamp": 1795300000000,
                  "origAmountMicros": 350000,
                  "origCurrency": "CNY",
                  "accountId": 1,
                  "accountAmountMicros": 350000,
                  "accountCurrency": "CNY",
                  "merchant": "旧版流水"
                }
              ]
            }
        """.trimIndent()

        val legacyDecoded = serializer.decodeFromJson(legacyTxJson)
        assertEquals(0L, legacyDecoded.transactions.single().discountAmountMicros)
        assertEquals(0L, legacyDecoded.transactions.single().feeAmountMicros)

        serializer.restoreFromJson(legacyTxJson)
        val restoredLegacyTx = db.transactionDao.getAll().single()
        assertEquals(201L, restoredLegacyTx.id)
        assertEquals(0L, restoredLegacyTx.discountAmountMicros)
        assertEquals(0L, restoredLegacyTx.feeAmountMicros)
    }
}
