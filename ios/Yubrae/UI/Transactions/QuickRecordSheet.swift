import SwiftUI

public struct QuickRecordSheet: View {
    @ObservedObject public var store: LedgerStore
    public let editingTransaction: TransactionDto?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var txType: String
    @State private var amountText: String
    @State private var origCurrency: String
    @State private var selectedAccountId: Int64
    @State private var selectedToAccountId: Int64?
    @State private var selectedParentCategoryId: Int64?
    @State private var selectedCategoryId: Int64?
    @State private var selectedLedgerId: Int64
    @State private var merchant: String
    @State private var note: String
    @State private var selectedTags: [String]
    @State private var customTagInput: String = ""
    @State private var txDate: Date
    @State private var reimburseStatus: String
    @State private var excludeFromStats: Bool
    @State private var excludeFromBudget: Bool

    // 跨币种手动覆盖（汇损反算）
    @State private var showForexOverride = false
    @State private var manualAccountAmountText: String = ""
    @State private var manualRateText: String = ""
    @State private var manualToAmountText: String = ""

    @State private var showDeleteConfirm = false
    @State private var validationMessage: String?

    public init(store: LedgerStore, editingTransaction: TransactionDto? = nil) {
        self.store = store
        self.editingTransaction = editingTransaction

        let activeAccounts = store.activeAccounts.isEmpty ? store.accounts : store.activeAccounts
        let defaultAccountId = activeAccounts.first?.id ?? 0
        let secondAccountId = activeAccounts.first(where: { $0.id != defaultAccountId })?.id

        if let tx = editingTransaction {
            let resolvedType = tx.type == TransactionType.ADJUST ? TransactionType.EXPENSE : tx.type
            _txType = State(initialValue: resolvedType)
            let digits = CurrencyCatalog.find(tx.origCurrency).minorDigits
            _amountText = State(initialValue: MoneyAmount.formatPlain(abs(tx.origAmountMicros), minorDigits: digits, grouping: false))
            _origCurrency = State(initialValue: tx.origCurrency)
            _selectedAccountId = State(initialValue: tx.accountId)
            _selectedToAccountId = State(initialValue: tx.toAccountId ?? secondAccountId)
            _selectedCategoryId = State(initialValue: tx.categoryId)
            let parentId = tx.categoryId.flatMap { store.category(byId: $0)?.parentId } ?? tx.categoryId
            _selectedParentCategoryId = State(initialValue: parentId)
            _selectedLedgerId = State(initialValue: tx.ledgerId)
            _merchant = State(initialValue: tx.merchant)
            _note = State(initialValue: tx.note)
            _selectedTags = State(initialValue: TagTextCodec.parse(tx.tags))
            _txDate = State(initialValue: Date(timeIntervalSince1970: Double(tx.timestamp) / 1000.0))
            _reimburseStatus = State(initialValue: tx.reimburseStatus)
            _excludeFromStats = State(initialValue: tx.excludeFromStats)
            _excludeFromBudget = State(initialValue: tx.excludeFromBudget)
        } else {
            _txType = State(initialValue: TransactionType.EXPENSE)
            _amountText = State(initialValue: "")
            _origCurrency = State(initialValue: store.settings.baseCurrency)
            _selectedAccountId = State(initialValue: defaultAccountId)
            _selectedToAccountId = State(initialValue: secondAccountId)
            let firstParent = store.parentCategories(for: TransactionType.EXPENSE).first
            _selectedParentCategoryId = State(initialValue: firstParent?.id)
            _selectedCategoryId = State(initialValue: firstParent?.id)
            _selectedLedgerId = State(initialValue: store.activeLedger?.id ?? store.settings.activeLedgerId)
            _merchant = State(initialValue: "")
            _note = State(initialValue: "")
            _selectedTags = State(initialValue: [])
            _txDate = State(initialValue: Date())
            _reimburseStatus = State(initialValue: ReimburseStatus.NONE)
            _excludeFromStats = State(initialValue: false)
            _excludeFromBudget = State(initialValue: false)
        }
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var usableAccounts: [AccountDto] {
        store.activeAccounts.isEmpty ? store.accounts : store.activeAccounts
    }

    private var orderedCurrencies: [String] {
        CurrencyCatalog.orderedCodes(pinned: store.settings.pinnedCurrencies)
    }

    private var parsedAmountMicros: Int64 {
        MoneyAmount.parseToMicros(amountText) ?? 0
    }

    private var conversionPreview: ConversionPreview? {
        guard parsedAmountMicros > 0, selectedAccountId > 0 else { return nil }
        let manualAcc = MoneyAmount.parseToMicros(manualAccountAmountText)
        let manualRate = MoneyAmount.parseRateToMicros(manualRateText)
        let dateStr = DefaultSeedData.formatISODate(epochMillis: Int64((txDate.timeIntervalSince1970 * 1000.0).rounded()))
        return store.previewConversion(
            origAmountMicros: parsedAmountMicros,
            origCurrency: origCurrency,
            accountId: selectedAccountId,
            manualAccountAmountMicros: manualAcc,
            manualRateToAccountMicros: manualRate,
            baseCurrency: store.settings.baseCurrency,
            onDate: dateStr
        )
    }

    private var parentCategories: [CategoryDto] {
        store.parentCategories(for: txType)
    }

    private var childCategories: [CategoryDto] {
        guard let pid = selectedParentCategoryId else { return [] }
        return store.childCategories(of: pid)
    }

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    typeSelectorCard
                    amountAndCurrencyCard
                    if let preview = conversionPreview,
                       (preview.origCurrency != preview.accountCurrency || preview.origCurrency != preview.baseCurrency) {
                        forexPreviewCard(preview)
                    }
                    accountSelectionCard
                    if txType != TransactionType.TRANSFER {
                        categorySelectionCard
                    }
                    detailsAndTagsCard
                    advancedOptionsCard

                    if let validationMessage {
                        Text(validationMessage)
                            .font(.footnote)
                            .foregroundColor(palette.dangerColor)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.horizontal, 4)
                    }

                    if editingTransaction != nil {
                        Button(role: .destructive) {
                            showDeleteConfirm = true
                        } label: {
                            Label("删除此笔账单", systemImage: "trash")
                                .font(.subheadline.weight(.semibold))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(
                                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                                        .fill(palette.dangerColor.opacity(0.12))
                                )
                        }
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle(editingTransaction == nil ? "记一笔" : "编辑账单")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("保存") {
                        saveBill()
                    }
                    .fontWeight(.semibold)
                }
            }
            .alert("确认删除账单？", isPresented: $showDeleteConfirm) {
                Button("取消", role: .cancel) {}
                Button("删除", role: .destructive) {
                    if let id = editingTransaction?.id {
                        do {
                            try store.deleteTransaction(id: id)
                            store.toastMessage = "已删除账单并回滚账户余额"
                            dismiss()
                        } catch {
                            store.lastErrorMessage = error.localizedDescription
                        }
                    }
                }
            } message: {
                Text("删除后将自动回滚关联账户的余额变动。")
            }
            .onChange(of: txType) { newType in
                syncCategoryForTypeChange(newType)
            }
        }
    }

    // MARK: - Sections

    private var typeSelectorCard: some View {
        Picker("交易类型", selection: $txType) {
            Text("支出").tag(TransactionType.EXPENSE)
            Text("收入").tag(TransactionType.INCOME)
            Text("转账").tag(TransactionType.TRANSFER)
        }
        .pickerStyle(.segmented)
    }

    private var amountAndCurrencyCard: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .center, spacing: 10) {
                    Menu {
                        ForEach(orderedCurrencies, id: \.self) { code in
                            let info = CurrencyCatalog.find(code)
                            Button {
                                origCurrency = code
                            } label: {
                                Text("\(info.flag) \(info.code) · \(info.nameZh) (\(info.symbol))")
                            }
                        }
                    } label: {
                        let info = CurrencyCatalog.find(origCurrency)
                        HStack(spacing: 4) {
                            Text(info.flag)
                            Text(info.code)
                                .font(.subheadline.weight(.bold))
                            Image(systemName: "chevron.down")
                                .font(.caption2)
                        }
                        .foregroundColor(palette.primary)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 8)
                        .background(Capsule().fill(palette.accentSoftFill))
                    }

                    TextField("0.00", text: $amountText)
                        .keyboardType(.decimalPad)
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                        .foregroundColor(amountAccentColor)
                        .multilineTextAlignment(.trailing)
                }

                // 快捷置顶币种
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(store.settings.pinnedCurrencies, id: \.self) { code in
                            let info = CurrencyCatalog.find(code)
                            let selected = origCurrency == code
                            Button {
                                origCurrency = code
                            } label: {
                                Text("\(info.flag) \(code)")
                                    .font(.caption.weight(.medium))
                                    .foregroundColor(selected ? palette.onPrimary : palette.textSecondary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 5)
                                    .background(
                                        Capsule().fill(selected ? palette.primary : palette.surfaceSecondary)
                                    )
                            }
                        }
                    }
                }
            }
        }
    }

    private var amountAccentColor: Color {
        switch txType {
        case TransactionType.INCOME: return palette.incomeColor
        case TransactionType.TRANSFER: return palette.transferColor
        default: return palette.expenseColor
        }
    }

    private func forexPreviewCard(_ preview: ConversionPreview) -> some View {
        YubraeCard(palette: palette, padding: 14) {
            VStack(alignment: .leading, spacing: 8) {
                HStack {
                    Label("多币种折算预览", systemImage: "arrow.triangle.2.circlepath")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.primary)
                    Spacer()
                    Button(showForexOverride ? "收起汇损覆写" : "手动改实扣/汇率") {
                        withAnimation(.easeInOut(duration: 0.2)) {
                            showForexOverride.toggle()
                        }
                    }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                }

                HStack {
                    Text("账户实扣")
                        .font(.caption)
                        .foregroundColor(palette.textSecondary)
                    Spacer()
                    Text(MoneyAmount.format(preview.accountAmountMicros, currencyCode: preview.accountCurrency, showCurrencyCode: true))
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                }

                if preview.baseCurrency != preview.accountCurrency {
                    HStack {
                        Text("折合基准币(\(preview.baseCurrency))")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        Spacer()
                        Text(MoneyAmount.format(preview.baseAmountMicros, currencyCode: preview.baseCurrency))
                            .font(.caption.weight(.medium))
                            .foregroundColor(palette.textSecondary)
                    }
                }

                Text("成交汇率：1 \(preview.origCurrency) ≈ \(MoneyAmount.formatRate(preview.rateToAccountMicros)) \(preview.accountCurrency)（\(preview.rateSource)）")
                    .font(.caption2)
                    .foregroundColor(palette.textTertiary)

                if showForexOverride {
                    Divider()
                    HStack(spacing: 10) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("实扣金额 (\(preview.accountCurrency))")
                                .font(.caption2)
                                .foregroundColor(palette.textSecondary)
                            TextField("留空自动算", text: $manualAccountAmountText)
                                .keyboardType(.decimalPad)
                                .font(.subheadline)
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                        }
                        VStack(alignment: .leading, spacing: 4) {
                            Text("自定义汇率")
                                .font(.caption2)
                                .foregroundColor(palette.textSecondary)
                            TextField("如 7.2500", text: $manualRateText)
                                .keyboardType(.decimalPad)
                                .font(.subheadline)
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                        }
                    }
                }
            }
        }
    }

    private var accountSelectionCard: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text(txType == TransactionType.TRANSFER ? "转出账户" : "结算账户")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                    Spacer()
                    if store.activeLedgers.count > 1 {
                        Menu {
                            ForEach(store.activeLedgers) { ledger in
                                Button {
                                    selectedLedgerId = ledger.id
                                } label: {
                                    Text(ledger.name)
                                }
                            }
                        } label: {
                            let currentLedgerName = store.ledgers.first(where: { $0.id == selectedLedgerId })?.name ?? "默认账本"
                            Label(currentLedgerName, systemImage: "book.closed")
                                .font(.caption.weight(.medium))
                                .foregroundColor(palette.primary)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(palette.accentSoftFill))
                        }
                    }
                }

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(usableAccounts) { account in
                            accountPill(account: account, isSelected: selectedAccountId == account.id) {
                                selectedAccountId = account.id
                                if editingTransaction == nil && amountText.isEmpty {
                                    origCurrency = account.currencyCode
                                }
                            }
                        }
                    }
                }

                if txType == TransactionType.TRANSFER {
                    Divider()
                    Text("转入目标账户")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(usableAccounts.filter { $0.id != selectedAccountId }) { account in
                                accountPill(account: account, isSelected: selectedToAccountId == account.id) {
                                    selectedToAccountId = account.id
                                }
                            }
                        }
                    }

                    if let fromAcc = store.account(byId: selectedAccountId),
                       let toId = selectedToAccountId,
                       let toAcc = store.account(byId: toId),
                       fromAcc.currencyCode != toAcc.currencyCode {
                        HStack {
                            Text("实到金额 (\(toAcc.currencyCode))")
                                .font(.caption)
                                .foregroundColor(palette.textSecondary)
                            TextField("留空按实时汇率换算", text: $manualToAmountText)
                                .keyboardType(.decimalPad)
                                .font(.subheadline)
                                .multilineTextAlignment(.trailing)
                        }
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.surfaceSecondary))
                    }
                }
            }
        }
    }

    private func accountPill(account: AccountDto, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: DefaultSeedData.sfSymbol(for: account.icon))
                    .font(.caption)
                VStack(alignment: .leading, spacing: 1) {
                    Text(account.name)
                        .font(.caption.weight(.semibold))
                        .lineLimit(1)
                    Text(account.currencyCode)
                        .font(.caption2)
                        .opacity(0.8)
                }
            }
            .foregroundColor(isSelected ? palette.onPrimary : palette.textPrimary)
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(isSelected ? palette.primary : palette.surfaceSecondary)
            )
        }
    }

    private var categorySelectionCard: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                Text("选择分类")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(palette.textPrimary)

                let columns = [GridItem(.adaptive(minimum: 74), spacing: 10)]
                LazyVGrid(columns: columns, spacing: 10) {
                    ForEach(parentCategories) { parent in
                        let isSelected = selectedParentCategoryId == parent.id
                        Button {
                            selectedParentCategoryId = parent.id
                            selectedCategoryId = parent.id
                        } label: {
                            VStack(spacing: 6) {
                                ZStack {
                                    Circle()
                                        .fill(isSelected ? Color(argbHex: parent.colorHex) : Color(argbHex: parent.colorHex).opacity(0.14))
                                        .frame(width: 40, height: 40)
                                    Image(systemName: DefaultSeedData.sfSymbol(for: parent.icon))
                                        .font(.system(size: 16, weight: .semibold))
                                        .foregroundColor(isSelected ? .white : Color(argbHex: parent.colorHex))
                                }
                                Text(parent.name)
                                    .font(.caption2.weight(isSelected ? .bold : .medium))
                                    .foregroundColor(isSelected ? palette.textPrimary : palette.textSecondary)
                                    .lineLimit(1)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 6)
                            .background(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .fill(isSelected ? palette.accentSoftFill : Color.clear)
                            )
                        }
                    }
                }

                if !childCategories.isEmpty {
                    Divider()
                    Text("细分品类")
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.textSecondary)
                    YubraeFlowLayout(spacing: 8, lineSpacing: 8) {
                        ForEach(childCategories) { child in
                            let selected = selectedCategoryId == child.id
                            Button {
                                selectedCategoryId = selected ? selectedParentCategoryId : child.id
                            } label: {
                                HStack(spacing: 4) {
                                    Image(systemName: DefaultSeedData.sfSymbol(for: child.icon))
                                        .font(.caption2)
                                    Text(child.name)
                                        .font(.caption.weight(.medium))
                                }
                                .foregroundColor(selected ? palette.onPrimary : palette.textPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(
                                    Capsule().fill(selected ? palette.primary : palette.surfaceSecondary)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private var detailsAndTagsCard: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 10) {
                    Image(systemName: "storefront")
                        .foregroundColor(palette.textSecondary)
                        .frame(width: 20)
                    TextField("商户或交易对象（如 星巴克）", text: $merchant)
                        .font(.subheadline)
                }
                Divider()
                HStack(spacing: 10) {
                    Image(systemName: "note.text")
                        .foregroundColor(palette.textSecondary)
                        .frame(width: 20)
                    TextField("备注（可选）", text: $note)
                        .font(.subheadline)
                }
                Divider()
                DatePicker("记账时间", selection: $txDate, displayedComponents: [.date, .hourAndMinute])
                    .font(.subheadline)
                Divider()

                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Text("标签")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.textPrimary)
                        Spacer()
                        if !selectedTags.isEmpty {
                            Text("已选 \(selectedTags.count) 个")
                                .font(.caption)
                                .foregroundColor(palette.primary)
                        }
                    }

                    HStack(spacing: 8) {
                        TextField("输入自定义标签后添加", text: $customTagInput)
                            .font(.caption)
                            .padding(8)
                            .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                        Button("添加") {
                            let clean = customTagInput.trimmingCharacters(in: .whitespacesAndNewlines)
                            if !clean.isEmpty {
                                if !selectedTags.contains(clean) {
                                    selectedTags.append(clean)
                                }
                                _ = store.saveTag(TagDto(name: clean, groupName: "自定义", isSystem: false))
                                customTagInput = ""
                            }
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.primary)
                    }

                    let displayTags = Array(store.tags.prefix(18))
                    if !displayTags.isEmpty {
                        YubraeFlowLayout(spacing: 6, lineSpacing: 6) {
                            ForEach(displayTags) { tag in
                                let isOn = selectedTags.contains(tag.name)
                                Button {
                                    if isOn {
                                        selectedTags.removeAll { $0 == tag.name }
                                    } else {
                                        selectedTags.append(tag.name)
                                    }
                                } label: {
                                    Text("#\(tag.name)")
                                        .font(.caption2.weight(.medium))
                                        .foregroundColor(isOn ? palette.onPrimary : palette.textSecondary)
                                        .padding(.horizontal, 9)
                                        .padding(.vertical, 5)
                                        .background(
                                            Capsule().fill(isOn ? Color(argbHex: tag.colorHex) : palette.surfaceSecondary)
                                        )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var advancedOptionsCard: some View {
        YubraeCard(palette: palette) {
            DisclosureGroup("高级账务属性（报销 / 统计排除）") {
                VStack(alignment: .leading, spacing: 12) {
                    if txType == TransactionType.EXPENSE {
                        Picker("报销状态", selection: $reimburseStatus) {
                            Text("无需报销").tag(ReimburseStatus.NONE)
                            Text("待报销").tag(ReimburseStatus.PENDING)
                            Text("已报销").tag(ReimburseStatus.REIMBURSED)
                        }
                        .pickerStyle(.segmented)
                    }
                    Toggle("不计入收支统计", isOn: $excludeFromStats)
                        .font(.subheadline)
                    Toggle("不计入预算消耗", isOn: $excludeFromBudget)
                        .font(.subheadline)
                }
                .padding(.top, 10)
            }
            .font(.subheadline.weight(.medium))
            .foregroundColor(palette.textPrimary)
        }
    }

    // MARK: - Actions

    private func syncCategoryForTypeChange(_ newType: String) {
        if newType == TransactionType.TRANSFER {
            selectedParentCategoryId = nil
            selectedCategoryId = nil
            if selectedToAccountId == nil || selectedToAccountId == selectedAccountId {
                selectedToAccountId = usableAccounts.first(where: { $0.id != selectedAccountId })?.id
            }
            return
        }
        let parents = store.parentCategories(for: newType)
        if let currentId = selectedCategoryId,
           let cat = store.category(byId: currentId),
           cat.type.caseInsensitiveCompare(newType) == .orderedSame {
            selectedParentCategoryId = cat.parentId ?? cat.id
        } else {
            selectedParentCategoryId = parents.first?.id
            selectedCategoryId = parents.first?.id
        }
    }

    private func saveBill() {
        validationMessage = nil
        guard parsedAmountMicros > 0 else {
            validationMessage = "请输入大于 0 的有效金额"
            return
        }
        guard selectedAccountId > 0 else {
            validationMessage = "请先在「资产」页创建一个账户"
            return
        }
        if txType == TransactionType.TRANSFER {
            guard let toId = selectedToAccountId, toId != selectedAccountId else {
                validationMessage = "请选择与转出账户不同的目标账户"
                return
            }
        }

        let manualAcc = MoneyAmount.parseToMicros(manualAccountAmountText)
        let manualRate = MoneyAmount.parseRateToMicros(manualRateText)
        let manualTo = MoneyAmount.parseToMicros(manualToAmountText)

        let request = RecordTransactionRequest(
            existingId: editingTransaction?.id,
            type: txType,
            timestamp: Int64((txDate.timeIntervalSince1970 * 1000.0).rounded()),
            origAmountMicros: parsedAmountMicros,
            origCurrency: origCurrency,
            accountId: selectedAccountId,
            manualAccountAmountMicros: manualAcc,
            manualRateToAccountMicros: manualRate,
            toAccountId: txType == TransactionType.TRANSFER ? selectedToAccountId : nil,
            manualToAmountMicros: txType == TransactionType.TRANSFER ? manualTo : nil,
            categoryId: txType == TransactionType.TRANSFER ? nil : selectedCategoryId,
            merchant: merchant.trimmingCharacters(in: .whitespacesAndNewlines),
            note: note.trimmingCharacters(in: .whitespacesAndNewlines),
            tags: TagTextCodec.format(selectedTags),
            reimburseStatus: txType == TransactionType.EXPENSE ? reimburseStatus : ReimburseStatus.NONE,
            sourceType: editingTransaction?.sourceType ?? CaptureChannel.MANUAL,
            rawEventId: editingTransaction?.rawEventId,
            status: TransactionStatus.CONFIRMED,
            ledgerId: selectedLedgerId,
            excludeFromStats: excludeFromStats,
            excludeFromBudget: excludeFromBudget,
            refundOfTxId: editingTransaction?.refundOfTxId,
            attachmentPaths: editingTransaction?.attachmentPaths ?? "",
            reimbursedTxId: editingTransaction?.reimbursedTxId
        )

        do {
            _ = try store.saveTransaction(request)
            store.toastMessage = editingTransaction == nil ? "已保存一笔新账单" : "账单修改已保存"
            dismiss()
        } catch {
            validationMessage = error.localizedDescription
        }
    }
}
