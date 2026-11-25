package com.yubrae.app.capture

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.speech.RecognitionService
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import java.util.Locale

data class SpeechEngineInfo(
    val packageName: String,
    val label: String,
    val componentName: String? = null
)

enum class SpeechErrorKind {
    PERMISSION_DENIED,
    ENGINE_AUTHORIZATION_OR_SERVICE,
    NO_SPEECH_OR_TIMEOUT,
    NETWORK_OR_UNAVAILABLE,
    UNKNOWN
}

data class SpeechErrorDiagnosis(
    val kind: SpeechErrorKind,
    val title: String,
    val message: String,
    val guideSteps: List<String>,
    val showAppPermissionAction: Boolean = false,
    val showEngineSettingsAction: Boolean = false,
    val showVoiceSettingsAction: Boolean = false,
    val showSystemDialogFallback: Boolean = false
)

object SpeechInputHelper {

    internal const val DEFAULT_LANGUAGE_TAG = "zh-CN"
    internal const val DEFAULT_PROMPT = "请说出要记的账单，可连说多笔"
    private const val ACTION_VOICE_INPUT_SETTINGS = "android.settings.VOICE_INPUT_SETTINGS"
    private const val SECURE_VOICE_RECOGNITION_SERVICE = "voice_recognition_service"

    private val XIAOMI_SPEECH_PACKAGES = setOf(
        "com.xiaomi.mibrain.speech",
        "com.miui.voiceassist",
        "com.xiaomi.voiceassistant",
        "com.xiaomi.aiasst.service"
    )

    fun parseComponentPackage(rawComponent: String?): String? {
        val raw = rawComponent?.trim().orEmpty()
        if (raw.isEmpty() || raw.equals("null", ignoreCase = true)) return null
        val slashIndex = raw.indexOf('/')
        if (slashIndex <= 0 || slashIndex >= raw.length - 1) return null
        val pkg = raw.substring(0, slashIndex).trim()
        val cls = raw.substring(slashIndex + 1).trim()
        if (pkg.isEmpty() || cls.isEmpty() || pkg.equals("null", ignoreCase = true)) return null
        return pkg
    }

    fun isXiaomiSpeechPackage(packageName: String?): Boolean {
        val normalized = packageName?.trim()?.lowercase(Locale.ROOT).orEmpty()
        if (normalized.isEmpty()) return false
        if (normalized in XIAOMI_SPEECH_PACKAGES) return true
        return (normalized.startsWith("com.xiaomi.") || normalized.startsWith("com.miui.")) &&
            (normalized.contains("speech") ||
                normalized.contains("voice") ||
                normalized.contains("mibrain"))
    }

    fun diagnoseSpeechRecognizerError(
        errorCode: Int,
        engine: SpeechEngineInfo?,
        isXiaomiRom: Boolean
    ): SpeechErrorDiagnosis {
        val isXiaomi = isXiaomiRom || isXiaomiSpeechPackage(engine?.packageName)
        val engineLabel = engine?.label?.trim()?.takeIf { it.isNotEmpty() } ?: "系统语音引擎"
        val hasEnginePackage = !engine?.packageName.isNullOrBlank()

        return when (errorCode) {
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.PERMISSION_DENIED,
                title = "缺少麦克风录音权限",
                message = if (isXiaomi) {
                    "「余白」或「$engineLabel」缺少录音权限，系统已拦截本次语音采集。"
                } else {
                    "未授予「余白」或「$engineLabel」录音权限，暂时无法进行语音识别。"
                },
                guideSteps = buildList {
                    add("1. 点击「应用权限设置」，确保「余白」的麦克风权限设为允许")
                    if (isXiaomi || hasEnginePackage) {
                        add("2. 点击「去授权语音引擎」，进入「$engineLabel」权限管理并允许「录音」")
                    }
                    if (isXiaomi) {
                        add("3. 若仍无法启动，可在「系统语音设置」中重新确认默认语音识别服务")
                    }
                },
                showAppPermissionAction = true,
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = isXiaomi,
                showSystemDialogFallback = false
            )

            SpeechRecognizer.ERROR_NETWORK -> if (isXiaomi) {
                SpeechErrorDiagnosis(
                    kind = SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE,
                    title = "语音引擎未授权或网络异常 (2)",
                    message = "可能是网络连接异常或小米系统语音引擎尚未授权（常见于“系统语音引擎需要您的授权 / 似乎出错了呢 (2)”）。",
                    guideSteps = listOf(
                        "1. 点击「去授权语音引擎」，检查「$engineLabel」的录音、联网权限并允许后台弹出/关联启动",
                        "2. 点击「系统语音设置」，确认默认语音识别服务并完成系统语音引擎隐私与账号授权",
                        "3. 确认手机网络畅通后，可点击「系统语音弹窗」触发一次系统引擎授权弹窗再试"
                    ),
                    showAppPermissionAction = false,
                    showEngineSettingsAction = true,
                    showVoiceSettingsAction = true,
                    showSystemDialogFallback = true
                )
            } else {
                SpeechErrorDiagnosis(
                    kind = SpeechErrorKind.NETWORK_OR_UNAVAILABLE,
                    title = "语音识别网络异常 (2)",
                    message = "「$engineLabel」连接语音服务失败，请检查设备网络状态或语音服务设置。",
                    guideSteps = listOf(
                        "1. 请确认手机已连接稳定的 Wi-Fi 或移动数据网络",
                        "2. 若需要使用离线/其他识别引擎，可前往「系统语音设置」切换语音输入服务",
                        "3. 也可尝试通过「系统语音弹窗」重新唤醒识别界面"
                    ),
                    showAppPermissionAction = false,
                    showEngineSettingsAction = hasEnginePackage,
                    showVoiceSettingsAction = true,
                    showSystemDialogFallback = true
                )
            }

            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NETWORK_OR_UNAVAILABLE,
                title = "语音识别连接超时 (1)",
                message = "「$engineLabel」等待网络响应超时，暂时未获取到转写结果。",
                guideSteps = listOf(
                    "1. 请检查当前网络连接是否稳定，稍后重新点击「语音输入」",
                    "2. 如多次超时，可点击「系统语音设置」切换或检查语音识别服务"
                ),
                showAppPermissionAction = false,
                showEngineSettingsAction = isXiaomi && hasEnginePackage,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_AUDIO -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.UNKNOWN,
                title = "麦克风录音异常 (3)",
                message = "无法采集语音音频，可能是麦克风被通话/其他应用占用，或系统麦克风权限受限。",
                guideSteps = listOf(
                    "1. 请关闭正在使用麦克风的通话、录音或语音助手应用后重试",
                    "2. 检查手机控制中心「麦克风」隐私开关是否处于开启状态",
                    "3. 点击「应用权限设置」或「去授权语音引擎」，确认录音权限已允许"
                ),
                showAppPermissionAction = true,
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = false,
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_SERVER -> SpeechErrorDiagnosis(
                kind = if (isXiaomi) {
                    SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE
                } else {
                    SpeechErrorKind.NETWORK_OR_UNAVAILABLE
                },
                title = "语音识别服务异常 (4)",
                message = if (isXiaomi) {
                    "「$engineLabel」服务端返回异常，可能是语音引擎未完成授权或云端服务暂时不可用。"
                } else {
                    "「$engineLabel」语音识别服务端返回异常，请检查网络或服务配置后重试。"
                },
                guideSteps = listOf(
                    "1. 检查网络连通性或稍后重试，必要时切换 Wi-Fi / 移动数据",
                    "2. 前往「系统语音设置」确认默认识别引擎状态正常",
                    "3. 可尝试使用「系统语音弹窗」直接唤起引擎确认服务授权"
                ),
                showAppPermissionAction = false,
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_CLIENT -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE,
                title = "语音识别客户端异常 (5)",
                message = "应用内连接「$engineLabel」失败，可能是系统语音服务尚未初始化、受后台限制或未授权。",
                guideSteps = listOf(
                    "1. 点击「去授权语音引擎」检查「$engineLabel」是否被禁用或限制后台启动",
                    "2. 点击「系统语音设置」确认已选定有效的默认语音识别服务",
                    "3. 尝试点击「系统语音弹窗」通过系统界面完成本次语音输入"
                ),
                showAppPermissionAction = false,
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NO_SPEECH_OR_TIMEOUT,
                title = "等待说话超时",
                message = "长时间未检测到有效说话声音，本次聆听已自动结束。",
                guideSteps = listOf(
                    "1. 点击「语音输入」弹出聆听框后请立即开始说话",
                    "2. 确认麦克风未被遮挡，或直接说出「午饭35元 微信」"
                )
            )

            SpeechRecognizer.ERROR_NO_MATCH -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NO_SPEECH_OR_TIMEOUT,
                title = "未听清语音内容",
                message = "没有匹配到可识别的文字，请靠近麦克风清晰说出账单金额与用途。",
                guideSteps = listOf(
                    "1. 保持周围相对安静，用正常语速再试一次",
                    "2. 试试说：「午饭花了35元，打车18元」"
                )
            )

            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE,
                title = "语音引擎正忙 (8)",
                message = "「$engineLabel」正在处理其他语音请求，请稍候片刻再试。",
                guideSteps = listOf(
                    "1. 若系统语音助手或输入法语音正占用引擎，请先关闭后等待 1~2 秒重试",
                    "2. 也可尝试使用「系统语音弹窗」或直接使用键盘语音输入"
                ),
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_TOO_MANY_REQUESTS,
            SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NETWORK_OR_UNAVAILABLE,
                title = "语音服务连接中断 ($errorCode)",
                message = "与「$engineLabel」的连接暂时中断或请求过于频繁，请稍后重试。",
                guideSteps = listOf(
                    "1. 请等待几秒后重新点击「语音输入」",
                    "2. 若持续中断，可点击「系统语音设置」检查识别服务或改用「系统语音弹窗」"
                ),
                showEngineSettingsAction = isXiaomi && hasEnginePackage,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )

            SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
            SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NETWORK_OR_UNAVAILABLE,
                title = "语音语言包不可用 ($errorCode)",
                message = "「$engineLabel」暂未准备好中文（zh-CN）离线或在线识别模型。",
                guideSteps = listOf(
                    "1. 点击「系统语音设置」检查语言设置或下载中文语音包",
                    "2. 可尝试连接网络后重试，或切换为「系统语音弹窗」识别"
                ),
                showEngineSettingsAction = hasEnginePackage,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )

            else -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.UNKNOWN,
                title = "语音识别遇到问题 ($errorCode)",
                message = "「$engineLabel」返回了未预期的错误码 ($errorCode)，你可以检查语音设置或尝试系统语音弹窗。",
                guideSteps = listOf(
                    "1. 点击「系统语音设置」确认默认语音识别服务是否正常",
                    "2. 可尝试使用「系统语音弹窗」，或直接使用键盘语音输入文字"
                ),
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true,
                showSystemDialogFallback = true
            )
        }
    }

    fun diagnoseActivityResultError(
        resultCode: Int,
        engine: SpeechEngineInfo?,
        isXiaomiRom: Boolean
    ): SpeechErrorDiagnosis {
        val isXiaomi = isXiaomiRom || isXiaomiSpeechPackage(engine?.packageName)
        val engineLabel = engine?.label?.trim()?.takeIf { it.isNotEmpty() } ?: "系统语音引擎"
        val hasEnginePackage = !engine?.packageName.isNullOrBlank()

        return when (resultCode) {
            RecognizerIntent.RESULT_NETWORK_ERROR -> if (isXiaomi) {
                SpeechErrorDiagnosis(
                    kind = SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE,
                    title = "语音引擎未授权或网络异常 (2)",
                    message = "可能是网络连接异常或小米系统语音引擎尚未授权（常见于“系统语音引擎需要您的授权 / 似乎出错了呢 (2)”）。",
                    guideSteps = listOf(
                        "1. 点击「去授权语音引擎」，进入「$engineLabel」应用信息并允许录音与联网权限",
                        "2. 点击「系统语音设置」，确认默认语音识别服务并同意引擎授权协议",
                        "3. 授权完成后返回余白，再次点击「语音输入」即可使用"
                    ),
                    showEngineSettingsAction = true,
                    showVoiceSettingsAction = true,
                    showSystemDialogFallback = false
                )
            } else {
                SpeechErrorDiagnosis(
                    kind = SpeechErrorKind.NETWORK_OR_UNAVAILABLE,
                    title = "系统语音网络异常",
                    message = "「$engineLabel」连接识别服务器失败，请检查网络连接后重试。",
                    guideSteps = listOf(
                        "1. 请确认手机 Wi-Fi 或移动数据网络可正常访问",
                        "2. 如需更换识别服务，可点击「系统语音设置」调整"
                    ),
                    showEngineSettingsAction = hasEnginePackage,
                    showVoiceSettingsAction = true
                )
            }

            RecognizerIntent.RESULT_AUDIO_ERROR -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.UNKNOWN,
                title = "系统语音录音异常",
                message = "「$engineLabel」未能成功采集麦克风音频，请检查麦克风占用与权限设置。",
                guideSteps = listOf(
                    "1. 确认麦克风未被通话或其他应用占用",
                    "2. 检查「余白」与「$engineLabel」的录音权限是否已允许"
                ),
                showAppPermissionAction = true,
                showEngineSettingsAction = hasEnginePackage || isXiaomi
            )

            RecognizerIntent.RESULT_CLIENT_ERROR -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE,
                title = "系统语音调用失败",
                message = "调用「$engineLabel」时发生客户端异常，可能是系统语音服务受限或未启用。",
                guideSteps = listOf(
                    "1. 点击「去授权语音引擎」确认服务未被禁用或冻结",
                    "2. 点击「系统语音设置」检查默认语音输入引擎配置"
                ),
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true
            )

            RecognizerIntent.RESULT_SERVER_ERROR -> SpeechErrorDiagnosis(
                kind = if (isXiaomi) {
                    SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE
                } else {
                    SpeechErrorKind.NETWORK_OR_UNAVAILABLE
                },
                title = "系统语音服务异常",
                message = if (isXiaomi) {
                    "「$engineLabel」服务响应异常，可能是未完成引擎授权或云端服务波动。"
                } else {
                    "「$engineLabel」语音服务端响应异常，请稍后重试。"
                },
                guideSteps = listOf(
                    "1. 检查设备网络连接，或前往「系统语音设置」确认引擎状态",
                    "2. 如为小米设备，可先点击「去授权语音引擎」检查权限与协议状态"
                ),
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true
            )

            RecognizerIntent.RESULT_NO_MATCH -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NO_SPEECH_OR_TIMEOUT,
                title = "未听清语音内容",
                message = "系统语音识别未匹配到有效内容，请重新清晰说出账单金额与事由。",
                guideSteps = listOf(
                    "1. 试试说：「午饭35元 微信」或「打车18 支付宝」",
                    "2. 若环境较嘈杂，也可直接在上方文本框输入或使用键盘语音"
                )
            )

            android.app.Activity.RESULT_CANCELED -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.NO_SPEECH_OR_TIMEOUT,
                title = "已取消语音输入",
                message = if (isXiaomi) {
                    "系统语音弹窗已关闭。若界面刚才提示“系统语音引擎需要您的授权”或“似乎出错了呢 (2)”，请先完成引擎授权。"
                } else {
                    "本次系统语音输入已取消，你可以重新点击「语音输入」或直接文字输入。"
                },
                guideSteps = if (isXiaomi) {
                    listOf(
                        "1. 若未授权，点击「去授权语音引擎」或「系统语音设置」完成授权",
                        "2. 如仅误触关闭，重新点击「语音输入」即可"
                    )
                } else {
                    listOf("1. 如需继续语音记账，可再次点击「语音输入」")
                },
                showEngineSettingsAction = isXiaomi,
                showVoiceSettingsAction = isXiaomi
            )

            else -> SpeechErrorDiagnosis(
                kind = SpeechErrorKind.UNKNOWN,
                title = "系统语音返回异常 ($resultCode)",
                message = "系统语音界面返回了未知状态码 ($resultCode)，请检查语音引擎设置或直接输入。",
                guideSteps = listOf(
                    "1. 前往「系统语音设置」确认默认识别引擎可用",
                    "2. 也可以在上方文本框直接使用输入法的语音按钮"
                ),
                showEngineSettingsAction = hasEnginePackage || isXiaomi,
                showVoiceSettingsAction = true
            )
        }
    }

    internal fun writeRecognitionExtras(
        callingPackage: String,
        languageTag: String = DEFAULT_LANGUAGE_TAG,
        prompt: String = DEFAULT_PROMPT,
        partialResults: Boolean = true,
        setAction: (String) -> Unit,
        putStringExtra: (String, String) -> Unit,
        putBooleanExtra: (String, Boolean) -> Unit
    ) {
        val cleanCallingPackage = callingPackage.trim()
        val cleanLanguage = languageTag.trim().ifEmpty { DEFAULT_LANGUAGE_TAG }
        val cleanPrompt = prompt.trim().ifEmpty { DEFAULT_PROMPT }

        setAction(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        putStringExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, cleanCallingPackage)
        putStringExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putStringExtra(RecognizerIntent.EXTRA_LANGUAGE, cleanLanguage)
        putStringExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, cleanLanguage)
        putBooleanExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, partialResults)
        putStringExtra(RecognizerIntent.EXTRA_PROMPT, cleanPrompt)
    }

    fun buildRecognitionIntent(
        callingPackage: String,
        languageTag: String = DEFAULT_LANGUAGE_TAG,
        prompt: String = DEFAULT_PROMPT,
        partialResults: Boolean = true
    ): Intent = Intent().also { intent ->
        writeRecognitionExtras(
            callingPackage = callingPackage,
            languageTag = languageTag,
            prompt = prompt,
            partialResults = partialResults,
            setAction = { intent.action = it },
            putStringExtra = { key, value -> intent.putExtra(key, value) },
            putBooleanExtra = { key, value -> intent.putExtra(key, value) }
        )
    }

    fun hasRecordAudioPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context.applicationContext,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    fun resolveActiveSpeechEngine(context: Context): SpeechEngineInfo? {
        val appContext = context.applicationContext
        val pm = appContext.packageManager

        val rawSecureComponent = runCatching {
            Settings.Secure.getString(appContext.contentResolver, SECURE_VOICE_RECOGNITION_SERVICE)
        }.getOrNull()
        val securePackage = parseComponentPackage(rawSecureComponent)
        if (!securePackage.isNullOrBlank()) {
            val secureLabel = resolveAppLabel(pm, securePackage)
            if (secureLabel != null) {
                return SpeechEngineInfo(
                    packageName = securePackage,
                    label = secureLabel,
                    componentName = rawSecureComponent?.trim()
                )
            }
        }

        val serviceResolve = runCatching {
            pm.queryIntentServices(Intent(RecognitionService.SERVICE_INTERFACE), 0)
                .firstOrNull { it.serviceInfo != null }
        }.getOrNull()
        if (serviceResolve?.serviceInfo != null) {
            val svc = serviceResolve.serviceInfo
            val pkg = svc.packageName.orEmpty().trim()
            if (pkg.isNotEmpty()) {
                val label = runCatching {
                    serviceResolve.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }.getOrNull() ?: resolveAppLabel(pm, pkg) ?: pkg
                return SpeechEngineInfo(
                    packageName = pkg,
                    label = label,
                    componentName = "$pkg/${svc.name}"
                )
            }
        }

        val activityIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        val resolvedActivity = runCatching {
            pm.resolveActivity(activityIntent, PackageManager.MATCH_DEFAULT_ONLY)
        }.getOrNull()
        val chosenActivity = if (resolvedActivity?.activityInfo?.packageName == "android") {
            runCatching {
                pm.queryIntentActivities(activityIntent, 0)
                    .firstOrNull { it.activityInfo != null && it.activityInfo.packageName != "android" }
            }.getOrNull() ?: resolvedActivity
        } else {
            resolvedActivity ?: runCatching {
                pm.queryIntentActivities(activityIntent, 0)
                    .firstOrNull { it.activityInfo != null }
            }.getOrNull()
        }

        val actInfo = chosenActivity?.activityInfo
        if (actInfo != null) {
            val pkg = actInfo.packageName.orEmpty().trim()
            if (pkg.isNotEmpty()) {
                val label = runCatching {
                    chosenActivity.loadLabel(pm)?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                }.getOrNull() ?: resolveAppLabel(pm, pkg) ?: pkg
                return SpeechEngineInfo(
                    packageName = pkg,
                    label = label,
                    componentName = "$pkg/${actInfo.name}"
                )
            }
        }

        if (!securePackage.isNullOrBlank()) {
            return SpeechEngineInfo(
                packageName = securePackage,
                label = securePackage,
                componentName = rawSecureComponent?.trim()
            )
        }
        return null
    }

    fun isInAppSpeechAvailable(context: Context): Boolean =
        runCatching {
            SpeechRecognizer.isRecognitionAvailable(context.applicationContext)
        }.getOrDefault(false)

    fun openSpeechEngineAppDetails(context: Context, packageName: String?): Boolean {
        val targetPackage = packageName?.trim()?.takeIf { it.isNotEmpty() }
            ?: resolveActiveSpeechEngine(context)?.packageName?.trim()?.takeIf { it.isNotEmpty() }
        if (targetPackage != null) {
            val detailsIntent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:$targetPackage")
            }
            if (safeStartActivity(context, detailsIntent)) {
                return true
            }
        }
        return openVoiceInputSettings(context)
    }

    fun openVoiceInputSettings(context: Context): Boolean {
        val candidates = listOf(
            Intent(ACTION_VOICE_INPUT_SETTINGS),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent(Settings.ACTION_SETTINGS)
        )
        return candidates.any { safeStartActivity(context, it) }
    }

    fun openAppPermissionSettings(context: Context): Boolean {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
        }
        return safeStartActivity(context, intent)
    }

    private fun resolveAppLabel(pm: PackageManager, packageName: String): String? =
        runCatching {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString().trim().takeIf { it.isNotEmpty() }
        }.getOrNull()

    private fun safeStartActivity(context: Context, intent: Intent): Boolean =
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        }.getOrDefault(false)
}
