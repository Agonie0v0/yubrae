import SwiftUI

/// Linear / 苹果备忘录风格极简「严格双行对称」账单行组件（供明细列表直接复用）。
internal struct TransactionRowView: View {
    let tx: TransactionDto
    let category: CategoryDto?
    let parentCategory: CategoryDto?
    let account: AccountDto?
    let toAccount: AccountDto?
    let refundedMicros: Int64
    let baseCurrency: String
    let isMasked: Bool
    let palette: YubraePalette

    private struct RowMicroBadge: Identifiable {
        let id: String
        let text: String
        let color: Color
    }

    private var iconSpec: (key: String, colorHex: Int64) {
        switch tx.type {
        case TransactionType.TRANSFER:
            return ("currency_exchange", 0xFF2563EB)
        case TransactionType.ADJUST:
            return ("calculate", 0xFF64748B)
        default:
            return (category?.icon ?? "category", category?.colorHex ?? 0xFF2AA876)
        }
    }

    private var line1Title: String {
        switch tx.type {
        case TransactionType.TRANSFER:
            return "\(account?.name ?? "账户") → \(toAccount?.name ?? "目标账户")"
        case TransactionType.ADJUST:
            return "余额校准"
        default:
            guard let cat = category else { return "未分类" }
            if let parent = parentCategory, !parent.name.isEmpty, parent.name != cat.name {
                return "\(parent.name)-\(cat.name)"
            }
            return cat.name
        }
    }

    private var microBadges: [RowMicroBadge] {
        var badges: [RowMicroBadge] = []

        if tx.status == TransactionStatus.PENDING {
            badges.append(RowMicroBadge(id: "pending", text: "待确认", color: palette.warningColor))
        }
        if tx.type == TransactionType.EXPENSE && refundedMicros > 0 {
            let refStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    refundedMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCurrency,
                    showSign: false
                ),
                isMasked: isMasked
            )
            badges.append(RowMicroBadge(id: "refunded", text: "退\(refStr)", color: palette.incomeColor))
        }
        if tx.type == TransactionType.INCOME && tx.refundOfTxId != nil {
            badges.append(RowMicroBadge(id: "refund", text: "退款", color: palette.incomeColor))
        }
        if tx.reimburseStatus == ReimburseStatus.PENDING {
            badges.append(RowMicroBadge(id: "reimburse", text: "待报销", color: palette.transferColor))
        }
        if tx.discountAmountMicros > 0 {
            let discStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    tx.discountAmountMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCurrency,
                    showSign: false
                ),
                isMasked: isMasked
            )
            badges.append(RowMicroBadge(id: "discount", text: "惠\(discStr)", color: palette.incomeColor))
        }
        if tx.feeAmountMicros > 0 {
            let feeStr = YubraeTheme.masked(
                YubraeTheme.formatPlainSignedAmount(
                    tx.feeAmountMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCurrency,
                    showSign: false
                ),
                isMasked: isMasked
            )
            badges.append(RowMicroBadge(id: "fee", text: "费\(feeStr)", color: palette.warningColor))
        }

        return Array(badges.prefix(2))
    }

    private var hasAttachments: Bool {
        tx.attachmentPaths
            .split { $0 == ";" || $0 == "," || $0 == "\n" }
            .contains { !$0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }
    }

    private var inlineTags: [String] {
        Array(TagTextCodec.parse(tx.tags).prefix(2))
    }

    private var line2MetaText: String {
        let excludedCandidates: [String?] = [
            line1Title,
            category?.name,
            parentCategory?.name,
            account?.name,
            toAccount?.name
        ]
        let excluded: Set<String> = Set(
            excludedCandidates
                .compactMap { $0?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() }
                .filter { !$0.isEmpty }
        )

        let merchant = tx.merchant.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanedNote = YubraeTheme.cleanListRowNote(tx.note).trimmingCharacters(in: .whitespacesAndNewlines)

        let keepMerchant = !merchant.isEmpty && !excluded.contains(merchant.lowercased())
        let keepNote = !cleanedNote.isEmpty && !excluded.contains(cleanedNote.lowercased())

        if keepMerchant && keepNote {
            if cleanedNote.localizedCaseInsensitiveContains(merchant) {
                return cleanedNote
            }
            if merchant.localizedCaseInsensitiveContains(cleanedNote) {
                return merchant
            }
            return "\(merchant) · \(cleanedNote)"
        }
        if keepMerchant { return merchant }
        if keepNote { return cleanedNote }
        return ""
    }

    private var isRefundedExpense: Bool {
        tx.type == TransactionType.EXPENSE && refundedMicros > 0
    }

    private var transferArrivalText: String? {
        guard tx.type == TransactionType.TRANSFER, let toAmt = tx.toAmountMicros else { return nil }
        let origCur = CurrencyCatalog.normalize(tx.origCurrency)
        let toCur = CurrencyCatalog.normalize(tx.toCurrency ?? tx.origCurrency)
        guard tx.discountAmountMicros > 0 || tx.feeAmountMicros > 0 || toCur != origCur || toAmt != tx.origAmountMicros else {
            return nil
        }
        let formatted = YubraeTheme.formatPlainSignedAmount(
            toAmt,
            currencyCode: toCur,
            baseCurrency: baseCurrency,
            showSign: false
        )
        return "到账 \(YubraeTheme.masked(formatted, isMasked: isMasked))"
    }

    var body: some View {
        HStack(alignment: .center, spacing: 10) {
            YubraeCategoryIconBadge(iconKey: iconSpec.key, colorHex: iconSpec.colorHex, size: 34)

            // 中列：严格最多 2 行
            VStack(alignment: .leading, spacing: 4) {
                // Line 1: 分类 / 转账方向 + 最多 2 枚极轻财务微标
                HStack(spacing: 5) {
                    Text(line1Title)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .truncationMode(.tail)

                    ForEach(microBadges) { badge in
                        microBadgeView(badge)
                    }
                }

                // Line 2: 时间 + 附件小图标 + 最多 2 枚内联标签 + 商户/清洗后备注
                HStack(spacing: 5) {
                    Text(YubraeTheme.formatDate(tx.timestamp, pattern: "HH:mm"))
                        .font(.caption2.monospacedDigit())
                        .foregroundColor(palette.textTertiary)
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)

                    if hasAttachments {
                        Image(systemName: "paperclip")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(palette.textTertiary)
                    }

                    ForEach(inlineTags, id: \.self) { tag in
                        Text("#\(tag)")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1)
                            .background(
                                RoundedRectangle(cornerRadius: 4, style: .continuous)
                                    .fill(palette.primary.opacity(palette.isDark ? 0.16 : 0.09))
                            )
                    }

                    if !line2MetaText.isEmpty {
                        Text(line2MetaText)
                            .font(.caption2)
                            .foregroundColor(palette.textSecondary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            // 右列：严格 2 行（主金额 + 账户或转账到账）
            VStack(alignment: .trailing, spacing: 3) {
                amountView

                if tx.type == TransactionType.TRANSFER {
                    if let arrival = transferArrivalText {
                        Text(arrival)
                            .font(.caption2.monospacedDigit())
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                } else if let acc = account {
                    HStack(spacing: 4) {
                        Circle()
                            .fill(Color(argbHex: acc.colorHex))
                            .frame(width: 6, height: 6)
                        Text(acc.name)
                            .font(.caption2)
                            .foregroundColor(palette.textTertiary)
                            .lineLimit(1)
                            .truncationMode(.tail)
                    }
                    .frame(maxWidth: 96, alignment: .trailing)
                }
            }
            .layoutPriority(1)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 11)
    }

    @ViewBuilder
    private var amountView: some View {
        if isRefundedExpense {
            let origRaw = YubraeTheme.formatPlainSignedAmount(
                abs(tx.origAmountMicros),
                currencyCode: tx.origCurrency,
                baseCurrency: baseCurrency,
                showSign: false
            )
            let netMicros = max(Int64(0), abs(tx.origAmountMicros) - refundedMicros)
            let netRaw: String = netMicros > 0
                ? YubraeTheme.formatPlainSignedAmount(
                    -netMicros,
                    currencyCode: tx.origCurrency,
                    baseCurrency: baseCurrency,
                    showSign: true
                )
                : "-\(YubraeTheme.formatPlainSignedAmount(0, currencyCode: tx.origCurrency, baseCurrency: baseCurrency, showSign: false))"

            HStack(alignment: .firstTextBaseline, spacing: 4) {
                Text(YubraeTheme.masked(origRaw, isMasked: isMasked))
                    .font(.caption.monospacedDigit())
                    .strikethrough(true, color: palette.textTertiary)
                    .foregroundColor(palette.textTertiary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)

                Text(YubraeTheme.masked(netRaw, isMasked: isMasked))
                    .font(.subheadline.weight(.semibold).monospacedDigit())
                    .foregroundColor(netMicros == 0 ? palette.textSecondary : palette.expenseColor)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        } else {
            let spec = normalAmountSpec
            let formatted = YubraeTheme.formatPlainSignedAmount(
                spec.signedMicros,
                currencyCode: tx.origCurrency,
                baseCurrency: baseCurrency,
                showSign: spec.showSign
            )
            Text(YubraeTheme.masked(formatted, isMasked: isMasked))
                .font(.subheadline.weight(.semibold).monospacedDigit())
                .foregroundColor(spec.color)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: false)
        }
    }

    private var normalAmountSpec: (signedMicros: Int64, showSign: Bool, color: Color) {
        switch tx.type {
        case TransactionType.EXPENSE:
            return (-abs(tx.origAmountMicros), true, palette.expenseColor)
        case TransactionType.INCOME:
            return (abs(tx.origAmountMicros), true, palette.incomeColor)
        case TransactionType.TRANSFER:
            return (abs(tx.origAmountMicros), false, palette.transferColor)
        default:
            return (tx.origAmountMicros, true, palette.textSecondary)
        }
    }

    private func microBadgeView(_ badge: RowMicroBadge) -> some View {
        Text(badge.text)
            .font(.system(size: 10, weight: .medium).monospacedDigit())
            .foregroundColor(badge.color)
            .lineLimit(1)
            .fixedSize(horizontal: true, vertical: false)
            .padding(.horizontal, 5)
            .padding(.vertical, 1.5)
            .background(
                RoundedRectangle(cornerRadius: 4, style: .continuous)
                    .fill(badge.color.opacity(palette.isDark ? 0.18 : 0.11))
            )
    }
}
