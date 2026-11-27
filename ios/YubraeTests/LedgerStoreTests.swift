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

    @MainActor
    func testTransactionSearchMatchesCategoryParentCategoryAndAccountNames() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = LedgerStore(
            storageDirectory: dir,
            useKeychain: false,
            timeZone: TimeZone(secondsFromGMT: 8 * 3600)!
        )

        let foodParent = try XCTUnwrap(
            store.categories.first(where: { $0.parentId == nil && $0.name == "餐饮美食" })
        )
        let diningOutChild = try XCTUnwrap(
            store.categories.first(where: { $0.parentId == foodParent.id && $0.name == "三餐外食" })
        )
        let breakfastChild = store.saveCategory(
            CategoryDto(
                parentId: foodParent.id,
                name: "早餐",
                type: TransactionType.EXPENSE,
                icon: "ramen_dining",
                colorHex: foodParent.colorHex
            )
        )
        let transportParent = try XCTUnwrap(
            store.categories.first(where: { $0.parentId == nil && $0.name == "交通出行" })
        )
        let transitChild = try XCTUnwrap(
            store.categories.first(where: { $0.parentId == transportParent.id && $0.name == "地铁公交" })
        )

        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        let cashId = try XCTUnwrap(store.accounts.first(where: { $0.name == "现金" })?.id)
        let alipayId = try XCTUnwrap(store.accounts.first(where: { $0.name == "支付宝" })?.id)

        let baseMillis: Int64 = 1_772_352_000_000

        let foodParentTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis,
                origAmountMicros: 3_500_000,
                origCurrency: "CNY",
                accountId: cashId,
                categoryId: foodParent.id,
                merchant: "食堂",
                note: "部门工作餐",
                tags: TagTextCodec.format(["工作餐"])
            )
        )
        let diningOutTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis + 1_000,
                origAmountMicros: 420_000,
                origCurrency: "CNY",
                accountId: wechatId,
                categoryId: diningOutChild.id,
                merchant: "Starbucks Reserve",
                note: "Team Lunch",
                tags: TagTextCodec.format(["Coffee", "工作日"])
            )
        )
        let breakfastTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis + 2_000,
                origAmountMicros: 120_000,
                origCurrency: "CNY",
                accountId: alipayId,
                categoryId: breakfastChild.id,
                merchant: "街角包子铺"
            )
        )
        let transportTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis + 3_000,
                origAmountMicros: 50_000,
                origCurrency: "CNY",
                accountId: cashId,
                categoryId: transitChild.id,
                merchant: "北京地铁",
                note: "早高峰通勤"
            )
        )
        let transferTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: baseMillis + 4_000,
                origAmountMicros: 10_000_000,
                origCurrency: "CNY",
                accountId: wechatId,
                toAccountId: debitId,
                categoryId: nil
            )
        )
        let adjustTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.ADJUST,
                timestamp: baseMillis + 5_000,
                origAmountMicros: 500_000,
                origCurrency: "CNY",
                accountId: cashId,
                categoryId: nil
            )
        )
        let categorizedTransfer = TransactionDto(
            id: 9999,
            type: TransactionType.TRANSFER,
            timestamp: baseMillis + 6_000,
            origAmountMicros: 100_000,
            origCurrency: "CNY",
            accountId: cashId,
            accountAmountMicros: 100_000,
            accountCurrency: "CNY",
            toAccountId: alipayId,
            categoryId: foodParent.id
        )

        // 1. 空查询与纯空白边界均放行
        XCTAssertTrue(store.matchesSearchQuery("", for: foodParentTx))
        XCTAssertTrue(store.matchesSearchQuery("   \n\t ", for: transferTx))
        XCTAssertTrue(store.matchesSearchQuery("", for: adjustTx))

        // 2. 搜索父级大类关键词 "餐饮"：命中直挂大类及各子类（三餐外食/早餐），不误命中交通出行或转账校准
        XCTAssertTrue(store.matchesSearchQuery("餐饮", for: foodParentTx))
        XCTAssertTrue(store.matchesSearchQuery("餐饮", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("  餐饮  ", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("餐饮", for: breakfastTx))
        XCTAssertFalse(store.matchesSearchQuery("餐饮", for: transportTx))
        XCTAssertFalse(store.matchesSearchQuery("餐饮", for: transferTx))
        XCTAssertFalse(store.matchesSearchQuery("餐饮", for: adjustTx))

        // 3. 搜索子类部分关键词 & 交通父级大类
        XCTAssertTrue(store.matchesSearchQuery("外食", for: diningOutTx))
        XCTAssertFalse(store.matchesSearchQuery("外食", for: foodParentTx))
        XCTAssertFalse(store.matchesSearchQuery("外食", for: breakfastTx))
        XCTAssertFalse(store.matchesSearchQuery("外食", for: transportTx))
        XCTAssertTrue(store.matchesSearchQuery("早餐", for: breakfastTx))
        XCTAssertTrue(store.matchesSearchQuery("交通", for: transportTx))
        XCTAssertFalse(store.matchesSearchQuery("交通", for: diningOutTx))

        // 4. 搜索账户名部分关键词（消费/转出账户与转入账户）
        XCTAssertTrue(store.matchesSearchQuery("微信", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("微信", for: transferTx))
        XCTAssertFalse(store.matchesSearchQuery("微信", for: foodParentTx))
        XCTAssertFalse(store.matchesSearchQuery("微信", for: breakfastTx))
        XCTAssertTrue(store.matchesSearchQuery("储蓄", for: transferTx))
        XCTAssertFalse(store.matchesSearchQuery("储蓄", for: diningOutTx))
        XCTAssertFalse(store.matchesSearchQuery("储蓄", for: adjustTx))
        XCTAssertTrue(store.matchesSearchQuery("支付宝", for: breakfastTx))

        // 5. 无分类特殊交易类型文案（转账 / 余额校准），且带分类记录不触发兜底文案
        XCTAssertTrue(store.matchesSearchQuery("转账", for: transferTx))
        XCTAssertFalse(store.matchesSearchQuery("转账", for: diningOutTx))
        XCTAssertFalse(store.matchesSearchQuery("转账", for: adjustTx))
        XCTAssertFalse(store.matchesSearchQuery("转账", for: categorizedTransfer))
        XCTAssertTrue(store.matchesSearchQuery("校准", for: adjustTx))
        XCTAssertTrue(store.matchesSearchQuery("余额校准", for: adjustTx))
        XCTAssertFalse(store.matchesSearchQuery("校准", for: transferTx))
        XCTAssertFalse(store.matchesSearchQuery("校准", for: diningOutTx))

        // 6. 原有商户、标签、备注大小写不敏感匹配与无匹配边界
        XCTAssertTrue(store.matchesSearchQuery("starbucks", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("STARBUCKS", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("coffee", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("COFFEE", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("工作日", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("lunch", for: diningOutTx))
        XCTAssertTrue(store.matchesSearchQuery("通勤", for: transportTx))
        XCTAssertFalse(store.matchesSearchQuery("不存在的关键字xyz", for: diningOutTx))
        XCTAssertFalse(store.matchesSearchQuery("不存在的关键字xyz", for: transferTx))
    }

    @MainActor
    func testAccountNoteAndSettingsBackwardCompatibleDecoding() throws {
        // 1. 模拟缺少 note / assetGroupOrder / collapsedAssetGroups 的旧版 JSON 解码
        let legacyAccountJson = """
        {
          "id": 42,
          "name": "旧版招商卡",
          "groupType": "DEBIT",
          "currencyCode": "CNY",
          "balanceMicros": 1200000
        }
        """
        let decodedLegacyAcc = try JSONDecoder().decode(AccountDto.self, from: Data(legacyAccountJson.utf8))
        XCTAssertEqual(decodedLegacyAcc.id, 42)
        XCTAssertEqual(decodedLegacyAcc.name, "旧版招商卡")
        XCTAssertEqual(decodedLegacyAcc.note, "")
        XCTAssertTrue(decodedLegacyAcc.includeInNetWorth)
        XCTAssertFalse(decodedLegacyAcc.archived)

        let legacySettingsJson = """
        {
          "baseCurrency": "CNY",
          "themeMode": "DARK",
          "themeStyle": "PRISM_TEAL"
        }
        """
        let decodedLegacySettings = try JSONDecoder().decode(AppSettings.self, from: Data(legacySettingsJson.utf8))
        XCTAssertEqual(decodedLegacySettings.themeMode, .dark)
        XCTAssertEqual(decodedLegacySettings.assetGroupOrder, [])
        XCTAssertEqual(decodedLegacySettings.collapsedAssetGroups, [])

        let legacySnapshotDto = try JSONDecoder().decode(SettingsSnapshotDto.self, from: Data(legacySettingsJson.utf8))
        XCTAssertEqual(legacySnapshotDto.assetGroupOrder, [])
        XCTAssertEqual(legacySnapshotDto.collapsedAssetGroups, [])

        // 2. 带新字段编码往返 + 本地持久化 + .klbk 加密备份往返
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: TimeZone(secondsFromGMT: 8 * 3600)!)

        var debit = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" }))
        debit.note = "工资卡 · 尾号 8821"
        _ = store.saveAccount(debit)

        store.updateAssetGroupOrder([AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET, AccountGroup.CREDIT])
        XCTAssertEqual(store.settings.assetGroupOrder, [AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET])

        store.toggleAssetGroupCollapsed(AccountGroup.FOREIGN)
        XCTAssertEqual(store.settings.collapsedAssetGroups, [AccountGroup.FOREIGN])
        store.toggleAssetGroupCollapsed(AccountGroup.CREDIT)
        XCTAssertTrue(store.settings.collapsedAssetGroups.contains(AccountGroup.CREDIT))
        store.toggleAssetGroupCollapsed(AccountGroup.FOREIGN)
        XCTAssertEqual(store.settings.collapsedAssetGroups, [AccountGroup.CREDIT])

        // 本地重载验证
        let reloaded = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: TimeZone(secondsFromGMT: 8 * 3600)!)
        XCTAssertEqual(reloaded.account(byId: debit.id)?.note, "工资卡 · 尾号 8821")
        XCTAssertEqual(reloaded.settings.assetGroupOrder, [AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET])
        XCTAssertEqual(reloaded.settings.collapsedAssetGroups, [AccountGroup.CREDIT])

        // .klbk 备份导出并还原至新实例验证
        let backupData = try reloaded.exportEncryptedBackup(passphrase: "assets-note-key")
        let dir2 = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let restoredStore = LedgerStore(storageDirectory: dir2, useKeychain: false, timeZone: TimeZone(secondsFromGMT: 8 * 3600)!)
        _ = try restoredStore.importEncryptedBackup(backupData, passphrase: "assets-note-key")
        XCTAssertEqual(restoredStore.account(byId: debit.id)?.note, "工资卡 · 尾号 8821")
        XCTAssertEqual(restoredStore.settings.assetGroupOrder, [AccountGroup.CREDIT, AccountGroup.DEBIT, AccountGroup.WALLET])
        XCTAssertEqual(restoredStore.settings.collapsedAssetGroups, [AccountGroup.CREDIT])
    }

    @MainActor
    func testReorderAccountsPersistsSortOrder() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        let originalIds = store.activeAccounts.map(\.id)
        XCTAssertGreaterThanOrEqual(originalIds.count, 5)
        let creditId = try XCTUnwrap(store.activeAccounts.first(where: { $0.groupType == AccountGroup.CREDIT })?.id)
        let debitId = try XCTUnwrap(store.activeAccounts.first(where: { $0.groupType == AccountGroup.DEBIT })?.id)

        // 只传部分 ID，确认传入顺序置顶（0, 10），未传入账户按原序顺延（20, 30...）
        store.reorderAccounts(orderedIds: [creditId, debitId, creditId, 999_999])

        let reordered = store.activeAccounts
        XCTAssertEqual(reordered[0].id, creditId)
        XCTAssertEqual(reordered[0].sortOrder, 0)
        XCTAssertEqual(reordered[1].id, debitId)
        XCTAssertEqual(reordered[1].sortOrder, 10)
        let expectedRemaining = originalIds.filter { $0 != creditId && $0 != debitId }
        XCTAssertEqual(Array(reordered.dropFirst(2).map(\.id)), expectedRemaining)
        XCTAssertEqual(reordered[2].sortOrder, 20)

        // 重启持久化后顺序完全保持
        let reloaded = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        XCTAssertEqual(reloaded.activeAccounts.map(\.id), reordered.map(\.id))
        XCTAssertEqual(reloaded.activeAccounts[0].sortOrder, 0)
        XCTAssertEqual(reloaded.activeAccounts[1].sortOrder, 10)
    }

    @MainActor
    func testBatchMoveTransactionsAccountRecomputesBalances() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let alipayId = try XCTUnwrap(store.accounts.first(where: { $0.name == "支付宝" })?.id)
        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        let usdId = try XCTUnwrap(store.accounts.first(where: { $0.currencyCode == "USD" })?.id)
        let cashId = try XCTUnwrap(store.accounts.first(where: { $0.name == "现金" })?.id)
        store.archiveAccount(id: cashId, archived: true)

        // 1. 参数校验：目标账户不存在或已归档应抛错
        XCTAssertThrowsError(try store.batchMoveTransactionsAccount(txIds: [], targetAccountId: 999_999)) { error in
            XCTAssertEqual(error as? LedgerStoreError, .accountNotFound(999_999))
        }
        XCTAssertThrowsError(try store.batchMoveTransactionsAccount(txIds: [], targetAccountId: cashId)) { error in
            XCTAssertEqual(error as? LedgerStoreError, .cannotMigrateToArchivedAccount("现金"))
        }

        // 2. 同币种批量改账户：支出 ¥120 + 收入 ¥500 从微信零钱迁移至银行储蓄卡
        let baseMillis = currentEpochMillis()
        let expTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis,
                origAmountMicros: 120 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "盒马鲜生"
            )
        )
        let incTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseMillis + 1_000,
                origAmountMicros: 500 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "兼职稿费"
            )
        )
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 380 * MoneyAmount.amountScale)

        let movedSameCur = try store.batchMoveTransactionsAccount(txIds: [expTx.id, incTx.id], targetAccountId: debitId)
        XCTAssertEqual(movedSameCur, 2)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 0)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 380 * MoneyAmount.amountScale)

        // 3. 跨币种批量改账户：USD -> CNY 汇率 7.20，将 $100 USD 支出从美元账户迁至银行储蓄卡（CNY）
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 7_200_000)
        let usdExpTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseMillis + 2_000,
                origAmountMicros: 100 * MoneyAmount.amountScale,
                origCurrency: "USD",
                accountId: usdId,
                merchant: "AWS"
            )
        )
        XCTAssertEqual(store.account(byId: usdId)?.balanceMicros, -100 * MoneyAmount.amountScale)

        let movedCrossCur = try store.batchMoveTransactionsAccount(txIds: [usdExpTx.id], targetAccountId: debitId)
        XCTAssertEqual(movedCrossCur, 1)
        XCTAssertEqual(store.account(byId: usdId)?.balanceMicros, 0)
        // 储蓄卡原余额 +380 CNY，迁入 -720 CNY -> -340 CNY
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, -340 * MoneyAmount.amountScale)
        let migratedUsdTx = try XCTUnwrap(store.transactions.first(where: { $0.id == usdExpTx.id }))
        XCTAssertEqual(migratedUsdTx.accountId, debitId)
        XCTAssertEqual(migratedUsdTx.accountCurrency, "CNY")
        XCTAssertEqual(migratedUsdTx.accountAmountMicros, 720 * MoneyAmount.amountScale)
        XCTAssertEqual(migratedUsdTx.rateToAccountMicros, 7_200_000)

        // 4. 转账防自转保护 + 合法迁移转出方
        let transferTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: baseMillis + 3_000,
                origAmountMicros: 200 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: wechatId,
                toAccountId: alipayId,
                merchant: "零钱转支付宝"
            )
        )
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, -200 * MoneyAmount.amountScale)
        XCTAssertEqual(store.account(byId: alipayId)?.balanceMicros, 200 * MoneyAmount.amountScale)

        // 目标账户等于转入方或转出方均必须跳过，防止自转
        XCTAssertEqual(try store.batchMoveTransactionsAccount(txIds: [transferTx.id], targetAccountId: alipayId), 0)
        XCTAssertEqual(try store.batchMoveTransactionsAccount(txIds: [transferTx.id], targetAccountId: wechatId), 0)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, -200 * MoneyAmount.amountScale)
        XCTAssertEqual(store.account(byId: alipayId)?.balanceMicros, 200 * MoneyAmount.amountScale)

        // 迁移转出方至储蓄卡：微信回滚至 0，储蓄卡再减 200（变为 -540），支付宝仍为 +200
        XCTAssertEqual(try store.batchMoveTransactionsAccount(txIds: [transferTx.id], targetAccountId: debitId), 1)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 0)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, -540 * MoneyAmount.amountScale)
        XCTAssertEqual(store.account(byId: alipayId)?.balanceMicros, 200 * MoneyAmount.amountScale)
        let migratedTransfer = try XCTUnwrap(store.transactions.first(where: { $0.id == transferTx.id }))
        XCTAssertEqual(migratedTransfer.accountId, debitId)
        XCTAssertEqual(migratedTransfer.toAccountId, alipayId)

        // 5. duplicateTransaction 验证
        let duplicated = try store.duplicateTransaction(id: expTx.id)
        XCTAssertNotEqual(duplicated.id, expTx.id)
        XCTAssertEqual(duplicated.merchant, "盒马鲜生")
        XCTAssertEqual(duplicated.accountId, debitId)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, -660 * MoneyAmount.amountScale)
    }

    @MainActor
    func testAccountDailyBalanceTrendAndCreditBillingCycleSummary() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)

        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        func millis(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 12) throws -> Int64 {
            let date = try XCTUnwrap(cal.date(from: DateComponents(year: year, month: month, day: day, hour: hour)))
            return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
        }

        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let creditAcc = try XCTUnwrap(store.accounts.first(where: { $0.groupType == AccountGroup.CREDIT }))

        // 1. 余额倒推趋势（锚点 2026-03-10 15:00 +08:00）
        let nowMillis = try millis(2026, 3, 10, 15)
        let incDay8 = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: try millis(2026, 3, 8, 10),
                origAmountMicros: 1_000 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                merchant: "入账"
            )
        )
        let expDay9 = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 3, 9, 14),
                origAmountMicros: 250 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                merchant: "采购"
            )
        )
        let transferDay10 = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: try millis(2026, 3, 10, 9),
                origAmountMicros: 100 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: wechatId
            )
        )
        // 未来日期交易（3/12）不应混入 3/10 收盘余额
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 3, 12, 9),
                origAmountMicros: 50 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                merchant: "预扣计划"
            )
        )

        // accountDeltaForTransaction 校验
        XCTAssertEqual(store.accountDeltaForTransaction(incDay8, accountId: debitId), 1_000 * MoneyAmount.amountScale)
        XCTAssertEqual(store.accountDeltaForTransaction(expDay9, accountId: debitId), -250 * MoneyAmount.amountScale)
        XCTAssertEqual(store.accountDeltaForTransaction(transferDay10, accountId: debitId), -100 * MoneyAmount.amountScale)
        XCTAssertEqual(store.accountDeltaForTransaction(transferDay10, accountId: wechatId), 100 * MoneyAmount.amountScale)
        let selfTransfer = TransactionDto(
            id: 8888,
            type: TransactionType.TRANSFER,
            timestamp: nowMillis,
            origAmountMicros: 100 * MoneyAmount.amountScale,
            origCurrency: "CNY",
            accountId: debitId,
            accountAmountMicros: 100 * MoneyAmount.amountScale,
            accountCurrency: "CNY",
            toAccountId: debitId,
            toAmountMicros: 100 * MoneyAmount.amountScale,
            toCurrency: "CNY"
        )
        XCTAssertEqual(store.accountDeltaForTransaction(selfTransfer, accountId: debitId), 0)

        let trend = store.computeAccountDailyBalanceTrend(accountId: debitId, days: 7, nowMillis: nowMillis)
        XCTAssertEqual(trend.count, 7)
        XCTAssertEqual(trend.first?.id, "2026-03-04")
        XCTAssertEqual(trend.last?.id, "2026-03-10")
        XCTAssertEqual(trend[0].balanceMicros, 0) // 3/4
        XCTAssertEqual(trend[3].balanceMicros, 0) // 3/7
        XCTAssertEqual(trend[4].balanceMicros, 1_000 * MoneyAmount.amountScale) // 3/8
        XCTAssertEqual(trend[5].balanceMicros, 750 * MoneyAmount.amountScale)   // 3/9
        XCTAssertEqual(trend[6].balanceMicros, 650 * MoneyAmount.amountScale)   // 3/10

        // 2. 信用卡账单周期消费与还款统计（账单日 5 日，还款日 23 日；当前 2026-03-10 -> 周期 3/6 ~ 4/5）
        // 3/5 属于上一账期，不应计入本期消费
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 3, 5, 20),
                origAmountMicros: 500 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: creditAcc.id,
                merchant: "上期消费"
            )
        )
        let cycleExpense = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 3, 8, 12),
                origAmountMicros: 300 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: creditAcc.id,
                merchant: "本期购物"
            )
        )
        // 退款冲减消费（-50）
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: try millis(2026, 3, 9, 10),
                origAmountMicros: 50 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: creditAcc.id,
                merchant: "部分退款",
                refundOfTxId: cycleExpense.id
            )
        )
        // 储蓄卡还信用卡 ¥120
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: try millis(2026, 3, 9, 18),
                origAmountMicros: 120 * MoneyAmount.amountScale,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: creditAcc.id,
                merchant: "信用卡还款"
            )
        )

        let summary = store.computeCreditBillingCycleSummary(account: creditAcc, nowMillis: nowMillis)
        let expectedStart = try millis(2026, 3, 6, 0)
        let expectedEnd = try millis(2026, 4, 6, 0) - 1
        XCTAssertEqual(summary.cycleStartMillis, expectedStart)
        XCTAssertEqual(summary.cycleEndMillis, expectedEnd)
        XCTAssertEqual(summary.cycleSpendMicros, 250 * MoneyAmount.amountScale)
        XCTAssertEqual(summary.cycleRepaidMicros, 120 * MoneyAmount.amountScale)
        XCTAssertEqual(summary.daysUntilBilling, 26)
        XCTAssertEqual(summary.daysUntilRepayment, 13)

        // 3. 大小月与闰年（2024-02-20，账单日/还款日 31 日 -> 钳位至 2 月 29 日，倒计时 9 天）
        let leapCard = AccountDto(
            id: 901,
            name: "闰年测试卡",
            groupType: AccountGroup.CREDIT,
            currencyCode: "CNY",
            billingDay: 31,
            repaymentDay: 31
        )
        let leapNow = try millis(2024, 2, 20, 12)
        let leapSummary = store.computeCreditBillingCycleSummary(account: leapCard, nowMillis: leapNow)
        XCTAssertEqual(leapSummary.cycleStartMillis, try millis(2024, 2, 1, 0))
        XCTAssertEqual(leapSummary.cycleEndMillis, try millis(2024, 3, 1, 0) - 1)
        XCTAssertEqual(leapSummary.daysUntilBilling, 9)
        XCTAssertEqual(leapSummary.daysUntilRepayment, 9)

        // 4. 无账单日回退自然月
        let noBillingCard = AccountDto(
            id: 902,
            name: "无账单日卡",
            groupType: AccountGroup.CREDIT,
            currencyCode: "CNY",
            billingDay: nil,
            repaymentDay: nil
        )
        let naturalSummary = store.computeCreditBillingCycleSummary(account: noBillingCard, nowMillis: nowMillis)
        XCTAssertEqual(naturalSummary.cycleStartMillis, try millis(2026, 3, 1, 0))
        XCTAssertEqual(naturalSummary.cycleEndMillis, try millis(2026, 4, 1, 0) - 1)
        XCTAssertNil(naturalSummary.daysUntilBilling)
        XCTAssertNil(naturalSummary.daysUntilRepayment)
    }

    @MainActor
    func testDebtIncludeInNetWorthEditingAndNetWorthCalculation() throws {
        // 1. 缺少 includeInNetWorth 等可选字段的旧版 DebtRecordDto JSON 解码向后兼容且默认为 false
        let legacyDebtJson = """
        {
          "id": 7,
          "direction": "LEND",
          "personName": "老张",
          "amountMicros": 5000000
        }
        """
        let decodedLegacy = try JSONDecoder().decode(DebtRecordDto.self, from: Data(legacyDebtJson.utf8))
        XCTAssertEqual(decodedLegacy.id, 7)
        XCTAssertEqual(decodedLegacy.direction, DebtDirection.LEND)
        XCTAssertEqual(decodedLegacy.personName, "老张")
        XCTAssertEqual(decodedLegacy.amountMicros, 5_000_000)
        XCTAssertEqual(decodedLegacy.settledAmountMicros, 0)
        XCTAssertEqual(decodedLegacy.status, DebtStatus.OPEN)
        XCTAssertEqual(decodedLegacy.note, "")
        XCTAssertFalse(decodedLegacy.includeInNetWorth)

        let modernDebtJson = """
        {
          "id": 8,
          "direction": "BORROW",
          "personName": "老李",
          "amountMicros": 2500000,
          "includeInNetWorth": true
        }
        """
        let decodedModern = try JSONDecoder().decode(DebtRecordDto.self, from: Data(modernDebtJson.utf8))
        XCTAssertTrue(decodedModern.includeInNetWorth)

        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        let unit = MoneyAmount.amountScale

        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 7_200_000)
        _ = try store.calibrateAccountBalance(
            accountId: debitId,
            targetBalanceMicros: 1_000 * unit,
            createRecord: false
        )

        // 基线净资产与缓存命中校验
        let baseNetWorth = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(baseNetWorth.totalAssetsMicros, 1_000 * unit)
        XCTAssertEqual(baseNetWorth.totalLiabilitiesMicros, 0)
        XCTAssertEqual(baseNetWorth.netWorthMicros, 1_000 * unit)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY"), baseNetWorth)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 0)

        // 2. includeInNetWorth == false 的借出/借入不影响净资产与应收汇总
        _ = store.saveDebt(
            DebtRecordDto(
                direction: DebtDirection.LEND,
                personName: "不计入借出",
                amountMicros: 500 * unit,
                currencyCode: "CNY",
                includeInNetWorth: false
            )
        )
        _ = store.saveDebt(
            DebtRecordDto(
                direction: DebtDirection.BORROW,
                personName: "不计入借入",
                amountMicros: 300 * unit,
                currencyCode: "CNY",
                includeInNetWorth: false
            )
        )
        let afterExcluded = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterExcluded.totalAssetsMicros, 1_000 * unit)
        XCTAssertEqual(afterExcluded.totalLiabilitiesMicros, 0)
        XCTAssertEqual(afterExcluded.netWorthMicros, 1_000 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 0)

        // 3. includeInNetWorth == true 的未结清借出（含多币种）增加总资产、净资产与应收总额
        let lendCny = store.saveDebt(
            DebtRecordDto(
                direction: DebtDirection.LEND,
                personName: "王小明",
                amountMicros: 400 * unit,
                currencyCode: "CNY",
                note: "周转借款",
                includeInNetWorth: true
            )
        )
        XCTAssertGreaterThan(lendCny.createdAt, 0)
        _ = store.saveDebt(
            DebtRecordDto(
                direction: DebtDirection.LEND,
                personName: "Alice",
                amountMicros: 100 * unit,
                currencyCode: "USD",
                includeInNetWorth: true
            )
        )
        let afterLend = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterLend.totalAssetsMicros, 2_120 * unit) // 1000 + 400 + 720
        XCTAssertEqual(afterLend.totalLiabilitiesMicros, 0)
        XCTAssertEqual(afterLend.netWorthMicros, 2_120 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_120 * unit)

        // 汇率变更必须使净资产与应收缓存立即失效
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 8_000_000)
        let afterRateChange = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterRateChange.totalAssetsMicros, 2_200 * unit) // 1000 + 400 + 800
        XCTAssertEqual(afterRateChange.netWorthMicros, 2_200 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_200 * unit)

        // 4. includeInNetWorth == true 的未结清借入增加总负债并扣减净资产，不计入借出应收
        let borrowCny = store.saveDebt(
            DebtRecordDto(
                direction: DebtDirection.BORROW,
                personName: "李华",
                amountMicros: 250 * unit,
                currencyCode: "CNY",
                includeInNetWorth: true
            )
        )
        let afterBorrow = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterBorrow.totalAssetsMicros, 2_200 * unit)
        XCTAssertEqual(afterBorrow.totalLiabilitiesMicros, 250 * unit)
        XCTAssertEqual(afterBorrow.netWorthMicros, 1_950 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_200 * unit)

        // 5. 部分与全额 settleDebt 实时联动净资产（验证缓存失效）
        let partialSettled = try store.settleDebt(debtId: lendCny.id, settleAmountMicros: 150 * unit, accountId: nil)
        XCTAssertEqual(partialSettled.status, DebtStatus.OPEN)
        XCTAssertEqual(partialSettled.settledAmountMicros, 150 * unit)
        let afterPartialSettle = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterPartialSettle.totalAssetsMicros, 2_050 * unit) // 1000 + 250 + 800
        XCTAssertEqual(afterPartialSettle.totalLiabilitiesMicros, 250 * unit)
        XCTAssertEqual(afterPartialSettle.netWorthMicros, 1_800 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_050 * unit)

        let fullSettledLend = try store.settleDebt(debtId: lendCny.id, settleAmountMicros: 250 * unit, accountId: nil)
        XCTAssertEqual(fullSettledLend.status, DebtStatus.SETTLED)
        let afterFullLendSettle = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterFullLendSettle.totalAssetsMicros, 1_800 * unit) // 1000 + 0 + 800
        XCTAssertEqual(afterFullLendSettle.totalLiabilitiesMicros, 250 * unit)
        XCTAssertEqual(afterFullLendSettle.netWorthMicros, 1_550 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 800 * unit)

        // 用储蓄卡归还借入 ¥250：储蓄卡资产减 250、负债清零，净资产保持不变
        let fullSettledBorrow = try store.settleDebt(debtId: borrowCny.id, settleAmountMicros: 250 * unit, accountId: debitId)
        XCTAssertEqual(fullSettledBorrow.status, DebtStatus.SETTLED)
        let afterBorrowSettle = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterBorrowSettle.totalAssetsMicros, 1_550 * unit) // 750 + 800
        XCTAssertEqual(afterBorrowSettle.totalLiabilitiesMicros, 0)
        XCTAssertEqual(afterBorrowSettle.netWorthMicros, 1_550 * unit)

        // 6. saveDebt 编辑已有债务：保留原 createdAt、修改金额/已核销额/备注/开关、钳位与状态自动维护、缓存即时刷新
        let editedOpen = store.saveDebt(
            DebtRecordDto(
                id: lendCny.id,
                direction: DebtDirection.LEND,
                personName: "王小明（续借）",
                amountMicros: 600 * unit,
                settledAmountMicros: 200 * unit,
                currencyCode: "CNY",
                accountId: nil,
                dueDateMillis: nil,
                note: "续借调整",
                status: DebtStatus.SETTLED, // 应自动校正为 OPEN
                createdAt: 0, // 应继承原 createdAt
                updatedAt: 0,
                includeInNetWorth: true
            )
        )
        XCTAssertEqual(editedOpen.createdAt, lendCny.createdAt)
        XCTAssertEqual(editedOpen.personName, "王小明（续借）")
        XCTAssertEqual(editedOpen.amountMicros, 600 * unit)
        XCTAssertEqual(editedOpen.settledAmountMicros, 200 * unit)
        XCTAssertEqual(editedOpen.note, "续借调整")
        XCTAssertEqual(editedOpen.status, DebtStatus.OPEN)
        XCTAssertTrue(editedOpen.includeInNetWorth)
        let afterEditOpen = store.computeNetWorth(displayCurrency: "CNY")
        XCTAssertEqual(afterEditOpen.totalAssetsMicros, 1_950 * unit) // 750 + 800 + 400
        XCTAssertEqual(afterEditOpen.netWorthMicros, 1_950 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_200 * unit)

        // 关闭 includeInNetWorth 后净资产缓存立即回落
        var toggledOff = editedOpen
        toggledOff.includeInNetWorth = false
        _ = store.saveDebt(toggledOff)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").totalAssetsMicros, 1_550 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 800 * unit)

        // 已核销额超过总额 -> 钳位至总额并自动转为 SETTLED
        var overflowSettled = editedOpen
        overflowSettled.settledAmountMicros = 999 * unit
        overflowSettled.includeInNetWorth = true
        let clampedHigh = store.saveDebt(overflowSettled)
        XCTAssertEqual(clampedHigh.settledAmountMicros, 600 * unit)
        XCTAssertEqual(clampedHigh.status, DebtStatus.SETTLED)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").totalAssetsMicros, 1_550 * unit)
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 800 * unit)

        // 已核销额为负数 -> 钳位至 0 并恢复为 OPEN
        var negativeSettled = editedOpen
        negativeSettled.settledAmountMicros = -100
        negativeSettled.includeInNetWorth = true
        let clampedLow = store.saveDebt(negativeSettled)
        XCTAssertEqual(clampedLow.settledAmountMicros, 0)
        XCTAssertEqual(clampedLow.status, DebtStatus.OPEN)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").totalAssetsMicros, 2_150 * unit) // 750 + 800 + 600
        XCTAssertEqual(store.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_400 * unit)

        // 7. 本地持久化重载与 .klbk 备份还原（同时验证 restoreFromSnapshot 使缓存失效）
        let reloaded = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        XCTAssertEqual(reloaded.debts.first(where: { $0.id == lendCny.id })?.includeInNetWorth, true)
        XCTAssertEqual(reloaded.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 2_150 * unit)
        XCTAssertEqual(reloaded.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_400 * unit)

        let backupData = try reloaded.exportEncryptedBackup(passphrase: "debt-networth-key")
        let dir2 = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let restoredStore = LedgerStore(storageDirectory: dir2, useKeychain: false, timeZone: tz)
        // 先预热空库缓存，确保导入还原能正确清空旧缓存
        XCTAssertEqual(restoredStore.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 0)
        _ = try restoredStore.importEncryptedBackup(backupData, passphrase: "debt-networth-key")
        XCTAssertEqual(restoredStore.debts.first(where: { $0.id == lendCny.id })?.includeInNetWorth, true)
        XCTAssertEqual(restoredStore.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 2_150 * unit)
        XCTAssertEqual(restoredStore.computeIncludedDebtReceivableMicros(displayCurrency: "CNY"), 1_400 * unit)
    }

    @MainActor
    func testTransactionDiscountAndFeeTransferExpenseIncomeAndBackwardCompat() throws {
        // 1. 验证缺少 discountAmountMicros / feeAmountMicros 的旧版 TransactionDto JSON 无损解码且默认为 0
        let legacyTxJson = """
        {
          "id": 3001,
          "type": "EXPENSE",
          "timestamp": 1772352000000,
          "origAmountMicros": 500000,
          "origCurrency": "CNY",
          "accountId": 1,
          "accountAmountMicros": 500000,
          "accountCurrency": "CNY",
          "merchant": "旧版账单"
        }
        """
        let decodedLegacy = try JSONDecoder().decode(TransactionDto.self, from: Data(legacyTxJson.utf8))
        XCTAssertEqual(decodedLegacy.id, 3001)
        XCTAssertEqual(decodedLegacy.origAmountMicros, 500_000)
        XCTAssertEqual(decodedLegacy.discountAmountMicros, 0)
        XCTAssertEqual(decodedLegacy.feeAmountMicros, 0)

        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        let unit = MoneyAmount.amountScale

        let debitId = try XCTUnwrap(store.accounts.first(where: { $0.name == "银行储蓄卡" })?.id)
        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let usdId = try XCTUnwrap(store.accounts.first(where: { $0.currencyCode == "USD" })?.id)
        let diningCat = try XCTUnwrap(store.categories.first(where: { $0.name == "餐饮美食" && $0.type == TransactionType.EXPENSE }))

        // 预设基线余额：银行储蓄卡 1000 元，微信零钱 500 元，净资产 1500 元
        _ = try store.calibrateAccountBalance(accountId: debitId, targetBalanceMicros: 1_000 * unit, createRecord: false)
        _ = try store.calibrateAccountBalance(accountId: wechatId, targetBalanceMicros: 500 * unit, createRecord: false)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 1_500 * unit)

        let baseTs: Int64 = 1_772_352_000_000 // 2026-03-01 16:00:00 +08:00

        // 2. 转账充值 50 优惠 2：实扣 48、目标到账 50、净资产 +2
        let rechargeTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: baseTs,
                origAmountMicros: 48 * unit,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: wechatId,
                merchant: "零钱充值立减",
                discountAmountMicros: 2 * unit,
                feeAmountMicros: 0
            )
        )
        XCTAssertEqual(rechargeTx.accountAmountMicros, 48 * unit)
        XCTAssertEqual(rechargeTx.toAmountMicros, 50 * unit)
        XCTAssertEqual(rechargeTx.discountAmountMicros, 2 * unit)
        XCTAssertEqual(rechargeTx.feeAmountMicros, 0)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 952 * unit)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 550 * unit)
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 1_502 * unit)

        // 3. 转账提现 100 手续费 0.10：源实扣 100.10、目标到账 100、净资产 -0.10
        let withdrawTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: baseTs + 10_000,
                origAmountMicros: 1_001_000, // 100.10
                origCurrency: "CNY",
                accountId: wechatId,
                toAccountId: debitId,
                merchant: "零钱提现",
                discountAmountMicros: 0,
                feeAmountMicros: 1_000 // 0.10
            )
        )
        XCTAssertEqual(withdrawTx.accountAmountMicros, 1_001_000)
        XCTAssertEqual(withdrawTx.toAmountMicros, 1_000_000)
        XCTAssertEqual(withdrawTx.discountAmountMicros, 0)
        XCTAssertEqual(withdrawTx.feeAmountMicros, 1_000)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 4_499_000) // 449.90
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 10_520_000) // 1052.00
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 15_019_000) // 1501.90

        // 4. 满 50 减 50 零元免单转账：源 -0、目标 +50、净资产 +50，且 0 元换算不除零
        let zeroPreview = store.previewConversion(
            origAmountMicros: 0,
            origCurrency: "USD",
            accountId: debitId
        )
        XCTAssertEqual(zeroPreview.accountAmountMicros, 0)
        XCTAssertEqual(zeroPreview.baseAmountMicros, 0)

        let freeTransferTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.TRANSFER,
                timestamp: baseTs + 20_000,
                origAmountMicros: 0,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: wechatId,
                merchant: "满50减50免单充值",
                discountAmountMicros: 50 * unit,
                feeAmountMicros: 0
            )
        )
        XCTAssertEqual(freeTransferTx.origAmountMicros, 0)
        XCTAssertEqual(freeTransferTx.accountAmountMicros, 0)
        XCTAssertEqual(freeTransferTx.toAmountMicros, 50 * unit)
        XCTAssertEqual(freeTransferTx.discountAmountMicros, 50 * unit)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 10_520_000)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 4_999_000) // 499.90
        XCTAssertEqual(store.computeNetWorth(displayCurrency: "CNY").netWorthMicros, 15_519_000) // 1551.90

        // 5. 支出 50 优惠 2：账户 -48、统计支出 48、computeDiscountFeeSummary 累计优惠 2（1 笔）
        let expenseTs = baseTs + 30_000
        let expenseTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: expenseTs,
                origAmountMicros: 48 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                categoryId: diningCat.id,
                merchant: "外卖满减",
                discountAmountMicros: 2 * unit,
                feeAmountMicros: 0
            )
        )
        XCTAssertEqual(expenseTx.discountAmountMicros, 2 * unit)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, 4_519_000) // 451.90

        let expenseStats = store.computeCategoryStats(
            startMillis: expenseTs - 1_000,
            endMillis: expenseTs + 1_000,
            type: TransactionType.EXPENSE,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(expenseStats.count, 1)
        XCTAssertEqual(expenseStats[0].totalBaseMicros, 48 * unit)

        let expenseDiscountSummary = store.computeDiscountFeeSummary(
            startMillis: expenseTs - 1_000,
            endMillis: expenseTs + 1_000,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(expenseDiscountSummary.discountBaseMicros, 2 * unit)
        XCTAssertEqual(expenseDiscountSummary.discountCount, 1)
        XCTAssertEqual(expenseDiscountSummary.feeBaseMicros, 0)
        XCTAssertEqual(expenseDiscountSummary.feeCount, 0)

        // 6. 收入 100 手续费 0.60：账户 +99.40、收入强制忽略优惠、computeDiscountFeeSummary 累计手续费 0.60（1 笔）
        let incomeTs = baseTs + 40_000
        let incomeTx = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: incomeTs,
                origAmountMicros: 994_000, // 99.40
                origCurrency: "CNY",
                accountId: debitId,
                merchant: "收款结算",
                discountAmountMicros: 5 * unit, // INCOME 必须清零
                feeAmountMicros: 6_000 // 0.60
            )
        )
        XCTAssertEqual(incomeTx.discountAmountMicros, 0)
        XCTAssertEqual(incomeTx.feeAmountMicros, 6_000)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, 11_514_000) // 1151.40

        let incomeFeeSummary = store.computeDiscountFeeSummary(
            startMillis: incomeTs - 1_000,
            endMillis: incomeTs + 1_000,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(incomeFeeSummary.discountBaseMicros, 0)
        XCTAssertEqual(incomeFeeSummary.discountCount, 0)
        XCTAssertEqual(incomeFeeSummary.feeBaseMicros, 6_000)
        XCTAssertEqual(incomeFeeSummary.feeCount, 1)

        // 全区间累计汇总：优惠 = 2 + 50 + 2 = 54 (3 笔)，手续费 = 0.10 + 0.60 = 0.70 (2 笔)
        let fullSummary = store.computeDiscountFeeSummary(
            startMillis: baseTs - 1_000,
            endMillis: incomeTs + 1_000,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(fullSummary.discountBaseMicros, 54 * unit)
        XCTAssertEqual(fullSummary.discountCount, 3)
        XCTAssertEqual(fullSummary.feeBaseMicros, 7_000)
        XCTAssertEqual(fullSummary.feeCount, 2)

        // 7. 编辑修改优惠额与删除回滚余额精确复原
        let debitBeforeEdit = try XCTUnwrap(store.account(byId: debitId)?.balanceMicros)
        let wechatBeforeEdit = try XCTUnwrap(store.account(byId: wechatId)?.balanceMicros)

        // 将充值 50 优惠 2（orig=48, discount=2）编辑为充值 50 优惠 5（orig=45, discount=5）
        let editedRecharge = try store.saveTransaction(
            RecordTransactionRequest(
                existingId: rechargeTx.id,
                type: TransactionType.TRANSFER,
                timestamp: baseTs,
                origAmountMicros: 45 * unit,
                origCurrency: "CNY",
                accountId: debitId,
                toAccountId: wechatId,
                merchant: "零钱充值立减（更正）",
                discountAmountMicros: 5 * unit,
                feeAmountMicros: 0
            )
        )
        XCTAssertEqual(editedRecharge.accountAmountMicros, 45 * unit)
        XCTAssertEqual(editedRecharge.toAmountMicros, 50 * unit)
        XCTAssertEqual(editedRecharge.discountAmountMicros, 5 * unit)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, debitBeforeEdit + 3 * unit)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, wechatBeforeEdit)

        // 删除该笔编辑后的充值：源账户回加 45、目标账户回扣 50（净相对最初基线完全抵消该笔交易）
        try store.deleteTransaction(id: editedRecharge.id)
        XCTAssertEqual(store.account(byId: debitId)?.balanceMicros, debitBeforeEdit + 48 * unit)
        XCTAssertEqual(store.account(byId: wechatId)?.balanceMicros, wechatBeforeEdit - 50 * unit)

        // 验证 duplicateTransaction 透传 discountAmountMicros / feeAmountMicros
        let duplicatedFree = try store.duplicateTransaction(id: freeTransferTx.id)
        XCTAssertEqual(duplicatedFree.origAmountMicros, 0)
        XCTAssertEqual(duplicatedFree.discountAmountMicros, 50 * unit)
        XCTAssertEqual(duplicatedFree.toAmountMicros, 50 * unit)
        try store.deleteTransaction(id: duplicatedFree.id)

        // 验证跨币种优惠/手续费折算汇总与参数校验
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 7_000_000)
        let usdTs = incomeTs + 20_000
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: usdTs,
                origAmountMicros: 9 * unit,
                origCurrency: "USD",
                accountId: usdId,
                discountAmountMicros: 1 * unit,
                feeAmountMicros: 0
            )
        )
        let usdSummary = store.computeDiscountFeeSummary(
            startMillis: usdTs - 1_000,
            endMillis: usdTs + 1_000,
            displayCurrency: "CNY"
        )
        XCTAssertEqual(usdSummary.discountBaseMicros, 7 * unit)
        XCTAssertEqual(usdSummary.discountCount, 1)

        // 负数优惠/手续费及 0 元无优惠支出必须抛出 nonPositiveAmount
        XCTAssertThrowsError(
            try store.saveTransaction(
                RecordTransactionRequest(
                    type: TransactionType.EXPENSE,
                    origAmountMicros: 10 * unit,
                    origCurrency: "CNY",
                    accountId: wechatId,
                    discountAmountMicros: -1
                )
            )
        ) { error in
            XCTAssertEqual(error as? LedgerStoreError, .nonPositiveAmount)
        }
        XCTAssertThrowsError(
            try store.saveTransaction(
                RecordTransactionRequest(
                    type: TransactionType.EXPENSE,
                    origAmountMicros: 0,
                    origCurrency: "CNY",
                    accountId: wechatId,
                    discountAmountMicros: 0,
                    feeAmountMicros: 0
                )
            )
        ) { error in
            XCTAssertEqual(error as? LedgerStoreError, .nonPositiveAmount)
        }

        // 8. 验证 JSON 编码字段名与 Android 端严格对齐，且持久化重载/加密备份往返无损保留
        let encodedTxJson = String(data: try JSONEncoder().encode(expenseTx), encoding: .utf8) ?? ""
        XCTAssertTrue(encodedTxJson.contains("\"discountAmountMicros\""))
        XCTAssertTrue(encodedTxJson.contains("\"feeAmountMicros\""))

        let backupData = try store.exportEncryptedBackup(passphrase: "discount-fee-key")
        let dir2 = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir2) }
        let restoredStore = LedgerStore(storageDirectory: dir2, useKeychain: false, timeZone: tz)
        _ = try restoredStore.importEncryptedBackup(backupData, passphrase: "discount-fee-key")
        XCTAssertEqual(restoredStore.transactions.first(where: { $0.id == expenseTx.id })?.discountAmountMicros, 2 * unit)
        XCTAssertEqual(restoredStore.transactions.first(where: { $0.id == incomeTx.id })?.feeAmountMicros, 6_000)
        XCTAssertEqual(restoredStore.transactions.first(where: { $0.id == withdrawTx.id })?.feeAmountMicros, 1_000)
    }

    @MainActor
    func testRefundAggregationAndCleanListRowNote() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = TimeZone(secondsFromGMT: 8 * 3600)!
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        let unit = MoneyAmount.amountScale

        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let usdId = try XCTUnwrap(store.accounts.first(where: { $0.currencyCode == "USD" })?.id)

        // 设定 USD -> CNY = 7.2000，用于跨币种退款折算验证
        store.saveCustomRate(from: "USD", to: "CNY", rateMicros: 7_200_000)

        let baseTs: Int64 = 1_772_352_000_000 // 2026-03-01 16:00:00 +08:00
        let nextMonthTs: Int64 = baseTs + 35 * 86_400_000

        // 1. 单笔关联退款（跨月）+ 待确认退款（应被忽略）+ 普通收入（不计入）
        let expenseSingle = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseTs,
                origAmountMicros: 100 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "网购外套"
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: nextMonthTs,
                origAmountMicros: 417_000, // 41.70 元
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "部分退款",
                status: TransactionStatus.CONFIRMED,
                refundOfTxId: expenseSingle.id
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: nextMonthTs + 1_000,
                origAmountMicros: 10 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "待确认退款",
                status: TransactionStatus.PENDING,
                refundOfTxId: expenseSingle.id
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseTs + 2_000,
                origAmountMicros: 50 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "普通收入"
            )
        )

        // 2. 多笔同币种关联退款聚合到同一原支出
        let expenseMulti = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseTs + 10_000,
                origAmountMicros: 200 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "商超采购"
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseTs + 20_000,
                origAmountMicros: 30 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                refundOfTxId: expenseMulti.id
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseTs + 30_000,
                origAmountMicros: 455_000, // 45.50 元
                origCurrency: "CNY",
                accountId: wechatId,
                refundOfTxId: expenseMulti.id
            )
        )

        // 3. 跨币种关联退款：$10.00 USD (按 7.20 折合 72.00 CNY) + ¥8.00 CNY = ¥80.00 CNY
        let expenseCross = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: baseTs + 40_000,
                origAmountMicros: 144 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "跨境直邮"
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseTs + 50_000,
                origAmountMicros: 10 * unit,
                origCurrency: "USD",
                accountId: usdId,
                refundOfTxId: expenseCross.id
            )
        )
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: baseTs + 60_000,
                origAmountMicros: 8 * unit,
                origCurrency: "CNY",
                accountId: wechatId,
                refundOfTxId: expenseCross.id
            )
        )

        let refundedMap = store.computeRefundedAmountsByOrigTxId()
        XCTAssertEqual(refundedMap.count, 3)
        XCTAssertEqual(refundedMap[expenseSingle.id], 417_000)
        XCTAssertEqual(refundedMap[expenseMulti.id], 755_000)
        XCTAssertEqual(refundedMap[expenseCross.id], 80 * unit)

        // 4. YubraeTheme.cleanListRowNote：过滤自动抓取废话与导入结构化元数据，保留真实备注
        XCTAssertEqual(YubraeTheme.cleanListRowNote(""), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("   "), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("/"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("刷卡完成，交易金额：1.80元"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("充值金额: 50.00元 · 卡内余额: 66.35元"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("支付成功，交易金额：12.50元"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("扣款成功，交易金额：1,280.50元，账户余额：2,000.00元"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("付款成功！消费金额：1,280.50元，账户余额：3,400.00元。"), "")
        XCTAssertEqual(
            YubraeTheme.cleanListRowNote("地址: 北京市朝阳区 · 优惠: 2.00元 · 报销明细: 差旅 · 退款明细: 部分退 · 交易单号: 202611270001"),
            ""
        )
        XCTAssertEqual(YubraeTheme.cleanListRowNote("优惠 2.00元"), "")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("信用卡还款"), "信用卡还款")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("午饭"), "午饭")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("六婆辣椒面香辣蘸料"), "六婆辣椒面香辣蘸料")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("1,280元团建餐"), "1,280元团建餐")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("超好吃！强烈推荐！"), "超好吃！强烈推荐！")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("重庆畅通卡 · 刷卡完成，交易金额：1.80元"), "重庆畅通卡")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("买咖啡，支付成功，交易金额：18.00元"), "买咖啡")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("买奶茶！支付成功！消费金额：18.00元"), "买奶茶")
        XCTAssertEqual(YubraeTheme.cleanListRowNote("优惠: 2.00元 · 午饭打包 · 卡内余额: 66.35元"), "午饭打包")

        // 5. YubraeTheme.formatPlainSignedAmount：全币种（含主币种）保留清晰货币符号，字母代码币种后置代码不重复
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-18_000, currencyCode: "CNY", baseCurrency: "CNY"),
            "-¥1.80"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(1_060_000, currencyCode: "cny", baseCurrency: "CNY"),
            "+¥106.00"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(500_000, currencyCode: "CNY", baseCurrency: "CNY", showSign: false),
            "¥50.00"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(12_805_000, currencyCode: "CNY", baseCurrency: "CNY", showSign: false),
            "¥1,280.50"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(0, currencyCode: "CNY", baseCurrency: "CNY", showSign: true),
            "¥0.00"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-531_600, currencyCode: "USD", baseCurrency: "CNY"),
            "-$53.16"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(120_000, currencyCode: "USD", baseCurrency: "CNY"),
            "+$12.00"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(120_000, currencyCode: "USD", baseCurrency: "CNY", showSign: false),
            "$12.00"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-12_000_000, currencyCode: "JPY", baseCurrency: "CNY"),
            "-JP¥1,200"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-12_000_000, currencyCode: "JPY", baseCurrency: "JPY"),
            "-JP¥1,200"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-120_000, currencyCode: "CHF", baseCurrency: "CNY"),
            "-12.00 CHF"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(-120_000, currencyCode: "CHF", baseCurrency: "CHF"),
            "-12.00 CHF"
        )
        XCTAssertEqual(
            YubraeTheme.formatPlainSignedAmount(500_000, currencyCode: "THB", baseCurrency: "CNY"),
            "+50.00 THB"
        )
        XCTAssertEqual(
            "-\(YubraeTheme.formatPlainSignedAmount(0, currencyCode: "CNY", baseCurrency: "CNY", showSign: false))",
            "-¥0.00"
        )

        // 6. YubraeTheme.formatDayHeaderParts：今天 / 昨天 / 同年周几 / 跨年日期
        var cal = Calendar(identifier: .gregorian)
        cal.timeZone = tz
        let refDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 11, day: 27, hour: 12)))
        let todayDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 11, day: 27, hour: 8, minute: 30)))
        let yesterdayDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 11, day: 26, hour: 20)))
        let wedDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2026, month: 11, day: 25, hour: 14)))
        let prevYearDate = try XCTUnwrap(cal.date(from: DateComponents(year: 2025, month: 11, day: 25, hour: 14)))

        let todayParts = YubraeTheme.formatDayHeaderParts(
            Int64((todayDate.timeIntervalSince1970 * 1000.0).rounded()),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(todayParts.dateText, "11月27日")
        XCTAssertEqual(todayParts.subText, "今天")

        let yesterdayParts = YubraeTheme.formatDayHeaderParts(
            Int64((yesterdayDate.timeIntervalSince1970 * 1000.0).rounded()),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(yesterdayParts.dateText, "11月26日")
        XCTAssertEqual(yesterdayParts.subText, "昨天")

        let wedParts = YubraeTheme.formatDayHeaderParts(
            Int64((wedDate.timeIntervalSince1970 * 1000.0).rounded()),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(wedParts.dateText, "11月25日")
        XCTAssertEqual(wedParts.subText, "周三")

        let prevYearParts = YubraeTheme.formatDayHeaderParts(
            Int64((prevYearDate.timeIntervalSince1970 * 1000.0).rounded()),
            referenceDate: refDate,
            calendar: cal
        )
        XCTAssertEqual(prevYearParts.dateText, "2025年11月25日")
        XCTAssertEqual(prevYearParts.subText, "周二")

        let liveTodayParts = YubraeTheme.formatDayHeaderParts(currentEpochMillis())
        XCTAssertEqual(liveTodayParts.subText, "今天")
        XCTAssertFalse(liveTodayParts.dateText.isEmpty)
    }

    @MainActor
    func testWeeklyTrendBucketsSundayToSaturday() throws {
        let dir = makeTempDir()
        defer { try? FileManager.default.removeItem(at: dir) }
        let tz = try XCTUnwrap(TimeZone(secondsFromGMT: 8 * 3600))
        let store = LedgerStore(storageDirectory: dir, useKeychain: false, timeZone: tz)
        let wechatId = try XCTUnwrap(store.accounts.first(where: { $0.name == "微信零钱" })?.id)
        let cal = YubraePeriodHelper.sundayCalendar(timeZone: tz)

        func millis(_ year: Int, _ month: Int, _ day: Int, _ hour: Int = 12) throws -> Int64 {
            let date = try XCTUnwrap(cal.date(from: DateComponents(year: year, month: month, day: day, hour: hour)))
            return Int64((date.timeIntervalSince1970 * 1000.0).rounded())
        }

        // 2026-11-22 (周日) 支出 ¥10.00 -> 应落入 2026-11-25 所在周的第 0 桶「周日」
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 11, 22, 10),
                origAmountMicros: 100_000,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "周日早餐"
            )
        )
        // 2026-11-25 (周三) 收入 ¥50.00 -> 应落入第 3 桶「周三」
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.INCOME,
                timestamp: try millis(2026, 11, 25, 14),
                origAmountMicros: 500_000,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "周三兼职"
            )
        )
        // 2026-11-28 (周六) 支出 ¥25.00 -> 应落入第 6 桶「周六」
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 11, 28, 22),
                origAmountMicros: 250_000,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "周六聚餐"
            )
        )
        // 2026-11-29 (下周日) 支出 ¥88.00 -> 属于下一自然周，不应混入 11/22~11/28 周
        _ = try store.saveTransaction(
            RecordTransactionRequest(
                type: TransactionType.EXPENSE,
                timestamp: try millis(2026, 11, 29, 9),
                origAmountMicros: 880_000,
                origCurrency: "CNY",
                accountId: wechatId,
                merchant: "下周日采购"
            )
        )

        let buckets = store.computeTrendBuckets(
            granularity: StatsGranularity.WEEKLY,
            anchorDateMillis: try millis(2026, 11, 25, 12),
            displayCurrency: "CNY"
        )
        XCTAssertEqual(buckets.map(\.label), ["周日", "周一", "周二", "周三", "周四", "周五", "周六"])
        XCTAssertEqual(buckets[0].expenseBaseMicros, 100_000)
        XCTAssertEqual(buckets[3].incomeBaseMicros, 500_000)
        XCTAssertEqual(buckets[6].expenseBaseMicros, 250_000)
        XCTAssertEqual(buckets.reduce(0) { $0 + $1.expenseBaseMicros }, 350_000)

        // 锚定下周日 2026-11-29，验证新一周从周日重新开始
        let nextWeekBuckets = store.computeTrendBuckets(
            granularity: StatsGranularity.WEEKLY,
            anchorDateMillis: try millis(2026, 11, 29, 12),
            displayCurrency: "CNY"
        )
        XCTAssertEqual(nextWeekBuckets.map(\.label), ["周日", "周一", "周二", "周三", "周四", "周五", "周六"])
        XCTAssertEqual(nextWeekBuckets[0].expenseBaseMicros, 880_000)
        XCTAssertEqual(nextWeekBuckets.reduce(0) { $0 + $1.expenseBaseMicros }, 880_000)
    }
}
