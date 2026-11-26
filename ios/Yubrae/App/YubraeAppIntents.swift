import AppIntents
import Foundation
import UIKit
import UniformTypeIdentifiers

// MARK: - AppEnum for Quick Record Type

public enum YubraeIntentBillType: String, AppEnum {
    case expense = "EXPENSE"
    case income = "INCOME"

    public static var typeDisplayRepresentation: TypeDisplayRepresentation {
        TypeDisplayRepresentation(name: "记账类型")
    }

    public static var caseDisplayRepresentations: [YubraeIntentBillType: DisplayRepresentation] {
        [
            .expense: DisplayRepresentation(title: "支出"),
            .income: DisplayRepresentation(title: "收入")
        ]
    }
}

// MARK: - 1. Quick Record Intent

public struct YubraeQuickRecordIntent: AppIntent {
    public static var title: LocalizedStringResource = "余白快捷记一笔"
    public static var description: IntentDescription? = IntentDescription("快速向余白写入一笔支出或收入，支持商户、备注、分类与多币种。")
    public static var openAppWhenRun: Bool = false

    @Parameter(title: "金额", description: "交易金额（正数，如 28.5）")
    public var amount: Double

    @Parameter(title: "类型", default: .expense)
    public var billType: YubraeIntentBillType

    @Parameter(title: "商户或标题", default: "")
    public var merchant: String

    @Parameter(title: "备注", default: "")
    public var note: String

    @Parameter(title: "分类名称")
    public var categoryName: String?

    @Parameter(title: "币种代码")
    public var currency: String?

    public init() {}

    public func perform() async throws -> some IntentResult & ProvidesDialog {
        let formattedRaw = String(format: "%.4f", amount)
        guard let amountMicros = MoneyAmount.parseToMicros(formattedRaw), amountMicros > 0 else {
            return .result(dialog: IntentDialog("金额必须大于 0，未能记录到余白。"))
        }

        let cleanMerchant = merchant.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanNote = note.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCategory = categoryName?.trimmingCharacters(in: .whitespacesAndNewlines)
        let cleanCurrency = currency?.trimmingCharacters(in: .whitespacesAndNewlines)

        let summaryText: String = try await MainActor.run {
            let store = YubraeRuntime.sharedStore
            let params = ExternalBillParams(
                origAmountMicros: amountMicros,
                type: billType.rawValue,
                categoryName: (cleanCategory?.isEmpty == false) ? cleanCategory : nil,
                accountNameOrHint: nil,
                toAccountNameOrHint: nil,
                merchant: cleanMerchant,
                note: cleanNote,
                timestamp: currentEpochMillis(),
                currency: (cleanCurrency?.isEmpty == false) ? cleanCurrency : store.settings.baseCurrency,
                showFloatingPrompt: false,
                sourceScheme: "shortcuts"
            )
            let saved = try store.recordExternalBill(params)
            let amountStr = MoneyAmount.format(saved.origAmountMicros, currencyCode: saved.origCurrency)
            let typeLabel = saved.type == TransactionType.INCOME ? "收入" : "支出"
            let titlePart = saved.merchant.isEmpty ? "" : "（\(saved.merchant)）"
            let msg = "已记入余白：\(typeLabel) \(amountStr)\(titlePart)"
            store.toastMessage = msg
            return msg
        }

        return .result(dialog: IntentDialog("\(summaryText)"))
    }
}

// MARK: - 2. Smart Text Intent (LLM + Offline Fallback)

public struct YubraeSmartTextIntent: AppIntent {
    public static var title: LocalizedStringResource = "余白一句话智能记账"
    public static var description: IntentDescription? = IntentDescription("输入口语或账单文本，自动拆单并写入余白账本（未配置大模型时自动使用本地离线解析）。")
    public static var openAppWhenRun: Bool = false

    @Parameter(title: "记账文本", description: "例如：午饭花了38元，瑞幸咖啡19.5元")
    public var text: String

    public init() {}

    public func perform() async throws -> some IntentResult & ProvidesDialog {
        let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else {
            return .result(dialog: IntentDialog("请先提供需要解析的记账文本。"))
        }

        let context = await MainActor.run {
            let store = YubraeRuntime.sharedStore
            return (store.settings, store.accounts, store.categories, store.accountAliases)
        }

        let drafts = await YubraeRuntime.llmService.parseSmartInput(
            text: cleaned,
            settings: context.0,
            accounts: context.1,
            categories: context.2,
            aliases: context.3
        )

        guard !drafts.isEmpty else {
            return .result(dialog: IntentDialog("未从文本中识别出有效金额或账单。"))
        }

        let summaryText: String = try await MainActor.run {
            let store = YubraeRuntime.sharedStore
            var savedList: [TransactionDto] = []
            for draft in drafts {
                let saved = try store.recordParsedDraft(draft)
                savedList.append(saved)
            }
            let message: String
            if savedList.count == 1, let first = savedList.first {
                let amountStr = MoneyAmount.format(first.origAmountMicros, currencyCode: first.origCurrency)
                let label = first.merchant.isEmpty ? (store.category(byId: first.categoryId)?.name ?? "记账") : first.merchant
                message = "余白已智能入账：\(label) \(amountStr)"
            } else {
                let snippets = savedList.prefix(3).map { tx in
                    let amountStr = MoneyAmount.format(tx.origAmountMicros, currencyCode: tx.origCurrency)
                    return tx.merchant.isEmpty ? amountStr : "\(tx.merchant) \(amountStr)"
                }.joined(separator: "、")
                message = "余白已批量入账 \(savedList.count) 笔：\(snippets)"
            }
            store.toastMessage = message
            return message
        }

        return .result(dialog: IntentDialog("\(summaryText)"))
    }
}

// MARK: - Vision Image Normalization

public struct NormalizedVisionImage: Equatable, Sendable {
    public let data: Data
    public let mimeType: String

    public init(data: Data, mimeType: String) {
        self.data = data
        self.mimeType = mimeType
    }
}

public enum YubraeReceiptImageNormalizer {
    public static func prepareForVision(
        data: Data,
        fallbackMimeType: String? = nil,
        maxDimension: CGFloat = 1600,
        compressionQuality: CGFloat = 0.78
    ) -> NormalizedVisionImage? {
        guard !data.isEmpty else { return nil }

        let cleanedFallback = fallbackMimeType?.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedFallbackMime = (cleanedFallback?.isEmpty == false) ? cleanedFallback! : "image/jpeg"

        guard let image = UIImage(data: data) else {
            return NormalizedVisionImage(data: data, mimeType: resolvedFallbackMime)
        }

        let pixelWidth = image.size.width * image.scale
        let pixelHeight = image.size.height * image.scale
        let maxSide = max(pixelWidth, pixelHeight)

        let targetImage: UIImage
        let needsResize = maxDimension > 0 && maxSide > maxDimension
        if pixelWidth > 0, pixelHeight > 0, (needsResize || image.imageOrientation != .up) {
            let ratio = needsResize ? (maxDimension / maxSide) : 1.0
            let maxClamp = maxDimension > 0 ? maxDimension : maxSide
            let targetSize = CGSize(
                width: max(1, min(maxClamp, (pixelWidth * ratio).rounded())),
                height: max(1, min(maxClamp, (pixelHeight * ratio).rounded()))
            )
            let format = UIGraphicsImageRendererFormat.default()
            format.scale = 1
            let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
            targetImage = renderer.image { _ in
                image.draw(in: CGRect(origin: .zero, size: targetSize))
            }
        } else {
            targetImage = image
        }

        let clampedQuality = min(1.0, max(0.0, compressionQuality))
        guard let jpegData = targetImage.jpegData(compressionQuality: clampedQuality), !jpegData.isEmpty else {
            return NormalizedVisionImage(data: data, mimeType: resolvedFallbackMime)
        }
        return NormalizedVisionImage(data: jpegData, mimeType: "image/jpeg")
    }
}

// MARK: - 3. Receipt / Screenshot Vision Intent

public struct YubraeReceiptImageIntent: AppIntent {
    public static var title: LocalizedStringResource = "余白小票与截图识图记账"
    public static var description: IntentDescription? = IntentDescription("传入支付截图或购物小票图片，调用视觉大模型自动识别金额商户并入账，可搭配轻点背面、选择照片或支付 App 离开自动化使用。")
    public static var openAppWhenRun: Bool = false

    public static let missingImageGuidance = "未收到图片。若想直接从相册选取截屏，请使用「余白从相册识图记账」；若在快捷指令中自动流转，请在上方先添加「选择照片」「拍摄截屏」或「获取最新截屏」动作。"

    @Parameter(
        title: "账单截图或小票图片",
        description: "可自动接收上一步「拍摄截屏」「选择照片」或「获取最新截屏」输出的图片。",
        supportedTypeIdentifiers: ["public.image"],
        inputConnectionBehavior: .connectToPreviousIntentResult
    )
    public var receiptImage: IntentFile?

    public static var parameterSummary: some ParameterSummary {
        Summary("识别 \(\.$receiptImage) 并自动记账")
    }

    public init() {}

    public func perform() async throws -> some IntentResult & ProvidesDialog {
        guard let receiptImage else {
            return .result(dialog: IntentDialog("\(Self.missingImageGuidance)"))
        }

        guard let normalized = YubraeReceiptImageNormalizer.prepareForVision(
            data: receiptImage.data,
            fallbackMimeType: receiptImage.type?.preferredMIMEType
        ) else {
            return .result(dialog: IntentDialog("未获取到有效图片数据，请重试。"))
        }

        let context = await MainActor.run {
            let store = YubraeRuntime.sharedStore
            return (store.settings, store.accounts, store.categories)
        }

        guard context.0.llmEnabled && LlmBillService.hasUsableLlmConfig(context.0) else {
            return .result(dialog: IntentDialog("请先打开余白「我的 -> AI 大模型设置」，启用视觉大模型并填写 API Key 后再使用识图记账。"))
        }

        do {
            guard let draft = try await YubraeRuntime.llmService.parseReceiptImage(
                imageData: normalized.data,
                mimeType: normalized.mimeType,
                settings: context.0,
                accounts: context.1,
                categories: context.2
            ) else {
                return .result(dialog: IntentDialog("图片中未识别到有效消费或支付凭证。"))
            }

            let summaryText: String = try await MainActor.run {
                let store = YubraeRuntime.sharedStore
                let saved = try store.recordParsedDraft(draft)
                let amountStr = MoneyAmount.format(saved.origAmountMicros, currencyCode: saved.origCurrency)
                let merchantText = saved.merchant.isEmpty ? "识图消费" : saved.merchant
                let msg = "余白识图入账成功：\(merchantText) \(amountStr)"
                store.toastMessage = msg
                return msg
            }
            return .result(dialog: IntentDialog("\(summaryText)"))
        } catch {
            return .result(dialog: IntentDialog("识图记账失败：\(error.localizedDescription)"))
        }
    }
}

// MARK: - 4. Pick Photo Receipt Intent (Open App + Photo Picker)

public struct YubraePickPhotoReceiptIntent: AppIntent {
    public static var title: LocalizedStringResource = "余白从相册识图记账"
    public static var description: IntentDescription? = IntentDescription("打开余白并直接调起 iPhone 系统相册，选取账单截图或小票照片后自动识图入账。")
    public static var openAppWhenRun: Bool = true

    public init() {}

    @MainActor
    public func perform() async throws -> some IntentResult & ProvidesDialog {
        YubraeRuntime.sharedStore.requestSmartInputLaunch(
            mode: .receiptImage,
            autoOpenPhotoPicker: true,
            autoParseOnPick: true
        )
        return .result(dialog: IntentDialog("已打开余白相册识图，请选择账单截图或小票照片。"))
    }
}

// MARK: - AppShortcutsProvider (iOS 17+ Preconfigured Cards; Individual AppIntents Remain iOS 16+)

@available(iOS 17.0, *)
public struct YubraeAppShortcuts: AppShortcutsProvider {
    @AppShortcutsBuilder
    public static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: YubraeQuickRecordIntent(),
            phrases: [
                "用 \(.applicationName) 记一笔",
                "在 \(.applicationName) 快捷记账"
            ],
            shortTitle: "快捷记一笔",
            systemImageName: "plus.circle.fill"
        )
        AppShortcut(
            intent: YubraeSmartTextIntent(),
            phrases: [
                "用 \(.applicationName) 智能记账",
                "让 \(.applicationName) 帮我记账"
            ],
            shortTitle: "一句话智能记账",
            systemImageName: "sparkles"
        )
        AppShortcut(
            intent: YubraePickPhotoReceiptIntent(),
            phrases: [
                "用 \(.applicationName) 识别小票",
                "用 \(.applicationName) 识图记账",
                "用 \(.applicationName) 从相册识图记账"
            ],
            shortTitle: "相册识图记账",
            systemImageName: "photo.on.rectangle.angled"
        )
    }
}
