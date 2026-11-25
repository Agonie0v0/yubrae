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
}
