package com.yubrae.app.capture

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * 零第三方依赖的应用内原生录音器。
 * 使用 AudioRecord (16kHz / Mono / PCM 16-bit) 后台采集，实时计算兼容 SpeechRecognizer 波形区间的 RMS dB，
 * 并封装标准 44 字节 RIFF/WAVE 文件头与 PCM 数据。
 */
class InAppAudioRecorder(
    private val maxDurationMillis: Long = 60_000L
) {
    private val stateLock = Any()
    private var activeSession: RecordingSession? = null

    private class RecordingSession(
        val record: AudioRecord,
        val maxDurationMillis: Long,
        val onRmsDb: (Float) -> Unit,
        val onElapsedMillis: (Long) -> Unit,
        val onAutoStopped: (ByteArray) -> Unit,
        val onError: (String) -> Unit
    ) {
        val recordLock = Any()
        val pcmOutput = ByteArrayOutputStream()
        val finalized = AtomicBoolean(false)

        @Volatile
        var stopRequested: Boolean = false

        @Volatile
        var cancelled: Boolean = false

        @Volatile
        var released: Boolean = false

        var thread: Thread? = null
    }

    @SuppressLint("MissingPermission")
    fun start(
        onRmsDb: (Float) -> Unit,
        onElapsedMillis: (Long) -> Unit,
        onAutoStopped: (ByteArray) -> Unit,
        onError: (String) -> Unit
    ): Boolean {
        // 幂等防护：启动新会话前先清理任何残留录音会话，防止麦克风资源泄漏
        cancel()

        val minBufferSize = try {
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        } catch (t: Throwable) {
            onError("初始化录音缓冲区失败：${t.message ?: "设备不支持 16kHz 单声道录音"}")
            return false
        }

        if (minBufferSize <= 0) {
            onError("当前设备不支持 16kHz PCM 录音（bufferSize=$minBufferSize）")
            return false
        }

        val bufferSizeBytes = maxOf(minBufferSize * 2, SAMPLE_RATE * 2 / 5)
        var audioRecord: AudioRecord? = null
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSizeBytes
            )
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                runCatching { audioRecord.release() }
                onError("麦克风初始化失败（AudioRecord 未就绪），请检查录音权限或设备占用")
                return false
            }
            audioRecord.startRecording()
            if (audioRecord.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                runCatching { audioRecord.stop() }
                runCatching { audioRecord.release() }
                onError("未能开启麦克风录音，请确认系统允许「余白」使用麦克风")
                return false
            }
        } catch (e: SecurityException) {
            runCatching { audioRecord?.release() }
            onError("缺少麦克风录音权限：${e.message ?: "请在系统设置中允许录音权限"}")
            return false
        } catch (e: IllegalStateException) {
            runCatching { audioRecord?.release() }
            onError("麦克风当前不可用或正被其他应用占用：${e.message ?: "请稍后重试"}")
            return false
        } catch (t: Throwable) {
            runCatching { audioRecord?.release() }
            onError("启动应用内录音失败：${t.message ?: t::class.java.simpleName}")
            return false
        }

        val session = RecordingSession(
            record = audioRecord,
            maxDurationMillis = maxDurationMillis,
            onRmsDb = onRmsDb,
            onElapsedMillis = onElapsedMillis,
            onAutoStopped = onAutoStopped,
            onError = onError
        )
        val readChunkShorts = minOf(maxOf(minBufferSize / 4, 640), 1600)
        val worker = Thread(
            { runCaptureLoop(session, readChunkShorts) },
            "yubrae-audio-recorder"
        ).apply {
            isDaemon = true
        }
        session.thread = worker

        synchronized(stateLock) {
            activeSession = session
        }

        return try {
            worker.start()
            true
        } catch (t: Throwable) {
            synchronized(stateLock) {
                if (activeSession === session) {
                    activeSession = null
                }
            }
            session.cancelled = true
            session.finalized.set(true)
            stopAndReleaseRecord(session)
            onError("启动录音线程失败：${t.message ?: t::class.java.simpleName}")
            false
        }
    }

    fun stopAndBuildWav(): ByteArray? {
        val session = synchronized(stateLock) {
            val current = activeSession
            activeSession = null
            current
        } ?: return null

        session.stopRequested = true
        stopRecordOnly(session)

        val worker = session.thread
        if (worker != null && Thread.currentThread() !== worker) {
            runCatching { worker.join(STOP_JOIN_TIMEOUT_MS) }
        }
        if (worker == null || !worker.isAlive) {
            stopAndReleaseRecord(session)
        }

        if (!session.finalized.compareAndSet(false, true)) {
            return null
        }
        if (session.cancelled) {
            return null
        }
        val pcmBytes = session.pcmOutput.toByteArray()
        if (pcmBytes.isEmpty()) {
            return null
        }
        return buildWavBytes(pcmBytes)
    }

    fun cancel() {
        val session = synchronized(stateLock) {
            val current = activeSession
            activeSession = null
            current
        } ?: return

        session.cancelled = true
        session.stopRequested = true
        session.finalized.set(true)
        stopRecordOnly(session)

        val worker = session.thread
        if (worker != null && Thread.currentThread() !== worker) {
            runCatching { worker.join(STOP_JOIN_TIMEOUT_MS) }
        }
        if (worker == null || !worker.isAlive) {
            stopAndReleaseRecord(session)
        }
    }

    private fun runCaptureLoop(session: RecordingSession, readChunkShorts: Int) {
        val shortBuffer = ShortArray(readChunkShorts)
        val scratchBytes = ByteArray(readChunkShorts * 2)
        val startNanos = System.nanoTime()
        var lastNotifyNanos = startNanos - CALLBACK_THROTTLE_NANOS
        var readErrorCode: Int? = null
        var autoStopped = false

        try {
            while (!session.stopRequested && !session.cancelled) {
                val readCount = session.record.read(shortBuffer, 0, shortBuffer.size)
                if (readCount > 0) {
                    writeLittleEndianPcm16(shortBuffer, readCount, scratchBytes, session.pcmOutput)

                    val nowNanos = System.nanoTime()
                    val elapsedMillis = ((nowNanos - startNanos) / 1_000_000L).coerceAtLeast(0L)

                    if (nowNanos - lastNotifyNanos >= CALLBACK_THROTTLE_NANOS) {
                        lastNotifyNanos = nowNanos
                        val rmsDb = computeRmsDb(shortBuffer, readCount)
                        if (!session.stopRequested && !session.cancelled) {
                            runCatching { session.onRmsDb(rmsDb) }
                            runCatching { session.onElapsedMillis(elapsedMillis) }
                        }
                    }

                    if (session.maxDurationMillis > 0L && elapsedMillis >= session.maxDurationMillis) {
                        autoStopped = true
                        break
                    }
                } else if (readCount < 0) {
                    if (!session.stopRequested && !session.cancelled) {
                        readErrorCode = readCount
                    }
                    break
                }
            }
        } catch (_: Throwable) {
            if (!session.stopRequested && !session.cancelled) {
                readErrorCode = -1
            }
        } finally {
            stopAndReleaseRecord(session)
        }

        if (session.cancelled) {
            return
        }

        if (readErrorCode != null && !session.stopRequested) {
            if (session.finalized.compareAndSet(false, true)) {
                clearActiveSessionIfMatches(session)
                runCatching {
                    session.onError("麦克风录音采集异常（错误码 $readErrorCode）")
                }
            }
            return
        }

        if (autoStopped && !session.stopRequested) {
            if (session.finalized.compareAndSet(false, true)) {
                clearActiveSessionIfMatches(session)
                val finalElapsed = ((System.nanoTime() - startNanos) / 1_000_000L)
                    .coerceAtLeast(session.maxDurationMillis.coerceAtLeast(0L))
                runCatching { session.onElapsedMillis(finalElapsed) }
                val pcmBytes = session.pcmOutput.toByteArray()
                if (!session.cancelled) {
                    val wavBytes = buildWavBytes(pcmBytes)
                    runCatching { session.onAutoStopped(wavBytes) }
                }
            }
        }
    }

    private fun clearActiveSessionIfMatches(session: RecordingSession) {
        synchronized(stateLock) {
            if (activeSession === session) {
                activeSession = null
            }
        }
    }

    private fun stopRecordOnly(session: RecordingSession) {
        synchronized(session.recordLock) {
            if (session.released) return
            runCatching {
                if (session.record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    session.record.stop()
                }
            }
        }
    }

    private fun stopAndReleaseRecord(session: RecordingSession) {
        synchronized(session.recordLock) {
            if (session.released) return
            session.released = true
            runCatching {
                if (session.record.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                    session.record.stop()
                }
            }
            runCatching {
                session.record.release()
            }
        }
    }

    private fun writeLittleEndianPcm16(
        samples: ShortArray,
        count: Int,
        scratchBytes: ByteArray,
        output: ByteArrayOutputStream
    ) {
        var outIdx = 0
        for (i in 0 until count) {
            val sample = samples[i].toInt()
            scratchBytes[outIdx++] = (sample and 0xFF).toByte()
            scratchBytes[outIdx++] = ((sample ushr 8) and 0xFF).toByte()
        }
        output.write(scratchBytes, 0, outIdx)
    }

    companion object {
        private const val SAMPLE_RATE = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val CALLBACK_THROTTLE_NANOS = 80_000_000L
        private const val STOP_JOIN_TIMEOUT_MS = 2_000L
        private const val MIN_RMS_DB = -2f
        private const val MAX_RMS_DB = 12f

        internal fun buildWavBytes(
            pcmData: ByteArray,
            sampleRate: Int = 16000,
            channels: Int = 1,
            bitsPerSample: Int = 16
        ): ByteArray {
            require(sampleRate > 0) { "sampleRate must be positive" }
            require(channels > 0) { "channels must be positive" }
            require(bitsPerSample > 0 && bitsPerSample % 8 == 0) {
                "bitsPerSample must be a positive multiple of 8"
            }

            val pcmSize = pcmData.size
            val bytesPerSample = bitsPerSample / 8
            val blockAlign = channels * bytesPerSample
            val byteRate = sampleRate * blockAlign
            val chunkSize = 36 + pcmSize

            val buffer = ByteBuffer.allocate(44 + pcmSize).order(ByteOrder.LITTLE_ENDIAN)
            buffer.put("RIFF".toByteArray(StandardCharsets.US_ASCII))
            buffer.putInt(chunkSize)
            buffer.put("WAVE".toByteArray(StandardCharsets.US_ASCII))
            buffer.put("fmt ".toByteArray(StandardCharsets.US_ASCII))
            buffer.putInt(16) // PCM fmt subchunk size
            buffer.putShort(1.toShort()) // AudioFormat = 1 (PCM)
            buffer.putShort(channels.toShort())
            buffer.putInt(sampleRate)
            buffer.putInt(byteRate)
            buffer.putShort(blockAlign.toShort())
            buffer.putShort(bitsPerSample.toShort())
            buffer.put("data".toByteArray(StandardCharsets.US_ASCII))
            buffer.putInt(pcmSize)
            buffer.put(pcmData)
            return buffer.array()
        }

        internal fun computeRmsDb(samples: ShortArray, count: Int): Float {
            val effectiveCount = minOf(count, samples.size)
            if (effectiveCount <= 0) return MIN_RMS_DB

            var sumSquares = 0.0
            for (i in 0 until effectiveCount) {
                val sample = samples[i].toDouble()
                sumSquares += sample * sample
            }
            val rms = sqrt(sumSquares / effectiveCount)
            if (rms <= 0.0) return MIN_RMS_DB

            // 将 16-bit PCM dBFS [-60, 0] 线性映射到与 SpeechRecognizer 波形动画兼容的 [-2f, 12f]
            val dbfs = 20.0 * log10(rms / 32768.0)
            val mapped = MIN_RMS_DB + (((dbfs + 60.0) / 60.0) * (MAX_RMS_DB - MIN_RMS_DB)).toFloat()
            return mapped.coerceIn(MIN_RMS_DB, MAX_RMS_DB)
        }
    }
}
