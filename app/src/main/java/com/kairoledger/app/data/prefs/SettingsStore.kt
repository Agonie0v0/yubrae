package com.kairoledger.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.kairoledger.app.core.model.CurrencyCatalog
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
}

data class AppSettings(
    val baseCurrency: String = CurrencyCatalog.DEFAULT_BASE_CURRENCY,
    val pinnedCurrencies: List<String> = CurrencyCatalog.PINNED_CURRENCIES,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val themeStyle: String = ThemeStyle.YIMU_FRESH,
    val activeLedgerId: Long = 1L,
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
    val webDavRemoteDir: String = "/KairoLedger",
    val backupPassphrase: String = "",
    val autoBackupEnabled: Boolean = false,
    val lastRateSyncMillis: Long = 0L,
    val lastBackupSyncMillis: Long = 0L
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
    }

    val settingsFlow: Flow<AppSettings> = context.kairoDataStore.data.map { prefs ->
        val pinnedRaw = prefs[Keys.PINNED_CURRENCIES]
        val pinned = pinnedRaw?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() }
            ?.takeIf { it.isNotEmpty() } ?: CurrencyCatalog.PINNED_CURRENCIES
        val themeStr = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val theme = runCatching { ThemeMode.valueOf(themeStr) }.getOrDefault(ThemeMode.SYSTEM)
        AppSettings(
            baseCurrency = prefs[Keys.BASE_CURRENCY] ?: CurrencyCatalog.DEFAULT_BASE_CURRENCY,
            pinnedCurrencies = pinned,
            themeMode = theme,
            dynamicColor = prefs[Keys.DYNAMIC_COLOR] ?: false,
            themeStyle = prefs[Keys.THEME_STYLE] ?: ThemeStyle.YIMU_FRESH,
            activeLedgerId = prefs[Keys.ACTIVE_LEDGER_ID] ?: 1L,
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
            webDavRemoteDir = prefs[Keys.WEBDAV_REMOTE_DIR] ?: "/KairoLedger",
            backupPassphrase = prefs[Keys.BACKUP_PASSPHRASE] ?: "",
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP] ?: false,
            lastRateSyncMillis = prefs[Keys.LAST_RATE_SYNC] ?: 0L,
            lastBackupSyncMillis = prefs[Keys.LAST_BACKUP_SYNC] ?: 0L
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
        }
    }

    suspend fun markRateSynced(epochMillis: Long = System.currentTimeMillis()) {
        context.kairoDataStore.edit { it[Keys.LAST_RATE_SYNC] = epochMillis }
    }

    suspend fun markBackupSynced(epochMillis: Long = System.currentTimeMillis()) {
        context.kairoDataStore.edit { it[Keys.LAST_BACKUP_SYNC] = epochMillis }
    }
}
