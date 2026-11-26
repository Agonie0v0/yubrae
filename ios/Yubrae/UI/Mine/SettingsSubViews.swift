import AppIntents
import LocalAuthentication
import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - 1. Multi-Ledger Management

public struct LedgerManagementView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme
    @State private var showAddSheet = false
    @State private var newLedgerName = ""
    @State private var newLedgerCurrency = CurrencyCatalog.defaultBaseCurrency
    @State private var deletingLedger: LedgerDto?
    @State private var migrateTargetId: Int64 = 0

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    public var body: some View {
        List {
            Section("活跃账本（点击切换当前账本）") {
                ForEach(store.activeLedgers) { ledger in
                    let isActive = store.activeLedger?.id == ledger.id
                    let txCount = store.transactions(forLedgerId: ledger.id).count
                    HStack {
                        YubraeCategoryIconBadge(iconKey: ledger.icon, colorHex: ledger.colorHex, size: 36)
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 6) {
                                Text(ledger.name)
                                    .font(.subheadline.weight(.semibold))
                                if isActive {
                                    Text("当前使用")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(.green)
                                }
                            }
                            Text("\(ledger.currencyCode) · \(txCount) 笔账单")
                                .font(.caption)
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                        if isActive {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(.accentColor)
                        }
                    }
                    .contentShape(Rectangle())
                    .onTapGesture {
                        store.setActiveLedger(id: ledger.id)
                        store.toastMessage = "已切换到「\(ledger.name)」"
                    }
                    .swipeActions(edge: .trailing) {
                        if store.activeLedgers.count > 1 {
                            Button("删除", role: .destructive) {
                                migrateTargetId = store.activeLedgers.first(where: { $0.id != ledger.id })?.id ?? 0
                                deletingLedger = ledger
                            }
                            Button("归档") {
                                store.archiveLedger(id: ledger.id, archived: true)
                                store.toastMessage = "已归档账本「\(ledger.name)」"
                            }
                            .tint(.orange)
                        }
                    }
                }
            }

            let archived = store.ledgers.filter(\.archived)
            if !archived.isEmpty {
                Section("已归档账本") {
                    ForEach(archived) { ledger in
                        HStack {
                            Text(ledger.name).foregroundColor(.secondary)
                            Spacer()
                            Button("恢复") {
                                store.archiveLedger(id: ledger.id, archived: false)
                                store.toastMessage = "已恢复账本「\(ledger.name)」"
                            }
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }
        }
        .navigationTitle("多账本管理")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    newLedgerName = ""
                    newLedgerCurrency = store.settings.baseCurrency
                    showAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                Form {
                    TextField("账本名称（如 家庭账本 / 日本旅行）", text: $newLedgerName)
                    Picker("默认币种", selection: $newLedgerCurrency) {
                        ForEach(CurrencyCatalog.all) { info in
                            Text("\(info.flag) \(info.code) · \(info.nameZh)").tag(info.code)
                        }
                    }
                }
                .navigationTitle("新建账本")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { showAddSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("创建") {
                            let name = newLedgerName.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !name.isEmpty else { return }
                            let saved = store.saveLedger(LedgerDto(name: name, currencyCode: newLedgerCurrency, sortOrder: store.ledgers.count * 10))
                            store.setActiveLedger(id: saved.id)
                            store.toastMessage = "已创建并切换到「\(name)」"
                            showAddSheet = false
                        }
                        .disabled(newLedgerName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            .presentationDetents([.medium])
            .yubraeSheetStyle(palette: palette)
        }
        .sheet(item: $deletingLedger) { ledger in
            NavigationStack {
                Form {
                    Section(footer: Text("可以选择将该账本下所有账单安全迁移至另一个活跃账本，或直接连同账单一起删除并回滚余额。")) {
                        Picker("迁移目标账本", selection: $migrateTargetId) {
                            Text("不迁移（连同账单删除）").tag(Int64(0))
                            ForEach(store.activeLedgers.filter { $0.id != ledger.id }) { target in
                                Text("迁移至「\(target.name)」").tag(target.id)
                            }
                        }
                    }
                    Section {
                        Button("确认删除「\(ledger.name)」", role: .destructive) {
                            do {
                                try store.deleteLedger(id: ledger.id, migrateToLedgerId: migrateTargetId > 0 ? migrateTargetId : nil)
                                store.toastMessage = "已删除账本「\(ledger.name)」"
                                deletingLedger = nil
                            } catch {
                                store.lastErrorMessage = error.localizedDescription
                            }
                        }
                    }
                }
                .navigationTitle("删除账本")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { deletingLedger = nil }
                    }
                }
            }
            .presentationDetents([.medium])
            .yubraeSheetStyle(palette: palette)
        }
    }
}

// MARK: - 2. Category Management

public struct CategoryManagementView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme
    @State private var selectedType: String = TransactionType.EXPENSE
    @State private var showAddSheet = false
    @State private var newName = ""
    @State private var parentId: Int64 = 0
    @State private var iconKey = "category"
    @State private var colorHex: Int64 = 0xFF2AA876

    private let iconChoices = ["restaurant", "local_cafe", "directions_car", "shopping_bag", "home", "subscriptions", "medical_services", "flight", "school", "favorite", "pets", "work", "payments", "trending_up", "category"]

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    public var body: some View {
        List {
            Section {
                Picker("分类类型", selection: $selectedType) {
                    Text("支出分类").tag(TransactionType.EXPENSE)
                    Text("收入分类").tag(TransactionType.INCOME)
                }
                .pickerStyle(.segmented)
            }

            ForEach(store.parentCategories(for: selectedType)) { parent in
                Section {
                    HStack {
                        YubraeCategoryIconBadge(iconKey: parent.icon, colorHex: parent.colorHex, size: 32)
                        Text(parent.name)
                            .font(.subheadline.weight(.semibold))
                        Spacer()
                        if !parent.isSystem {
                            Button(role: .destructive) {
                                store.deleteCategory(id: parent.id)
                                store.toastMessage = "已删除分类「\(parent.name)」"
                            } label: {
                                Image(systemName: "trash")
                                    .font(.caption)
                            }
                            .buttonStyle(.borderless)
                        }
                    }

                    let children = store.childCategories(of: parent.id)
                    ForEach(children) { child in
                        HStack {
                            Spacer().frame(width: 22)
                            Image(systemName: DefaultSeedData.sfSymbol(for: child.icon))
                                .foregroundColor(Color(argbHex: child.colorHex))
                                .frame(width: 20)
                            Text(child.name)
                                .font(.subheadline)
                            Spacer()
                            if !child.isSystem {
                                Button(role: .destructive) {
                                    store.deleteCategory(id: child.id)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.caption)
                                }
                                .buttonStyle(.borderless)
                            }
                        }
                    }
                }
            }

            Section {
                Button {
                    let result = store.ensurePresetCatalogSeeded()
                    store.toastMessage = "已补齐 \(result.addedCategories) 个预置分类与 \(result.addedTags) 个预置标签"
                } label: {
                    Label("一键补齐缺失的官方预置分类与标签", systemImage: "arrow.triangle.2.circlepath")
                }
            }
        }
        .navigationTitle("分类与图标管理")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    newName = ""
                    parentId = 0
                    showAddSheet = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAddSheet) {
            NavigationStack {
                Form {
                    TextField("分类名称", text: $newName)
                    Picker("所属一级大类", selection: $parentId) {
                        Text("作为一级大类").tag(Int64(0))
                        ForEach(store.parentCategories(for: selectedType)) { p in
                            Text("归属于「\(p.name)」").tag(p.id)
                        }
                    }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 10) {
                            ForEach(iconChoices, id: \.self) { key in
                                Button {
                                    iconKey = key
                                } label: {
                                    YubraeCategoryIconBadge(iconKey: key, colorHex: colorHex, size: 34)
                                        .overlay(Circle().strokeBorder(iconKey == key ? Color(argbHex: colorHex) : .clear, lineWidth: 2))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.vertical, 4)
                    }
                }
                .navigationTitle("新增\(selectedType == TransactionType.INCOME ? "收入" : "支出")分类")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { showAddSheet = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            let clean = newName.trimmingCharacters(in: .whitespacesAndNewlines)
                            guard !clean.isEmpty else { return }
                            let resolvedColor = parentId > 0 ? (store.category(byId: parentId)?.colorHex ?? colorHex) : colorHex
                            _ = store.saveCategory(
                                CategoryDto(
                                    parentId: parentId > 0 ? parentId : nil,
                                    name: clean,
                                    type: selectedType,
                                    icon: iconKey,
                                    colorHex: resolvedColor,
                                    sortOrder: store.categories.count * 5,
                                    isSystem: false
                                )
                            )
                            store.toastMessage = "已添加分类「\(clean)」"
                            showAddSheet = false
                        }
                        .disabled(newName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
            .presentationDetents([.medium])
            .yubraeSheetStyle(palette: palette)
        }
    }
}

// MARK: - 3. Tag Management

private struct TagGroupSection: Identifiable {
    var id: String { group }
    let group: String
    let items: [TagDto]
}

public struct TagManagementView: View {
    @ObservedObject public var store: LedgerStore
    @State private var newTagName = ""
    @State private var newTagGroup = "常用"

    private var groupedTags: [TagGroupSection] {
        var order: [String] = []
        var map: [String: [TagDto]] = [:]
        for tag in store.tags {
            if map[tag.groupName] == nil { order.append(tag.groupName) }
            map[tag.groupName, default: []].append(tag)
        }
        return order.map { TagGroupSection(group: $0, items: map[$0] ?? []) }
    }

    public init(store: LedgerStore) {
        self.store = store
    }

    public var body: some View {
        List {
            Section("快速新建标签") {
                HStack {
                    TextField("标签名称（如 健身 / 出差）", text: $newTagName)
                    TextField("分组", text: $newTagGroup)
                        .frame(width: 72)
                        .multilineTextAlignment(.trailing)
                    Button("添加") {
                        let clean = newTagName.trimmingCharacters(in: .whitespacesAndNewlines)
                        guard !clean.isEmpty else { return }
                        _ = store.saveTag(TagDto(name: clean, groupName: newTagGroup, isSystem: false))
                        store.toastMessage = "已添加标签 #\(clean)"
                        newTagName = ""
                    }
                    .buttonStyle(.borderless)
                    .disabled(newTagName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }

            ForEach(groupedTags) { section in
                Section(section.group) {
                    ForEach(section.items) { tag in
                        HStack {
                            Circle()
                                .fill(Color(argbHex: tag.colorHex))
                                .frame(width: 10, height: 10)
                            Text("#\(tag.name)")
                                .font(.subheadline)
                            Spacer()
                            if tag.isSystem {
                                Text("预置")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        .swipeActions(edge: .trailing) {
                            Button("删除", role: .destructive) {
                                store.deleteTag(id: tag.id)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("标签库管理")
    }
}

// MARK: - 4. Budgets & Recurring Rules

public struct BudgetAndRecurringView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme
    @State private var showAddRule = false
    @State private var ruleName = ""
    @State private var ruleAmount = ""
    @State private var ruleType = TransactionType.EXPENSE
    @State private var ruleAccountId: Int64 = 0
    @State private var ruleCycle = "MONTHLY"
    @State private var ruleDay = 1

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    public var body: some View {
        List {
            Section("预算规则（\(store.budgets.count)）") {
                if store.budgets.isEmpty {
                    Text("暂无预算，可在「统计」页快速添加月度/年度预算。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    ForEach(store.budgets) { b in
                        HStack {
                            VStack(alignment: .leading) {
                                Text(b.name).font(.subheadline.weight(.semibold))
                                Text("\(b.period == "YEARLY" ? "年度" : "月度") · \(MoneyAmount.format(b.limitAmountMicros, currencyCode: b.currencyCode))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Button("删除", role: .destructive) {
                                store.deleteBudget(id: b.id)
                            }
                            .font(.caption)
                            .buttonStyle(.borderless)
                        }
                    }
                }
            }

            Section("周期自动记账规则（\(store.recurringRules.count)）") {
                if store.recurringRules.isEmpty {
                    Text("可添加房租、工资、会员订阅等周期记账，App 打开时自动检查入账。")
                        .font(.caption)
                        .foregroundColor(.secondary)
                } else {
                    ForEach(store.recurringRules) { rule in
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(rule.name).font(.subheadline.weight(.semibold))
                                Text("\(rule.cycleType) · 下次执行：\(YubraeTheme.formatDate(rule.nextRunEpochMillis, pattern: "yyyy-MM-dd"))")
                                    .font(.caption)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Text(MoneyAmount.format(rule.amountMicros, currencyCode: rule.currencyCode))
                                .font(.subheadline.monospacedDigit())
                        }
                        .swipeActions {
                            Button("删除", role: .destructive) {
                                store.deleteRecurringRule(id: rule.id)
                            }
                        }
                    }
                }

                Button("立即检查并执行到期周期规则") {
                    let count = store.processDueRecurringRules()
                    store.toastMessage = count > 0 ? "已自动入账 \(count) 笔周期账单" : "当前暂无到期的周期规则"
                }
            }
        }
        .navigationTitle("预算与周期记账")
        .toolbar {
            ToolbarItem(placement: .navigationBarTrailing) {
                Button {
                    ruleName = ""
                    ruleAmount = ""
                    ruleAccountId = store.activeAccounts.first?.id ?? 0
                    showAddRule = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showAddRule) {
            NavigationStack {
                Form {
                    TextField("规则名称（如 房租 / iCloud+ 订阅）", text: $ruleName)
                    Picker("类型", selection: $ruleType) {
                        Text("支出").tag(TransactionType.EXPENSE)
                        Text("收入").tag(TransactionType.INCOME)
                    }
                    .pickerStyle(.segmented)
                    TextField("金额", text: $ruleAmount)
                        .keyboardType(.decimalPad)
                    Picker("结算账户", selection: $ruleAccountId) {
                        ForEach(store.activeAccounts) { acc in
                            Text(acc.name).tag(acc.id)
                        }
                    }
                    Picker("周期", selection: $ruleCycle) {
                        Text("每月").tag("MONTHLY")
                        Text("每周").tag("WEEKLY")
                        Text("每天").tag("DAILY")
                        Text("每年").tag("YEARLY")
                    }
                    Stepper("扣款日：每月第 \(ruleDay) 日", value: $ruleDay, in: 1...28)
                }
                .navigationTitle("新建周期记账")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { showAddRule = false }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            guard let micros = MoneyAmount.parseToMicros(ruleAmount), micros > 0, ruleAccountId > 0 else { return }
                            let accCur = store.account(byId: ruleAccountId)?.currencyCode ?? store.settings.baseCurrency
                            let dto = RecurringRuleDto(
                                name: ruleName.trimmingCharacters(in: .whitespacesAndNewlines),
                                type: ruleType,
                                amountMicros: micros,
                                currencyCode: accCur,
                                accountId: ruleAccountId,
                                merchant: ruleName.trimmingCharacters(in: .whitespacesAndNewlines),
                                cycleType: ruleCycle,
                                dayOfCycle: ruleDay,
                                nextRunEpochMillis: currentEpochMillis() + 86_400_000,
                                enabled: true
                            )
                            _ = store.saveRecurringRule(dto)
                            store.toastMessage = "已添加周期记账规则"
                            showAddRule = false
                        }
                        .disabled(ruleName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || (MoneyAmount.parseToMicros(ruleAmount) ?? 0) <= 0)
                    }
                }
            }
            .presentationDetents([.medium])
            .yubraeSheetStyle(palette: palette)
        }
    }
}

// MARK: - 5. iOS Shortcuts Automation Guide & URL Scheme Tester

@MainActor
enum ShortcutsDeepLinkHelper {
    static func openShortcuts(createNew: Bool, onFallbackMessage: ((String) -> Void)? = nil) {
        let primaryUrl = URL(string: createNew ? "shortcuts://create-shortcut" : "shortcuts://")!
        let homeUrl = URL(string: "shortcuts://")!
        let appStoreUrl = URL(string: "https://apps.apple.com/app/id1462947752")!
        let app = UIApplication.shared
        if app.canOpenURL(primaryUrl) {
            app.open(primaryUrl)
        } else if createNew && app.canOpenURL(homeUrl) {
            app.open(homeUrl)
        } else if app.canOpenURL(appStoreUrl) {
            onFallbackMessage?("未检测到「快捷指令」App，正在前往 App Store")
            app.open(appStoreUrl)
        } else {
            onFallbackMessage?("当前环境无法打开「快捷指令」App，请从主屏幕进入")
        }
    }
}

public struct ShortcutsAutomationGuideView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.openURL) private var openURL
    @Environment(\.colorScheme) private var colorScheme

    @State private var testAmount = "28.50"
    @State private var testType = TransactionType.EXPENSE
    @State private var testMerchant = "瑞幸咖啡"
    @State private var testCategory = "咖啡茶饮"
    @State private var testAccount = "支付宝"
    @State private var testCurrency = "CNY"
    @State private var testRemark = "快捷指令测试入账"

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var generatedTestURL: URL? {
        ExternalBillUrlParser.buildURL(
            scheme: "yubrae",
            action: "addbill",
            amount: testAmount,
            type: testType,
            currency: testCurrency,
            category: testCategory,
            account: testAccount,
            merchant: testMerchant,
            remark: testRemark
        )
    }

    public var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                YubraeCard(palette: palette) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("直达 Apple「快捷指令」", systemImage: "bolt.horizontal.circle.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.primary)
                        if #available(iOS 17.0, *) {
                            ShortcutsLink()
                            Text("点击上方系统卡片可直接查看或启用「快捷记一笔」「一句话智能记账」「小票识图记账」。")
                                .font(.caption)
                                .foregroundColor(palette.textSecondary)
                        }
                        HStack(spacing: 10) {
                            Button {
                                ShortcutsDeepLinkHelper.openShortcuts(createNew: true) { msg in
                                    store.toastMessage = msg
                                }
                            } label: {
                                Label("新建快捷指令", systemImage: "plus.app.fill")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.borderedProminent)

                            Button {
                                ShortcutsDeepLinkHelper.openShortcuts(createNew: false) { msg in
                                    store.toastMessage = msg
                                }
                            } label: {
                                Label("打开快捷指令 App", systemImage: "arrow.up.forward.app")
                                    .font(.subheadline.weight(.semibold))
                                    .frame(maxWidth: .infinity)
                            }
                            .buttonStyle(.bordered)
                        }
                    }
                }

                // 方案 1：轻点背面 / 离开支付 App 截屏自动识图
                YubraeCard(palette: palette) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("方案一：轻点背面 / 支付退出自动截屏记账（推荐）", systemImage: "hand.tap.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.primary)
                        Text("余白已注册原生 iOS 16+ App Intents，无需越狱即可与苹果「快捷指令」无缝联动：")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        stepLine("1", "在余白「我的 → AI 大模型设置」开启支持视觉识图的模型（如 gpt-4o-mini / qwen-vl）。")
                        stepLine("2", "打开系统「快捷指令」App，新建快捷指令，添加动作：「拍摄截屏」→「余白小票与截图识图记账」（传入上一步截屏）。")
                        stepLine("3", "在 iPhone「设置 → 辅助功能 → 触控 → 轻点背面」将轻点两下绑定为该快捷指令；或在快捷指令「自动化」中配置「当关闭微信 / 支付宝时运行」。")
                    }
                }

                // 方案 2：Siri / 快捷指令一句话拆单
                YubraeCard(palette: palette) {
                    VStack(alignment: .leading, spacing: 10) {
                        Label("方案二：Siri 语音与原生快捷指令动作", systemImage: "waveform.circle.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.primary)
                        Text("以下 3 个原生动作已自动注册到系统「快捷指令」与 Siri：")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        intentBadgeRow(title: "余白快捷记一笔", desc: "直接传入金额、支出/收入、商户、备注与分类。")
                        intentBadgeRow(title: "余白一句话智能记账", desc: "传入语音听写或剪贴板文本，离线/AI 自动拆单入账。")
                        intentBadgeRow(title: "余白小票与截图识图记账", desc: "传入图片文件自动调用视觉大模型识图记账。")
                    }
                }

                // 方案 3：yubrae://addbill URL Scheme 协议测试台
                YubraeCard(palette: palette) {
                    VStack(alignment: .leading, spacing: 12) {
                        Label("方案三：URL Scheme 协议与实时测试", systemImage: "link.circle.fill")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.primary)
                        Text("支持 `yubrae://addbill` 与旧版 `kairoledger://addbill`，可在快捷指令「打开 URL」或外部脚本中静默入账。")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)

                        Group {
                            HStack {
                                Text("金额").font(.caption).foregroundColor(palette.textSecondary).frame(width: 48, alignment: .leading)
                                TextField("28.50", text: $testAmount)
                                    .keyboardType(.decimalPad)
                                    .font(.subheadline)
                                    .padding(8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                                Picker("类型", selection: $testType) {
                                    Text("支出").tag(TransactionType.EXPENSE)
                                    Text("收入").tag(TransactionType.INCOME)
                                }
                                .pickerStyle(.segmented)
                                .frame(width: 112)
                            }
                            HStack {
                                Text("商户").font(.caption).foregroundColor(palette.textSecondary).frame(width: 48, alignment: .leading)
                                TextField("商户名", text: $testMerchant)
                                    .font(.subheadline)
                                    .padding(8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                            }
                            HStack {
                                Text("分类").font(.caption).foregroundColor(palette.textSecondary).frame(width: 48, alignment: .leading)
                                TextField("分类名", text: $testCategory)
                                    .font(.subheadline)
                                    .padding(8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                                Text("账户").font(.caption).foregroundColor(palette.textSecondary)
                                TextField("账户名", text: $testAccount)
                                    .font(.subheadline)
                                    .padding(8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                            }
                            HStack {
                                Text("备注").font(.caption).foregroundColor(palette.textSecondary).frame(width: 48, alignment: .leading)
                                TextField("备注", text: $testRemark)
                                    .font(.subheadline)
                                    .padding(8)
                                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                            }
                        }

                        if let url = generatedTestURL {
                            Text(url.absoluteString)
                                .font(.caption2.monospaced())
                                .foregroundColor(palette.textSecondary)
                                .padding(10)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .background(RoundedRectangle(cornerRadius: 10).fill(palette.surfaceSecondary))

                            HStack(spacing: 10) {
                                Button {
                                    UIPasteboard.general.string = url.absoluteString
                                    store.toastMessage = "已复制 yubrae:// 测试链接"
                                } label: {
                                    Label("复制链接", systemImage: "doc.on.doc")
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(palette.primary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.accentSoftFill))
                                }

                                Button {
                                    openURL(url)
                                } label: {
                                    Label("实测 URL 入账", systemImage: "paperplane.fill")
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(palette.onPrimary)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 10)
                                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.primary))
                                }
                            }
                        }
                    }
                }
            }
            .padding(16)
        }
        .background(palette.background.ignoresSafeArea())
        .navigationTitle("快捷指令与自动记账")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func stepLine(_ num: String, _ text: String) -> some View {
        HStack(alignment: .top, spacing: 8) {
            Text(num)
                .font(.caption2.weight(.bold))
                .foregroundColor(palette.onPrimary)
                .frame(width: 18, height: 18)
                .background(Circle().fill(palette.primary))
            Text(text)
                .font(.caption)
                .foregroundColor(palette.textPrimary)
                .fixedSize(horizontal: false, vertical: true)
        }
    }

    private func intentBadgeRow(title: String, desc: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text("• \(title)")
                .font(.caption.weight(.semibold))
                .foregroundColor(palette.textPrimary)
            Text(desc)
                .font(.caption2)
                .foregroundColor(palette.textSecondary)
                .padding(.leading, 10)
        }
    }
}

// MARK: - 6. LLM Settings & Connection Test

public struct LlmSettingsView: View {
    @ObservedObject public var store: LedgerStore

    @State private var enabled: Bool
    @State private var baseUrl: String
    @State private var apiKey: String
    @State private var model: String
    @State private var isTesting = false
    @State private var testResultMessage: String?
    @State private var testSucceeded = false

    public init(store: LedgerStore) {
        self.store = store
        _enabled = State(initialValue: store.settings.llmEnabled)
        _baseUrl = State(initialValue: store.settings.llmBaseUrl)
        _apiKey = State(initialValue: store.settings.llmApiKey)
        _model = State(initialValue: store.settings.llmModel)
    }

    public var body: some View {
        Form {
            Section(footer: Text("兼容 OpenAI /chat/completions 协议，API Key 仅保存在本机 Keychain 中，不随备份明文同步。未启用 AI 时，一句话智能记账仍可使用本地离线解析引擎。")) {
                Toggle("启用 AI 大模型与视觉小票识图", isOn: $enabled)
            }

            Section("快捷服务商模板") {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        presetChip("OpenAI", url: "https://api.openai.com/v1", model: "gpt-4o-mini")
                        presetChip("DeepSeek", url: "https://api.deepseek.com/v1", model: "deepseek-chat")
                        presetChip("硅基流动 VL", url: "https://api.siliconflow.cn/v1", model: "Qwen/Qwen2.5-VL-72B-Instruct")
                        presetChip("Ollama 本地", url: "http://127.0.0.1:11434/v1", model: "qwen2.5:7b")
                    }
                    .padding(.vertical, 4)
                }
            }

            Section("接口参数") {
                TextField("Base URL（如 https://api.openai.com/v1）", text: $baseUrl)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.URL)
                SecureField("API Key（本地 Ollama 可留空）", text: $apiKey)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                TextField("模型名称（如 gpt-4o-mini）", text: $model)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
            }

            Section {
                Button("保存设置") {
                    saveConfig()
                    store.toastMessage = "AI 大模型配置已保存"
                }
                .fontWeight(.semibold)

                Button {
                    Task { await runConnectionTest() }
                } label: {
                    HStack {
                        if isTesting {
                            ProgressView()
                        } else {
                            Image(systemName: "network")
                        }
                        Text(isTesting ? "正在测试连通性..." : "测试大模型连通性")
                    }
                }
                .disabled(isTesting)

                if let msg = testResultMessage {
                    Text(msg)
                        .font(.caption)
                        .foregroundColor(testSucceeded ? .green : .red)
                }
            }
        }
        .navigationTitle("AI 大模型设置")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func presetChip(_ title: String, url: String, model: String) -> some View {
        Button(title) {
            self.baseUrl = url
            self.model = model
        }
        .font(.caption.weight(.semibold))
        .buttonStyle(.bordered)
    }

    private func saveConfig() {
        store.updateSettings { s in
            s.llmEnabled = enabled
            s.llmBaseUrl = baseUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            s.llmApiKey = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
            s.llmModel = model.trimmingCharacters(in: .whitespacesAndNewlines)
        }
    }

    @MainActor
    private func runConnectionTest() async {
        saveConfig()
        isTesting = true
        testResultMessage = nil
        defer { isTesting = false }
        do {
            let reply = try await YubraeRuntime.llmService.testConnection(settings: store.settings)
            testSucceeded = true
            testResultMessage = reply
        } catch {
            testSucceeded = false
            testResultMessage = "连接失败：\(error.localizedDescription)"
        }
    }
}

// MARK: - 7. Exchange Rate Center

public struct ExchangeRateCenterView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme
    @State private var isRefreshing = false
    @State private var editingCurrency: CurrencyInfo?
    @State private var customRateInput = ""

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var baseCurrency: String {
        store.settings.baseCurrency
    }

    public var body: some View {
        List {
            Section("主基准币种") {
                Picker("本位币", selection: Binding(
                    get: { store.settings.baseCurrency },
                    set: { newCode in
                        store.updateSettings { $0.baseCurrency = newCode }
                    }
                )) {
                    ForEach(CurrencyCatalog.all) { info in
                        Text("\(info.flag) \(info.code) · \(info.nameZh)").tag(info.code)
                    }
                }

                Button {
                    Task { await refreshOnlineRates() }
                } label: {
                    HStack {
                        if isRefreshing {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.triangle.2.circlepath")
                        }
                        Text(isRefreshing ? "正在从 Frankfurter / OpenER 拉取汇率..." : "联网刷新实时汇率")
                    }
                }
                .disabled(isRefreshing)

                if store.settings.lastRateSyncMillis > 0 {
                    Text("上次同步：\(YubraeTheme.formatDate(store.settings.lastRateSyncMillis, pattern: "yyyy-MM-dd HH:mm"))")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            Section("常用置顶币种（记账页优先展示）") {
                YubraeFlowLayout(spacing: 8, lineSpacing: 8) {
                    ForEach(CurrencyCatalog.all) { info in
                        let pinned = store.settings.pinnedCurrencies.contains(info.code)
                        Button {
                            togglePinned(info.code)
                        } label: {
                            Text("\(info.flag) \(info.code)")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(pinned ? .white : .primary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(pinned ? Color.accentColor : Color.secondary.opacity(0.14)))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }

            Section("折合 \(baseCurrency) 参考汇率（点击可自定义汇率）") {
                ForEach(CurrencyCatalog.all.filter { $0.code != baseCurrency }) { info in
                    let quote = store.getRate(from: info.code, to: baseCurrency)
                    Button {
                        customRateInput = MoneyAmount.formatRate(quote.rateMicros, decimals: 4)
                        editingCurrency = info
                    } label: {
                        HStack {
                            Text(info.flag)
                                .font(.title3)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("1 \(info.code) · \(info.nameZh)")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundColor(.primary)
                                Text("来源：\(quote.source) · \(quote.date)")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Text("\(MoneyAmount.formatRate(quote.rateMicros, decimals: 4)) \(baseCurrency)")
                                .font(.subheadline.weight(.bold).monospacedDigit())
                                .foregroundColor(.accentColor)
                        }
                    }
                }
            }
        }
        .navigationTitle("汇率中心")
        .navigationBarTitleDisplayMode(.inline)
        .sheet(item: $editingCurrency) { info in
            NavigationStack {
                Form {
                    Section("自定义汇率：1 \(info.code) 折合 \(baseCurrency)") {
                        TextField("如 7.2450", text: $customRateInput)
                            .keyboardType(.decimalPad)
                    }
                }
                .navigationTitle("自定义 \(info.code) 汇率")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("取消") { editingCurrency = nil }
                    }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("保存") {
                            if let rateMicros = MoneyAmount.parseRateToMicros(customRateInput), rateMicros > 0 {
                                store.saveCustomRate(from: info.code, to: baseCurrency, rateMicros: rateMicros)
                                store.toastMessage = "已更新 \(info.code) → \(baseCurrency) 自定义汇率"
                                editingCurrency = nil
                            } else {
                                store.lastErrorMessage = "请输入大于 0 的有效汇率"
                            }
                        }
                        .fontWeight(.semibold)
                    }
                }
            }
            .presentationDetents([.medium])
            .yubraeSheetStyle(palette: palette)
        }
    }

    private func togglePinned(_ code: String) {
        store.updateSettings { s in
            if s.pinnedCurrencies.contains(code) {
                if s.pinnedCurrencies.count > 1 {
                    s.pinnedCurrencies.removeAll { $0 == code }
                }
            } else {
                s.pinnedCurrencies.append(code)
            }
        }
    }

    @MainActor
    private func refreshOnlineRates() async {
        isRefreshing = true
        defer { isRefreshing = false }
        do {
            let count = try await YubraeRuntime.exchangeRateService.refreshRates(in: store, baseCurrency: baseCurrency)
            store.toastMessage = "汇率已更新（共刷新 \(count) 组货币对）"
        } catch {
            store.lastErrorMessage = error.localizedDescription
        }
    }
}

// MARK: - 8. Encrypted .klbk Backup & WebDAV Cloud Sync

public struct BackupAndSyncView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    @State private var passphrase: String
    @State private var webDavUrl: String
    @State private var webDavUsername: String
    @State private var webDavPassword: String
    @State private var webDavRemoteDir: String

    // File Export / Import
    @State private var exportDocument = KlbkBackupDocument()
    @State private var exportFileName = "yubrae_backup.klbk"
    @State private var showFileExporter = false
    @State private var showFileImporter = false

    // WebDAV Operations
    @State private var isWorking = false
    @State private var statusMessage: String?
    @State private var remoteFiles: [RemoteBackupFile] = []
    @State private var restoringRemoteFile: RemoteBackupFile?
    @State private var showRestoreConfirmAlert = false

    public init(store: LedgerStore) {
        self.store = store
        _passphrase = State(initialValue: store.settings.backupPassphrase)
        _webDavUrl = State(initialValue: store.settings.webDavUrl)
        _webDavUsername = State(initialValue: store.settings.webDavUsername)
        _webDavPassword = State(initialValue: store.settings.webDavPassword)
        _webDavRemoteDir = State(initialValue: store.settings.webDavRemoteDir)
    }

    public var body: some View {
        Form {
            Section(header: Text("AES-256-GCM 端到端加密口令"), footer: Text("使用与 Android 端完全字节互通的 KLBK1 (PBKDF2 120,000 轮 + AES-256-GCM) 加密格式，两端的 .klbk 文件可直接互相导入与 WebDAV 同步。")) {
                SecureField("设置或输入备份加密口令", text: $passphrase)
                Button("保存口令到本机 Keychain") {
                    persistInputs()
                    store.toastMessage = "加密口令已安全保存"
                }
            }

            Section("本地离线 .klbk 导入 / 导出") {
                Button {
                    triggerLocalExport()
                } label: {
                    Label("导出加密备份文件 (.klbk)", systemImage: "square.and.arrow.up")
                }

                Button {
                    persistInputs()
                    if passphrase.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        store.lastErrorMessage = "请先填写上方解密口令再选择 .klbk 文件"
                    } else {
                        showFileImporter = true
                    }
                } label: {
                    Label("从「文件」导入 .klbk 恢复（支持 Android 备份）", systemImage: "square.and.arrow.down")
                }
            }

            Section("WebDAV 云同步（坚果云 / Nextcloud / 群晖 / Alist）") {
                TextField("服务器地址（如 https://dav.jianguoyun.com/dav）", text: $webDavUrl)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.URL)
                TextField("用户名 / 邮箱", text: $webDavUsername)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                SecureField("应用专用密码", text: $webDavPassword)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                TextField("远程目录（默认 /Yubrae）", text: $webDavRemoteDir)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)

                HStack {
                    Button("保存配置") {
                        persistInputs()
                        store.toastMessage = "WebDAV 配置已保存"
                    }
                    .buttonStyle(.borderless)
                    Spacer()
                    Button("测试连接") {
                        Task { await testWebDav() }
                    }
                    .buttonStyle(.borderless)
                    .disabled(isWorking)
                }

                Button {
                    Task { await uploadToWebDav() }
                } label: {
                    Label("立即加密上传备份至 WebDAV", systemImage: "icloud.and.arrow.up")
                }
                .disabled(isWorking)

                Button {
                    Task { await loadRemoteBackups() }
                } label: {
                    Label("拉取云端 .klbk 备份列表", systemImage: "icloud.and.arrow.down")
                }
                .disabled(isWorking)

                if isWorking {
                    HStack(spacing: 8) {
                        ProgressView()
                        Text("正在与 WebDAV 通信...")
                            .font(.caption)
                            .foregroundColor(.secondary)
                    }
                }

                if let statusMessage {
                    Text(statusMessage)
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }

            if !remoteFiles.isEmpty {
                Section("云端备份文件（点击还原）") {
                    ForEach(remoteFiles) { file in
                        Button {
                            restoringRemoteFile = file
                            showRestoreConfirmAlert = true
                        } label: {
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(file.name)
                                        .font(.caption.monospaced())
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                    Text("\(YubraeTheme.formatFileSize(file.sizeBytes)) · \(file.modifiedAtMillis > 0 ? YubraeTheme.formatDate(file.modifiedAtMillis, pattern: "yyyy-MM-dd HH:mm") : "未知时间")")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Text("还原")
                                    .font(.caption.weight(.semibold))
                                    .foregroundColor(.accentColor)
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle("备份与 WebDAV 同步")
        .navigationBarTitleDisplayMode(.inline)
        .fileExporter(
            isPresented: $showFileExporter,
            document: exportDocument,
            contentType: .yubraeKlbk,
            defaultFilename: exportFileName
        ) { result in
            switch result {
            case .success:
                store.toastMessage = "已成功导出加密备份 \(exportFileName)"
            case .failure(let err):
                store.lastErrorMessage = "导出取消或失败：\(err.localizedDescription)"
            }
        }
        .fileImporter(
            isPresented: $showFileImporter,
            allowedContentTypes: [.yubraeKlbk, .data],
            allowsMultipleSelection: false
        ) { result in
            handleFileImport(result)
        }
        .yubraeConfirmDialog(
            isPresented: $showRestoreConfirmAlert,
            palette: palette,
            icon: "icloud.and.arrow.down.fill",
            title: "确认从云端备份恢复？",
            message: "将解密「\(restoringRemoteFile?.name ?? "")」并覆盖本机当前全部账本数据，请确认加密口令一致。",
            confirmTitle: "覆盖恢复",
            cancelTitle: "取消",
            isDestructive: true,
            onCancel: {
                restoringRemoteFile = nil
            },
            onConfirm: {
                if let file = restoringRemoteFile {
                    Task { await restoreRemote(file) }
                }
            }
        )
    }

    private func persistInputs() {
        store.updateSettings { s in
            s.backupPassphrase = passphrase
            s.webDavUrl = webDavUrl.trimmingCharacters(in: .whitespacesAndNewlines)
            s.webDavUsername = webDavUsername.trimmingCharacters(in: .whitespacesAndNewlines)
            s.webDavPassword = webDavPassword
            let dir = webDavRemoteDir.trimmingCharacters(in: .whitespacesAndNewlines)
            s.webDavRemoteDir = dir.isEmpty ? "/Yubrae" : dir
        }
    }

    private func triggerLocalExport() {
        persistInputs()
        do {
            let encrypted = try store.exportEncryptedBackup(passphrase: passphrase)
            exportDocument = KlbkBackupDocument(data: encrypted)
            exportFileName = WebDavClient.makeBackupFileName()
            showFileExporter = true
        } catch {
            store.lastErrorMessage = error.localizedDescription
        }
    }

    private func handleFileImport(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first else { return }
            let accessed = url.startAccessingSecurityScopedResource()
            defer {
                if accessed { url.stopAccessingSecurityScopedResource() }
            }
            do {
                let data = try Data(contentsOf: url)
                let summary = try store.importEncryptedBackup(data, passphrase: passphrase)
                store.toastMessage = summary.descriptionZh
                statusMessage = summary.descriptionZh
            } catch {
                store.lastErrorMessage = error.localizedDescription
            }
        case .failure(let err):
            store.lastErrorMessage = "选择文件失败：\(err.localizedDescription)"
        }
    }

    @MainActor
    private func testWebDav() async {
        persistInputs()
        isWorking = true
        defer { isWorking = false }
        do {
            let msg = try await YubraeRuntime.webDavClient.testConnection(config: WebDavConfig(settings: store.settings))
            statusMessage = msg
            store.toastMessage = msg
        } catch {
            statusMessage = error.localizedDescription
            store.lastErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func uploadToWebDav() async {
        persistInputs()
        isWorking = true
        defer { isWorking = false }
        do {
            let uploaded = try await store.uploadWebDavBackup(client: YubraeRuntime.webDavClient)
            statusMessage = "上传成功：\(uploaded.name)（\(YubraeTheme.formatFileSize(uploaded.sizeBytes))）"
            store.toastMessage = "WebDAV 加密备份上传成功"
            await loadRemoteBackups()
        } catch {
            statusMessage = error.localizedDescription
            store.lastErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func loadRemoteBackups() async {
        persistInputs()
        isWorking = true
        defer { isWorking = false }
        do {
            remoteFiles = try await YubraeRuntime.webDavClient.listRemoteBackups(config: WebDavConfig(settings: store.settings))
            statusMessage = remoteFiles.isEmpty ? "远程目录暂无 .klbk 备份文件" : "已发现 \(remoteFiles.count) 个云端备份"
        } catch {
            statusMessage = error.localizedDescription
            store.lastErrorMessage = error.localizedDescription
        }
    }

    @MainActor
    private func restoreRemote(_ file: RemoteBackupFile) async {
        persistInputs()
        isWorking = true
        defer {
            isWorking = false
            restoringRemoteFile = nil
        }
        do {
            let summary = try await store.restoreWebDavBackup(remotePath: file.path, client: YubraeRuntime.webDavClient)
            statusMessage = summary.descriptionZh
            store.toastMessage = summary.descriptionZh
        } catch {
            statusMessage = error.localizedDescription
            store.lastErrorMessage = error.localizedDescription
        }
    }
}

// MARK: - 9. Appearance & Privacy Security

public struct AppearanceAndPrivacyView: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.colorScheme) private var colorScheme

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    public var body: some View {
        Form {
            Section("明暗显示模式") {
                Picker("外观模式", selection: Binding(
                    get: { store.settings.themeMode },
                    set: { mode in store.updateSettings { $0.themeMode = mode } }
                )) {
                    ForEach(ThemeMode.allCases) { mode in
                        Text(mode.labelZh).tag(mode)
                    }
                }
                .pickerStyle(.segmented)

                Toggle("深色模式使用 OLED 纯黑背景", isOn: Binding(
                    get: { store.settings.oledPureBlack },
                    set: { val in store.updateSettings { $0.oledPureBlack = val } }
                ))

                Toggle("绿支红收（国际金融配色）", isOn: Binding(
                    get: { store.settings.greenExpenseMode },
                    set: { val in store.updateSettings { $0.greenExpenseMode = val } }
                ))
            }

            Section("余白 10 套主题皮肤") {
                let columns = [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)]
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(ThemeStyle.allCases) { style in
                        let selected = ThemeStyle.from(store.settings.themeStyle) == style
                        Button {
                            store.updateSettings { $0.themeStyle = style.rawValue }
                        } label: {
                            HStack(spacing: 10) {
                                Circle()
                                    .fill(Color(argbHex: style.primaryColorHex))
                                    .frame(width: 24, height: 24)
                                    .overlay(
                                        Image(systemName: "checkmark")
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(.white)
                                            .opacity(selected ? 1 : 0)
                                    )
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(style.displayName)
                                        .font(.caption.weight(.bold))
                                        .foregroundColor(palette.textPrimary)
                                    Text(style.tagline)
                                        .font(.system(size: 10))
                                        .foregroundColor(palette.textSecondary)
                                        .lineLimit(1)
                                }
                                Spacer(minLength: 0)
                            }
                            .padding(10)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(Color(argbHex: style.containerColorHex).opacity(colorScheme == .dark ? 0.16 : 0.72))
                            )
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .strokeBorder(selected ? Color(argbHex: style.primaryColorHex) : Color.clear, lineWidth: 2)
                            )
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 6)
            }

            Section(header: Text("隐私与安全"), footer: Text("开启面容 ID 应用锁后，冷启动或从后台切回余白时需先验证生物识别或锁屏密码。")) {
                Toggle("面容 ID / 触控 ID 应用锁", isOn: Binding(
                    get: { store.settings.biometricLockEnabled },
                    set: { enabled in
                        if enabled {
                            verifyAndEnableBiometric()
                        } else {
                            store.updateSettings { $0.biometricLockEnabled = false }
                        }
                    }
                ))

                Toggle("全局金额隐私遮罩（••••）", isOn: Binding(
                    get: { store.settings.privacyMaskEnabled },
                    set: { val in store.updateSettings { $0.privacyMaskEnabled = val } }
                ))

                Toggle("切换后台多任务时模糊隐藏页面", isOn: Binding(
                    get: { store.settings.hideRecentsPreviewEnabled },
                    set: { val in store.updateSettings { $0.hideRecentsPreviewEnabled = val } }
                ))
            }
        }
        .navigationTitle("外观与隐私")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func verifyAndEnableBiometric() {
        let context = LAContext()
        var err: NSError?
        guard context.canEvaluatePolicy(.deviceOwnerAuthentication, error: &err) else {
            store.lastErrorMessage = "当前设备未启用面容 ID 或系统密码"
            return
        }
        context.evaluatePolicy(.deviceOwnerAuthentication, localizedReason: "验证身份以开启余白应用锁") { success, error in
            DispatchQueue.main.async {
                if success {
                    store.updateSettings { $0.biometricLockEnabled = true }
                    store.toastMessage = "已开启面容 ID 应用锁"
                } else {
                    store.lastErrorMessage = error?.localizedDescription ?? "身份验证未通过"
                }
            }
        }
    }
}

// MARK: - 10. App Update Settings

public struct UpdateSettingsView: View {
    @ObservedObject public var store: LedgerStore
    public let availableUpdate: AppUpdateInfo?
    public let isCheckingUpdate: Bool
    public let onCheckUpdate: () -> Void
    public let onPresentUpdateSheet: () -> Void

    @Environment(\.openURL) private var openURL
    @State private var mirrorInput: String
    @State private var validationError: String?

    public init(
        store: LedgerStore,
        availableUpdate: AppUpdateInfo?,
        isCheckingUpdate: Bool,
        onCheckUpdate: @escaping () -> Void,
        onPresentUpdateSheet: @escaping () -> Void
    ) {
        self.store = store
        self.availableUpdate = availableUpdate
        self.isCheckingUpdate = isCheckingUpdate
        self.onCheckUpdate = onCheckUpdate
        self.onPresentUpdateSheet = onPresentUpdateSheet
        _mirrorInput = State(initialValue: store.settings.updateMirrorPrefix)
    }

    public var body: some View {
        Form {
            Section("版本信息") {
                HStack {
                    Text("应用名称")
                    Spacer()
                    Text("余白 YUBRAE (iOS)").foregroundColor(.secondary)
                }
                HStack {
                    Text("当前版本")
                    Spacer()
                    Text("v\(AppUpdateChecker.currentAppVersion)").fontWeight(.semibold)
                }
                if store.settings.lastUpdateCheckMillis > 0 {
                    HStack {
                        Text("上次检查时间")
                        Spacer()
                        Text(YubraeTheme.formatDate(store.settings.lastUpdateCheckMillis, pattern: "yyyy-MM-dd HH:mm"))
                            .foregroundColor(.secondary)
                    }
                }
                Toggle("启动时自动静默检查新版本", isOn: Binding(
                    get: { store.settings.autoCheckUpdateEnabled },
                    set: { val in store.updateSettings { $0.autoCheckUpdateEnabled = val } }
                ))
            }

            Section(header: Text("GitHub 镜像加速前缀（可选）"), footer: Text("如直连 GitHub Release 较慢，可填写 https:// 镜像前缀（如 https://gh-proxy.com/），留空则直连官方地址。")) {
                TextField("https://gh-proxy.com/", text: $mirrorInput)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled(true)
                    .keyboardType(.URL)
                if let validationError {
                    Text(validationError)
                        .font(.caption)
                        .foregroundColor(.red)
                }
                Button("校验并保存镜像前缀") {
                    switch AppUpdateChecker.validateMirrorPrefix(mirrorInput) {
                    case .valid(let normalized):
                        validationError = nil
                        mirrorInput = normalized
                        store.updateSettings { $0.updateMirrorPrefix = normalized }
                        store.toastMessage = normalized.isEmpty ? "已清空镜像加速前缀" : "镜像前缀已保存"
                    case .invalid(let reason):
                        validationError = reason
                    }
                }
            }

            Section("检查更新") {
                Button {
                    onCheckUpdate()
                } label: {
                    HStack {
                        if isCheckingUpdate {
                            ProgressView()
                        } else {
                            Image(systemName: "arrow.clockwise.circle.fill")
                        }
                        Text(isCheckingUpdate ? "正在检查 GitHub Release..." : "立即检查新版本")
                    }
                }
                .disabled(isCheckingUpdate)

                if let update = availableUpdate {
                    Button {
                        onPresentUpdateSheet()
                    } label: {
                        HStack {
                            Text("查看新版本 \(update.tagName) 与下载 .ipa")
                                .fontWeight(.semibold)
                            Spacer()
                            Image(systemName: "chevron.right")
                                .font(.caption)
                        }
                    }
                }

                if let url = URL(string: AppUpdateChecker.defaultReleasePageUrl) {
                    Button {
                        openURL(url)
                    } label: {
                        Label("访问 GitHub Releases 主页", systemImage: "safari")
                    }
                }
            }
        }
        .navigationTitle("检查版本更新")
        .navigationBarTitleDisplayMode(.inline)
    }
}
