import SwiftUI
import UIKit
import UniformTypeIdentifiers

// MARK: - Custom UTType & KLBK Backup FileDocument

public extension UTType {
    static let yubraeKlbk = UTType(exportedAs: "com.yubrae.app.klbk", conformingTo: .data)
}

public struct KlbkBackupDocument: FileDocument {
    public static var readableContentTypes: [UTType] { [.yubraeKlbk, .data] }
    public static var writableContentTypes: [UTType] { [.yubraeKlbk, .data] }

    public var data: Data

    public init(data: Data = Data()) {
        self.data = data
    }

    public init(configuration: ReadConfiguration) throws {
        guard let fileData = configuration.file.regularFileContents else {
            throw CocoaError(.fileReadCorruptFile)
        }
        self.data = fileData
    }

    public func fileWrapper(configuration: WriteConfiguration) throws -> FileWrapper {
        FileWrapper(regularFileWithContents: data)
    }
}

// MARK: - Color Hex Helper

public extension Color {
    init(argbHex: Int64) {
        let comps = colorComponents(fromARGBHex: argbHex)
        self.init(.sRGB, red: comps.red, green: comps.green, blue: comps.blue, opacity: comps.alpha)
    }
}

// MARK: - Theme Palette & Visual Tokens

public struct YubraePalette {
    public let style: ThemeStyle
    public let isDark: Bool
    public let primary: Color
    public let onPrimary: Color
    public let primaryContainer: Color
    public let onPrimaryContainer: Color
    public let background: Color
    public let surface: Color
    public let surfaceSecondary: Color
    public let outline: Color
    public let textPrimary: Color
    public let textSecondary: Color
    public let textTertiary: Color
    public let expenseColor: Color
    public let incomeColor: Color
    public let transferColor: Color
    public let warningColor: Color
    public let dangerColor: Color
    public let heroGradient: LinearGradient

    public var accentSoftFill: Color {
        isDark ? primary.opacity(0.22) : primaryContainer
    }
}

public enum YubraeTheme {
    public static func colorScheme(for mode: ThemeMode) -> ColorScheme? {
        switch mode {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }

    public static func palette(for settings: AppSettings, colorScheme: ColorScheme) -> YubraePalette {
        let style = ThemeStyle.from(settings.themeStyle)
        let isDark = colorScheme == .dark
        let basePrimary = Color(argbHex: style.primaryColorHex)
        let baseContainer = Color(argbHex: style.containerColorHex)

        let primary: Color
        if isDark && style == .obsidianMono {
            primary = Color(argbHex: 0xFFE5E7EB)
        } else if isDark {
            primary = lightenedColor(fromARGBHex: style.primaryColorHex, factor: 0.20)
        } else {
            primary = basePrimary
        }

        let onPrimary: Color = (isDark && style == .obsidianMono) ? Color(argbHex: 0xFF111318) : .white

        let primaryContainer: Color = isDark
            ? primary.opacity(0.22)
            : baseContainer

        let onPrimaryContainer: Color = isDark
            ? Color.white.opacity(0.95)
            : Color(argbHex: style.primaryColorHex)

        let background: Color
        let surface: Color
        let surfaceSecondary: Color
        let outline: Color

        if isDark {
            if settings.oledPureBlack {
                background = .black
                surface = Color(argbHex: 0xFF101316)
                surfaceSecondary = Color(argbHex: 0xFF1A1F24)
                outline = Color.white.opacity(0.10)
            } else {
                background = Color(argbHex: 0xFF0E1412)
                surface = Color(argbHex: 0xFF161E1B)
                surfaceSecondary = Color(argbHex: 0xFF1F2A26)
                outline = Color.white.opacity(0.09)
            }
        } else {
            background = lightBackground(for: style)
            surface = .white
            surfaceSecondary = Color(argbHex: 0xFFF3F6F5)
            outline = Color(argbHex: 0xFFE3EAE7)
        }

        let textPrimary = isDark ? Color(argbHex: 0xFFF2F6F4) : Color(argbHex: 0xFF17211D)
        let textSecondary = isDark ? Color(argbHex: 0xFF9EB0A8) : Color(argbHex: 0xFF5D6F67)
        let textTertiary = isDark ? Color(argbHex: 0xFF6E8078) : Color(argbHex: 0xFF8EA098)

        let redTone = isDark ? Color(argbHex: 0xFFF87171) : Color(argbHex: 0xFFD9383A)
        let greenTone = isDark ? Color(argbHex: 0xFF34D399) : Color(argbHex: 0xFF1E9E6B)
        let expenseColor = settings.greenExpenseMode ? greenTone : redTone
        let incomeColor = settings.greenExpenseMode ? redTone : greenTone
        let transferColor = isDark ? Color(argbHex: 0xFF60A5FA) : Color(argbHex: 0xFF2563EB)
        let warningColor = isDark ? Color(argbHex: 0xFFFBBF24) : Color(argbHex: 0xFFD97706)
        let dangerColor = redTone

        let gradientColors: [Color]
        if isDark {
            gradientColors = [
                Color(argbHex: style.primaryColorHex).opacity(0.82),
                Color(argbHex: 0xFF12231D)
            ]
        } else {
            gradientColors = [
                basePrimary,
                darkenedColor(fromARGBHex: style.primaryColorHex, factor: 0.16)
            ]
        }

        return YubraePalette(
            style: style,
            isDark: isDark,
            primary: primary,
            onPrimary: onPrimary,
            primaryContainer: primaryContainer,
            onPrimaryContainer: onPrimaryContainer,
            background: background,
            surface: surface,
            surfaceSecondary: surfaceSecondary,
            outline: outline,
            textPrimary: textPrimary,
            textSecondary: textSecondary,
            textTertiary: textTertiary,
            expenseColor: expenseColor,
            incomeColor: incomeColor,
            transferColor: transferColor,
            warningColor: warningColor,
            dangerColor: dangerColor,
            heroGradient: LinearGradient(
                colors: gradientColors,
                startPoint: .topLeading,
                endPoint: .bottomTrailing
            )
        )
    }

    public static func masked(_ text: String, isMasked: Bool) -> String {
        isMasked ? "••••" : text
    }

    public static func formatDate(_ epochMillis: Int64, pattern: String = "MM-dd HH:mm") -> String {
        let df = DateFormatter()
        df.locale = Locale(identifier: "zh_CN")
        df.dateFormat = pattern
        return df.string(from: Date(timeIntervalSince1970: Double(epochMillis) / 1000.0))
    }

    public static func formatDaySectionTitle(_ epochMillis: Int64) -> String {
        let date = Date(timeIntervalSince1970: Double(epochMillis) / 1000.0)
        let cal = Calendar.current
        if cal.isDateInToday(date) {
            return "今天 · \(formatDate(epochMillis, pattern: "M月d日"))"
        }
        if cal.isDateInYesterday(date) {
            return "昨天 · \(formatDate(epochMillis, pattern: "M月d日"))"
        }
        return formatDate(epochMillis, pattern: "M月d日 EEEE")
    }

    public static func formatFileSize(_ bytes: Int64) -> String {
        if bytes <= 0 { return "未知大小" }
        let formatter = ByteCountFormatter()
        formatter.allowedUnits = [.useKB, .useMB, .useGB]
        formatter.countStyle = .file
        return formatter.string(fromByteCount: bytes)
    }

    private static func lightBackground(for style: ThemeStyle) -> Color {
        switch style {
        case .yimuFresh: return Color(argbHex: 0xFFF4F8F6)
        case .prismTeal: return Color(argbHex: 0xFFF3F8FA)
        case .sakuraPink: return Color(argbHex: 0xFFFCF5F8)
        case .skyBlue: return Color(argbHex: 0xFFF3F7FC)
        case .amberWarm: return Color(argbHex: 0xFFFCF8F2)
        case .lavenderMist: return Color(argbHex: 0xFFF7F5FC)
        case .forestMatcha: return Color(argbHex: 0xFFF6F8F3)
        case .coralSunset: return Color(argbHex: 0xFFFCF5F3)
        case .obsidianMono: return Color(argbHex: 0xFFF4F5F7)
        case .latteMocha: return Color(argbHex: 0xFFFAF6F2)
        }
    }

    private static func lightenedColor(fromARGBHex hex: Int64, factor: Double) -> Color {
        let c = colorComponents(fromARGBHex: hex)
        return Color(
            .sRGB,
            red: min(1.0, c.red + (1.0 - c.red) * factor),
            green: min(1.0, c.green + (1.0 - c.green) * factor),
            blue: min(1.0, c.blue + (1.0 - c.blue) * factor),
            opacity: 1.0
        )
    }

    private static func darkenedColor(fromARGBHex hex: Int64, factor: Double) -> Color {
        let c = colorComponents(fromARGBHex: hex)
        return Color(
            .sRGB,
            red: max(0.0, c.red * (1.0 - factor)),
            green: max(0.0, c.green * (1.0 - factor)),
            blue: max(0.0, c.blue * (1.0 - factor)),
            opacity: 1.0
        )
    }
}

// MARK: - Brand Mark & Reusable UI Components

/// 「余白 YUBRAE」薄荷清风几何品牌徽标。
public struct YubraeBrandMark: View {
    public var size: CGFloat = 56
    public var primaryColor: Color = Color(argbHex: ThemeStyle.yimuFresh.primaryColorHex)

    public init(size: CGFloat = 56, primaryColor: Color = Color(argbHex: ThemeStyle.yimuFresh.primaryColorHex)) {
        self.size = size
        self.primaryColor = primaryColor
    }

    public var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: size * 0.28, style: .continuous)
                .fill(
                    LinearGradient(
                        colors: [primaryColor, primaryColor.opacity(0.76)],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )
            Circle()
                .strokeBorder(Color.white.opacity(0.32), lineWidth: max(1.5, size * 0.035))
                .frame(width: size * 0.68, height: size * 0.68)
            Image(systemName: "leaf.fill")
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundColor(.white)
        }
        .frame(width: size, height: size)
        .shadow(color: primaryColor.opacity(0.24), radius: size * 0.16, x: 0, y: size * 0.08)
    }
}

public struct YubraeCategoryIconBadge: View {
    public let iconKey: String
    public let colorHex: Int64
    public var size: CGFloat = 38

    public init(iconKey: String, colorHex: Int64, size: CGFloat = 38) {
        self.iconKey = iconKey
        self.colorHex = colorHex
        self.size = size
    }

    public var body: some View {
        let tint = Color(argbHex: colorHex)
        ZStack {
            Circle()
                .fill(tint.opacity(0.15))
            Image(systemName: DefaultSeedData.sfSymbol(for: iconKey))
                .font(.system(size: size * 0.44, weight: .semibold))
                .foregroundColor(tint)
        }
        .frame(width: size, height: size)
    }
}

public struct YubraeCard<Content: View>: View {
    public let palette: YubraePalette
    public var padding: CGFloat = 16
    public var cornerRadius: CGFloat = 18
    @ViewBuilder public let content: () -> Content

    public init(
        palette: YubraePalette,
        padding: CGFloat = 16,
        cornerRadius: CGFloat = 18,
        @ViewBuilder content: @escaping () -> Content
    ) {
        self.palette = palette
        self.padding = padding
        self.cornerRadius = cornerRadius
        self.content = content
    }

    public var body: some View {
        content()
            .padding(padding)
            .background(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .fill(palette.surface)
            )
            .overlay(
                RoundedRectangle(cornerRadius: cornerRadius, style: .continuous)
                    .strokeBorder(palette.outline, lineWidth: 0.8)
            )
    }
}

/// iOS 16 原生兼容的空态视图（不依赖 iOS 17 ContentUnavailableView）。
public struct YubraeEmptyStateView: View {
    public let icon: String
    public let title: String
    public let subtitle: String
    public let palette: YubraePalette
    public var actionTitle: String? = nil
    public var action: (() -> Void)? = nil

    public init(
        icon: String,
        title: String,
        subtitle: String,
        palette: YubraePalette,
        actionTitle: String? = nil,
        action: (() -> Void)? = nil
    ) {
        self.icon = icon
        self.title = title
        self.subtitle = subtitle
        self.palette = palette
        self.actionTitle = actionTitle
        self.action = action
    }

    public var body: some View {
        VStack(spacing: 12) {
            ZStack {
                Circle()
                    .fill(palette.accentSoftFill)
                    .frame(width: 60, height: 60)
                Image(systemName: icon)
                    .font(.system(size: 24, weight: .semibold))
                    .foregroundColor(palette.primary)
            }
            Text(title)
                .font(.headline)
                .foregroundColor(palette.textPrimary)
            Text(subtitle)
                .font(.subheadline)
                .foregroundColor(palette.textSecondary)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            if let actionTitle, let action {
                Button(action: action) {
                    Text(actionTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.onPrimary)
                        .padding(.horizontal, 18)
                        .padding(.vertical, 9)
                        .background(Capsule().fill(palette.primary))
                }
                .padding(.top, 4)
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 36)
    }
}

public struct YubraeToastBanner: View {
    public let text: String
    public let isError: Bool

    public init(text: String, isError: Bool = false) {
        self.text = text
        self.isError = isError
    }

    public var body: some View {
        HStack(spacing: 8) {
            Image(systemName: isError ? "exclamationmark.triangle.fill" : "checkmark.circle.fill")
                .foregroundColor(isError ? Color(argbHex: 0xFFFCA5A5) : Color(argbHex: 0xFF6EE7B7))
            Text(text)
                .font(.subheadline.weight(.medium))
                .foregroundColor(.white)
                .lineLimit(2)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 11)
        .background(
            Capsule(style: .continuous)
                .fill(Color(argbHex: 0xFF15221D).opacity(0.94))
                .shadow(color: Color.black.opacity(0.22), radius: 12, x: 0, y: 6)
        )
        .padding(.horizontal, 20)
    }
}

/// iOS 16+ 统一居中确认弹窗，替代原生简陋 `.alert`。
public struct YubraeConfirmDialog: View {
    @Binding public var isPresented: Bool
    public let palette: YubraePalette
    public let icon: String
    public let title: String
    public let message: String
    public var confirmTitle: String
    public var cancelTitle: String
    public var isDestructive: Bool
    public var onCancel: (() -> Void)?
    public let onConfirm: () -> Void

    public init(
        isPresented: Binding<Bool>,
        palette: YubraePalette,
        icon: String,
        title: String,
        message: String,
        confirmTitle: String = "确认",
        cancelTitle: String = "取消",
        isDestructive: Bool = false,
        onCancel: (() -> Void)? = nil,
        onConfirm: @escaping () -> Void
    ) {
        self._isPresented = isPresented
        self.palette = palette
        self.icon = icon
        self.title = title
        self.message = message
        self.confirmTitle = confirmTitle
        self.cancelTitle = cancelTitle
        self.isDestructive = isDestructive
        self.onCancel = onCancel
        self.onConfirm = onConfirm
    }

    public var body: some View {
        ZStack {
            if isPresented {
                Color.black.opacity(0.42)
                    .ignoresSafeArea()
                    .contentShape(Rectangle())
                    .onTapGesture {
                        dismissDialog(invokeCancel: true)
                    }
                    .transition(.opacity)

                dialogCard
                    .padding(.horizontal, 24)
                    .transition(.scale(scale: 0.92).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .allowsHitTesting(isPresented)
        .animation(.spring(response: 0.28, dampingFraction: 0.86), value: isPresented)
    }

    private var dialogCard: some View {
        let accent = isDestructive ? palette.dangerColor : palette.primary
        let confirmFill = LinearGradient(
            colors: isDestructive
                ? [palette.dangerColor, palette.dangerColor.opacity(0.88)]
                : [palette.primary, palette.primary.opacity(0.86)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )

        return VStack(spacing: 16) {
            ZStack {
                Circle()
                    .fill(accent.opacity(palette.isDark ? 0.22 : 0.14))
                    .frame(width: 52, height: 52)
                Image(systemName: icon)
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundColor(accent)
            }

            VStack(spacing: 8) {
                Text(title)
                    .font(.headline.weight(.bold))
                    .foregroundColor(palette.textPrimary)
                    .multilineTextAlignment(.center)

                Text(message)
                    .font(.subheadline)
                    .foregroundColor(palette.textSecondary)
                    .multilineTextAlignment(.center)
                    .fixedSize(horizontal: false, vertical: true)
            }

            HStack(spacing: 12) {
                Button {
                    dismissDialog(invokeCancel: true)
                } label: {
                    Text(cancelTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(palette.textPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(palette.surfaceSecondary)
                        )
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .strokeBorder(palette.outline.opacity(0.65), lineWidth: 0.6)
                        )
                }
                .buttonStyle(.plain)

                Button {
                    dismissDialog(invokeCancel: false)
                    onConfirm()
                } label: {
                    Text(confirmTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundColor(isDestructive ? .white : palette.onPrimary)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(confirmFill)
                        )
                }
                .buttonStyle(.plain)
            }
            .padding(.top, 4)
        }
        .padding(22)
        .frame(maxWidth: 340)
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(palette.surface)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .strokeBorder(palette.outline, lineWidth: 0.9)
        )
        .shadow(color: Color.black.opacity(palette.isDark ? 0.45 : 0.18), radius: 22, x: 0, y: 10)
    }

    private func dismissDialog(invokeCancel: Bool) {
        withAnimation(.spring(response: 0.28, dampingFraction: 0.86)) {
            isPresented = false
        }
        if invokeCancel {
            onCancel?()
        }
    }
}

public extension View {
    func yubraeConfirmDialog(
        isPresented: Binding<Bool>,
        palette: YubraePalette,
        icon: String,
        title: String,
        message: String,
        confirmTitle: String = "确认",
        cancelTitle: String = "取消",
        isDestructive: Bool = false,
        onCancel: (() -> Void)? = nil,
        onConfirm: @escaping () -> Void
    ) -> some View {
        overlay {
            YubraeConfirmDialog(
                isPresented: isPresented,
                palette: palette,
                icon: icon,
                title: title,
                message: message,
                confirmTitle: confirmTitle,
                cancelTitle: cancelTitle,
                isDestructive: isDestructive,
                onCancel: onCancel,
                onConfirm: onConfirm
            )
        }
    }

    @ViewBuilder
    func yubraeSheetStyle(palette: YubraePalette) -> some View {
        if #available(iOS 16.4, *) {
            self
                .presentationDragIndicator(.visible)
                .presentationCornerRadius(24)
                .tint(palette.primary)
        } else {
            self
                .presentationDragIndicator(.visible)
                .tint(palette.primary)
        }
    }
}

/// iOS 16+ 原生 `Layout` 协议流式换行布局，用于标签与快捷选项芯片。
public struct YubraeFlowLayout: Layout {
    public var spacing: CGFloat
    public var lineSpacing: CGFloat

    public init(spacing: CGFloat = 8, lineSpacing: CGFloat = 8) {
        self.spacing = spacing
        self.lineSpacing = lineSpacing
    }

    public func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let maxWidth = proposal.width ?? .infinity
        var currentX: CGFloat = 0
        var currentY: CGFloat = 0
        var rowHeight: CGFloat = 0
        var totalWidth: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX > 0 && currentX + size.width > maxWidth {
                currentX = 0
                currentY += rowHeight + lineSpacing
                rowHeight = 0
            }
            currentX += size.width + spacing
            totalWidth = max(totalWidth, currentX - spacing)
            rowHeight = max(rowHeight, size.height)
        }
        return CGSize(width: min(maxWidth, totalWidth), height: currentY + rowHeight)
    }

    public func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var currentX = bounds.minX
        var currentY = bounds.minY
        var rowHeight: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if currentX > bounds.minX && currentX + size.width > bounds.maxX {
                currentX = bounds.minX
                currentY += rowHeight + lineSpacing
                rowHeight = 0
            }
            subview.place(
                at: CGPoint(x: currentX, y: currentY),
                proposal: ProposedViewSize(width: size.width, height: size.height)
            )
            currentX += size.width + spacing
            rowHeight = max(rowHeight, size.height)
        }
    }
}
