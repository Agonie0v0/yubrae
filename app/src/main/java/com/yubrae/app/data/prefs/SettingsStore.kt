package com.yubrae.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.yubrae.app.core.model.CurrencyCatalog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.kairoDataStore: DataStore<Preferences> by preferencesDataStore(name = "kairo_settings")

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

object ThemeStyle {
    const val YIMU_FRESH = "YIMU_FRESH"
    const val PRISM_TEAL = "PRISM_TEAL"
    const val SAKURA_PINK = "SAKURA_PINK"
    const val SKY_BLUE = "SKY_BLUE"
    const val AMBER_WARM = "AMBER_WARM"
    const val LAVENDER_MIST = "LAVENDER_MIST"
    const val FOREST_MATCHA = "FOREST_MATCHA"
    const val CORAL_SUNSET = "CORAL_SUNSET"
    const val OBSIDIAN_MONO = "OBSIDIAN_MONO"
    const val LATTE_MOCHA = "LATTE_MOCHA"

    val all = listOf(
        YIMU_FRESH,
        PRISM_TEAL,
        SAKURA_PINK,
        SKY_BLUE,
        AMBER_WARM,
        LAVENDER_MIST,
        FOREST_MATCHA,
        CORAL_SUNSET,
        OBSIDIAN_MONO,
        LATTE_MOCHA
    )
}

object AssetHiddenModule {
    const val REIMBURSE = "REIMBURSE"
    const val DEBT = "DEBT"
    const val INVEST = "INVEST"

    val all = listOf(REIMBURSE, DEBT, INVEST)
}

data class AppSettings(
    val baseCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val pinnedCurrencies: List<String> = CurrencyCatalog.PINNED_CURRENCIES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val themeStyle: String = ThemeStyle.YIMU_FRESH,
    val activeLedgerId: Long = 0L,
    val privacyMaskEnabled: Boolean = false,
    val biometricLockEnabled: Boolean = false,
    val floatingRichEditorEnabled: Boolean = true,
    val notificationCaptureEnabled: Boolean = true,
    val smsCaptureEnabled: Boolean = true,
    val accessibilityCaptureEnabled: Boolean = true,
    val accessibilityPaySuccessEnabled: Boolean = true,
    val accessibilityBillDetailEnabled: Boolean = true,
    val autoAccountingBridgeEnabled: Boolean = true,
    val floatingConfirmEnabled: Boolean = true,
    val floatingAutoConfirmSeconds: Int = 8,
    val defaultPendingWhenUncertain: Boolean = false,
    val llmEnabled: Boolean = false,
    val llmBaseUrl: String = "https://api.openai.com/v1",
    val llmApiKey: String = "",
    val llmModel: String = "gpt-4o-mini",
    val webDavUrl: String = "",
    val webDavUsername: String = "",
    val webDavPassword: String = "",
    val webDavRemoteDir: String = "/Yubrae",
    val backupPassphrase: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastRateSyncMillis: Long = 0L,
    val lastBackupSyncMillis: Long = 0L,
    val oledPureBlack: Boolean = false,
    val greenExpenseMode: Boolean = false,
    val accessibilityOrderDetailEnabled: Boolean = true,
    val hiddenAssetAccountIds: Set<Long> = emptySet(),
    val hiddenAssetModules: Set<String> = emptySet(),
    val hideRecentsPreviewEnabled: Boolean = false
)

class SettingsStore(private val context: Context) {

    private object Keys {
        val BASE_CURRENCY = stringPreferencesKey("base_currency")
        val PINNED_CURRENCIES = stringPreferencesKey("pinned_currencies")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_STYLE = stringPreferencesKey("theme_style")
        val ACTIVE_LEDGER_ID = longPreferencesKey("active_ledger_id")
        val PRIVACY_MASK = booleanPreferencesKey("privacy_mask")
        val BIOMETRIC_LOCK = booleanPreferencesKey("biometric_lock")
        val FLOATING_RICH_EDITOR = booleanPreferencesKey("floating_rich_editor")
        val NOTIFICATION_CAPTURE = booleanPreferencesKey("notification_capture")
        val SMS_CAPTURE = booleanPreferencesKey("sms_capture")
        val ACCESSIBILITY_CAPTURE = booleanPreferencesKey("accessibility_capture")
        val ACCESSIBILITY_PAY_SUCCESS = booleanPreferencesKey("accessibility_pay_success")
        val ACCESSIBILITY_BILL_DETAIL = booleanPreferencesKey("accessibility_bill_detail")
        val AUTO_ACCOUNTING_BRIDGE = booleanPreferencesKey("auto_accounting_bridge")
        val FLOATING_CONFIRM = booleanPreferencesKey("floating_confirm")
        val FLOATING_SECONDS = intPreferencesKey("floating_seconds")
        val PENDING_WHEN_UNCERTAIN = booleanPreferencesKey("pending_when_uncertain")
        val LLM_ENABLED = booleanPreferencesKey("llm_enabled")
        val LLM_BASE_URL = stringPreferencesKey("llm_base_url")
        val LLM_API_KEY = stringPreferencesKey("llm_api_key")
        val LLM_MODEL = stringPreferencesKey("llm_model")
        val WEBDAV_URL = stringPreferencesKey("webdav_url")
        val WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val WEBDAV_REMOTE_DIR = stringPreferencesKey("webdav_remote_dir")
        val BACKUP_PASSPHRASE = stringPreferencesKey("backup_passphrase")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_RATE_SYNC = longPreferencesKey("last_rate_sync")
        val LAST_BACKUP_SYNC = longPreferencesKey("last_backup_sync")
        val OLED_PURE_BLACK = booleanPreferencesKey("oled_pure_black")
        val GREEN_EXPENSE_MODE = booleanPreferencesKey("green_expense_mode")
        val ACCESSIBILITY_ORDER_DETAIL = booleanPreferencesKey("accessibility_order_detail")
        val HIDDEN_ASSET_ACCOUNT_IDS = stringPreferencesKey("hidden_asset_account_ids")
        val HIDDEN_ASSET_MODULES = stringPreferencesKey("hidden_asset_modules")
        val HIDE_RECENTS_PREVIEW = booleanPreferencesKey("hide_recents_preview")
    }

    val settingsFlow: Flow<AppSettings> = context.kairoDataStore.data.map { prefs ->
        val pinnedRaw = prefs[Keys.PINNED_CURRENCIES]
        val pinned = pinnedRaw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() } ?: CurrencyCatalog.PINNED_CURRENCIES
        val hiddenAccountIds = prefs[Keys.HIDDEN_ASSET_ACCOUNT_IDS]
            ?.split(",")
            ?.mapNotNull { it.trim().toLongOrNull() }
            ?.toSet()
            ?: emptySet()
        val hiddenModules = prefs[Keys.HIDDEN_ASSET_MODULES]
            ?.split(",")
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.toSet()
            ?: emptySet()
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        AppSettings(
            baseCurrency = prefs[Keys.BASE_CURRENCY] ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY,
            pinnedCurrencies = pinned,
            themeMode = theme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            themeStyle = prefs[Keys.THEME_STYLE] ?: ThemeStyle.YIMU_FRESH,
            activeLedgerId = prefs[Keys.ACTIVE_LEDGER_ID] ?: 0L,
            privacyMaskEnabled = prefs[Keys.PRIVACY_MASK] ?: false,
            biometricLockEnabled = prefs[Keys.BIOMETRIC_LOCK] ?: false,
            floatingRichEditorEnabled = prefs[Keys.FLOATING_RICH_EDITOR] ?: true,
            notificationCaptureEnabled = prefs[Keys.NOTIFICATION_CAPTURE] ?: true,
            smsCaptureEnabled = prefs[Keys.SMS_CAPTURE] ?: true,
            accessibilityCaptureEnabled = prefs[Keys.ACCESSIBILITY_CAPTURE] ?: true,
            accessibilityPaySuccessEnabled = prefs[Keys.ACCESSIBILITY_PAY_SUCCESS] ?: true,
            accessibilityBillDetailEnabled = prefs[Keys.ACCESSIBILITY_BILL_DETAIL] ?: true,
            autoAccountingBridgeEnabled = prefs[Keys.AUTO_ACCOUNTING_BRIDGE] ?: true,
            floatingConfirmEnabled = prefs[Keys.FLOATING_CONFIRM] ?: true,
            floatingAutoConfirmSeconds = prefs[Keys.FLOATING_SECONDS] ?: 8,
            defaultPendingWhenUncertain = prefs[Keys.PENDING_WHEN_UNCERTAIN] ?: false,
            llmEnabled = prefs[Keys.LLM_ENABLED] ?: false,
            llmBaseUrl = prefs[Keys.LLM_BASE_URL] ?: "https://api.openai.com/v1",
            llmApiKey = prefs[Keys.LLM_API_KEY] ?: "",
            llmModel = prefs[Keys.LLM_MODEL] ?: "gpt-4o-mini",
            webDavUrl = prefs[Keys.WEBDAV_URL] ?: "",
            webDavUsername = prefs[Keys.WEBDAV_USERNAME] ?: "",
            webDavPassword = prefs[Keys.WEBDAV_PASSWORD] ?: "",
            webDavRemoteDir = prefs[Keys.WEBDAV_REMOTE_DIR] ?: "/Yubrae",
            backupPassphrase = prefs[Keys.BACKUP_PASSPHRASE] ?: "",
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP] ?: false,
            lastRateSyncMillis = prefs[Keys.LAST_RATE_SYNC] ?: 0L,
            lastBackupSyncMillis = prefs[Keys.LAST_BACKUP_SYNC] ?: 0L,
            oledPureBlack = prefs[Keys.OLED_PURE_BLACK] ?: false,
            greenExpenseMode = prefs[Keys.GREEN_EXPENSE_MODE] ?: false,
            accessibilityOrderDetailEnabled = prefs[Keys.ACCESSIBILITY_ORDER_DETAIL] ?: true,
            hiddenAssetAccountIds = hiddenAccountIds,
            hiddenAssetModules = hiddenModules,
            hideRecentsPreviewEnabled = prefs[Keys.HIDE_RECENTS_PREVIEW] ?: false
        )
    }

    suspend fun getSnapshot(): AppSettings = settingsFlow.first()

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        val current = getSnapshot()
        val updated = transform(current)
        saveAll(updated)
    }

    suspend fun saveAll(settings: AppSettings) {
        context.kairoDataStore.edit { prefs ->
            prefs[Keys.BASE_CURRENCY] = settings.baseCurrency
            prefs[Keys.PINNED_CURRENCIES] = settings.pinnedCurrencies.joinToString(",")
            prefs[Keys.THEME_MODE] = settings.themeMode.name
            prefs[Keys.DYNAMIC_COLOR] = settings.dynamicColor
            prefs[Keys.THEME_STYLE] = settings.themeStyle
            prefs[Keys.ACTIVE_LEDGER_ID] = settings.activeLedgerId
            prefs[Keys.PRIVACY_MASK] = settings.privacyMaskEnabled
            prefs[Keys.BIOMETRIC_LOCK] = settings.biometricLockEnabled
            prefs[Keys.FLOATING_RICH_EDITOR] = settings.floatingRichEditorEnabled
            prefs[Keys.NOTIFICATION_CAPTURE] = settings.notificationCaptureEnabled
            prefs[Keys.SMS_CAPTURE] = settings.smsCaptureEnabled
            prefs[Keys.ACCESSIBILITY_CAPTURE] = settings.accessibilityCaptureEnabled
            prefs[Keys.ACCESSIBILITY_PAY_SUCCESS] = settings.accessibilityPaySuccessEnabled
            prefs[Keys.ACCESSIBILITY_BILL_DETAIL] = settings.accessibilityBillDetailEnabled
            prefs[Keys.AUTO_ACCOUNTING_BRIDGE] = settings.autoAccountingBridgeEnabled
            prefs[Keys.FLOATING_CONFIRM] = settings.floatingConfirmEnabled
            prefs[Keys.FLOATING_SECONDS] = settings.floatingAutoConfirmSeconds
            prefs[Keys.PENDING_WHEN_UNCERTAIN] = settings.defaultPendingWhenUncertain
            prefs[Keys.LLM_ENABLED] = settings.llmEnabled
            prefs[Keys.LLM_BASE_URL] = settings.llmBaseUrl
            prefs[Keys.LLM_API_KEY] = settings.llmApiKey
            prefs[Keys.LLM_MODEL] = settings.llmModel
            prefs[Keys.WEBDAV_URL] = settings.webDavUrl
            prefs[Keys.WEBDAV_USERNAME] = settings.webDavUsername
            prefs[Keys.WEBDAV_PASSWORD] = settings.webDavPassword
            prefs[Keys.WEBDAV_REMOTE_DIR] = settings.webDavRemoteDir
            prefs[Keys.BACKUP_PASSPHRASE] = settings.backupPassphrase
            prefs[Keys.AUTO_BACKUP] = settings.autoBackupEnabled
            prefs[Keys.LAST_RATE_SYNC] = settings.lastRateSyncMillis
            prefs[Keys.LAST_BACKUP_SYNC] = settings.lastBackupSyncMillis
            prefs[Keys.OLED_PURE_BLACK] = settings.oledPureBlack
            prefs[Keys.GREEN_EXPENSE_MODE] = settings.greenExpenseMode
            prefs[Keys.ACCESSIBILITY_ORDER_DETAIL] = settings.accessibilityOrderDetailEnabled
            prefs[Keys.HIDDEN_ASSET_ACCOUNT_IDS] = settings.hiddenAssetAccountIds.sorted().joinToString(",")
            prefs[Keys.HIDDEN_ASSET_MODULES] = settings.hiddenAssetModules.sorted().joinToString(",")
            prefs[Keys.HIDE_RECENTS_PREVIEW] = settings.hideRecentsPreviewEnabled
        }
    }

    suspend fun markRateSynced(epochMillis: Long = System.currentTimeMillis()) {
        context.kairoDataStore.edit { it[Keys.LAST_RATE_SYNC] = epochMillis }
    }

    suspend fun markBackupSynced(epochMillis: Long = System.currentTimeMillis()) {
        context.kairoDataStore.edit { it[Keys.LAST_BACKUP_SYNC] = epochMillis }
    }
}
