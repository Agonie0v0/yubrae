package com.yubrae.app.capture

import android.app.Activity
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechInputHelperTest {

    @Test
    fun parseComponentPackage_extractsValidComponentPackagesAndRejectsInvalidInputs() {
        assertEquals(
            "com.xiaomi.mibrain.speech",
            SpeechInputHelper.parseComponentPackage("com.xiaomi.mibrain.speech/.SpeechService")
        )
        assertEquals(
            "com.google.android.tts",
            SpeechInputHelper.parseComponentPackage(
                "  com.google.android.tts/com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService  "
            )
        )
        assertNull(SpeechInputHelper.parseComponentPackage(null))
        assertNull(SpeechInputHelper.parseComponentPackage(""))
        assertNull(SpeechInputHelper.parseComponentPackage("   "))
        assertNull(SpeechInputHelper.parseComponentPackage("null"))
        assertNull(SpeechInputHelper.parseComponentPackage("com.xiaomi.mibrain.speech"))
        assertNull(SpeechInputHelper.parseComponentPackage("com.xiaomi.mibrain.speech/"))
        assertNull(SpeechInputHelper.parseComponentPackage("/.SpeechService"))
    }

    @Test
    fun isXiaomiSpeechPackage_recognizesXiaomiAndMiuiPackagesOnly() {
        assertTrue(SpeechInputHelper.isXiaomiSpeechPackage("com.xiaomi.mibrain.speech"))
        assertTrue(SpeechInputHelper.isXiaomiSpeechPackage("COM.MIUI.VOICEASSIST"))
        assertTrue(SpeechInputHelper.isXiaomiSpeechPackage("com.xiaomi.voiceassistant"))
        assertTrue(SpeechInputHelper.isXiaomiSpeechPackage("com.xiaomi.aiasst.service"))
        assertTrue(SpeechInputHelper.isXiaomiSpeechPackage("com.xiaomi.custom.speech.engine"))

        assertFalse(SpeechInputHelper.isXiaomiSpeechPackage("com.google.android.tts"))
        assertFalse(SpeechInputHelper.isXiaomiSpeechPackage("com.google.android.as"))
        assertFalse(SpeechInputHelper.isXiaomiSpeechPackage("com.xiaomi.market"))
        assertFalse(SpeechInputHelper.isXiaomiSpeechPackage(null))
        assertFalse(SpeechInputHelper.isXiaomiSpeechPackage("  "))
    }

    @Test
    fun diagnoseSpeechRecognizerError_handlesXiaomiErrorNetworkAuthorizationCase() {
        val xiaomiEngine = SpeechEngineInfo(
            packageName = "com.xiaomi.mibrain.speech",
            label = "小爱语音引擎",
            componentName = "com.xiaomi.mibrain.speech/.SpeechService"
        )

        val byPackageOnly = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            engine = xiaomiEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, byPackageOnly.kind)
        assertTrue(byPackageOnly.showEngineSettingsAction)
        assertTrue(byPackageOnly.showVoiceSettingsAction)
        assertTrue(byPackageOnly.showSystemDialogFallback)
        assertFalse(byPackageOnly.showAppPermissionAction)
        assertTrue(byPackageOnly.message.contains("似乎出错了呢 (2)"))
        assertTrue(byPackageOnly.guideSteps.size >= 3)

        val byRomOnly = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            engine = SpeechEngineInfo("com.example.speech", "默认语音引擎"),
            isXiaomiRom = true
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, byRomOnly.kind)
        assertTrue(byRomOnly.showEngineSettingsAction)
        assertTrue(byRomOnly.showVoiceSettingsAction)

        val nonXiaomi = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_NETWORK,
            engine = SpeechEngineInfo("com.google.android.tts", "Google 语音服务"),
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NETWORK_OR_UNAVAILABLE, nonXiaomi.kind)
        assertTrue(nonXiaomi.showVoiceSettingsAction)
        assertTrue(nonXiaomi.showSystemDialogFallback)
        assertFalse(nonXiaomi.message.contains("小米"))
    }

    @Test
    fun diagnoseSpeechRecognizerError_handlesCommonErrorsWithActionFlags() {
        val genericEngine = SpeechEngineInfo("com.google.android.tts", "Google 语音服务")

        val permDenied = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.PERMISSION_DENIED, permDenied.kind)
        assertTrue(permDenied.showAppPermissionAction)
        assertTrue(permDenied.showEngineSettingsAction)
        assertFalse(permDenied.showSystemDialogFallback)

        val noMatch = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_NO_MATCH,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NO_SPEECH_OR_TIMEOUT, noMatch.kind)
        assertFalse(noMatch.showAppPermissionAction)
        assertFalse(noMatch.showEngineSettingsAction)
        assertFalse(noMatch.showVoiceSettingsAction)
        assertFalse(noMatch.showSystemDialogFallback)

        val timeout = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NO_SPEECH_OR_TIMEOUT, timeout.kind)

        val busy = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, busy.kind)
        assertTrue(busy.title.contains("8"))
        assertTrue(busy.showSystemDialogFallback)

        val audioErr = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_AUDIO,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertTrue(audioErr.showAppPermissionAction)
        assertTrue(audioErr.showEngineSettingsAction)

        val clientErr = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_CLIENT,
            engine = genericEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, clientErr.kind)
        assertTrue(clientErr.showVoiceSettingsAction)
        assertTrue(clientErr.showSystemDialogFallback)

        val serverErrXiaomi = SpeechInputHelper.diagnoseSpeechRecognizerError(
            errorCode = SpeechRecognizer.ERROR_SERVER,
            engine = null,
            isXiaomiRom = true
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, serverErrXiaomi.kind)
        assertTrue(serverErrXiaomi.showEngineSettingsAction)
        assertTrue(serverErrXiaomi.showVoiceSettingsAction)
    }

    @Test
    fun diagnoseActivityResultError_handlesRecognizerResultsAndCancel() {
        val xiaomiEngine = SpeechEngineInfo("com.miui.voiceassist", "小爱同学")
        val aospEngine = SpeechEngineInfo("com.google.android.tts", "Google 语音服务")

        val xiaomiNetwork = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_NETWORK_ERROR,
            engine = xiaomiEngine,
            isXiaomiRom = true
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, xiaomiNetwork.kind)
        assertTrue(xiaomiNetwork.showEngineSettingsAction)
        assertTrue(xiaomiNetwork.showVoiceSettingsAction)
        assertTrue(xiaomiNetwork.message.contains("似乎出错了呢 (2)"))

        val genericNetwork = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_NETWORK_ERROR,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NETWORK_OR_UNAVAILABLE, genericNetwork.kind)
        assertTrue(genericNetwork.showVoiceSettingsAction)

        val audioErr = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_AUDIO_ERROR,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertTrue(audioErr.showAppPermissionAction)

        val clientErr = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_CLIENT_ERROR,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, clientErr.kind)
        assertTrue(clientErr.showVoiceSettingsAction)

        val serverErr = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_SERVER_ERROR,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NETWORK_OR_UNAVAILABLE, serverErr.kind)

        val noMatch = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = RecognizerIntent.RESULT_NO_MATCH,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NO_SPEECH_OR_TIMEOUT, noMatch.kind)

        val xiaomiCanceled = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = Activity.RESULT_CANCELED,
            engine = xiaomiEngine,
            isXiaomiRom = true
        )
        assertEquals(SpeechErrorKind.NO_SPEECH_OR_TIMEOUT, xiaomiCanceled.kind)
        assertTrue(xiaomiCanceled.showEngineSettingsAction)
        assertTrue(xiaomiCanceled.showVoiceSettingsAction)

        val genericCanceled = SpeechInputHelper.diagnoseActivityResultError(
            resultCode = Activity.RESULT_CANCELED,
            engine = aospEngine,
            isXiaomiRom = false
        )
        assertEquals(SpeechErrorKind.NO_SPEECH_OR_TIMEOUT, genericCanceled.kind)
        assertFalse(genericCanceled.showEngineSettingsAction)
    }

    @Test
    fun writeRecognitionExtras_populatesAllRequiredIntentActionAndExtras() {
        var capturedAction: String? = null
        val stringExtras = linkedMapOf<String, String>()
        val booleanExtras = linkedMapOf<String, Boolean>()

        SpeechInputHelper.writeRecognitionExtras(
            callingPackage = "com.yubrae.app",
            setAction = { capturedAction = it },
            putStringExtra = { k, v -> stringExtras[k] = v },
            putBooleanExtra = { k, v -> booleanExtras[k] = v }
        )

        assertEquals(RecognizerIntent.ACTION_RECOGNIZE_SPEECH, capturedAction)
        assertEquals("com.yubrae.app", stringExtras[RecognizerIntent.EXTRA_CALLING_PACKAGE])
        assertEquals(
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
            stringExtras[RecognizerIntent.EXTRA_LANGUAGE_MODEL]
        )
        assertEquals("zh-CN", stringExtras[RecognizerIntent.EXTRA_LANGUAGE])
        assertEquals("zh-CN", stringExtras[RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE])
        assertEquals("请说出要记的账单，可连说多笔", stringExtras[RecognizerIntent.EXTRA_PROMPT])
        assertEquals(true, booleanExtras[RecognizerIntent.EXTRA_PARTIAL_RESULTS])
    }

    @Test
    fun isExcludedNonAsrService_filtersAutomationMetaAuraAndTrampolineBridges() {
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.arlosoft.macrodroid",
                serviceClassName = "com.arlosoft.macrodroid.voiceservice.RecognitionServiceTrampoline",
                label = "MacroDroid"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "net.dinglisch.android.taskerm",
                serviceClassName = "net.dinglisch.android.taskerm.VoiceService"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.llamalab.automate",
                serviceClassName = "com.llamalab.automate.SpeechRecognitionService"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.facebook.aura",
                serviceClassName = "com.facebook.aura.assist.HatchRecognitionService",
                label = "Aura"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.meta.wearable.companion",
                serviceClassName = "com.meta.wearable.companion.SpeechService"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.example.custom",
                serviceClassName = "com.example.custom.VoiceTRAMPOLINEService"
            )
        )
        assertTrue(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.example.custom",
                serviceClassName = "com.example.custom.AsrBridgeService"
            )
        )

        assertFalse(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.xiaomi.mibrain.speech",
                serviceClassName = "com.xiaomi.mibrain.speech.asr.AsrService",
                label = "小爱语音引擎"
            )
        )
        assertFalse(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.google.android.tts",
                serviceClassName = "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
                label = "Google 语音服务"
            )
        )
        assertFalse(
            SpeechInputHelper.isExcludedNonAsrService(
                packageName = "com.iflytek.speechcloud",
                serviceClassName = "com.iflytek.speechcloud.SpeechRecognitionService",
                label = "讯飞语音+"
            )
        )
        assertFalse(SpeechInputHelper.isExcludedNonAsrService(null, null, null))
    }

    @Test
    fun buildSpeechEngineCandidates_filtersXiaomi14RealDeviceListToAsrService() {
        val raw = listOf(
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.arlosoft.macrodroid",
                serviceClassName = "com.arlosoft.macrodroid.voiceservice.RecognitionServiceTrampoline",
                label = "MacroDroid",
                isSystemApp = false
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.xiaomi.mibrain.speech",
                serviceClassName = "com.xiaomi.mibrain.speech.asr.AsrService",
                label = "小爱语音引擎",
                isSystemApp = true
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.facebook.aura",
                serviceClassName = "com.facebook.aura.assist.HatchRecognitionService",
                label = "Aura",
                isSystemApp = false
            )
        )

        val candidates = SpeechInputHelper.buildSpeechEngineCandidates(
            rawEntries = raw,
            isXiaomiRom = true
        )

        assertEquals(1, candidates.size)
        val first = candidates.first()
        assertEquals("com.xiaomi.mibrain.speech", first.packageName)
        assertEquals("com.xiaomi.mibrain.speech.asr.AsrService", first.serviceClassName)
        assertEquals("小爱语音引擎", first.label)
        assertTrue(first.isXiaomiEngine)
        assertTrue(first.isSystemApp)

        val info = first.toEngineInfo()
        assertEquals("com.xiaomi.mibrain.speech", info.packageName)
        assertEquals("小爱语音引擎", info.label)
        assertEquals("com.xiaomi.mibrain.speech/com.xiaomi.mibrain.speech.asr.AsrService", info.componentName)
    }

    @Test
    fun buildSpeechEngineCandidates_ranksByRomDeduplicatesAndHonorsValidSystemDefault() {
        val raw = listOf(
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "net.dinglisch.android.taskerm",
                serviceClassName = "net.dinglisch.android.taskerm.SpeechSvc",
                label = "Tasker",
                isSystemApp = false,
                isSystemDefault = true // 即使系统默认指向被排除服务，也必须被过滤
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.example.thirdparty",
                serviceClassName = "com.example.thirdparty.SpeechSvc",
                label = "第三方识别",
                isSystemApp = false
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.iflytek.speechcloud",
                serviceClassName = "com.iflytek.speechcloud.AsrService",
                label = "讯飞语记",
                isSystemApp = false
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.google.android.tts",
                serviceClassName = "com.google.android.apps.speech.tts.googletts.service.GoogleTTSRecognitionService",
                label = "Google 语音服务",
                isSystemApp = true
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.miui.voiceassist",
                serviceClassName = "com.miui.voiceassist.VoiceService",
                label = "小爱同学",
                isSystemApp = true
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.xiaomi.mibrain.speech",
                serviceClassName = ".asr.AsrService",
                label = "小爱语音引擎",
                isSystemApp = true
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.xiaomi.mibrain.speech",
                serviceClassName = "com.xiaomi.mibrain.speech.asr.AsrService",
                label = "com.xiaomi.mibrain.speech",
                isSystemApp = false
            ),
            SpeechInputHelper.RawSpeechServiceEntry(
                packageName = "com.meta.wearable.audio",
                serviceClassName = "com.meta.wearable.audio.WearableSpeechService",
                label = "Meta Wearable",
                isSystemApp = false
            )
        )

        val xiaomiRanked = SpeechInputHelper.buildSpeechEngineCandidates(raw, isXiaomiRom = true)
        assertEquals(
            listOf(
                "com.xiaomi.mibrain.speech",
                "com.miui.voiceassist",
                "com.google.android.tts",
                "com.iflytek.speechcloud",
                "com.example.thirdparty"
            ),
            xiaomiRanked.map { it.packageName }
        )
        assertEquals("com.xiaomi.mibrain.speech.asr.AsrService", xiaomiRanked[0].serviceClassName)
        assertEquals("小爱语音引擎", xiaomiRanked[0].label)

        val nonXiaomiRanked = SpeechInputHelper.buildSpeechEngineCandidates(raw, isXiaomiRom = false)
        assertEquals(
            listOf(
                "com.google.android.tts",
                "com.miui.voiceassist",
                "com.xiaomi.mibrain.speech",
                "com.iflytek.speechcloud",
                "com.example.thirdparty"
            ),
            nonXiaomiRanked.map { it.packageName }
        )

        val withValidDefault = raw + SpeechInputHelper.RawSpeechServiceEntry(
            packageName = "com.iflytek.speechcloud",
            serviceClassName = "com.iflytek.speechcloud.AsrService",
            label = "讯飞语记",
            isSystemApp = false,
            isSystemDefault = true
        )
        val defaultFirst = SpeechInputHelper.buildSpeechEngineCandidates(withValidDefault, isXiaomiRom = true)
        assertEquals("com.iflytek.speechcloud", defaultFirst.first().packageName)
        assertEquals(5, defaultFirst.size)
    }

    @Test
    fun isRetryableEngineBindingError_distinguishesBindingErrorsFromUserSpeechResults() {
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_CLIENT))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_SERVER))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_NETWORK))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_NETWORK_TIMEOUT))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_AUDIO))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_TOO_MANY_REQUESTS))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED))
        assertTrue(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE))

        assertFalse(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_SPEECH_TIMEOUT))
        assertFalse(SpeechInputHelper.isRetryableEngineBindingError(SpeechRecognizer.ERROR_NO_MATCH))
        assertFalse(SpeechInputHelper.isRetryableEngineBindingError(-1))
    }

    @Test
    fun diagnoseAllEnginesFailed_listsLegitimateTriedEnginesAndNeverMentionsExcludedTrampolines() {
        val triedWithNoise = listOf(
            SpeechEngineInfo(
                packageName = "com.arlosoft.macrodroid",
                label = "MacroDroid",
                componentName = "com.arlosoft.macrodroid/com.arlosoft.macrodroid.voiceservice.RecognitionServiceTrampoline"
            ),
            SpeechEngineInfo(
                packageName = "com.xiaomi.mibrain.speech",
                label = "小爱语音引擎",
                componentName = "com.xiaomi.mibrain.speech/com.xiaomi.mibrain.speech.asr.AsrService"
            ),
            SpeechEngineInfo(
                packageName = "com.iflytek.speechcloud",
                label = "讯飞语音+",
                componentName = "com.iflytek.speechcloud/com.iflytek.speechcloud.AsrService"
            )
        )

        val multiFailure = SpeechInputHelper.diagnoseAllEnginesFailed(
            lastErrorCode = SpeechRecognizer.ERROR_CLIENT,
            triedEngines = triedWithNoise,
            isXiaomiRom = true,
            llmEnabled = false
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, multiFailure.kind)
        assertTrue(multiFailure.title.contains("2"))
        assertTrue(multiFailure.message.contains("小爱语音引擎"))
        assertTrue(multiFailure.message.contains("com.xiaomi.mibrain.speech"))
        assertTrue(multiFailure.message.contains("讯飞语音+"))
        assertTrue(multiFailure.message.contains("com.iflytek.speechcloud"))
        assertFalse(multiFailure.message.contains("MacroDroid"))
        assertFalse(multiFailure.message.contains("com.arlosoft.macrodroid"))
        assertTrue(multiFailure.guideSteps.any { it.contains("改用键盘语音") })
        assertTrue(multiFailure.showVoiceSettingsAction)
        assertTrue(multiFailure.showEngineSettingsAction)

        val onlyExcludedTried = listOf(
            SpeechEngineInfo(
                packageName = "com.arlosoft.macrodroid",
                label = "MacroDroid",
                componentName = "com.arlosoft.macrodroid/com.arlosoft.macrodroid.voiceservice.RecognitionServiceTrampoline"
            ),
            SpeechEngineInfo(
                packageName = "com.facebook.aura",
                label = "Aura",
                componentName = "com.facebook.aura/com.facebook.aura.assist.HatchRecognitionService"
            )
        )
        val noValidEngineDiag = SpeechInputHelper.diagnoseAllEnginesFailed(
            lastErrorCode = SpeechRecognizer.ERROR_CLIENT,
            triedEngines = onlyExcludedTried,
            isXiaomiRom = true,
            llmEnabled = true
        )
        assertEquals(SpeechErrorKind.ENGINE_AUTHORIZATION_OR_SERVICE, noValidEngineDiag.kind)
        assertEquals("未找到可用的系统语音引擎", noValidEngineDiag.title)
        assertFalse(noValidEngineDiag.message.contains("MacroDroid"))
        assertFalse(noValidEngineDiag.message.contains("Aura"))
        assertTrue(noValidEngineDiag.guideSteps.any { it.contains("改用键盘语音") })
        assertTrue(noValidEngineDiag.showVoiceSettingsAction)

        val permissionDiag = SpeechInputHelper.diagnoseAllEnginesFailed(
            lastErrorCode = SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS,
            triedEngines = listOf(
                SpeechEngineInfo(
                    packageName = "com.xiaomi.mibrain.speech",
                    label = "小爱语音引擎",
                    componentName = "com.xiaomi.mibrain.speech/com.xiaomi.mibrain.speech.asr.AsrService"
                )
            ),
            isXiaomiRom = true,
            llmEnabled = true
        )
        assertEquals(SpeechErrorKind.PERMISSION_DENIED, permissionDiag.kind)
        assertTrue(permissionDiag.showAppPermissionAction)
        assertTrue(permissionDiag.showEngineSettingsAction)
    }
}
