import Foundation
import XCTest
@testable import Yubrae

final class LedgerStoreTests: XCTestCase {

    private func makeTempDir() -> URL {
        FileManager.default.temporaryDirectory
            .appendingPathComponent("yubrae-store-test-\(UUID().uuidString)", isDirectory: true)
    }

    @MainActor
    func testExpenseIncomeTransferEditDeleteAndBalanceLinkage() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: TimeZone(secondsFromGMT: 8 * 3600)!)

        // 默认种子检查：1 账本、16 支出大类 + 9 收入大类、28 标签、9 账户
        XCTAssertEqual(store.ledgers.count, 1)
        XCTAssertEqual(store.parentCategories(for: TransactionType.EXPENSE).count, 16)
        XCTAssertEqual(store.parentCategories(for: TransactionType.INCOME).count, 9)
        XCTAssertEqual(store.tags.count, 28)
        XCTAssertEqual(store.accounts.count, 9)

        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)

        // 1. 收入 ¥1000.00 -> 银行储蓄卡余额 = +1000.00
        let incomeTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                origAmountMicros: 1000 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                merchant: "公司发薪"
            )
        )
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 1000 * MoneyAmount.amountScale)

        // 2. 转账 ¥300.00：银行储蓄卡 -> 微信零钱
        let transferTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                origAmountMicros: 300 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: wechatId,
                merchant: "零钱充值"
            )
        )
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 700 * MoneyAmount.amountScale)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 300 * MoneyAmount.amountScale)

        // 3. 支出 ¥45.50 从微信零钱扣款
        let expenseTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                origAmountMicros: 455_000, // 45.50 元
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "瑞幸咖啡"
            )
        )
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 2_545_000) // 254.50 元

        // 4. 编辑该支出为 ¥60.00，确认旧金额精准回滚再扣新金额
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                existingId: expenseTx.id,
                type: TransactionType.EXPENSE,
                origAmountMicros: 600_000, // 60.00 元
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "瑞幸咖啡升级杯"
            )
        )
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 2_400_000) // 240.00 元

        // 5. 删除转账，验证转出回加、转入回扣
        try store.deleteTransaction(id: transferTx.id)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 1000 * MoneyAmount.amountScale)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, -600_000)

        // 6. 余额校准将微信零钱调整为 ¥100.00
        _ = try store.calibrateAccountBalance(accountId: wechatId, targetBalanceMicros: 1_000_000)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 1_000_000)

        // 7. 重启持久化验证：从同一目录重新加载
        let reloaded = LedgerStore(storageDirectory: dir, useKeychain: false)
        XCTAssertEqual(reloaded.account(byId: debitId)?.balanceMicros, 1000 * MoneyAmount.amountScale)
        XCTAssertEqual(reloaded.account(byId: wechatId)?.balanceMicros, 1_000_000)
        XCTAssertTrue(reloaded.transactions.contains(where: { $0.id == incomeTx.id }))
    }

    @MainActor
    func testMultiCurrencyConversionAndFrozenRateAnalytics() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        let creditId = try XCTUnwrap(store.accounts.first(where: { $0.name == "全币种信用卡" })?.id)
        let usdAccId = try XCTUnwrap(store.accounts.first(where: { $0.currencyCode == "USD" })?.id)
        let subCatId = try XCTUnwrap(store.categories.first(where: { $0.name == "AI与云服务" })?.id)

        // 设定 USD -> CNY = 7.2000
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 7_200_000)

        // 用人民币信用卡消费 $20.00 USD -> 应折算 144.00 CNY (1_440_000)
        let txMillis: Int64 = 1_772_352_000_000 // 2026-03-01 16:00:00 +08:00
        let tx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: txMillis,
                origAmountMicros: 20 * MoneyAmount.amountScale,
                origCurrency: "USD",
                accountId: creditId,
                categoryId: subCatId,
                merchant: "OpenAI"
            )
        )
        XCTAssertEqual(tx.accountAmountMicros, 1_440_000)
        XCTAssertEqual(tx.rateToBaseMicros, 7_200_000)
        XCTAssertEqual(store.account(byId: creditId)?.balanceMicros, -1_440_000)

        // 往美元账户存入 $100.00
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: txMillis,
                origAmountMicros: 100 * MoneyAmount.amountScale,
                origCurrency: "USD",
                accountId: usdAccId,
                merchant: "美元存款"
            )
        )

        // 修改后续汇率为 USD -> CNY = 8.0000
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 8_000_000)

        // 净资产按最新汇率计算：资产 = $100 * 8.0 = ¥800.00，负债 = ¥144.00，净资产 = ¥656.00
        let netWorth = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(netWorth.totalAssetsMicros, 8_000_000)
        XCTAssertEqual(netWorth.totalLiabilitiesMicros, 1_440_000)
        XCTAssertEqual(netWorth.netWorthMicros, 6_560_000)

        // 分类统计必须使用历史冻结汇率（7.20），不能漂移成 160.00！
        let stats = store.computeCategoryStats(
            startMillis: txMillis - 86_400_000,
            endMillis: txMillis + 86_400_000,
            type: TransactionType.EXPENSE,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(stats.count, 1)
        XCTAssertEqual(stats[0].categoryName, "AI与云服务")
        XCTAssertEqual(stats[0].totalBaseMicros, 1_440_000)
        XCTAssertEqual(stats[0].percentage, 1.0, accuracy: 0.0001)
    }

    @MainActor
    func testAndroidV3BackupJsonCompatibilityAndKlbkRestore() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = LedgerStore(storageDirectory: dir, useKeychain: false)

        // 模拟 Android 端精简版与 V3 混合 JSON（故意省略多个可选默认值字段，验证容错解码）
        let androidJson = """
        {
          "formatVersion": 3,
          "exportedAtMillis": 1772000000000,
          "appName": "KairoLedger",
          "settings": {
            "baseCurrency": "CNY",
            "themeMode": "DARK",
            "themeStyle": "PRISM_TEAL",
            "oledPureBlack": true,
            "greenExpenseMode": true,
            "activeLedgerId": 2
          },
          "ledgers": [
            { "id": 1, "name": "日常账本" },
            { "id": 2, "name": "日本旅行", "currencyCode": "JPY", "sortOrder": 1 }
          ],
          "accounts": [
            { "id": 10, "name": "招商储蓄卡", "groupType": "DEBIT", "currencyCode": "CNY", "balanceMicros": 50000000 }
          ],
          "categories": [
            { "id": 101, "name": "餐饮美食", "type": "EXPENSE" }
          ],
          "transactions": [
            {
              "id": 9001,
              "type": "EXPENSE",
              "timestamp": 1772000000000,
              "origAmountMicros": 385000,
              "origCurrency": "CNY",
              "accountId": 10,
              "accountAmountMicros": 385000,
              "accountCurrency": "CNY",
              "categoryId": 101,
              "merchant": "一兰拉面",
              "ledgerId": 2
            }
          ],
          "tags": [
            { "id": 5, "name": "旅行" }
          ]
        }
        """

        // 加密为 .klbk 再导入还原
        let encrypted = try KlbkCrypto.encryptString(androidJson, passphrase: "android-ios-sync")
        let summary = try store.importEncryptedBackup(encrypted, passphrase: "android-ios-sync")

        XCTAssertEqual(summary.accountsCount, 1)
        XCTAssertEqual(summary.transactionsCount, 1)
        XCTAssertEqual(summary.categoriesCount, 1)
        XCTAssertEqual(summary.tagsCount, 1)
        XCTAssertEqual(store.settings.themeMode, .dark)
        XCTAssertEqual(store.settings.themeStyle, ThemeStyle.PRISM_TEAL)
        XCTAssertTrue(store.settings.oledPureBlack)
        XCTAssertTrue(store.settings.greenExpenseMode)
        XCTAssertEqual(store.settings.activeLedgerId, 2)
        XCTAssertEqual(store.transactions.first?.merchant, "一兰拉面")
        XCTAssertEqual(store.transactions.first?.status, TransactionStatus.CONFIRMED)
        XCTAssertEqual(store.transactions.first?.reimburseStatus, ReimburseStatus.NONE)

        // 再由 iOS 导出 .klbk 给另一实例还原，验证双向全字段保留
        let reExported = try store.exportEncryptedBackup(passphrase: "roundtrip-key")
        let dir2 = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let store2 = LedgerStore(storageDirectory: dir2, useKeychain: false)
        let summary2 = try store2.importEncryptedBackup(reExported, passphrase: "roundtrip-key")
        XCTAssertEqual(summary2.transactionsCount, 1)
        XCTAssertEqual(store2.ledgers.count, 2)
        XCTAssertEqual(store2.account(byId: 10)?.balanceMicros, 50_000_000)
    }

    @MainActor
    func testLedgerDeletionMigrationAndBudgetProgress() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        let secondLedger = store.saveLedger(LedgerDto(name: "副账本", currencyCode: "CNY", sortOrder: 1))
        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let diningParent = try XCTUnwrap(store.categories.first(where: { $0.name == "餐饮美食" }))
        let coffeeChild = try XCTUnwrap(store.categories.first(where: { $0.name == "咖啡茶饮" }))

        // 2026-05-10 12:00 +08:00
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        let mayDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 5, day: 10, hour: 12)))
        let mayMillis = Int64((mayDate.timeIntervalSince1970 * 1000.0).rounded())

        _ = store.saveBudget(
            BudgetDto(
                name: "餐饮预算",
                categoryId: diningParent.id,
                period: "MONTHLY",
                limitAmountMicros: 500 * MoneyAmount.amountScale,
                currencyCode: "CNY",
                ledgerId: secondLedger.id
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: mayMillis,
                origAmountMicros: 125 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: wechatId,
                categoryId: coffeeChild.id,
                merchant: "星巴克",
                ledgerId: secondLedger.id
            )
        )

        // 父类预算应自动统计子类消费（125 / 500 = 0.25）
        let progress = store.computeBudgetProgress(year: 2026, month: 5, ledgerId: secondLedger.id, nowMillis: mayMillis)
        XCTAssertEqual(progress.count, 1)
        XCTAssertEqual(progress[0].spentInBudgetCurrencyMicros, 1_250_000)
        XCTAssertEqual(progress[0].progressRatio, 0.25, accuracy: 0.0001)
        XCTAssertFalse(progress[0].overBudget)

        // 删除副账本并迁移交易与预算到默认账本 (id=1)
        try store.deleteLedger(id: secondLedger.id, migrateToLedgerId: 1)
        XCTAssertEqual(store.ledgers.count, 1)
        XCTAssertEqual(store.transactions.first?.ledgerId, 1)
        XCTAssertEqual(store.budgets.first?.ledgerId, 1)

        // 禁止删除最后一个活跃账本
        XCTAssertThrowsError(try store.deleteLedger(id: 1)) { error in
            XCTAssertEqual(error as? LedgerStoreError, .cannotDeleteLastActiveLedger)
        }
    }
}
