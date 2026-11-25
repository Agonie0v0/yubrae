import SwiftUI

/// 全局共享运行时容器，确保 SwiftUI 界面与原生 iOS App Intents 快捷指令共享同一个 `LedgerStore` 实例。
@MainActor
public enum YubraeRuntime {
    public static let sharedStore = LedgerStore()
    nonisolated public static let llmService = LlmBillService()
    nonisolated public static let exchangeRateService = ExchangeRateService()
    nonisolated public static let webDavClient = WebDavClient()
    nonisolated public static let updateChecker = AppUpdateChecker()
}

@main
struct YubraeApp: App {
    @ObservedObject private var store = YubraeRuntime.sharedStore

    var body: some Scene {
        WindowGroup {
            RootTabView(store: store)
                .environmentObject(store)
                .preferredColorScheme(YubraeTheme.colorScheme(for: store.settings.themeMode))
        }
    }
}
