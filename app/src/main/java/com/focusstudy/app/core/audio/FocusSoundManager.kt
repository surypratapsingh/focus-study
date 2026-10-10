package com.focusstudy.app.core.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.concurrent.thread

enum class FocusSoundType(val title: String, val emoji: String, val description: String) {
    OFF("Off", "🔇", "Silent focus"),
    WHITE_NOISE("White Noise", "📻", "Continuous static masking speech"),
    BROWN_NOISE("Brown Noise", "🌊", "Deep soothing rumble like heavy rain"),
    ALPHA_WAVES("10Hz Alpha", "🧘", "Binaural flow beat (200Hz + 210Hz)")
}

/**
 * Native Zero-Asset Ambient Focus Soundscape Generator (DEC-032).
 * Synthesizes White Noise, Brown Noise, and 10Hz Binaural Alpha Waves directly in PCM
 * via Android AudioTrack. Requires 0 external audio files, adds 0 MB to APK size, and runs 100% offline.
 */
object FocusSoundManager {

    private val _currentSound = MutableStateFlow(FocusSoundType.OFF)
    val currentSound: StateFlow<FocusSoundType> = _currentSound.asStateFlow()

    @Volatile
    private var isPlaying = false

    @Volatile
    private var soundThread: Thread? = null

    @Volatile
    private var currentType: FocusSoundType = FocusSoundType.OFF

    @Volatile
    var volume: Float = 0.4f

    fun play(type: FocusSoundType) {
        if (type == FocusSoundType.OFF) {
            stop()
            return
        }

        if (currentType == type && isPlaying) return

        stop()

        currentType = type
        _currentSound.value = type
        isPlaying = true

        soundThread = thread(isDaemon = true, name = "FocusSoundSynthesisThread") {
            try {
                runAudioLoop(type)
            } catch (t: Throwable) {
                // Graceful fallback if running in unit test JVM or unsupported audio device
                isPlaying = false
                _currentSound.value = FocusSoundType.OFF
            }
        }
    }

    fun stop() {
        isPlaying = false
        soundThread?.interrupt()
        soundThread = null
        currentType = FocusSoundType.OFF
        _currentSound.value = FocusSoundType.OFF
    }

    private fun runAudioLoop(type: FocusSoundType) {
        val sampleRate = 44100
        val bufferSize = try {
            AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)
        } catch (t: Throwable) {
            // JVM mock / test environment safety
            return
        }

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(sampleRate)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
            .build()

        val track = try {
            AudioTrack.Builder()
                .setAudioAttributes(audioAttributes)
                .setAudioFormat(audioFormat)
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
        } catch (t: Throwable) {
            return
        }

        try {
            track.play()
        } catch (t: Throwable) {
            track.release()
            return
        }

        val frameCount = 1024
        val buffer = ShortArray(frameCount * 2) // Stereo (L, R)
        var phaseL = 0.0
        var phaseR = 0.0
        var lastBrownL = 0f
        var lastBrownR = 0f

        val freqL = 200.0 // 200 Hz carrier
        val freqR = 210.0 // 210 Hz carrier (10 Hz binaural alpha beat difference)
        val twoPi = 2.0 * Math.PI

        try {
            while (isPlaying && !Thread.currentThread().isInterrupted) {
                val currentVol = volume

                when (type) {
                    FocusSoundType.WHITE_NOISE -> {
                        for (i in 0 until frameCount) {
                            val whiteL = ((Math.random() * 2.0 - 1.0) * Short.MAX_VALUE * currentVol * 0.4f).toInt().toShort()
                            val whiteR = ((Math.random() * 2.0 - 1.0) * Short.MAX_VALUE * currentVol * 0.4f).toInt().toShort()
                            buffer[i * 2] = whiteL
                            buffer[i * 2 + 1] = whiteR
                        }
                    }
                    FocusSoundType.BROWN_NOISE -> {
                        for (i in 0 until frameCount) {
                            val whiteL = (Math.random() * 2.0 - 1.0).toFloat()
                            val whiteR = (Math.random() * 2.0 - 1.0).toFloat()
                            lastBrownL = (lastBrownL + (0.02f * whiteL)) / 1.02f
                            lastBrownR = (lastBrownR + (0.02f * whiteR)) / 1.02f

                            val sampleL = (lastBrownL * 3.5f * Short.MAX_VALUE * currentVol)
                                .coerceIn(-Short.MAX_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                            val sampleR = (lastBrownR * 3.5f * Short.MAX_VALUE * currentVol)
                                .coerceIn(-Short.MAX_VALUE.toFloat(), Short.MAX_VALUE.toFloat()).toInt().toShort()
                            buffer[i * 2] = sampleL
                            buffer[i * 2 + 1] = sampleR
                        }
                    }
                    FocusSoundType.ALPHA_WAVES -> {
                        for (i in 0 until frameCount) {
                            val sampleL = (Math.sin(phaseL) * Short.MAX_VALUE * currentVol * 0.5f).toInt().toShort()
                            val sampleR = (Math.sin(phaseR) * Short.MAX_VALUE * currentVol * 0.5f).toInt().toShort()
                            buffer[i * 2] = sampleL
                            buffer[i * 2 + 1] = sampleR

                            phaseL += (twoPi * freqL / sampleRate)
                            if (phaseL > twoPi) phaseL -= twoPi
                            phaseR += (twoPi * freqR / sampleRate)
                            if (phaseR > twoPi) phaseR -= twoPi
                        }
                    }
                    FocusSoundType.OFF -> break
                }

                track.write(buffer, 0, buffer.size)
            }
        } finally {
            try {
                track.stop()
                track.release()
            } catch (ignored: Throwable) {}
        }
    }
}
