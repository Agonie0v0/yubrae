package com.yubrae.app.capture

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class InAppAudioRecorderTest {

    @Test
    fun buildWavBytes_writesValid44ByteRiffWaveHeaderAndLittleEndianPcm() {
        val pcm = byteArrayOf(0x01, 0x02, 0x03, 0x04, 0x7F, 0x00, 0x80.toByte(), 0xFF.toByte())
        val wav = InAppAudioRecorder.buildWavBytes(pcm)

        assertEquals(44 + pcm.size, wav.size)
        assertEquals("RIFF", String(wav, 0, 4, StandardCharsets.US_ASCII))
        assertEquals("WAVE", String(wav, 8, 4, StandardCharsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, StandardCharsets.US_ASCII))
        assertEquals("data", String(wav, 36, 4, StandardCharsets.US_ASCII))

        val header = ByteBuffer.wrap(wav, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36 + pcm.size, header.getInt(4))   // chunkSize
        assertEquals(16, header.getInt(16))             // fmt subchunk size
        assertEquals(1.toShort(), header.getShort(20))  // PCM format = 1
        assertEquals(1.toShort(), header.getShort(22))  // channels = 1
        assertEquals(16_000, header.getInt(24))         // sampleRate = 16000
        assertEquals(32_000, header.getInt(28))         // byteRate = 16000 * 1 * 2
        assertEquals(2.toShort(), header.getShort(32))  // blockAlign = 2
        assertEquals(16.toShort(), header.getShort(34)) // bitsPerSample = 16
        assertEquals(pcm.size, header.getInt(40))       // data subchunk size

        assertArrayEquals(pcm, wav.copyOfRange(44, wav.size))
    }

    @Test
    fun buildWavBytes_handlesEmptyPcmAndCustomParameters() {
        val emptyWav = InAppAudioRecorder.buildWavBytes(ByteArray(0))
        assertEquals(44, emptyWav.size)
        val emptyBuf = ByteBuffer.wrap(emptyWav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, emptyBuf.getInt(4))
        assertEquals(0, emptyBuf.getInt(40))

        val stereoSample = byteArrayOf(0x10, 0x00, 0x20, 0x00)
        val stereoWav = InAppAudioRecorder.buildWavBytes(
            pcmData = stereoSample,
            sampleRate = 44_100,
            channels = 2,
            bitsPerSample = 16
        )
        val stereoBuf = ByteBuffer.wrap(stereoWav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(48, stereoWav.size)
        assertEquals(2.toShort(), stereoBuf.getShort(22))
        assertEquals(44_100, stereoBuf.getInt(24))
        assertEquals(176_400, stereoBuf.getInt(28))
        assertEquals(4.toShort(), stereoBuf.getShort(32))
        assertEquals(16.toShort(), stereoBuf.getShort(34))
        assertEquals(stereoSample.size, stereoBuf.getInt(40))
    }

    @Test
    fun computeRmsDb_distinguishesEmptySilenceQuietAndSineWave() {
        val emptyDb = InAppAudioRecorder.computeRmsDb(ShortArray(0), 0)
        val zeroCountDb = InAppAudioRecorder.computeRmsDb(ShortArray(160) { 1000 }, 0)
        val silenceDb = InAppAudioRecorder.computeRmsDb(ShortArray(800), 800)

        assertEquals(-2f, emptyDb, 0.0001f)
        assertEquals(-2f, zeroCountDb, 0.0001f)
        assertEquals(-2f, silenceDb, 0.0001f)

        val quietSamples = ShortArray(800) { idx ->
            (300.0 * sin(2.0 * PI * 440.0 * idx / 16_000.0)).roundToInt().toShort()
        }
        val loudSineSamples = ShortArray(800) { idx ->
            (22_000.0 * sin(2.0 * PI * 1_000.0 * idx / 16_000.0)).roundToInt().toShort()
        }
        val fullScaleSamples = ShortArray(160) { Short.MAX_VALUE }

        val quietDb = InAppAudioRecorder.computeRmsDb(quietSamples, quietSamples.size)
        val loudDb = InAppAudioRecorder.computeRmsDb(loudSineSamples, loudSineSamples.size)
        val peakDb = InAppAudioRecorder.computeRmsDb(fullScaleSamples, fullScaleSamples.size)

        assertTrue("Quiet signal should be above floor (-2f): $quietDb", quietDb > silenceDb)
        assertTrue("Loud sine ($loudDb) should exceed quiet ($quietDb)", loudDb > quietDb + 4f)
        assertTrue("Loud sine should stay within [-2f, 12f]: $loudDb", loudDb in -2f..12f)
        assertEquals(12f, peakDb, 0.05f)
    }

    @Test
    fun stopAndBuildWavAndCancel_areIdempotentWhenNotStarted() {
        val recorder = InAppAudioRecorder(maxDurationMillis = 15_000L)
        assertNull(recorder.stopAndBuildWav())
        recorder.cancel()
        recorder.cancel()
        assertNull(recorder.stopAndBuildWav())
    }
}
