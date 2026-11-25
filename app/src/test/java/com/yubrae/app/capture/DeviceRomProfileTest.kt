package com.yubrae.app.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRomProfileTest {

    @Test
    fun detect_identifiesHyperOsAndMiui() {
        val hyper = DeviceRomProfile.detect(
            manufacturer = "Xiaomi",
            brand = "Xiaomi",
            model = "23127PN0CC",
            androidRelease = "15"
        ) { key ->
            when (key) {
                "ro.mi.os.version.name" -> "OS2.0.10.0"
                "ro.miui.ui.version.name" -> "V816"
                else -> ""
            }
        }
        assertEquals(RomFamily.HYPER_OS, hyper.family)
        assertTrue(hyper.isXiaomiFamily)
        assertEquals("HyperOS OS2.0.10.0 (V816) · Android 15", hyper.osDisplay)
        assertTrue(hyper.guideSteps.isNotEmpty())

        val miui = DeviceRomProfile.detect(
            manufacturer = "Xiaomi",
            brand = "Redmi",
            model = "Redmi K60",
            androidRelease = "13"
        ) { key ->
            if (key == "ro.miui.ui.version.name") "V140" else ""
        }
        assertEquals(RomFamily.MIUI, miui.family)
        assertTrue(miui.isXiaomiFamily)
        assertTrue(miui.osDisplay.startsWith("MIUI V140"))
    }

    @Test
    fun detect_identifiesMajorNonXiaomiRomsAccurately() {
        val harmony = DeviceRomProfile.detect("HUAWEI", "HUAWEI", "Mate 60 Pro", "12") {
            if (it == "hw_sc.build.platform.version") "4.2.0" else ""
        }
        assertEquals(RomFamily.HARMONY_OS, harmony.family)
        assertFalse(harmony.isXiaomiFamily)
        assertEquals("HarmonyOS 4.2.0 · Android 12", harmony.osDisplay)

        val magic = DeviceRomProfile.detect("HONOR", "HONOR", "Magic6", "14") {
            if (it == "ro.build.version.magic") "8.0" else ""
        }
        assertEquals(RomFamily.MAGIC_OS, magic.family)
        assertFalse(magic.isXiaomiFamily)
        assertTrue(magic.osDisplay.contains("MagicOS 8.0"))

        val colorOs = DeviceRomProfile.detect("OPPO", "OPPO", "Find X7", "14") {
            if (it == "ro.build.version.oplusrom") "V14.0.0" else ""
        }
        assertEquals(RomFamily.COLOR_OS, colorOs.family)

        val oxygenOs = DeviceRomProfile.detect("OnePlus", "OnePlus", "CPH2581", "14") {
            if (it == "ro.oxygen.version") "14.0.0" else ""
        }
        assertEquals(RomFamily.OXYGEN_OS, oxygenOs.family)

        val realmeUi = DeviceRomProfile.detect("realme", "realme", "RMX3800", "14") {
            if (it == "ro.build.version.realmeui") "V5.0" else ""
        }
        assertEquals(RomFamily.REALME_UI, realmeUi.family)

        val originOs = DeviceRomProfile.detect("vivo", "vivo", "X100", "14") {
            if (it == "ro.vivo.os.build.display.id") "OriginOS 4" else ""
        }
        assertEquals(RomFamily.ORIGIN_OS, originOs.family)

        val oneUi = DeviceRomProfile.detect("samsung", "samsung", "SM-S9280", "15") {
            if (it == "ro.build.version.oneui") "70000" else ""
        }
        assertEquals(RomFamily.ONE_UI, oneUi.family)
        assertEquals("One UI 7.0 · Android 15", oneUi.osDisplay)

        val flyme = DeviceRomProfile.detect("meizu", "meizu", "Meizu 21", "14") {
            if (it == "ro.build.display.id") "Flyme 10.5.0.0A" else ""
        }
        assertEquals(RomFamily.FLYME, flyme.family)

        val pixel = DeviceRomProfile.detect("Google", "google", "Pixel 9 Pro", "15") { "" }
        assertEquals(RomFamily.AOSP, pixel.family)
        assertFalse(pixel.isXiaomiFamily)
        assertEquals("Pixel 9 Pro · Android 15", pixel.osDisplay)
    }

    @Test
    fun detect_doesNotMisclassifyXiaomiHardwareRunningAospRom() {
        val xiaomiLineage = DeviceRomProfile.detect("Xiaomi", "Xiaomi", "23127PN0CC", "15") { "" }
        assertEquals(RomFamily.AOSP, xiaomiLineage.family)
        assertFalse(xiaomiLineage.isXiaomiFamily)
        assertEquals("23127PN0CC · Android 15", xiaomiLineage.osDisplay)

        val redmiPixelOs = DeviceRomProfile.detect("Xiaomi", "Redmi", "Redmi K60", "14") { "" }
        assertEquals(RomFamily.AOSP, redmiPixelOs.family)
        assertFalse(redmiPixelOs.isXiaomiFamily)

        val pocoCrDroid = DeviceRomProfile.detect("Xiaomi", "POCO", "POCO F5", "14") { "" }
        assertEquals(RomFamily.AOSP, pocoCrDroid.family)
        assertFalse(pocoCrDroid.isXiaomiFamily)

        // 仅凭 ro.miui.ui.version.code 也可识别 MIUI，且不依赖硬件名兜底
        val miuiByCode = DeviceRomProfile.detect("Xiaomi", "Xiaomi", "Mi 10", "12") { key ->
            if (key == "ro.miui.ui.version.code") "13" else ""
        }
        assertEquals(RomFamily.MIUI, miuiByCode.family)
        assertTrue(miuiByCode.isXiaomiFamily)
        assertEquals("MIUI · Android 12", miuiByCode.osDisplay)
    }

    @Test
    fun detect_formatsEmuiPrefixAndDistinguishesFuntouchOs12PlusFromOriginOs() {
        val emui = DeviceRomProfile.detect("HUAWEI", "HUAWEI", "P40 Pro", "10") { key ->
            if (key == "ro.build.version.emui") "EmotionUI_12.0.0" else ""
        }
        assertEquals(RomFamily.EMUI, emui.family)
        assertFalse(emui.isXiaomiFamily)
        assertEquals("EMUI 12.0.0 · Android 10", emui.osDisplay)

        val funtouch14 = DeviceRomProfile.detect("vivo", "vivo", "V2309", "14") { key ->
            when (key) {
                "ro.vivo.os.build.display.id" -> "Funtouch OS 14"
                "ro.vivo.os.version" -> "14.0"
                else -> ""
            }
        }
        assertEquals(RomFamily.FUNTOUCH_OS, funtouch14.family)
        assertFalse(funtouch14.isXiaomiFamily)
        assertEquals("Funtouch OS 14 · Android 14", funtouch14.osDisplay)

        val originFallback = DeviceRomProfile.detect("vivo", "vivo", "X100", "14") { key ->
            if (key == "ro.vivo.os.version") "14.0" else ""
        }
        assertEquals(RomFamily.ORIGIN_OS, originFallback.family)
        assertEquals("OriginOS 14.0 · Android 14", originFallback.osDisplay)
    }

    @Test
    fun allRomFamiliesProvideNonEmptyGuides() {
        RomFamily.entries.forEach { family ->
            val (title, steps) = DeviceRomProfile.guideForFamily(family)
            assertTrue("Title empty for $family", title.isNotBlank())
            assertTrue("Steps empty for $family", steps.size >= 3)
        }
    }
}
