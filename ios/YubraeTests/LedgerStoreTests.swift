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

        // 默认种子检查：1 账本、16 支出大类 + 9 收入大类（共 115 分类）、21 标签、9 账户
        XCTAssertEqual(store.ledgers.count, 1)
        XCTAssertEqual(store.parentCategories(for: TransactionType.EXPENSE).count, 16)
        XCTAssertEqual(store.parentCategories(for: TransactionType.INCOME).count, 9)
        XCTAssertEqual(store.categories.count, 115)
        XCTAssertEqual(store.tags.count, 21)
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

        // 6. 余额校准将微信零钱调整为 ¥100.00（生成差额记录）
        let adjustTx = try store.calibrateAccountBalance(accountId: wechatId, targetBalanceMicros: 1_000_000)
        XCTAssertNotNil(adjustTx)
        XCTAssertEqual(adjustTx?.type, TransactionType.ADJUST)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 1_000_000)

        // 6b. 仅调整余额但不生成差额变动记录
        let txCountBeforeSilent = store.transactions.count
        let silentAdjust = try store.calibrateAccountBalance(
            accountId: wechatId,
            targetBalanceMicros: 2_345_000,
            createRecord: false
        )
        XCTAssertNil(silentAdjust)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 2_345_000)
        XCTAssertEqual(store.transactions.count, txCountBeforeSilent)

        // 7. 重启持久化验证：从同一目录重新加载
        let reloaded = LedgerStore(storageDirectory: dir, useKeychain: false)
        XCTAssertEqual(reloaded.account(byId: debitId)?.balanceMicros, 1000 * MoneyAmount.amountScale)
        XCTAssertEqual(reloaded.account(byId: wechatId)?.balanceMicros, 2_345_000)
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

    @MainActor
    func testDeleteAccountCascadeRollbackAndMigrationAndAllowDeletingAllDefaults() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        XCTAssertFalse(store.accounts.isEmpty)
        let wechat = try XCTUnwrap(store.accounts.first { $0.name == "微信零钱" })
        let bank = try XCTUnwrap(store.accounts.first { $0.name == "银行储蓄卡" })

        try store.calibrateAccountBalance(accountId: wechat.id, targetBalanceMicros: 1_000_000)
        try store.calibrateAccountBalance(accountId: bank.id, targetBalanceMicros: 500_000)

        try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: 1_763_899_200_000,
                origAmountMicros: 200_000,
                origCurrency: "CNY",
                accountId: wechat.id,
                toAccountId: bank.id
            )
        )
        XCTAssertEqual(store.account(byId: wechat.id)?.balanceMicros, 800_000)
        XCTAssertEqual(store.account(byId: bank.id)?.balanceMicros, 700_000)

        // 级联删除微信零钱并回滚转账对银行储蓄卡的影响
        try store.deleteAccount(id: wechat.id, migrateToAccountId: nil)
        XCTAssertNil(store.account(byId: wechat.id))
        XCTAssertEqual(store.account(byId: bank.id)?.balanceMicros, 500_000)
        XCTAssertTrue(store.accountAliases.allSatisfy { $0.accountId != wechat.id })

        // 迁移删除到目标账户
        let cash = try XCTUnwrap(store.accounts.first { $0.name == "现金" })
        try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: 1_763_899_300_000,
                origAmountMicros: 150_000,
                origCurrency: "CNY",
                accountId: cash.id
            )
        )
        try store.deleteAccount(id: cash.id, migrateToAccountId: bank.id)
        XCTAssertNil(store.account(byId: cash.id))
        XCTAssertEqual(store.account(byId: bank.id)?.balanceMicros, 650_000)

        // 全部剩余默认账户也可删除且重开 Store 不复活
        for id in store.accounts.map(\.id) {
            try store.deleteAccount(id: id, migrateToAccountId: nil)
        }
        XCTAssertTrue(store.accounts.isEmpty)

        let reopened = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        XCTAssertTrue(reopened.accounts.isEmpty)
    }

    @MainActor
    func testPresetCategoriesAndTagsDeduplicatedWithUniqueSfSymbolsAndMigration() throws {
        // 1. 验证默认分类（115 个）：去重完整、无其他杂项、115 个唯一 iconKey 且对应 115 个非兜底 SF Symbol
        let categories = DefaultSeedData.makeDefaultCategories()
        XCTAssertEqual(categories.count, 115)
        XCTAssertEqual(Set(categories.map { "\($0.type)::\($0.name)" }).count, 115)
        XCTAssertFalse(categories.contains { $0.name == "其他杂项" })
        XCTAssertEqual(Set(categories.map(\.icon)).count, 115)
        let categorySymbols = categories.map { DefaultSeedData.sfSymbol(for: $0.icon) }
        XCTAssertEqual(Set(categorySymbols).count, 115)
        XCTAssertFalse(categorySymbols.contains("square.grid.2x2.fill"))

        // 验证 7 个细化分类图标键
        XCTAssertEqual(categories.first { $0.name == "课程培训" }?.icon, "menu_course")
        XCTAssertEqual(categories.first { $0.name == "文具办公" }?.icon, "stationery")
        XCTAssertEqual(categories.first { $0.name == "宠物食品" }?.icon, "pet_food")
        XCTAssertEqual(categories.first { $0.name == "宠物用品" }?.icon, "pet_supplies")
        XCTAssertEqual(categories.first { $0.name == "早教培训" }?.icon, "early_edu")
        XCTAssertEqual(categories.first { $0.name == "餐补交通补" }?.icon, "commute_allowance")
        XCTAssertEqual(categories.first { $0.name == "购物退款" }?.icon, "refund_parcel")

        // 2. 验证默认标签（21 个）：无重复名称、与分类名零重叠、无已清理冗余系统标签、与 115 个分类合并共 136 个全局唯一非兜底 SF Symbol
        let tags = DefaultSeedData.makeDefaultTags()
        XCTAssertEqual(tags.count, 21)
        XCTAssertEqual(Set(tags.map(\.name)).count, 21)
        let categoryNames = Set(categories.map(\.name))
        XCTAssertTrue(tags.allSatisfy { !categoryNames.contains($0.name) })
        let removedTagNames: Set<String> = ["外卖", "网购", "聚餐", "宠物", "请客", "公司报销", "定期订阅"]
        XCTAssertTrue(tags.allSatisfy { !removedTagNames.contains($0.name) })
        XCTAssertEqual(Set(tags.map(\.icon)).count, 21)
        XCTAssertTrue(tags.allSatisfy { $0.icon.hasPrefix("tag_") })
        let tagSymbols = tags.map { DefaultSeedData.sfSymbol(for: $0.icon) }
        XCTAssertEqual(Set(tagSymbols).count, 21)
        XCTAssertFalse(tagSymbols.contains("square.grid.2x2.fill"))
        XCTAssertEqual(Set(categories.map(\.icon) + tags.map(\.icon)).count, 136)
        XCTAssertEqual(Set(categorySymbols + tagSymbols).count, 136)

        // 3. 构造历史快照并通过 LedgerStore.init 进行端到端无损迁移、标签修复与升级验证
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        try FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)

        var snapshot = DefaultSeedData.makeInitialSnapshot()
        snapshot.categories = [
            CategoryDto(id: 10, parentId: nil, name: "餐饮美食", type: TransactionType.EXPENSE, icon: "restaurant", isSystem: true),
            CategoryDto(id: 11, parentId: 10, name: "三餐外食", type: TransactionType.EXPENSE, icon: "lunch_dining", isSystem: true),
            CategoryDto(id: 12, parentId: nil, name: "宠物生活", type: TransactionType.EXPENSE, icon: "pets", isSystem: true),
            CategoryDto(id: 13, parentId: 12, name: "宠物用品", type: TransactionType.EXPENSE, icon: "star", isSystem: true),
            CategoryDto(id: 20, parentId: nil, name: "其他支出", type: TransactionType.EXPENSE, icon: "more_horiz", isSystem: true),
            CategoryDto(id: 21, parentId: 20, name: "其他杂项", type: TransactionType.EXPENSE, icon: "category", isSystem: true)
        ]
        snapshot.tags = [
            TagDto(id: 1, name: "网购", groupName: "渠道平台", isSystem: true, icon: "shopping_cart"),
            TagDto(id: 2, name: "大促囤货", groupName: "场景行程", isSystem: true, icon: "shopping_basket"),
            TagDto(id: 3, name: "外卖", groupName: "自定义", isSystem: false, icon: "star"),
            TagDto(id: 4, name: "海淘", groupName: "导入", isSystem: false, icon: ""),
            TagDto(id: 5, name: "手冲咖啡", groupName: "自定义", isSystem: false, icon: "")
        ]
        snapshot.transactions = [
            TransactionDto(
                id: 100,
                type: TransactionType.EXPENSE,
                timestamp: 1_772_352_000_000,
                origAmountMicros: 500_000,
                origCurrency: "CNY",
                accountId: 1,
                accountAmountMicros: 500_000,
                accountCurrency: "CNY",
                categoryId: 21,
                merchant: "杂项迁移测试"
            )
        ]

        let data = try JSONEncoder().encode(snapshot)
        try data.write(to: dir.appendingPathComponent("snapshot.json"))

        let store = LedgerStore(
            storageDirectory: dir,
            useKeychain: false,
            timeZone: TimeZone(secondsFromGMT: 8 * 3600)!
        )

        XCTAssertEqual(store.categories.count, 115)
        XCTAssertFalse(store.categories.contains { $0.name == "其他杂项" })
        XCTAssertEqual(store.categories.first { $0.name == "三餐外食" }?.icon, "ramen_dining")
        // 用户自定义的系统分类图标不被覆盖
        XCTAssertEqual(store.categories.first { $0.name == "宠物用品" }?.icon, "star")

        // 原关联到 “其他杂项(id=21)” 的流水无损迁移到 “其他支出(id=20)”
        XCTAssertEqual(store.transactions.first { $0.id == 100 }?.categoryId, 20)

        // 21 个预置系统标签 + 3 个用户非系统标签（其中空白图标自动修复，自定义 "star" 保留）
        XCTAssertEqual(store.tags.count, 24)
        XCTAssertFalse(store.tags.contains { $0.isSystem && $0.name == "网购" })
        XCTAssertEqual(store.tags.first { $0.name == "大促囤货" }?.icon, "tag_stockup")
        let customTag = try XCTUnwrap(store.tags.first { !$0.isSystem && $0.name == "外卖" })
        XCTAssertEqual(customTag.icon, "star")
        let repairedHaitaoTag = try XCTUnwrap(store.tags.first { !$0.isSystem && $0.name == "海淘" })
        XCTAssertEqual(repairedHaitaoTag.icon, "tag_crossborder")
        let repairedCoffeeTag = try XCTUnwrap(store.tags.first { !$0.isSystem && $0.name == "手冲咖啡" })
        XCTAssertFalse(repairedCoffeeTag.icon.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

        // 幂等性检查
        let res = store.ensurePresetCatalogSeeded()
        XCTAssertEqual(res.addedCategories, 0)
        XCTAssertEqual(res.addedTags, 0)

        // 4. 历史一木导入同义大类（居家生活 -> 居家物业、购物消费 -> 日用购物）无损合并、子类去重与图标修复验证
        let dir2 = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir2) }
        try FileManager.default.createDirectory(at: dir2, withIntermediateDirectories: true)

        var yimuSnapshot = DefaultSeedData.makeInitialSnapshot()
        let homeCanonParent = try XCTUnwrap(yimuSnapshot.categories.first { $0.parentId == nil && $0.name == "居家物业" })
        let homeCanonBroadband = try XCTUnwrap(yimuSnapshot.categories.first { $0.parentId == homeCanonParent.id && $0.name == "话费宽带" })
        let shopCanonParent = try XCTUnwrap(yimuSnapshot.categories.first { $0.parentId == nil && $0.name == "日用购物" })

        yimuSnapshot.categories.append(contentsOf: [
            CategoryDto(id: 500, parentId: nil, name: "居家生活", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 501, parentId: 500, name: "话费宽带", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 502, parentId: 500, name: "电费", type: TransactionType.EXPENSE, icon: "bolt", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 503, parentId: 500, name: "水费", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 504, parentId: 500, name: "燃气费", type: TransactionType.EXPENSE, icon: "bolt", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 505, parentId: 500, name: "物业费", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 506, parentId: 500, name: "房租还贷", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 507, parentId: 500, name: "快递费", type: TransactionType.EXPENSE, icon: "category", colorHex: 0xFF0B57D0, isSystem: false),
            CategoryDto(id: 600, parentId: nil, name: "购物消费", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 601, parentId: 600, name: "办公用品", type: TransactionType.EXPENSE, icon: "palette", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 602, parentId: 600, name: "虚拟充值", type: TransactionType.EXPENSE, icon: "sports_esports", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 603, parentId: 600, name: "手机数码", type: TransactionType.EXPENSE, icon: "laptop_mac", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 604, parentId: 600, name: "个护美妆", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 605, parentId: 600, name: "日常家居", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 606, parentId: 600, name: "生活电器", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 607, parentId: 600, name: "配饰腕表", type: TransactionType.EXPENSE, icon: "diamond", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 608, parentId: 600, name: "服务器", type: TransactionType.EXPENSE, icon: "smart_toy", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 609, parentId: 600, name: "服饰运动", type: TransactionType.EXPENSE, icon: "shopping_bag", colorHex: 0xFFDB2777, isSystem: false),
            CategoryDto(id: 610, parentId: 600, name: "母婴玩具", type: TransactionType.EXPENSE, icon: "pets", colorHex: 0xFFDB2777, isSystem: false)
        ])
        yimuSnapshot.transactions = [
            TransactionDto(
                id: 901, type: TransactionType.EXPENSE, timestamp: 1_772_352_000_000,
                origAmountMicros: 1_000_000, origCurrency: "CNY", accountId: 1,
                accountAmountMicros: 1_000_000, accountCurrency: "CNY", categoryId: 501, merchant: "中国电信宽带"
            ),
            TransactionDto(
                id: 902, type: TransactionType.EXPENSE, timestamp: 1_772_352_100_000,
                origAmountMicros: 800_000, origCurrency: "CNY", accountId: 1,
                accountAmountMicros: 800_000, accountCurrency: "CNY", categoryId: 502, merchant: "国家电网"
            )
        ]
        yimuSnapshot.budgets = [
            BudgetDto(id: 1, name: "居家生活预算", categoryId: 500, period: "MONTHLY", limitAmountMicros: 30_000_000)
        ]
        yimuSnapshot.recurringRules = [
            RecurringRuleDto(id: 1, name: "月度宽带", amountMicros: 1_000_000, accountId: 1, categoryId: 501, nextRunEpochMillis: 1_772_352_000_000)
        ]
        yimuSnapshot.installments = [
            InstallmentPlanDto(id: 1, accountId: 6, name: "购物分期", totalAmountMicros: 12_000_000, totalPeriods: 12, nextDueMillis: 1_772_352_000_000, categoryId: 600)
        ]
        yimuSnapshot.tags.append(TagDto(id: 99, name: "美团闪购", groupName: "导入", isSystem: false, icon: ""))

        try JSONEncoder().encode(yimuSnapshot).write(to: dir2.appendingPathComponent("snapshot.json"))
        let yimuStore = LedgerStore(storageDirectory: dir2, useKeychain: false, timeZone: TimeZone(secondsFromGMT: 8 * 3600)!)

        XCTAssertFalse(yimuStore.categories.contains { $0.name == "居家生活" || $0.name == "购物消费" })
        XCTAssertEqual(yimuStore.categories.filter { $0.name == "话费宽带" }.count, 1)
        XCTAssertEqual(yimuStore.transactions.first { $0.id == 901 }?.categoryId, homeCanonBroadband.id)
        XCTAssertEqual(yimuStore.transactions.first { $0.id == 902 }?.categoryId, 502)
        XCTAssertEqual(yimuStore.budgets.first?.categoryId, homeCanonParent.id)
        XCTAssertEqual(yimuStore.recurringRules.first?.categoryId, homeCanonBroadband.id)
        XCTAssertEqual(yimuStore.installments.first?.categoryId, shopCanonParent.id)

        let homeChildren = yimuStore.childCategories(of: homeCanonParent.id)
        XCTAssertTrue(homeChildren.allSatisfy { $0.icon != "category" && $0.icon != homeCanonParent.icon })
        XCTAssertEqual(homeChildren.first { $0.name == "电费" }?.icon, "electric_plug")
        XCTAssertEqual(homeChildren.first { $0.name == "水费" }?.icon, "water_drop")
        XCTAssertEqual(homeChildren.first { $0.name == "燃气费" }?.icon, "propane_tank")
        XCTAssertEqual(homeChildren.first { $0.name == "物业费" }?.icon, "property_fee")
        XCTAssertEqual(homeChildren.first { $0.name == "房租还贷" }?.icon, "house_contract")
        XCTAssertEqual(homeChildren.first { $0.name == "快递费" }?.icon, "local_shipping")
        XCTAssertEqual(homeChildren.first { $0.name == "电费" }?.colorHex, homeCanonParent.colorHex)
        XCTAssertEqual(Set(homeChildren.map(\.icon)).count, homeChildren.count)

        let shopChildren = yimuStore.childCategories(of: shopCanonParent.id)
        XCTAssertTrue(shopChildren.allSatisfy { $0.icon != "category" && $0.icon != shopCanonParent.icon })
        XCTAssertEqual(shopChildren.first { $0.name == "办公用品" }?.icon, "office_folder")
        XCTAssertEqual(shopChildren.first { $0.name == "虚拟充值" }?.icon, "virtual_recharge")
        XCTAssertEqual(shopChildren.first { $0.name == "手机数码" }?.icon, "smartphone_dev")
        XCTAssertEqual(shopChildren.first { $0.name == "个护美妆" }?.icon, "personal_care")
        XCTAssertEqual(shopChildren.first { $0.name == "日常家居" }?.icon, "household_lamp")
        XCTAssertEqual(shopChildren.first { $0.name == "生活电器" }?.icon, "appliance_washer")
        XCTAssertEqual(shopChildren.first { $0.name == "配饰腕表" }?.icon, "wrist_watch")
        XCTAssertEqual(shopChildren.first { $0.name == "服务器" }?.icon, "server_rack")
        XCTAssertEqual(shopChildren.first { $0.name == "服饰运动" }?.icon, "sport_shoe")
        XCTAssertEqual(shopChildren.first { $0.name == "母婴玩具" }?.icon, "baby_rattle")
        XCTAssertEqual(Set(shopChildren.map(\.icon)).count, shopChildren.count)

        XCTAssertEqual(yimuStore.tags.first { !$0.isSystem && $0.name == "美团闪购" }?.icon, "tag_meituan")
        let yimuIdempotent = yimuStore.ensurePresetCatalogSeeded()
        XCTAssertEqual(yimuIdempotent.addedCategories, 0)
        XCTAssertEqual(yimuIdempotent.addedTags, 0)
    }
}
