import PhotosUI
import SwiftUI
import UIKit

private enum SmartInputMode: Int, CaseIterable, Identifiable {
    case text = 0
    case receiptImage = 1

    var id: Int { rawValue }
    var title: String {
        switch self {
        case .text: return "一句话拆单"
        case .receiptImage: return "小票/截图识图"
        }
    }
}

private struct EditableDraftItem: Identifiable {
    let id: UUID
    var selected: Bool
    var draft: ParsedBillDraft
    var amountText: String

    init(draft: ParsedBillDraft) {
        self.id = draft.id
        self.selected = true
        self.draft = draft
        let digits = CurrencyCatalog.find(draft.origCurrency).minorDigits
        self.amountText = MoneyAmount.formatPlain(draft.origAmountMicros, minorDigits: digits, grouping: false)
    }
}

public struct SmartInputSheet: View {
    @ObservedObject public var store: LedgerStore
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme

    @State private var mode: SmartInputMode = .text
    @State private var inputText: String = ""
    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var pickedImage: UIImage?
    @State private var pickedImageData: Data?

    @State private var isParsing = false
    @State private var statusBanner: String?
    @State private var errorBanner: String?
    @State private var draftItems: [EditableDraftItem] = []

    public init(store: LedgerStore) {
        self.store = store
    }

    private var palette: YubraePalette {
        YubraeTheme.palette(for: store.settings, colorScheme: colorScheme)
    }

    private var selectedCount: Int {
        draftItems.filter(\.selected).count
    }

    private let examplePrompts: [String] = [
        "今天午饭花了38元，瑞幸咖啡19.5元用支付宝",
        "昨天打车65元信用卡，超市买水果42.8元微信",
        "发工资12000元储蓄卡，房租2800元转账"
    ]

    public var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 14) {
                    Picker("智能记账方式", selection: $mode) {
                        ForEach(SmartInputMode.allCases) { m in
                            Text(m.title).tag(m)
                        }
                    }
                    .pickerStyle(.segmented)

                    if mode == .text {
                        textInputSection
                    } else {
                        receiptImageSection
                    }

                    if let statusBanner {
                        HStack(spacing: 8) {
                            Image(systemName: "sparkles")
                                .foregroundColor(palette.primary)
                            Text(statusBanner)
                                .font(.footnote)
                                .foregroundColor(palette.textSecondary)
                            Spacer()
                        }
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(palette.accentSoftFill))
                    }

                    if let errorBanner {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(palette.dangerColor)
                            Text(errorBanner)
                                .font(.footnote)
                                .foregroundColor(palette.dangerColor)
                            Spacer()
                        }
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 12).fill(palette.dangerColor.opacity(0.10)))
                    }

                    if !draftItems.isEmpty {
                        draftsSection
                    }
                }
                .padding(16)
            }
            .background(palette.background.ignoresSafeArea())
            .navigationTitle("智能记账")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("关闭") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    if selectedCount > 0 {
                        Button("入账(\(selectedCount))") {
                            commitSelectedDrafts()
                        }
                        .fontWeight(.semibold)
                    }
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("完成") {
                        dismissKeyboard()
                    }
                    .font(.subheadline.weight(.semibold))
                }
            }
            .onChange(of: selectedPhotoItem) { newItem in
                loadPickedPhoto(newItem)
            }
        }
    }

    // MARK: - Text Mode

    private var textInputSection: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Label(
                        store.settings.llmEnabled && LlmBillService.hasUsableLlmConfig(store.settings)
                            ? "AI 大模型拆单已就绪"
                            : "本地离线自然语言引擎（免 API Key）",
                        systemImage: "wand.and.stars"
                    )
                    .font(.caption.weight(.semibold))
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                    Spacer(minLength: 6)
                    Button {
                        if let paste = UIPasteboard.general.string, !paste.isEmpty {
                            inputText = paste
                        }
                    } label: {
                        Label("粘贴", systemImage: "doc.on.clipboard")
                            .font(.caption.weight(.semibold))
                            .foregroundColor(palette.primary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.plain)
                }

                ZStack(alignment: .topLeading) {
                    if inputText.isEmpty {
                        Text("一句话输入单笔或多笔消费，例如：\n「中午吃拉面 32 块，下午买霸王茶姬 18 元微信支付，昨晚滴滴打车 45 元」")
                            .font(.subheadline)
                            .foregroundColor(palette.textTertiary)
                            .padding(.top, 8)
                            .padding(.leading, 6)
                    }
                    TextEditor(text: $inputText)
                        .font(.subheadline)
                        .foregroundColor(palette.textPrimary)
                        .frame(minHeight: 110)
                        .scrollContentBackground(.hidden)
                }
                .padding(8)
                .background(RoundedRectangle(cornerRadius: 12).fill(palette.surfaceSecondary))

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(examplePrompts, id: \.self) { prompt in
                            Button {
                                inputText = prompt
                            } label: {
                                Text(prompt)
                                    .font(.caption2)
                                    .foregroundColor(palette.textSecondary)
                                    .lineLimit(1)
                                    .padding(.horizontal, 10)
                                    .padding(.vertical, 6)
                                    .background(Capsule().fill(palette.surfaceSecondary))
                            }
                        }
                    }
                }

                Button {
                    Task { await parseSmartText() }
                } label: {
                    HStack(spacing: 8) {
                        if isParsing {
                            ProgressView()
                                .tint(palette.onPrimary)
                        } else {
                            Image(systemName: "sparkles")
                        }
                        Text(isParsing ? "正在智能拆单..." : "开始智能解析")
                            .font(.subheadline.weight(.semibold))
                    }
                    .foregroundColor(palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.primary))
                }
                .disabled(isParsing || inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            }
        }
    }

    // MARK: - Vision Image Mode

    private var receiptImageSection: some View {
        YubraeCard(palette: palette) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Label("小票 / 支付截图视觉识图", systemImage: "doc.text.viewfinder")
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                    Spacer(minLength: 6)
                    if !store.settings.llmEnabled || !LlmBillService.hasUsableLlmConfig(store.settings) {
                        Text("需配置 AI")
                            .font(.caption2.weight(.semibold))
                            .foregroundColor(palette.warningColor)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: true, vertical: false)
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Capsule().fill(palette.warningColor.opacity(0.15)))
                    }
                }

                Text("支持购物小票、微信/支付宝付款截图、外币刷卡单。建议在「我的 -> AI 大模型设置」启用支持 Vision 的模型（如 gpt-4o-mini / qwen-vl / gemini）。")
                    .font(.caption)
                    .foregroundColor(palette.textSecondary)

                if let pickedImage {
                    Image(uiImage: pickedImage)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 220)
                        .frame(maxWidth: .infinity)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.outline, lineWidth: 1)
                        )
                }

                HStack(spacing: 10) {
                    PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                        Label("从相册选择图片", systemImage: "photo.on.rectangle")
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .foregroundColor(palette.primary)
                            .frame(maxWidth: .infinity)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 11)
                            .background(RoundedRectangle(cornerRadius: 12).fill(palette.accentSoftFill))
                    }

                    Button {
                        if let clipImage = UIPasteboard.general.image {
                            prepareImageForVision(clipImage)
                        } else {
                            errorBanner = "剪贴板中暂无图片，可先截屏复制后再粘贴"
                        }
                    } label: {
                        Label("粘贴截图", systemImage: "doc.on.clipboard")
                            .font(.subheadline.weight(.semibold))
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                            .fixedSize(horizontal: true, vertical: false)
                            .foregroundColor(palette.textPrimary)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 11)
                            .background(RoundedRectangle(cornerRadius: 12).fill(palette.surfaceSecondary))
                    }
                    .buttonStyle(.plain)
                }

                Button {
                    Task { await parsePickedReceipt() }
                } label: {
                    HStack(spacing: 8) {
                        if isParsing {
                            ProgressView()
                                .tint(palette.onPrimary)
                        } else {
                            Image(systemName: "viewfinder")
                        }
                        Text(isParsing ? "正在识图解析..." : "识别小票金额与明细")
                            .font(.subheadline.weight(.semibold))
                    }
                    .foregroundColor(palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 12)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(palette.primary))
                }
                .disabled(isParsing || pickedImageData == nil)
            }
        }
    }

    // MARK: - Parsed Drafts

    private var draftsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(spacing: 8) {
                Text("解析草稿（可微调后批量入账）")
                    .font(.subheadline.weight(.semibold))
                    .foregroundColor(palette.textPrimary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
                Spacer(minLength: 6)
                Text("已勾选 \(selectedCount)/\(draftItems.count)")
                    .font(.caption)
                    .foregroundColor(palette.primary)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }

            ForEach($draftItems) { $item in
                YubraeCard(palette: palette, padding: 14) {
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 10) {
                            Button {
                                item.selected.toggle()
                            } label: {
                                Image(systemName: item.selected ? "checkmark.circle.fill" : "circle")
                                    .font(.title3)
                                    .foregroundColor(item.selected ? palette.primary : palette.textTertiary)
                            }
                            .buttonStyle(.plain)

                            Picker("类型", selection: $item.draft.type) {
                                Text("支出").tag(TransactionType.EXPENSE)
                                Text("收入").tag(TransactionType.INCOME)
                                Text("转账").tag(TransactionType.TRANSFER)
                            }
                            .pickerStyle(.segmented)
                        }

                        HStack(spacing: 8) {
                            Text(item.draft.origCurrency)
                                .font(.caption.weight(.bold))
                                .foregroundColor(palette.primary)
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                                .padding(.horizontal, 9)
                                .padding(.vertical, 6)
                                .background(Capsule().fill(palette.accentSoftFill))

                            TextField("0.00", text: $item.amountText)
                                .keyboardType(.decimalPad)
                                .font(.headline.monospacedDigit())
                                .multilineTextAlignment(.trailing)
                                .frame(maxWidth: .infinity)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 7)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                        }

                        HStack(spacing: 8) {
                            TextField("商户", text: $item.draft.merchant)
                                .font(.subheadline)
                                .frame(minWidth: 80)
                                .padding(8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))

                            Menu {
                                ForEach(store.activeAccounts) { acc in
                                    Button(acc.name) {
                                        item.draft.accountId = acc.id
                                    }
                                }
                            } label: {
                                let accName = item.draft.accountId.flatMap { store.account(byId: $0)?.name } ?? "自动匹配账户"
                                HStack(spacing: 4) {
                                    Text(accName)
                                        .font(.caption.weight(.medium))
                                        .lineLimit(1)
                                        .truncationMode(.tail)
                                    Image(systemName: "chevron.down")
                                        .font(.caption2)
                                }
                                .foregroundColor(palette.textPrimary)
                                .padding(.horizontal, 10)
                                .padding(.vertical, 8)
                                .background(RoundedRectangle(cornerRadius: 8).fill(palette.surfaceSecondary))
                            }
                        }

                        if item.draft.type != TransactionType.TRANSFER {
                            HStack(spacing: 8) {
                                Menu {
                                    ForEach(store.categories.filter { $0.type == item.draft.type }) { cat in
                                        Button(cat.name) {
                                            item.draft.categoryId = cat.id
                                            item.draft.categoryName = cat.name
                                        }
                                    }
                                } label: {
                                    let catName = item.draft.categoryId.flatMap { store.category(byId: $0)?.name }
                                        ?? (item.draft.categoryName.isEmpty ? "选择分类" : item.draft.categoryName)
                                    Label(catName, systemImage: "square.grid.2x2")
                                        .font(.caption.weight(.medium))
                                        .lineLimit(1)
                                        .foregroundColor(palette.primary)
                                        .padding(.horizontal, 10)
                                        .padding(.vertical, 5)
                                        .background(Capsule().fill(palette.accentSoftFill))
                                }

                                Spacer(minLength: 6)
                                Text(YubraeTheme.formatDate(item.draft.timestamp, pattern: "MM-dd HH:mm"))
                                    .font(.caption2)
                                    .foregroundColor(palette.textTertiary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                            }
                        }
                    }
                }
            }

            Button {
                commitSelectedDrafts()
            } label: {
                Text("确认批量入账（\(selectedCount) 笔）")
                    .font(.headline)
                    .foregroundColor(palette.onPrimary)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(palette.primary))
            }
            .disabled(selectedCount == 0)
        }
    }

    // MARK: - Logic

    private func dismissKeyboard() {
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)
    }

    @MainActor
    private func parseSmartText() async {
        errorBanner = nil
        statusBanner = nil
        isParsing = true
        defer { isParsing = false }

        let drafts = await YubraeRuntime.llmService.parseSmartInput(
            text: inputText,
            settings: store.settings,
            accounts: store.accounts,
            categories: store.categories,
            aliases: store.accountAliases
        )
        if drafts.isEmpty {
            errorBanner = "未能识别出有效金额或账单，请尝试补充数字金额（如「午饭 35 元」）。"
            draftItems = []
        } else {
            draftItems = drafts.map { EditableDraftItem(draft: $0) }
            let engineLabel = drafts.first?.parserUsed.hasPrefix("LLM") == true ? "AI 大模型" : "本地离线引擎"
            statusBanner = "已通过\(engineLabel)拆解出 \(drafts.count) 笔草稿，核对无误后点击入账。"
        }
    }

    private func loadPickedPhoto(_ item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            if let data = try? await item.loadTransferable(type: Data.self),
               let uiImage = UIImage(data: data) {
                await MainActor.run {
                    prepareImageForVision(uiImage)
                }
            }
        }
    }

    private func prepareImageForVision(_ image: UIImage) {
        errorBanner = nil
        let resized = resizeImageIfNeeded(image, maxDimension: 1600)
        pickedImage = resized
        pickedImageData = resized.jpegData(compressionQuality: 0.78)
    }

    private func resizeImageIfNeeded(_ image: UIImage, maxDimension: CGFloat) -> UIImage {
        let maxSide = max(image.size.width, image.size.height)
        guard maxSide > maxDimension else { return image }
        let ratio = maxDimension / maxSide
        let newSize = CGSize(width: image.size.width * ratio, height: image.size.height * ratio)
        let renderer = UIGraphicsImageRenderer(size: newSize)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: newSize))
        }
    }

    @MainActor
    private func parsePickedReceipt() async {
        guard let imageData = pickedImageData else { return }
        errorBanner = nil
        statusBanner = nil

        guard store.settings.llmEnabled && LlmBillService.hasUsableLlmConfig(store.settings) else {
            errorBanner = "请先在「我的 -> AI 大模型设置」中启用 AI 并填写 API Key，再使用视觉小票识图。"
            return
        }

        isParsing = true
        defer { isParsing = false }

        do {
            if let draft = try await YubraeRuntime.llmService.parseReceiptImage(
                imageData: imageData,
                mimeType: "image/jpeg",
                settings: store.settings,
                accounts: store.accounts,
                categories: store.categories
            ) {
                draftItems = [EditableDraftItem(draft: draft)]
                statusBanner = "小票识别完成（置信度 \(Int(draft.confidence * 100))%），确认后即可入账。"
            } else {
                errorBanner = "该图片未识别到有效消费小票或支付凭证。"
                draftItems = []
            }
        } catch {
            errorBanner = "识图请求失败：\(error.localizedDescription)"
        }
    }

    private func commitSelectedDrafts() {
        var savedCount = 0
        for item in draftItems where item.selected {
            var draft = item.draft
            if let parsed = MoneyAmount.parseToMicros(item.amountText), parsed > 0 {
                draft.origAmountMicros = parsed
            }
            guard draft.origAmountMicros > 0 else { continue }
            do {
                _ = try store.recordParsedDraft(draft)
                savedCount += 1
            } catch {
                errorBanner = error.localizedDescription
                return
            }
        }
        if savedCount > 0 {
            store.toastMessage = "已成功入账 \(savedCount) 笔智能账单"
            dismiss()
        }
    }
}
