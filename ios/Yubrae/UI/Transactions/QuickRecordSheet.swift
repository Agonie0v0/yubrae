import Combine
import Foundation
import SwiftUI
import UIKit

private enum IOSRecordWorkspacePanel: Equatable {
    case category
    case tags
    case account
    case discountFee
    case dateTime
    case more
}

private enum IOSQuickRecordFocusField: Hashable {
    case note
    case discount
    case fee
}

public struct QuickRecordSheet: View {
    @ObservedObject public var store: LedgerStore
    public let editingTransaction: TransactionDto?

    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var txType: String
    @State private var activePanel: IOSRecordWorkspacePanel = .category
    @State private var amountExpr: String
    @State private var discountInput: String
    @State private var feeInput: String
    @State private var expenseAmountIsActualPaid: Bool = false
    @State private var origCurrency: String
    @State private var selectedAccountId: Int64
    @State private var selectedToAccountId: Int64?
    @State private var selectedCategoryId: Int64?
    @State private var selectedLedgerId: Int64
    @State private var merchant: String
    @State private var note: String
    @State private var selectedTags: [String]
    @State private var txDate: Date
    @State private var reimburseStatus: String
    @State private var excludeFromStats: Bool
    @State private var excludeFromBudget: Bool

    // 分类搜索与内联新建状态
    @State private var categorySearchQuery: String = ""
    @State private var isCreatingCategory: Bool = false
    @State private var newCatName: String = ""
    @State private var newCatParentId: Int64? = nil
    @State private var newCatIcon: String = "category"
    @State private var newCatColorHex: Int64 = 0xFF2AA876
    @State private var newCatManualIcon: Bool = false
    @State private var newCatManualColor: Bool = false
    @State private var newCatError: String? = nil

    // 标签搜索、分组筛选与即时新建状态
    @State private var tagSearchQuery: String = ""
    @State private var activeTagGroup: String = IOSRecordUsageRanker.tagTabAll
    @State private var showTagCreateStyle: Bool = false
    @State private var newTagGroup: String = "自定义"
    @State private var newTagColorHex: Int64 = 0xFF2AA876

    // 跨币种与弹窗控制
    @State private var showCurrencySheet = false
    @State private var isKeyboardVisible = false
    private var shouldShowKeypad: Bool { !isKeyboardVisible && focusedField == nil }
    @State private var showForexOverride = false
    @State private var manualAccountAmountText: String = ""
    @State private var manualRateText: String = ""
    @State private var manualToAmountText: String = ""

    @State private var showDeleteConfirm = false
    @State private var validationMessage: String?
    @State private var inSheetToast: String?
    @State private var inSheetToastTask: Task<Void, Never>?

    @FocusState private var focusedField: IOSQuickRecordFocusField?

    public init(
        store: LedgerStore,
        editingTransaction: TransactionDto? = nil,
        initialType: String = TransactionType.EXPENSE,
        initialAccountId: Int64? = nil,
        initialToAccountId: Int64? = nil,
        initialAmountMicros: Int64? = nil
    ) {
        self.store = store
        self.editingTransaction = editingTransaction

        let activeAccounts = store.activeAccounts.isEmpty ? store.accounts : store.activeAccounts
        let defaultAccountId = activeAccounts.first?.id ?? 0
        let secondAccountId = activeAccounts.first(where: { $0.id != defaultAccountId })?.id

        if let tx = editingTransaction {
            let resolvedType = tx.type == TransactionType.ADJUST ? TransactionType.EXPENSE : tx.type
            _txType = State(initialValue: resolvedType)
            let digits = CurrencyCatalog.find(tx.origCurrency).minorDigits
            let resolvedDiscount: Int64 = (resolvedType == TransactionType.INCOME || tx.type == TransactionType.ADJUST)
                ? 0
                : max(Int64(0), tx.discountAmountMicros)
            let resolvedFee: Int64 = tx.type == TransactionType.ADJUST
                ? 0
                : max(Int64(0), tx.feeAmountMicros)
            let rawOrig = abs(tx.origAmountMicros)
            let restoredNominal: Int64
            switch resolvedType {
            case TransactionType.INCOME:
                restoredNominal = max(Int64(0), rawOrig + resolvedFee)
            case TransactionType.EXPENSE, TransactionType.TRANSFER:
                let candidate = rawOrig + resolvedDiscount - resolvedFee
                restoredNominal = candidate > 0 ? candidate : rawOrig
            default:
                restoredNominal = rawOrig
            }
            _amountExpr = State(initialValue: MoneyAmount.formatPlain(restoredNominal, minorDigits: digits, grouping: false))
            _discountInput = State(initialValue: resolvedDiscount > 0 ? Self.formatCompactDecimalInput(resolvedDiscount) : "")
            _feeInput = State(initialValue: resolvedFee > 0 ? Self.formatCompactDecimalInput(resolvedFee) : "")
            _expenseAmountIsActualPaid = State(initialValue: false)
            _origCurrency = State(initialValue: tx.origCurrency)
            _selectedAccountId = State(initialValue: tx.accountId)
            _selectedToAccountId = State(initialValue: tx.toAccountId ?? secondAccountId)
            _selectedCategoryId = State(initialValue: tx.categoryId)
            _selectedLedgerId = State(initialValue: tx.ledgerId)
            _merchant = State(initialValue: tx.merchant)
            _note = State(initialValue: tx.note)
            _selectedTags = State(initialValue: TagTextCodec.parse(tx.tags))
            _txDate = State(initialValue: Date(timeIntervalSince1970: Double(tx.timestamp) / 1000.0))
            _reimburseStatus = State(initialValue: tx.reimburseStatus)
            _excludeFromStats = State(initialValue: tx.excludeFromStats)
            _excludeFromBudget = State(initialValue: tx.excludeFromBudget)
        } else {
            let validTypes: Set<String> = [
                TransactionType.EXPENSE,
                TransactionType.INCOME,
                TransactionType.TRANSFER
            ]
            let resolvedType = validTypes.contains(initialType) ? initialType : TransactionType.EXPENSE

            let validInitialFrom = initialAccountId.flatMap { targetId in
                activeAccounts.first(where: { $0.id == targetId }) ?? store.accounts.first(where: { $0.id == targetId })
            }
            let validInitialTo = initialToAccountId.flatMap { targetId in
                activeAccounts.first(where: { $0.id == targetId }) ?? store.accounts.first(where: { $0.id == targetId })
            }

            let resolvedFromAccount: AccountDto?
            if let validInitialFrom {
                resolvedFromAccount = validInitialFrom
            } else if resolvedType == TransactionType.TRANSFER, let toAcc = validInitialTo {
                resolvedFromAccount = activeAccounts.first(where: {
                    $0.id != toAcc.id && ($0.groupType == AccountGroup.DEBIT || $0.groupType == AccountGroup.WALLET)
                })
                    ?? activeAccounts.first(where: { $0.id != toAcc.id && $0.groupType != AccountGroup.CREDIT })
                    ?? activeAccounts.first(where: { $0.id != toAcc.id })
                    ?? activeAccounts.first
            } else {
                resolvedFromAccount = activeAccounts.first
            }

            let resolvedAccountId = resolvedFromAccount?.id ?? defaultAccountId

            let resolvedToAccount: AccountDto?
            if let validInitialTo, validInitialTo.id != resolvedAccountId {
                resolvedToAccount = validInitialTo
            } else {
                resolvedToAccount = activeAccounts.first(where: { $0.id != resolvedAccountId })
            }
            let resolvedToAccountId = resolvedToAccount?.id

            let resolvedCurrency: String
            if resolvedType == TransactionType.TRANSFER,
               let toAcc = resolvedToAccount,
               validInitialTo != nil,
               (validInitialFrom == nil || toAcc.groupType == AccountGroup.CREDIT) {
                resolvedCurrency = CurrencyCatalog.normalize(toAcc.currencyCode)
            } else if let fromAcc = resolvedFromAccount {
                resolvedCurrency = CurrencyCatalog.normalize(fromAcc.currencyCode)
            } else {
                resolvedCurrency = store.settings.baseCurrency
            }

            let resolvedAmountExpr: String
            if let presetMicros = initialAmountMicros, presetMicros > 0 {
                let digits = CurrencyCatalog.find(resolvedCurrency).minorDigits
                resolvedAmountExpr = MoneyAmount.formatPlain(presetMicros, minorDigits: digits, grouping: false)
            } else {
                resolvedAmountExpr = ""
            }

            let resolvedCategoryId: Int64? = resolvedType == TransactionType.TRANSFER
                ? nil
                : store.parentCategories(for: resolvedType).first?.id

            _txType = State(initialValue: resolvedType)
            _amountExpr = State(initialValue: resolvedAmountExpr)
            _discountInput = State(initialValue: "")
            _feeInput = State(initialValue: "")
            _expenseAmountIsActualPaid = State(initialValue: false)
            _origCurrency = State(initialValue: resolvedCurrency)
            _selectedAccountId = State(initialValue: resolvedAccountId)
            _selectedToAccountId = State(initialValue: resolvedToAccountId)
            _selectedCategoryId = State(initialValue: resolvedCategoryId)
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

    private var selectedAccount: AccountDto? {
        usableAccounts.first(where: { $0.id == selectedAccountId }) ?? store.account(byId: selectedAccountId)
    }

    private var selectedToAccount: AccountDto? {
        guard let toId = selectedToAccountId else { return nil }
        return usableAccounts.first(where: { $0.id == toId }) ?? store.account(byId: toId)
    }

    private var selectedCategory: CategoryDto? {
        store.category(byId: selectedCategoryId)
    }

    private var selectedCategoryParent: CategoryDto? {
        selectedCategory?.parentId.flatMap { store.category(byId: $0) }
    }

    private var evaluatedEnteredDecimal: Decimal? {
        QuickExprEvaluator.evaluate(amountExpr)
    }

    private var hasValidEnteredAmount: Bool {
        guard let dec = evaluatedEnteredDecimal else { return false }
        return dec >= 0
    }

    private var enteredAmountMicros: Int64 {
        QuickExprEvaluator.evaluateToMicros(amountExpr) ?? 0
    }

    private var parsedDiscountMicros: Int64 {
        guard txType != TransactionType.INCOME else { return 0 }
        return max(Int64(0), MoneyAmount.parseToMicros(discountInput) ?? 0)
    }

    private var parsedFeeMicros: Int64 {
        max(Int64(0), MoneyAmount.parseToMicros(feeInput) ?? 0)
    }

    private var hasDiscountOrFee: Bool {
        parsedDiscountMicros > 0 || parsedFeeMicros > 0
    }

    private var nominalAmountMicros: Int64 {
        switch txType {
        case TransactionType.EXPENSE:
            return expenseAmountIsActualPaid
                ? (enteredAmountMicros + parsedDiscountMicros - parsedFeeMicros)
                : enteredAmountMicros
        case TransactionType.TRANSFER, TransactionType.INCOME:
            return enteredAmountMicros
        default:
            return enteredAmountMicros
        }
    }

    private var actualSubmitOrigMicros: Int64 {
        switch txType {
        case TransactionType.TRANSFER:
            return enteredAmountMicros - parsedDiscountMicros + parsedFeeMicros
        case TransactionType.EXPENSE:
            return expenseAmountIsActualPaid
                ? enteredAmountMicros
                : (enteredAmountMicros - parsedDiscountMicros + parsedFeeMicros)
        case TransactionType.INCOME:
            return enteredAmountMicros - parsedFeeMicros
        default:
            return enteredAmountMicros
        }
    }

    private var discountFeeFormulaError: String? {
        let trimmedAmount = amountExpr.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedAmount.isEmpty else { return nil }
        switch txType {
        case TransactionType.TRANSFER:
            if actualSubmitOrigMicros < 0 {
                return "优惠不能大于转账金额"
            }
            return nil
        case TransactionType.EXPENSE:
            if expenseAmountIsActualPaid {
                if hasValidEnteredAmount && (enteredAmountMicros > 0 || parsedDiscountMicros > 0) && nominalAmountMicros <= 0 {
                    return "手续费不能大于实付与优惠之和"
                }
                return nil
            } else {
                if actualSubmitOrigMicros < 0 {
                    return "优惠不能大于原价"
                }
                return nil
            }
        case TransactionType.INCOME:
            if actualSubmitOrigMicros < 0 {
                return "手续费不能大于收入金额"
            }
            return nil
        default:
            return nil
        }
    }

    private var discountFeeCapsuleLabel: String {
        let disc = parsedDiscountMicros
        let fee = parsedFeeMicros
        if disc <= 0 && fee <= 0 {
            return txType == TransactionType.INCOME ? "手续费" : "优惠/手续费"
        }
        let sym = CurrencyCatalog.find(origCurrency).symbol
        if disc > 0 && fee > 0 {
            return "惠\(sym)\(Self.formatCompactDecimalInput(disc))·费\(sym)\(Self.formatCompactDecimalInput(fee))"
        }
        if disc > 0 {
            return "惠 \(sym)\(Self.formatCompactDecimalInput(disc))"
        }
        return "费 \(sym)\(Self.formatCompactDecimalInput(fee))"
    }

    private var amountAccentColor: Color {
        switch txType {
        case TransactionType.INCOME: return palette.incomeColor
        case TransactionType.TRANSFER: return palette.transferColor
        default: return palette.expenseColor
        }
    }

    private var conversionPreview: ConversionPreview? {
        guard actualSubmitOrigMicros > 0, selectedAccountId > 0 else { return nil }
        let manualAcc = MoneyAmount.parseToMicros(manualAccountAmountText)
        let manualRate = MoneyAmount.parseRateToMicros(manualRateText)
        let dateStr = DefaultSeedData.formatISODate(epochMillis: Int64((txDate.timeIntervalSince1970 * 1000.0).rounded()))
        return store.previewConversion(
            origAmountMicros: actualSubmitOrigMicros,
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

    private var activeParentId: Int64? {
        guard let cat = selectedCategory else { return nil }
        return cat.parentId ?? cat.id
    }

    private var moreActiveCount: Int {
        var count = 0
        if !merchant.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { count += 1 }
        if reimburseStatus != ReimburseStatus.NONE { count += 1 }
        if excludeFromStats { count += 1 }
        if excludeFromBudget { count += 1 }
        return count
    }

    public var body: some View {
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                topHeaderBar
                amountAndCategoryBanner
                if let validationMessage {
                    Text(validationMessage)
                        .font(.caption.weight(.medium))
                        .foregroundColor(palette.dangerColor)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 14)
                        .padding(.bottom, 4)
                }

                mainWorkspaceArea
                    .frame(maxWidth: .infinity, maxHeight: .infinity)

                Divider().overlay(palette.outline)

                workbenchAttrBar
                if activePanel != .tags && !selectedTags.isEmpty {
                    selectedTagsSummaryStrip
                }
                independentNoteRow

                Divider().overlay(palette.outline)

                Group {
                    if shouldShowKeypad {
                        calculatorKeypadView
                    } else {
                        collapsedBottomSaveBar
                    }
                }
                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { _ in
                    isKeyboardVisible = true
                }
                .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { _ in
                    isKeyboardVisible = false
                    focusedField = nil
                }
            }
            .background(palette.background.ignoresSafeArea())

            if let toastText = inSheetToast {
                YubraeToastBanner(text: toastText, isError: false)
                    .padding(.top, 10)
                    .transition(.move(edge: .top).combined(with: .opacity))
                    .zIndex(50)
                    .allowsHitTesting(false)
            }
        }
        .onChange(of: amountExpr) { _ in
            validationMessage = nil
        }
        .onChange(of: discountInput) { _ in
            validationMessage = nil
        }
        .onChange(of: feeInput) { _ in
            validationMessage = nil
        }
        .onChange(of: expenseAmountIsActualPaid) { _ in
            validationMessage = nil
        }
        .onChange(of: activePanel) { newPanel in
            if newPanel != .discountFee && (focusedField == .discount || focusedField == .fee) {
                dismissSystemKeyboard()
            }
        }
        .onChange(of: txType) { newType in
            syncCategoryForTypeChange(newType)
            isCreatingCategory = false
            categorySearchQuery = ""
            if newType == TransactionType.INCOME {
                discountInput = ""
                expenseAmountIsActualPaid = false
            } else if newType == TransactionType.TRANSFER {
                expenseAmountIsActualPaid = false
            }
            if activePanel != .category {
                activePanel = .category
            }
        }
        .sheet(isPresented: $showCurrencySheet) {
            CurrencyPickerSheetView(
                selectedCode: $origCurrency,
                pinnedCodes: store.settings.pinnedCurrencies,
                palette: palette
            )
            .yubraeSheetStyle(palette: palette)
        }
        .yubraeConfirmDialog(
            isPresented: $showDeleteConfirm,
            palette: palette,
            icon: "trash.fill",
            title: "确认删除账单？",
            message: "删除后将自动回滚关联账户的余额变动。",
            confirmTitle: "删除",
            cancelTitle: "取消",
            isDestructive: true
        ) {
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
        .onDisappear {
            inSheetToastTask?.cancel()
            inSheetToastTask = nil
        }
    }

    // MARK: - 1. 顶栏

    private var topHeaderBar: some View {
        HStack(spacing: 6) {
            HStack(spacing: 0) {
                if editingTransaction != nil {
                    Button {
                        showDeleteConfirm = true
                    } label: {
                        Image(systemName: "trash")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(palette.dangerColor)
                            .frame(width: 32, height: 32)
                    }
                    .buttonStyle(.plain)
                } else if store.activeLedgers.count > 1 {
                    Menu {
                        ForEach(store.activeLedgers) { ledger in
                            Button {
                                selectedLedgerId = ledger.id
                            } label: {
                                Text(ledger.name)
                            }
                        }
                    } label: {
                        let ledger = store.ledgers.first(where: { $0.id == selectedLedgerId })
                        HStack(spacing: 4) {
                            Image(systemName: "book.closed.fill")
                                .font(.caption2)
                            Text(ledger?.name ?? "账本")
                                .font(.caption.weight(.semibold))
                                .lineLimit(1)
                                .truncationMode(.tail)
                        }
                        .foregroundColor(palette.primary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(palette.accentSoftFill))
                    }
                    .frame(maxWidth: 86, alignment: .leading)
                }
                Spacer(minLength: 0)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            HStack(spacing: 2) {
                ForEach([
                    (TransactionType.EXPENSE, "支出", palette.expenseColor),
                    (TransactionType.INCOME, "收入", palette.incomeColor),
                    (TransactionType.TRANSFER, "转账", palette.transferColor)
                ], id: \.0) { code, label, tint in
                    let isSelected = txType == code
                    Button {
                        txType = code
                    } label: {
                        Text(label)
                            .font(.caption.weight(isSelected ? .bold : .medium))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundColor(isSelected ? tint : palette.textSecondary)
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(
                                Capsule().fill(isSelected ? tint.opacity(0.15) : Color.clear)
                            )
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(3)
            .background(Capsule().fill(palette.surfaceSecondary))
            .fixedSize(horizontal: true, vertical: false)
            .layoutPriority(1)

            HStack(spacing: 0) {
                Spacer(minLength: 0)
                Button {
                    dismiss()
                } label: {
                    Image(systemName: "xmark")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textSecondary)
                        .frame(width: 32, height: 32)
                }
                .buttonStyle(.plain)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
        }
        .padding(.horizontal, 12)
        .padding(.top, 14)
        .padding(.bottom, 6)
    }

    // MARK: - 2. 独立金额与分类摘要卡

    private var amountAndCategoryBanner: some View {
        let curInfo = CurrencyCatalog.find(origCurrency)
        return VStack(spacing: 6) {
            HStack(spacing: 10) {
                Button {
                    activePanel = .category
                } label: {
                    HStack(spacing: 8) {
                        if txType == TransactionType.TRANSFER {
                            ZStack {
                                Circle()
                                    .fill(palette.transferColor.opacity(0.14))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "arrow.left.arrow.right")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(palette.transferColor)
                            }
                            VStack(alignment: .leading, spacing: 1) {
                                Text("\(selectedAccount?.name ?? "转出") → \(selectedToAccount?.name ?? "转入")")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(palette.textPrimary)
                                    .lineLimit(1)
                                Text("账户互转")
                                    .font(.caption2)
                                    .foregroundColor(palette.textSecondary)
                                    .lineLimit(1)
                            }
                        } else {
                            let cat = selectedCategory
                            YubraeCategoryIconBadge(
                                iconKey: cat?.icon ?? "category",
                                colorHex: cat?.colorHex ?? 0xFF2AA876,
                                size: 36
                            )
                            VStack(alignment: .leading, spacing: 1) {
                                Text(cat?.name ?? "选分类")
                                    .font(.subheadline.weight(.bold))
                                    .foregroundColor(palette.textPrimary)
                                    .lineLimit(1)
                                if let parent = selectedCategoryParent {
                                    Text(parent.name)
                                        .font(.caption2)
                                        .foregroundColor(palette.textSecondary)
                                        .lineLimit(1)
                                }
                            }
                        }
                    }
                }
                .buttonStyle(.plain)

                Spacer(minLength: 6)

                HStack(spacing: 6) {
                    Button {
                        showCurrencySheet = true
                    } label: {
                        HStack(spacing: 3) {
                            Text(curInfo.flag)
                                .font(.caption2)
                            Text(curInfo.code)
                                .font(.caption2.weight(.bold))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(palette.textPrimary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(palette.surfaceSecondary))
                    }
                    .buttonStyle(.plain)

                    Button {
                        dismissSystemKeyboard()
                    } label: {
                        VStack(alignment: .trailing, spacing: 1) {
                            Text("\(curInfo.symbol)\(amountExpr.isEmpty ? "0.00" : amountExpr)")
                                .font(.system(size: 26, weight: .bold, design: .rounded))
                                .monospacedDigit()
                                .foregroundColor(amountExpr.isEmpty ? amountAccentColor.opacity(0.45) : amountAccentColor)
                                .lineLimit(1)
                                .minimumScaleFactor(0.65)
                                .truncationMode(.head)
                                .layoutPriority(1)
                            if QuickExprEvaluator.containsOperator(amountExpr), enteredAmountMicros > 0 {
                                Text("= \(MoneyAmount.format(enteredAmountMicros, currencyCode: origCurrency))")
                                    .font(.caption2.weight(.medium))
                                    .monospacedDigit()
                                    .foregroundColor(amountAccentColor.opacity(0.85))
                                    .lineLimit(1)
                            }
                        }
                    }
                    .buttonStyle(.plain)
                    .layoutPriority(1)
                }
            }

            if hasDiscountOrFee {
                discountFeeFormulaPreviewBar
            }

            if let preview = conversionPreview,
               (preview.origCurrency != preview.accountCurrency || preview.origCurrency != preview.baseCurrency) {
                Button {
                    activePanel = .more
                    showForexOverride = true
                } label: {
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.caption2)
                            .foregroundColor(palette.primary)
                        let accStr = MoneyAmount.format(preview.accountAmountMicros, currencyCode: preview.accountCurrency, showCurrencyCode: true)
                        Text("实扣 \(accStr) · 汇率 \(MoneyAmount.formatRate(preview.rateToAccountMicros))")
                            .font(.caption2)
                            .monospacedDigit()
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                        Spacer(minLength: 6)
                        Text("调整")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .padding(.horizontal, 9)
                    .padding(.vertical, 5)
                    .background(RoundedRectangle(cornerRadius: 8).fill(palette.accentSoftFill.opacity(0.65)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 16, style: .continuous)
                .strokeBorder(palette.outline, lineWidth: 0.7)
        )
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }

    private var discountFeeFormulaPreviewBar: some View {
        let trimmedAmount = amountExpr.trimmingCharacters(in: .whitespacesAndNewlines)
        let formulaError = discountFeeFormulaError
        let disc = parsedDiscountMicros
        let fee = parsedFeeMicros

        return Button {
            activePanel = .discountFee
        } label: {
            if let err = formulaError {
                HStack(spacing: 6) {
                    Image(systemName: "exclamationmark.triangle.fill")
                        .font(.caption2)
                        .foregroundColor(palette.dangerColor)
                    Text(err)
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(palette.dangerColor)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 4)
                    Text("去调整")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(palette.dangerColor)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(palette.dangerColor.opacity(0.12))
                )
            } else {
                let equationText: String = {
                    if trimmedAmount.isEmpty {
                        return "输入金额后自动核算实付/到账"
                    }
                    let nomFmt = MoneyAmount.format(max(Int64(0), nominalAmountMicros), currencyCode: origCurrency)
                    let actFmt = MoneyAmount.format(max(Int64(0), actualSubmitOrigMicros), currencyCode: origCurrency)
                    switch txType {
                    case TransactionType.TRANSFER:
                        return "实扣 \(actFmt) → 到账 \(nomFmt)"
                    case TransactionType.INCOME:
                        return "原额 \(nomFmt) → 实收 \(actFmt)"
                    default:
                        return "原价 \(nomFmt) → 实付 \(actFmt)"
                    }
                }()

                HStack(spacing: 5) {
                    Text(equationText)
                        .font(.caption2.weight(.semibold).monospacedDigit())
                        .foregroundColor(trimmedAmount.isEmpty ? palette.textSecondary : palette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.75)
                    Spacer(minLength: 4)
                    if disc > 0 {
                        discountFeeMiniBadge(
                            "惠 \(MoneyAmount.format(disc, currencyCode: origCurrency))",
                            color: palette.incomeColor
                        )
                    }
                    if fee > 0 {
                        discountFeeMiniBadge(
                            "费 \(MoneyAmount.format(fee, currencyCode: origCurrency))",
                            color: palette.warningColor
                        )
                    }
                }
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(
                    RoundedRectangle(cornerRadius: 8, style: .continuous)
                        .fill(palette.surfaceSecondary)
                )
            }
        }
        .buttonStyle(.plain)
    }

    private func discountFeeMiniBadge(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 10, weight: .semibold).monospacedDigit())
            .foregroundColor(color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(Capsule().fill(color.opacity(0.14)))
    }

    // MARK: - 3. 中部大视野工作区

    @ViewBuilder
    private var mainWorkspaceArea: some View {
        switch activePanel {
        case .category:
            if txType == TransactionType.TRANSFER {
                transferWorkspaceView
            } else {
                categoryWorkspaceView
            }
        case .tags:
            tagsWorkspaceView
        case .account:
            accountWorkspaceView
        case .discountFee:
            discountFeeWorkspaceView
        case .dateTime:
            dateTimeWorkspaceView
        case .more:
            moreWorkspaceView
        }
    }

    private var categoryWorkspaceView: some View {
        VStack(spacing: 8) {
            if isCreatingCategory {
                inlineCreateCategoryView
            } else {
                HStack(spacing: 8) {
                    HStack(spacing: 6) {
                        Image(systemName: "magnifyingglass")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        TextField("搜索分类（如 外卖 / 地铁）", text: $categorySearchQuery)
                            .font(.subheadline)
                            .submitLabel(.done)
                        if !categorySearchQuery.isEmpty {
                            Button {
                                categorySearchQuery = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.caption)
                                    .foregroundColor(palette.textTertiary)
                            }
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 7)
                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(palette.outline, lineWidth: 0.7))

                    Button {
                        openInlineCategoryCreator(presetName: "", parentId: nil)
                    } label: {
                        Label("新建", systemImage: "plus")
                            .font(.caption.weight(.semibold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundColor(palette.primary)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 8)
                            .background(Capsule().fill(palette.accentSoftFill))
                    }
                    .buttonStyle(.plain)
                }
                .padding(.horizontal, 12)

                let trimmedQuery = categorySearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                if !trimmedQuery.isEmpty {
                    let results = IOSRecordUsageRanker.searchCategories(query: trimmedQuery, type: txType, categories: store.categories)
                    let exists = IOSRecordUsageRanker.hasExactCategoryName(name: trimmedQuery, type: txType, categories: store.categories)
                    ScrollView {
                        VStack(spacing: 8) {
                            if !exists {
                                Button {
                                    openInlineCategoryCreator(presetName: trimmedQuery, parentId: nil)
                                } label: {
                                    HStack(spacing: 10) {
                                        ZStack {
                                            Circle().fill(palette.primary).frame(width: 28, height: 28)
                                            Image(systemName: "plus").font(.caption.weight(.bold)).foregroundColor(palette.onPrimary)
                                        }
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text("新建「\(trimmedQuery)」分类")
                                                .font(.subheadline.weight(.bold))
                                                .foregroundColor(palette.onPrimaryContainer)
                                            Text("一键创建并自动选中")
                                                .font(.caption2)
                                                .foregroundColor(palette.onPrimaryContainer.opacity(0.8))
                                        }
                                        Spacer()
                                    }
                                    .padding(10)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(palette.accentSoftFill))
                                }
                                .buttonStyle(.plain)
                            }

                            ForEach(results) { item in
                                let isSel = selectedCategoryId == item.category.id
                                let catColor = Color(argbHex: item.category.colorHex)
                                Button {
                                    selectedCategoryId = item.category.id
                                    categorySearchQuery = ""
                                } label: {
                                    HStack(spacing: 10) {
                                        YubraeCategoryIconBadge(iconKey: item.category.icon, colorHex: item.category.colorHex, size: 32)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(item.category.name)
                                                .font(.subheadline.weight(isSel ? .bold : .medium))
                                                .foregroundColor(isSel ? catColor : palette.textPrimary)
                                            Text(item.parent.map { "隶属于 \($0.name)" } ?? "一级分类")
                                                .font(.caption2)
                                                .foregroundColor(palette.textSecondary)
                                        }
                                        Spacer()
                                        if isSel {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundColor(catColor)
                                        }
                                    }
                                    .padding(10)
                                    .background(
                                        RoundedRectangle(cornerRadius: 12)
                                            .fill(isSel ? catColor.opacity(0.12) : palette.surface)
                                    )
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                    }
                } else {
                    let frequent = IOSRecordUsageRanker.rankFrequentCategories(
                        type: txType,
                        categories: store.categories,
                        transactions: store.transactions
                    )
                    let parents = parentCategories
                    let rows = stride(from: 0, to: parents.count + 1, by: 5).map { idx -> [CategoryDto?] in
                        (idx..<min(idx + 5, parents.count + 1)).map { pos in
                            pos < parents.count ? parents[pos] : nil
                        }
                    }

                    ScrollView {
                        VStack(alignment: .leading, spacing: 10) {
                            if !frequent.isEmpty {
                                VStack(alignment: .leading, spacing: 5) {
                                    Text("常用直达")
                                        .font(.caption2.weight(.semibold))
                                        .foregroundColor(palette.textSecondary)
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 6) {
                                            ForEach(frequent) { cat in
                                                let isSel = selectedCategoryId == cat.id
                                                let catColor = Color(argbHex: cat.colorHex)
                                                Button {
                                                    selectedCategoryId = cat.id
                                                } label: {
                                                    HStack(spacing: 5) {
                                                        Image(systemName: DefaultSeedData.sfSymbol(for: cat.icon))
                                                            .font(.caption2.weight(.semibold))
                                                            .foregroundColor(catColor)
                                                        Text(cat.name)
                                                            .font(.caption.weight(isSel ? .bold : .medium))
                                                            .foregroundColor(isSel ? catColor : palette.textPrimary)
                                                    }
                                                    .padding(.horizontal, 10)
                                                    .padding(.vertical, 6)
                                                    .background(
                                                        Capsule().fill(isSel ? catColor.opacity(0.16) : palette.surface)
                                                    )
                                                    .overlay(
                                                        Capsule().strokeBorder(isSel ? catColor : palette.outline, lineWidth: isSel ? 1 : 0.6)
                                                    )
                                                }
                                                .buttonStyle(.plain)
                                            }
                                        }
                                    }
                                }
                            }

                            VStack(spacing: 6) {
                                ForEach(rows.indices, id: \.self) { rowIdx in
                                    let rowSlots = rows[rowIdx]
                                    HStack(spacing: 4) {
                                        ForEach(0..<5, id: \.self) { colIdx in
                                            if colIdx < rowSlots.count {
                                                if let cat = rowSlots[colIdx] {
                                                    let isActive = activeParentId == cat.id
                                                    let children = store.childCategories(of: cat.id)
                                                    let catColor = Color(argbHex: cat.colorHex)
                                                    Button {
                                                        selectedCategoryId = cat.id
                                                    } label: {
                                                        VStack(spacing: 4) {
                                                            ZStack(alignment: .bottomTrailing) {
                                                                ZStack {
                                                                    Circle()
                                                                        .fill(isActive ? catColor : catColor.opacity(0.14))
                                                                        .frame(width: 38, height: 38)
                                                                    Image(systemName: DefaultSeedData.sfSymbol(for: cat.icon))
                                                                        .font(.system(size: 16, weight: .semibold))
                                                                        .foregroundColor(isActive ? .white : catColor)
                                                                }
                                                                if !children.isEmpty {
                                                                    Circle()
                                                                        .fill(catColor)
                                                                        .frame(width: 7, height: 7)
                                                                        .overlay(Circle().stroke(palette.background, lineWidth: 1))
                                                                }
                                                            }
                                                            Text(cat.name)
                                                                .font(.caption2.weight(isActive ? .bold : .medium))
                                                                .foregroundColor(isActive ? catColor : palette.textPrimary)
                                                                .lineLimit(1)
                                                        }
                                                        .frame(maxWidth: .infinity)
                                                        .padding(.vertical, 5)
                                                        .background(
                                                            RoundedRectangle(cornerRadius: 11, style: .continuous)
                                                                .fill(isActive ? catColor.opacity(0.10) : Color.clear)
                                                        )
                                                    }
                                                    .buttonStyle(.plain)
                                                } else {
                                                    Button {
                                                        openInlineCategoryCreator(presetName: "", parentId: nil)
                                                    } label: {
                                                        VStack(spacing: 4) {
                                                            ZStack {
                                                                Circle()
                                                                    .fill(palette.primary.opacity(0.10))
                                                                    .frame(width: 38, height: 38)
                                                                    .overlay(Circle().strokeBorder(palette.primary.opacity(0.35), lineWidth: 0.9))
                                                                Image(systemName: "plus")
                                                                    .font(.system(size: 16, weight: .bold))
                                                                    .foregroundColor(palette.primary)
                                                            }
                                                            Text("新建")
                                                                .font(.caption2.weight(.semibold))
                                                                .foregroundColor(palette.primary)
                                                        }
                                                        .frame(maxWidth: .infinity)
                                                        .padding(.vertical, 5)
                                                    }
                                                    .buttonStyle(.plain)
                                                }
                                            } else {
                                                Spacer().frame(maxWidth: .infinity)
                                            }
                                        }
                                    }

                                    if let activePid = activeParentId,
                                       rowSlots.contains(where: { $0?.id == activePid }),
                                       let activeParent = store.category(byId: activePid) {
                                        let children = store.childCategories(of: activePid)
                                        if !children.isEmpty {
                                            VStack(alignment: .leading, spacing: 6) {
                                                Text("\(activeParent.name) · 细分")
                                                    .font(.caption2.weight(.semibold))
                                                    .foregroundColor(Color(argbHex: activeParent.colorHex))
                                                YubraeFlowLayout(spacing: 6, lineSpacing: 6) {
                                                    ForEach(children) { sub in
                                                        let subSel = selectedCategoryId == sub.id
                                                        let subColor = Color(argbHex: sub.colorHex)
                                                        Button {
                                                            selectedCategoryId = subSel ? activePid : sub.id
                                                        } label: {
                                                            HStack(spacing: 4) {
                                                                Image(systemName: DefaultSeedData.sfSymbol(for: sub.icon))
                                                                    .font(.caption2)
                                                                Text(sub.name)
                                                                    .font(.caption.weight(subSel ? .bold : .medium))
                                                            }
                                                            .foregroundColor(subSel ? .white : palette.textPrimary)
                                                            .padding(.horizontal, 10)
                                                            .padding(.vertical, 5)
                                                            .background(
                                                                Capsule().fill(subSel ? subColor : palette.surfaceSecondary)
                                                            )
                                                        }
                                                        .buttonStyle(.plain)
                                                    }
                                                    Button {
                                                        openInlineCategoryCreator(presetName: "", parentId: activePid)
                                                    } label: {
                                                        Label("子类", systemImage: "plus")
                                                            .font(.caption.weight(.semibold))
                                                            .foregroundColor(palette.primary)
                                                            .padding(.horizontal, 10)
                                                            .padding(.vertical, 5)
                                                            .background(
                                                                Capsule().strokeBorder(palette.primary.opacity(0.45), lineWidth: 0.8)
                                                            )
                                                    }
                                                    .buttonStyle(.plain)
                                                }
                                            }
                                            .padding(10)
                                            .frame(maxWidth: .infinity, alignment: .leading)
                                            .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                                            .overlay(RoundedRectangle(cornerRadius: 12).strokeBorder(Color(argbHex: activeParent.colorHex).opacity(0.3), lineWidth: 0.7))
                                        }
                                    }
                                }
                            }
                        }
                        .padding(.horizontal, 12)
                        .padding(.bottom, 8)
                    }
                }
            }
        }
    }

    private var inlineCreateCategoryView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 8) {
                    Button {
                        isCreatingCategory = false
                    } label: {
                        Image(systemName: "chevron.left")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.textPrimary)
                            .frame(width: 30, height: 30)
                            .background(Circle().fill(palette.surfaceSecondary))
                    }
                    Text(newCatParentId == nil ? "新建分类" : "新建二级分类")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    YubraeCategoryIconBadge(iconKey: newCatIcon, colorHex: newCatColorHex, size: 32)
                }

                TextField("输入分类名称（如 瑞幸咖啡 / 养车）", text: $newCatName)
                    .font(.subheadline)
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                    .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(palette.outline, lineWidth: 0.7))
                    .onChange(of: newCatName) { val in
                        newCatError = nil
                        let p = newCatParentId.flatMap { store.category(byId: $0) }
                        let suggested = IOSRecordUsageRanker.suggestCategoryStyle(name: val, parent: p)
                        if !newCatManualIcon { newCatIcon = suggested.icon }
                        if !newCatManualColor { newCatColorHex = suggested.colorHex }
                    }

                if let newCatError {
                    Text(newCatError)
                        .font(.caption)
                        .foregroundColor(palette.dangerColor)
                }

                Text("层级归属")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        Button {
                            newCatParentId = nil
                        } label: {
                            Text("作为一级大类")
                                .font(.caption.weight(newCatParentId == nil ? .bold : .medium))
                                .foregroundColor(newCatParentId == nil ? palette.onPrimary : palette.textPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(newCatParentId == nil ? palette.primary : palette.surfaceSecondary))
                        }
                        ForEach(parentCategories) { p in
                            let sel = newCatParentId == p.id
                            Button {
                                newCatParentId = p.id
                                if !newCatManualColor { newCatColorHex = p.colorHex }
                            } label: {
                                Text(p.name)
                                    .font(.caption.weight(sel ? .bold : .medium))
                                    .foregroundColor(sel ? .white : palette.textPrimary)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Capsule().fill(sel ? Color(argbHex: p.colorHex) : palette.surfaceSecondary))
                            }
                        }
                    }
                }

                Text("快捷图标")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(IOSRecordUsageRanker.quickCategoryIconKeys, id: \.self) { key in
                            let sel = newCatIcon == key
                            Button {
                                newCatManualIcon = true
                                newCatIcon = key
                            } label: {
                                ZStack {
                                    Circle()
                                        .fill(Color(argbHex: newCatColorHex).opacity(sel ? 0.22 : 0.12))
                                        .frame(width: 34, height: 34)
                                    Image(systemName: DefaultSeedData.sfSymbol(for: key))
                                        .font(.caption.weight(.semibold))
                                        .foregroundColor(Color(argbHex: newCatColorHex))
                                }
                                .overlay(Circle().strokeBorder(sel ? Color(argbHex: newCatColorHex) : Color.clear, lineWidth: 1.5))
                            }
                        }
                    }
                }

                Text("颜色")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(IOSRecordUsageRanker.selectableColorPalette, id: \.self) { hex in
                            let sel = newCatColorHex == hex
                            Button {
                                newCatManualColor = true
                                newCatColorHex = hex
                            } label: {
                                Circle()
                                    .fill(Color(argbHex: hex))
                                    .frame(width: 26, height: 26)
                                    .overlay(
                                        Image(systemName: "checkmark")
                                            .font(.caption2.weight(.bold))
                                            .foregroundColor(.white)
                                            .opacity(sel ? 1 : 0)
                                    )
                            }
                        }
                    }
                }

                Button {
                    saveInlineCategory()
                } label: {
                    Text("保存并选中")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(RoundedRectangle(cornerRadius: 12).fill(palette.primary))
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    private var tagsWorkspaceView: some View {
        let browser = IOSRecordUsageRanker.buildTagBrowserData(
            query: tagSearchQuery,
            selectedGroup: activeTagGroup,
            availableTags: store.tags,
            selectedTagNames: selectedTags,
            allTransactions: store.transactions
        )
        let trimmed = tagSearchQuery.trimmingCharacters(in: .whitespacesAndNewlines)

        return VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 8) {
                Text("选择标签")
                    .font(.subheadline.weight(.bold))
                    .lineLimit(1)
                if !selectedTags.isEmpty {
                    Text("已选 \(selectedTags.count)")
                        .font(.caption2.weight(.semibold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(palette.primary)
                        .padding(.horizontal, 7)
                        .padding(.vertical, 2)
                        .background(Capsule().fill(palette.accentSoftFill))
                }
                Spacer(minLength: 6)
                if !selectedTags.isEmpty {
                    Button("清空") {
                        selectedTags = []
                    }
                    .font(.caption.weight(.medium))
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
                    .foregroundColor(palette.dangerColor)
                }
                Button {
                    activePanel = .category
                } label: {
                    Text("完成")
                        .font(.caption.weight(.bold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(palette.onPrimary)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 5)
                        .background(Capsule().fill(palette.primary))
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, 12)

            HStack(spacing: 6) {
                Image(systemName: "magnifyingglass")
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)
                TextField("搜索标签，或直接输入新建标签名", text: $tagSearchQuery)
                    .font(.subheadline)
                    .onChange(of: tagSearchQuery) { val in
                        let c = val.trimmingCharacters(in: .whitespacesAndNewlines)
                        if !c.isEmpty {
                            newTagColorHex = IOSRecordUsageRanker.stableFallbackColorHex(c)
                        }
                    }
                if !tagSearchQuery.isEmpty {
                    Button {
                        tagSearchQuery = ""
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.caption)
                            .foregroundColor(palette.textTertiary)
                    }
                }
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 7)
            .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
            .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(palette.outline, lineWidth: 0.7))
            .padding(.horizontal, 12)

            if browser.canQuickCreate {
                VStack(spacing: 6) {
                    HStack(spacing: 8) {
                        Button {
                            quickCreateAndSelectTag(name: trimmed)
                        } label: {
                            HStack(spacing: 8) {
                                ZStack {
                                    Circle().fill(Color(argbHex: newTagColorHex)).frame(width: 24, height: 24)
                                    Image(systemName: "plus").font(.caption2.weight(.bold)).foregroundColor(.white)
                                }
                                VStack(alignment: .leading, spacing: 1) {
                                    Text("新建并选中 #\(trimmed)")
                                        .font(.caption.weight(.bold))
                                        .lineLimit(1)
                                        .foregroundColor(palette.onPrimaryContainer)
                                    Text("分组：\(newTagGroup)")
                                        .font(.caption2)
                                        .lineLimit(1)
                                        .foregroundColor(palette.onPrimaryContainer.opacity(0.8))
                                }
                                Spacer(minLength: 4)
                            }
                        }
                        .buttonStyle(.plain)

                        Button {
                            showTagCreateStyle.toggle()
                        } label: {
                            Text(showTagCreateStyle ? "收起" : "样式")
                                .font(.caption2.weight(.semibold))
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .foregroundColor(palette.primary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(palette.surface))
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(9)
                    .background(RoundedRectangle(cornerRadius: 11).fill(palette.accentSoftFill))

                    if showTagCreateStyle {
                        VStack(alignment: .leading, spacing: 6) {
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 6) {
                                    ForEach(IOSRecordUsageRanker.canonicalTagGroups, id: \.self) { grp in
                                        Button {
                                            newTagGroup = grp
                                        } label: {
                                            Text(grp)
                                                .font(.caption2.weight(newTagGroup == grp ? .bold : .medium))
                                                .foregroundColor(newTagGroup == grp ? palette.onPrimary : palette.textPrimary)
                                                .padding(.horizontal, 9)
                                                .padding(.vertical, 4)
                                                .background(Capsule().fill(newTagGroup == grp ? palette.primary : palette.surfaceSecondary))
                                        }
                                    }
                                }
                            }
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 7) {
                                    ForEach(IOSRecordUsageRanker.selectableColorPalette, id: \.self) { hex in
                                        Button {
                                            newTagColorHex = hex
                                        } label: {
                                            Circle()
                                                .fill(Color(argbHex: hex))
                                                .frame(width: 22, height: 22)
                                                .overlay(
                                                    Image(systemName: "checkmark")
                                                        .font(.system(size: 10, weight: .bold))
                                                        .foregroundColor(.white)
                                                        .opacity(newTagColorHex == hex ? 1 : 0)
                                                )
                                        }
                                    }
                                }
                            }
                        }
                        .padding(8)
                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                    }
                }
                .padding(.horizontal, 12)
            }

            if !selectedTags.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        ForEach(selectedTags, id: \.self) { name in
                            let colorHex = store.tags.first(where: { $0.name == name })?.colorHex
                                ?? IOSRecordUsageRanker.stableFallbackColorHex(name)
                            Button {
                                selectedTags.removeAll { $0 == name }
                            } label: {
                                HStack(spacing: 4) {
                                    Text("#\(name)")
                                    Image(systemName: "xmark")
                                        .font(.system(size: 9, weight: .bold))
                                }
                                .font(.caption2.weight(.bold))
                                .foregroundColor(Color(argbHex: colorHex))
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Capsule().fill(Color(argbHex: colorHex).opacity(0.15)))
                                .overlay(Capsule().strokeBorder(Color(argbHex: colorHex).opacity(0.7), lineWidth: 0.8))
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 12)
                }
            }

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(browser.groups, id: \.self) { grp in
                        let sel = activeTagGroup == grp
                        Button {
                            activeTagGroup = grp
                        } label: {
                            Text(grp)
                                .font(.caption.weight(sel ? .bold : .medium))
                                .foregroundColor(sel ? palette.primary : palette.textSecondary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Capsule().fill(sel ? palette.accentSoftFill : palette.surfaceSecondary))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.horizontal, 12)
            }

            ScrollView {
                VStack(alignment: .leading, spacing: 10) {
                    if browser.sections.isEmpty {
                        Text(trimmed.isEmpty ? "当前分组暂无标签" : "未找到「\(trimmed)」，点击上方即可一键新建")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                            .padding(.top, 8)
                    } else {
                        ForEach(browser.sections) { section in
                            VStack(alignment: .leading, spacing: 6) {
                                if activeTagGroup == IOSRecordUsageRanker.tagTabAll {
                                    Text("\(section.groupName) · \(section.tags.count)")
                                        .font(.caption2.weight(.bold))
                                        .foregroundColor(palette.textSecondary)
                                }
                                YubraeFlowLayout(spacing: 7, lineSpacing: 7) {
                                    ForEach(section.tags) { item in
                                        let isOn = selectedTags.contains(item.name)
                                        let tagColor = Color(argbHex: item.colorHex)
                                        Button {
                                            if isOn {
                                                selectedTags.removeAll { $0 == item.name }
                                            } else {
                                                selectedTags.append(item.name)
                                            }
                                        } label: {
                                            HStack(spacing: 4) {
                                                Circle()
                                                    .fill(tagColor)
                                                    .frame(width: 6, height: 6)
                                                Text(item.name)
                                                    .font(.caption.weight(isOn ? .bold : .medium))
                                                if isOn {
                                                    Image(systemName: "checkmark")
                                                        .font(.system(size: 9, weight: .bold))
                                                }
                                            }
                                            .foregroundColor(isOn ? tagColor : palette.textPrimary)
                                            .padding(.horizontal, 10)
                                            .padding(.vertical, 6)
                                            .background(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .fill(isOn ? tagColor.opacity(0.16) : palette.surface)
                                            )
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .strokeBorder(isOn ? tagColor : palette.outline, lineWidth: isOn ? 1 : 0.6)
                                            )
                                        }
                                        .buttonStyle(.plain)
                                    }
                                }
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
            }
        }
    }

    private var accountWorkspaceView: some View {
        let grouped = Dictionary(grouping: usableAccounts, by: \.groupType)
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(txType == TransactionType.TRANSFER ? "选择转出账户" : "选择结算账户")
                    .font(.subheadline.weight(.bold))
                Spacer()
                Button("返回分类") { activePanel = .category }
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
            }
            .padding(.horizontal, 12)

            ScrollView {
                VStack(alignment: .leading, spacing: 8) {
                    ForEach(AccountGroup.allCases) { grp in
                        if let list = grouped[grp.rawValue], !list.isEmpty {
                            Text(grp.labelZh)
                                .font(.caption2.weight(.semibold))
                                .foregroundColor(palette.textSecondary)
                                .padding(.top, 4)
                            ForEach(list) { acc in
                                let sel = selectedAccountId == acc.id
                                let accColor = Color(argbHex: acc.colorHex)
                                Button {
                                    selectedAccountId = acc.id
                                    if editingTransaction == nil && amountExpr.isEmpty {
                                        origCurrency = acc.currencyCode
                                    }
                                    if txType == TransactionType.TRANSFER && selectedToAccountId == acc.id {
                                        selectedToAccountId = usableAccounts.first(where: { $0.id != acc.id })?.id
                                    }
                                    activePanel = .category
                                } label: {
                                    HStack(spacing: 10) {
                                        YubraeCategoryIconBadge(iconKey: acc.icon, colorHex: acc.colorHex, size: 32)
                                        VStack(alignment: .leading, spacing: 1) {
                                            Text(acc.name)
                                                .font(.subheadline.weight(sel ? .bold : .medium))
                                                .foregroundColor(sel ? accColor : palette.textPrimary)
                                            Text("\(acc.currencyCode) · 余额 \(MoneyAmount.format(acc.balanceMicros, currencyCode: acc.currencyCode))")
                                                .font(.caption2)
                                                .monospacedDigit()
                                                .foregroundColor(palette.textSecondary)
                                        }
                                        Spacer()
                                        if sel {
                                            Image(systemName: "checkmark")
                                                .font(.caption.weight(.bold))
                                                .foregroundColor(accColor)
                                        }
                                    }
                                    .padding(10)
                                    .background(RoundedRectangle(cornerRadius: 12).fill(sel ? accColor.opacity(0.12) : palette.surface))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.bottom, 10)
            }
        }
    }

    private var discountFeeWorkspaceView: some View {
        let curInfo = CurrencyCatalog.find(origCurrency)
        let isIncome = txType == TransactionType.INCOME
        let isTransfer = txType == TransactionType.TRANSFER
        let isExpense = txType == TransactionType.EXPENSE

        return ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Text(isIncome ? "手续费设置" : "优惠与手续费")
                        .font(.subheadline.weight(.bold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                    Spacer(minLength: 6)
                    if !discountInput.isEmpty || !feeInput.isEmpty {
                        Button("清空") {
                            discountInput = ""
                            feeInput = ""
                        }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.dangerColor)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .buttonStyle(.plain)
                    }
                    Button {
                        dismissSystemKeyboard()
                        activePanel = .category
                    } label: {
                        Text("完成")
                            .font(.caption.weight(.bold))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundColor(palette.onPrimary)
                            .padding(.horizontal, 11)
                            .padding(.vertical, 5)
                            .background(Capsule().fill(palette.primary))
                    }
                    .buttonStyle(.plain)
                }

                if isExpense {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 8) {
                            discountModeCapsule(
                                title: "从金额减免/加收",
                                selected: !expenseAmountIsActualPaid
                            ) {
                                expenseAmountIsActualPaid = false
                            }
                            discountModeCapsule(
                                title: "当前已是实付",
                                selected: expenseAmountIsActualPaid
                            ) {
                                expenseAmountIsActualPaid = true
                            }
                        }
                        Text(
                            expenseAmountIsActualPaid
                                ? "上方金额直接记为最终实付，仅额外记录享受的优惠或手续费"
                                : "上方输入商品原价，自动减去优惠并加上手续费得到实际支付"
                        )
                        .font(.caption2)
                        .foregroundColor(palette.textSecondary)
                    }
                }

                if !isIncome {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 6) {
                            Text("优惠金额 (\(curInfo.code))")
                                .font(.caption.weight(.semibold))
                                .foregroundColor(palette.textPrimary)
                                .lineLimit(1)
                            discountFeeMiniBadge("省钱", color: palette.incomeColor)
                            Spacer(minLength: 0)
                        }
                        HStack(spacing: 8) {
                            Text(curInfo.symbol)
                                .font(.subheadline.weight(.bold))
                                .foregroundColor(palette.incomeColor)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                            TextField("0.00", text: $discountInput)
                                .keyboardType(.decimalPad)
                                .font(.subheadline.weight(.semibold).monospacedDigit())
                                .focused($focusedField, equals: .discount)
                            if !discountInput.isEmpty {
                                Button {
                                    discountInput = ""
                                } label: {
                                    Image(systemName: "xmark.circle.fill")
                                        .font(.caption)
                                        .foregroundColor(palette.textTertiary)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 9)
                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .strokeBorder(
                                    focusedField == .discount ? palette.incomeColor : palette.outline,
                                    lineWidth: focusedField == .discount ? 1.2 : 0.7
                                )
                        )
                        Text(
                            isTransfer
                                ? "例如充 ¥50 优惠 ¥2，目标账户到账 ¥50、转出实扣 ¥48（支持全额满减 0 元单）"
                                : "例如原价 ¥50 满减 ¥2，实际支付 ¥48（支持全额满减 0 元免单）"
                        )
                        .font(.caption2)
                        .foregroundColor(palette.textTertiary)
                    }
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 6) {
                        Text("手续费 (\(curInfo.code))")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.textPrimary)
                            .lineLimit(1)
                        discountFeeMiniBadge(isIncome ? "扣减" : "加收", color: palette.warningColor)
                        Spacer(minLength: 0)
                    }
                    HStack(spacing: 8) {
                        Text(curInfo.symbol)
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.warningColor)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                        TextField("0.00", text: $feeInput)
                            .keyboardType(.decimalPad)
                            .font(.subheadline.weight(.semibold).monospacedDigit())
                            .focused($focusedField, equals: .fee)
                        if !feeInput.isEmpty {
                            Button {
                                feeInput = ""
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.caption)
                                    .foregroundColor(palette.textTertiary)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 9)
                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .strokeBorder(
                                focusedField == .fee ? palette.warningColor : palette.outline,
                                lineWidth: focusedField == .fee ? 1.2 : 0.7
                            )
                    )
                    Text(
                        isTransfer
                            ? "例如提现 ¥100 手续费 ¥0.1，转出实扣 ¥100.1、目标到账 ¥100"
                            : (isIncome
                                ? "例如收款 ¥100 平台服务费 ¥0.6，实际入账 ¥99.4"
                                : "例如跨币种/分期服务费，将计入实际支付金额")
                    )
                    .font(.caption2)
                    .foregroundColor(palette.textTertiary)
                }

                if hasDiscountOrFee || enteredAmountMicros > 0 {
                    VStack(spacing: 6) {
                        let nomLabel = isTransfer ? "目标到账面值" : (isIncome ? "应收原额" : "商品原价")
                        let actLabel = isTransfer ? "转出实扣" : (isIncome ? "实际入账" : "实际支付")
                        discountFeeBreakdownRow(
                            label: nomLabel,
                            value: MoneyAmount.format(max(Int64(0), nominalAmountMicros), currencyCode: origCurrency),
                            valueColor: palette.textPrimary
                        )
                        if parsedDiscountMicros > 0 {
                            discountFeeBreakdownRow(
                                label: "已享优惠",
                                value: "-\(MoneyAmount.format(parsedDiscountMicros, currencyCode: origCurrency))",
                                valueColor: palette.incomeColor
                            )
                        }
                        if parsedFeeMicros > 0 {
                            discountFeeBreakdownRow(
                                label: "手续费",
                                value: "\(isIncome ? "-" : "+")\(MoneyAmount.format(parsedFeeMicros, currencyCode: origCurrency))",
                                valueColor: palette.warningColor
                            )
                        }
                        Divider().overlay(palette.outline)
                        if let err = discountFeeFormulaError {
                            Text(err)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(palette.dangerColor)
                                .frame(maxWidth: .infinity, alignment: .leading)
                        } else {
                            discountFeeBreakdownRow(
                                label: actLabel,
                                value: MoneyAmount.format(max(Int64(0), actualSubmitOrigMicros), currencyCode: origCurrency),
                                valueColor: amountAccentColor,
                                bold: true
                            )
                        }
                    }
                    .padding(11)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.surface))
                    .overlay(RoundedRectangle(cornerRadius: 12, style: .continuous).strokeBorder(palette.outline, lineWidth: 0.6))
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    private func discountModeCapsule(title: String, selected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.caption.weight(selected ? .bold : .medium))
                .foregroundColor(selected ? palette.primary : palette.textSecondary)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .padding(.horizontal, 8)
                .background(
                    Capsule().fill(selected ? palette.accentSoftFill : palette.surface)
                )
                .overlay(
                    Capsule().strokeBorder(selected ? palette.primary : palette.outline, lineWidth: selected ? 1 : 0.7)
                )
        }
        .buttonStyle(.plain)
    }

    private func discountFeeBreakdownRow(
        label: String,
        value: String,
        valueColor: Color,
        bold: Bool = false
    ) -> some View {
        HStack(spacing: 8) {
            Text(label)
                .font(bold ? .caption.weight(.bold) : .caption)
                .foregroundColor(bold ? palette.textPrimary : palette.textSecondary)
                .lineLimit(1)
            Spacer(minLength: 6)
            Text(value)
                .font((bold ? Font.subheadline.weight(.bold) : Font.caption.weight(.semibold)).monospacedDigit())
                .foregroundColor(valueColor)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
    }

    private var transferWorkspaceView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    transferTile(title: "转出账户", account: selectedAccount)
                    Button {
                        if let from = selectedAccount, let to = selectedToAccount {
                            selectedAccountId = to.id
                            selectedToAccountId = from.id
                            origCurrency = to.currencyCode
                        }
                    } label: {
                        Image(systemName: "arrow.left.arrow.right")
                            .font(.subheadline.weight(.bold))
                            .foregroundColor(palette.primary)
                            .frame(width: 34, height: 34)
                            .background(Circle().fill(palette.accentSoftFill))
                    }
                    transferTile(title: "转入账户", account: selectedToAccount)
                }

                Text("选择转出")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(usableAccounts) { acc in
                            transferAccountPill(acc: acc, selected: selectedAccountId == acc.id) {
                                selectedAccountId = acc.id
                                origCurrency = acc.currencyCode
                                if selectedToAccountId == acc.id {
                                    selectedToAccountId = usableAccounts.first(where: { $0.id != acc.id })?.id
                                }
                            }
                        }
                    }
                }

                Text("选择转入")
                    .font(.caption2.weight(.semibold))
                    .foregroundColor(palette.textSecondary)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(usableAccounts.filter { $0.id != selectedAccountId }) { acc in
                            transferAccountPill(acc: acc, selected: selectedToAccountId == acc.id) {
                                selectedToAccountId = acc.id
                            }
                        }
                    }
                }

                if let fromAcc = selectedAccount,
                   let toAcc = selectedToAccount,
                   fromAcc.currencyCode != toAcc.currencyCode {
                    HStack {
                        Text("实到金额 (\(toAcc.currencyCode))")
                            .font(.caption)
                            .foregroundColor(palette.textSecondary)
                        TextField("留空自动换算", text: $manualToAmountText)
                            .keyboardType(.decimalPad)
                            .font(.subheadline)
                            .multilineTextAlignment(.trailing)
                    }
                    .padding(10)
                    .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    private func transferTile(title: String, account: AccountDto?) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(title)
                .font(.caption2)
                .foregroundColor(palette.textSecondary)
            Text(account?.name ?? "请选择")
                .font(.subheadline.weight(.bold))
                .foregroundColor(palette.textPrimary)
                .lineLimit(1)
            Text(account?.currencyCode ?? "-")
                .font(.caption2)
                .foregroundColor(palette.textSecondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(10)
        .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
    }

    private func transferAccountPill(acc: AccountDto, selected: Bool, onTap: @escaping () -> Void) -> some View {
        let color = Color(argbHex: acc.colorHex)
        return Button(action: onTap) {
            HStack(spacing: 5) {
                Circle().fill(color).frame(width: 7, height: 7)
                Text("\(acc.name) · \(acc.currencyCode)")
                    .font(.caption.weight(selected ? .bold : .medium))
            }
            .foregroundColor(selected ? color : palette.textPrimary)
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Capsule().fill(selected ? color.opacity(0.14) : palette.surface))
            .overlay(Capsule().strokeBorder(selected ? color : palette.outline, lineWidth: selected ? 1 : 0.6))
        }
        .buttonStyle(.plain)
    }

    private var dateTimeWorkspaceView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("日期与时间")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Button("返回分类") { activePanel = .category }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.primary)
                }

                HStack(spacing: 8) {
                    ForEach([
                        ("此刻", Date()),
                        ("今天", Calendar.current.date(bySettingHour: Calendar.current.component(.hour, from: txDate), minute: Calendar.current.component(.minute, from: txDate), second: 0, of: Date()) ?? Date()),
                        ("昨天", Calendar.current.date(byAdding: .day, value: -1, to: txDate) ?? Date())
                    ], id: \.0) { label, targetDate in
                        Button {
                            txDate = targetDate
                        } label: {
                            Text(label)
                                .font(.caption.weight(.semibold))
                                .foregroundColor(palette.primary)
                                .padding(.horizontal, 12)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(palette.accentSoftFill))
                        }
                    }
                }

                DatePicker("记账时间", selection: $txDate, displayedComponents: [.date, .hourAndMinute])
                    .environment(\.calendar, YubraePeriodHelper.sundayCalendar())
                    .font(.subheadline)
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    private var moreWorkspaceView: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                HStack {
                    Text("商户、报销与高级设置")
                        .font(.subheadline.weight(.bold))
                    Spacer()
                    Button("返回分类") { activePanel = .category }
                        .font(.caption.weight(.semibold))
                        .foregroundColor(palette.primary)
                }

                VStack(alignment: .leading, spacing: 5) {
                    Text("商户或交易对象")
                        .font(.caption2.weight(.semibold))
                        .foregroundColor(palette.textSecondary)
                    TextField("如：星巴克、京东超市", text: $merchant)
                        .font(.subheadline)
                        .padding(10)
                        .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
                }

                if txType == TransactionType.EXPENSE {
                    VStack(alignment: .leading, spacing: 5) {
                        Text("报销状态")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                        Picker("报销状态", selection: $reimburseStatus) {
                            Text("无需报销").tag(ReimburseStatus.NONE)
                            Text("待报销").tag(ReimburseStatus.PENDING)
                            Text("已报销").tag(ReimburseStatus.REIMBURSED)
                        }
                        .pickerStyle(.segmented)
                    }
                }

                VStack(spacing: 8) {
                    Toggle("不计入收支统计", isOn: $excludeFromStats).font(.subheadline)
                    Divider()
                    Toggle("不计入预算消耗", isOn: $excludeFromBudget).font(.subheadline)
                }
                .padding(12)
                .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))

                if let preview = conversionPreview,
                   (preview.origCurrency != preview.accountCurrency || showForexOverride) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("汇损与汇率手动覆写 (\(preview.origCurrency) → \(preview.accountCurrency))")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.textSecondary)
                        HStack(spacing: 8) {
                            TextField("实扣金额(\(preview.accountCurrency))", text: $manualAccountAmountText)
                                .keyboardType(.decimalPad)
                                .font(.subheadline)
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                            TextField("自定义汇率", text: $manualRateText)
                                .keyboardType(.decimalPad)
                                .font(.subheadline)
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                        }
                    }
                    .padding(12)
                    .background(RoundedRectangle(cornerRadius: 12).fill(palette.surface))
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 10)
        }
    }

    // MARK: - 4. 底部高频工作台（精简 5 胶囊 + 独立备注 + 计算器键盘）

    private var workbenchAttrBar: some View {
        let dfSelected = activePanel == .discountFee || hasDiscountOrFee
        let dfTint: Color? = {
            if activePanel == .discountFee { return palette.primary }
            if parsedDiscountMicros > 0 && parsedFeeMicros == 0 { return palette.incomeColor }
            if parsedFeeMicros > 0 && parsedDiscountMicros == 0 { return palette.warningColor }
            if hasDiscountOrFee { return palette.primary }
            return nil
        }()

        return ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                attrButton(
                    icon: "wallet.pass.fill",
                    label: selectedAccount?.name ?? "账户",
                    selected: activePanel == .account || selectedAccount != nil,
                    maxLabelWidth: 120
                ) {
                    activePanel = activePanel == .account ? .category : .account
                }

                attrButton(
                    icon: "percent",
                    label: discountFeeCapsuleLabel,
                    selected: dfSelected,
                    accentTint: dfTint
                ) {
                    if activePanel == .discountFee {
                        dismissSystemKeyboard()
                        activePanel = .category
                    } else {
                        activePanel = .discountFee
                    }
                }

                attrButton(
                    icon: "clock.fill",
                    label: formatCompactDateTime(),
                    selected: activePanel == .dateTime
                ) {
                    activePanel = activePanel == .dateTime ? .category : .dateTime
                }

                attrButton(
                    icon: "tag.fill",
                    label: selectedTags.isEmpty ? "标签" : "标签(\(selectedTags.count))",
                    selected: activePanel == .tags || !selectedTags.isEmpty
                ) {
                    activePanel = activePanel == .tags ? .category : .tags
                }

                attrButton(
                    icon: "slider.horizontal.3",
                    label: moreActiveCount == 0 ? "更多" : "更多(\(moreActiveCount))",
                    selected: activePanel == .more || moreActiveCount > 0
                ) {
                    activePanel = activePanel == .more ? .category : .more
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
        }
    }

    private func attrButton(
        icon: String,
        label: String,
        selected: Bool,
        maxLabelWidth: CGFloat? = nil,
        accentTint: Color? = nil,
        action: @escaping () -> Void
    ) -> some View {
        let fgColor: Color = selected ? (accentTint ?? palette.primary) : palette.textSecondary
        let bgColor: Color = selected
            ? (accentTint.map { $0.opacity(0.14) } ?? palette.accentSoftFill)
            : palette.surfaceSecondary
        return Button(action: action) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.caption2)
                if let maxLabelWidth {
                    Text(label)
                        .font(.caption2.weight(selected ? .semibold : .medium).monospacedDigit())
                        .lineLimit(1)
                        .truncationMode(.tail)
                        .frame(maxWidth: maxLabelWidth)
                } else {
                    Text(label)
                        .font(.caption2.weight(selected ? .semibold : .medium).monospacedDigit())
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
            }
            .foregroundColor(fgColor)
            .padding(.vertical, 7)
            .padding(.horizontal, 10)
            .background(Capsule().fill(bgColor))
        }
        .buttonStyle(.plain)
    }

    private var selectedTagsSummaryStrip: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(selectedTags, id: \.self) { name in
                    let hex = store.tags.first(where: { $0.name == name })?.colorHex
                        ?? IOSRecordUsageRanker.stableFallbackColorHex(name)
                    Button {
                        activePanel = .tags
                    } label: {
                        Text("#\(name)")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(Color(argbHex: hex))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(Color(argbHex: hex).opacity(0.14)))
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 12)
            .padding(.bottom, 5)
        }
    }

    private var independentNoteRow: some View {
        HStack(spacing: 8) {
            Image(systemName: "note.text")
                .font(.caption)
                .foregroundColor(palette.textSecondary)
            TextField(merchant.isEmpty ? "添加备注…" : "备注（商户：\(merchant)）", text: $note)
                .font(.subheadline)
                .focused($focusedField, equals: .note)
                .submitLabel(.done)
            if !note.isEmpty {
                Button {
                    note = ""
                } label: {
                    Image(systemName: "xmark.circle.fill")
                        .font(.caption)
                        .foregroundColor(palette.textTertiary)
                }
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 7)
        .background(RoundedRectangle(cornerRadius: 10).fill(palette.surface))
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(palette.outline, lineWidth: 0.6))
        .padding(.horizontal, 12)
        .padding(.bottom, 6)
    }

    private var calculatorKeypadView: some View {
        let isEdit = editingTransaction != nil

        return VStack(spacing: 5) {
            HStack(spacing: 5) {
                keypadButton("清零", secondary: true) { amountExpr = "" }
                keypadButton("÷", isOp: true) { amountExpr = QuickExprEvaluator.appendOperator(amountExpr, op: "÷") }
                keypadButton("×", isOp: true) { amountExpr = QuickExprEvaluator.appendOperator(amountExpr, op: "×") }
                keypadIconButton("delete.left.fill") {
                    if !amountExpr.isEmpty { amountExpr.removeLast() }
                }
            }
            HStack(spacing: 5) {
                keypadButton("7") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "7") }
                keypadButton("8") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "8") }
                keypadButton("9") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "9") }
                keypadButton("-", isOp: true) { amountExpr = QuickExprEvaluator.appendOperator(amountExpr, op: "-") }
            }
            HStack(spacing: 5) {
                keypadButton("4") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "4") }
                keypadButton("5") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "5") }
                keypadButton("6") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "6") }
                keypadButton("+", isOp: true) { amountExpr = QuickExprEvaluator.appendOperator(amountExpr, op: "+") }
            }
            GeometryReader { geo in
                let spacing: CGFloat = 5
                let colWidth = max(0, (geo.size.width - spacing * 3) / 4)
                let leftThreeColsWidth = max(0, colWidth * 3 + spacing * 2)

                HStack(spacing: spacing) {
                    VStack(spacing: spacing) {
                        HStack(spacing: spacing) {
                            keypadButton("1") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "1") }
                            keypadButton("2") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "2") }
                            keypadButton("3") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "3") }
                        }
                        HStack(spacing: spacing) {
                            keypadButton("00") {
                                amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "0")
                                amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "0")
                            }
                            keypadButton("0") { amountExpr = QuickExprEvaluator.appendDigit(amountExpr, digit: "0") }
                            keypadButton(".") { amountExpr = QuickExprEvaluator.appendDot(amountExpr) }
                        }
                    }
                    .frame(width: leftThreeColsWidth)

                    if !isEdit {
                        VStack(spacing: spacing) {
                            Button {
                                saveBill(continueNext: true)
                            } label: {
                                Text("再记")
                                    .font(.subheadline.weight(.bold))
                                    .lineLimit(1)
                                    .foregroundColor(palette.primary)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(RoundedRectangle(cornerRadius: 11).fill(palette.accentSoftFill))
                            }
                            .buttonStyle(.plain)
                            Button {
                                saveBill(continueNext: false)
                            } label: {
                                Text("完成")
                                    .font(.subheadline.weight(.bold))
                                    .lineLimit(1)
                                    .foregroundColor(palette.onPrimary)
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 42)
                                    .background(RoundedRectangle(cornerRadius: 11).fill(palette.primary))
                            }
                            .buttonStyle(.plain)
                        }
                        .frame(width: colWidth)
                    } else {
                        Button {
                            saveBill(continueNext: false)
                        } label: {
                            Text("保存")
                                .font(.headline.weight(.bold))
                                .lineLimit(1)
                                .foregroundColor(palette.onPrimary)
                                .frame(maxWidth: .infinity)
                                .frame(height: 89)
                                .background(RoundedRectangle(cornerRadius: 11).fill(palette.primary))
                        }
                        .buttonStyle(.plain)
                        .frame(width: colWidth)
                    }
                }
            }
            .frame(height: 89)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 8)
        .background(palette.surface.ignoresSafeArea(edges: .bottom))
    }

    private func keypadButton(_ title: String, isOp: Bool = false, secondary: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(secondary ? .caption.weight(.semibold) : .title3.weight(.semibold))
                .monospacedDigit()
                .lineLimit(1)
                .foregroundColor(isOp ? palette.primary : (secondary ? palette.textSecondary : palette.textPrimary))
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(
                    RoundedRectangle(cornerRadius: 11)
                        .fill(isOp ? palette.accentSoftFill : palette.surfaceSecondary)
                )
        }
        .buttonStyle(.plain)
    }

    private func keypadIconButton(_ systemName: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Image(systemName: systemName)
                .font(.subheadline.weight(.semibold))
                .foregroundColor(palette.textSecondary)
                .frame(maxWidth: .infinity)
                .frame(height: 42)
                .background(RoundedRectangle(cornerRadius: 11).fill(palette.surfaceSecondary))
        }
        .buttonStyle(.plain)
    }

    private func dismissSystemKeyboard() {
        focusedField = nil
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    private var collapsedBottomSaveBar: some View {
        HStack(spacing: 8) {
            if editingTransaction == nil {
                Button {
                    saveBill(continueNext: true)
                } label: {
                    Text("再记一笔")
                        .font(.subheadline.weight(.bold))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                        .foregroundColor(palette.primary)
                        .padding(.horizontal, 14)
                        .padding(.vertical, 11)
                        .background(RoundedRectangle(cornerRadius: 11).fill(palette.accentSoftFill))
                }
                .buttonStyle(.plain)
            }
            Button {
                saveBill(continueNext: false)
            } label: {
                Text(editingTransaction == nil ? "完成记账" : "保存修改")
                    .font(.subheadline.weight(.bold))
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    .foregroundColor(palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 11)
                    .background(RoundedRectangle(cornerRadius: 11).fill(palette.primary))
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 8)
        .background(palette.surface.ignoresSafeArea(edges: .bottom))
    }

    // MARK: - Helpers & Actions

    private func formatCompactDateTime() -> String {
        let cal = Calendar.current
        let df = DateFormatter()
        df.dateFormat = "HH:mm"
        let timePart = df.string(from: txDate)
        if cal.isDateInToday(txDate) { return "今天 \(timePart)" }
        if cal.isDateInYesterday(txDate) { return "昨天 \(timePart)" }
        let md = DateFormatter()
        md.dateFormat = "M/d HH:mm"
        return md.string(from: txDate)
    }

    private func openInlineCategoryCreator(presetName: String, parentId: Int64?) {
        let p = parentId.flatMap { store.category(byId: $0) }
        let suggested = IOSRecordUsageRanker.suggestCategoryStyle(name: presetName, parent: p)
        newCatName = presetName
        newCatParentId = parentId
        newCatIcon = suggested.icon
        newCatColorHex = suggested.colorHex
        newCatManualIcon = false
        newCatManualColor = false
        newCatError = nil
        categorySearchQuery = ""
        isCreatingCategory = true
    }

    private func saveInlineCategory() {
        let trimmed = newCatName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            newCatError = "请输入分类名称"
            return
        }
        if store.categories.contains(where: {
            $0.type.caseInsensitiveCompare(txType) == .orderedSame &&
                $0.parentId == newCatParentId &&
                $0.name.trimmingCharacters(in: .whitespacesAndNewlines).caseInsensitiveCompare(trimmed) == .orderedSame
        }) {
            newCatError = "该层级已存在同名分类"
            return
        }
        let maxOrder = store.categories
            .filter { $0.type.caseInsensitiveCompare(txType) == .orderedSame }
            .map(\.sortOrder).max() ?? 100
        let saved = store.saveCategory(
            CategoryDto(
                parentId: newCatParentId,
                name: trimmed,
                type: txType,
                icon: newCatIcon,
                colorHex: newCatColorHex,
                sortOrder: maxOrder + 10,
                isSystem: false
            )
        )
        selectedCategoryId = saved.id
        isCreatingCategory = false
        showInSheetToast("已新建分类「\(saved.name)」")
    }

    private func quickCreateAndSelectTag(name: String) {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return }
        if !selectedTags.contains(clean) {
            selectedTags.append(clean)
        }
        let nextOrder = (store.tags.map(\.sortOrder).max() ?? 280) + 10
        _ = store.saveTag(
            TagDto(
                name: clean,
                groupName: showTagCreateStyle ? newTagGroup : "自定义",
                colorHex: showTagCreateStyle ? newTagColorHex : IOSRecordUsageRanker.stableFallbackColorHex(clean),
                sortOrder: nextOrder,
                isSystem: false,
                icon: ""
            )
        )
        tagSearchQuery = ""
        showTagCreateStyle = false
    }

    private func syncCategoryForTypeChange(_ newType: String) {
        if newType == TransactionType.TRANSFER {
            selectedCategoryId = nil
            if selectedToAccountId == nil || selectedToAccountId == selectedAccountId {
                selectedToAccountId = usableAccounts.first(where: { $0.id != selectedAccountId })?.id
            }
            return
        }
        if let currentId = selectedCategoryId,
           let cat = store.category(byId: currentId),
           cat.type.caseInsensitiveCompare(newType) == .orderedSame {
            return
        }
        selectedCategoryId = store.parentCategories(for: newType).first?.id
    }

    private func saveBill(continueNext: Bool) {
        validationMessage = nil
        let discount = parsedDiscountMicros
        let fee = parsedFeeMicros
        let actualOrig = actualSubmitOrigMicros
        let nominal = nominalAmountMicros

        if txType == TransactionType.EXPENSE && expenseAmountIsActualPaid {
            guard hasValidEnteredAmount else {
                validationMessage = QuickExprEvaluator.containsOperator(amountExpr)
                    ? "请输入有效金额或完善算式"
                    : "请输入有效的实付金额"
                return
            }
            if actualOrig < 0 {
                validationMessage = "实付金额不能为负数"
                return
            }
            if nominal <= 0 {
                validationMessage = fee > 0 ? "手续费不能大于实付与优惠之和" : "请输入大于 0 的有效金额"
                return
            }
            if actualOrig == 0 && discount <= 0 {
                validationMessage = "请输入大于 0 的有效金额"
                return
            }
        } else {
            guard enteredAmountMicros > 0 else {
                validationMessage = QuickExprEvaluator.containsOperator(amountExpr)
                    ? "请输入有效金额或完善算式"
                    : "请输入大于 0 的有效金额"
                return
            }
            if actualOrig < 0 {
                validationMessage = discountFeeFormulaError ?? "优惠或手续费超出金额范围"
                return
            }
            if nominal <= 0 {
                validationMessage = "请输入大于 0 的有效金额"
                return
            }
            if actualOrig == 0 && txType != TransactionType.INCOME && discount <= 0 {
                validationMessage = "请输入大于 0 的有效金额"
                return
            }
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
            origAmountMicros: actualOrig,
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
            reimbursedTxId: editingTransaction?.reimbursedTxId,
            discountAmountMicros: txType == TransactionType.INCOME ? 0 : discount,
            feeAmountMicros: fee
        )

        do {
            _ = try store.saveTransaction(request)
            if continueNext {
                amountExpr = ""
                discountInput = ""
                feeInput = ""
                expenseAmountIsActualPaid = false
                merchant = ""
                note = ""
                selectedTags = []
                reimburseStatus = ReimburseStatus.NONE
                excludeFromStats = false
                excludeFromBudget = false
                manualAccountAmountText = ""
                manualRateText = ""
                manualToAmountText = ""
                activePanel = .category
                dismissSystemKeyboard()
                showInSheetToast("已记一笔，可继续记录")
            } else {
                store.toastMessage = editingTransaction == nil ? "已保存一笔新账单" : "账单修改已保存"
                dismiss()
            }
        } catch {
            validationMessage = error.localizedDescription
        }
    }

    private static func formatCompactDecimalInput(_ amountMicros: Int64) -> String {
        let absMicros: Int64 = amountMicros == Int64.min ? Int64.max : abs(amountMicros)
        let dec = MoneyAmount.microsToDecimal(absMicros)
        let formatter = NumberFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.numberStyle = .decimal
        formatter.usesGroupingSeparator = false
        formatter.minimumFractionDigits = 0
        formatter.maximumFractionDigits = 4
        formatter.roundingMode = .halfUp
        return formatter.string(from: NSDecimalNumber(decimal: dec)) ?? "0"
    }

    private func showInSheetToast(_ message: String) {
        inSheetToastTask?.cancel()
        withAnimation(.spring(response: 0.32, dampingFraction: 0.85)) {
            inSheetToast = message
        }
        inSheetToastTask = Task {
            try? await Task.sleep(nanoseconds: 2_000_000_000)
            guard !Task.isCancelled else { return }
            await MainActor.run {
                guard !Task.isCancelled else { return }
                withAnimation(.easeOut(duration: 0.22)) {
                    inSheetToast = nil
                }
            }
        }
    }
}

private struct CurrencyPickerSheetView: View {
    @Binding var selectedCode: String
    let pinnedCodes: [String]
    let palette: YubraePalette
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            List {
                ForEach(CurrencyCatalog.orderedCodes(pinned: pinnedCodes), id: \.self) { code in
                    let info = CurrencyCatalog.find(code)
                    let isSel = code == selectedCode
                    Button {
                        selectedCode = code
                        dismiss()
                    } label: {
                        HStack {
                            Text(info.flag)
                            Text("\(info.code) · \(info.nameZh)")
                                .foregroundColor(palette.textPrimary)
                            Spacer()
                            if isSel {
                                Image(systemName: "checkmark")
                                    .foregroundColor(palette.primary)
                            }
                        }
                    }
                }
            }
            .navigationTitle("选择币种")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
            }
        }
    }
}
