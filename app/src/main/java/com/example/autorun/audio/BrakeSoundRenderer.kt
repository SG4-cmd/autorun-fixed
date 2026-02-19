package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.*
import kotlin.random.Random

/**
 * 【BrakeSoundRenderer: ブレーキ摩擦音の合成】
 * 速度に応じたピッチ変動と、スベリ感をノイズフィルタでシミュレート。
 */
class BrakeSoundRenderer {

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    @Volatile private var targetIsBraking = false
    @Volatile private var targetSpeedKmh = 0f

    private var renderedVolume = 0f
    private var renderedSpeedKmh = 0f
    
    private val noise = Random(System.currentTimeMillis())
    private var filterY1 = 0f

    fun start() {
        if (isRunning) return
        isRunning = true
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        thread = Thread {
            val samples = ShortArray(1024)
            while (isRunning) {
                generateSamples(samples)
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply { name = "BrakeAudioThread"; priority = Thread.NORM_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        try { thread?.join(500) } catch (e: Exception) {}
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun update(isBraking: Boolean, speedKmh: Float) {
        this.targetIsBraking = isBraking
        this.targetSpeedKmh = speedKmh
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isBrakeSoundEnabled) {
            samples.fill(0)
            return
        }

        val volTarget = if (targetIsBraking && targetSpeedKmh > 1f) 
            (targetSpeedKmh / 150f).coerceIn(0.1f, 0.4f) else 0f
        val volStep = (volTarget - renderedVolume) / samples.size
        val speedStep = (targetSpeedKmh - renderedSpeedKmh) / samples.size

        for (i in samples.indices) {
            renderedVolume += volStep
            renderedSpeedKmh += speedStep

            if (renderedVolume < 0.001f) {
                samples[i] = 0
                continue
            }

            val raw = noise.nextFloat() * 2f - 1f

            val cutoff = 2000f + (renderedSpeedKmh * 30f).coerceAtMost(6000f)
            val dt = 1f / sampleRate
            val rc = 1f / (2f * PI.toFloat() * cutoff)
            val alpha = dt / (rc + dt)
            
            val filtered = alpha * raw + (1f - alpha) * filterY1
            filterY1 = filtered

            val pcmValue = (filtered * renderedVolume * GameSettings.ENGINE_SOUND_VOLUME).coerceIn(-1f, 1f)
            samples[i] = (pcmValue * 32767f).toInt().toShort()
        }
    }
}
