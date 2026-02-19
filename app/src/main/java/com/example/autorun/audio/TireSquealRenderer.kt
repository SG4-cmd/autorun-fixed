package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.*
import kotlin.random.Random

/**
 * 【TireSquealRenderer: タイヤ鳴り（スキール音）の合成】
 * 横滑り量（SlipRatio）およびブレーキ状態に応じて摩擦音をリアルタイム合成。
 */
class TireSquealRenderer {

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    @Volatile private var targetSlipRatio = 0f
    @Volatile private var targetSpeedKmh = 0f
    @Volatile private var targetIsBraking = false

    private var renderedVolume = 0f

    private val squealFilter = BiquadFilter()
    private val noise = Random(System.currentTimeMillis())

    private class BiquadFilter {
        var b0=0f; var b1=0f; var b2=0f; var a1=0f; var a2=0f
        var x1=0f; var x2=0f; var y1=0f; var y2=0f
        fun process(v: Float): Float {
            val y = b0*v + b1*x1 + b2*x2 - a1*y1 - a2*y2
            x2=x1; x1=v; y2=y1; y1=y
            return y
        }
        fun setPeaking(freq: Float, sampleRate: Float, gainDb: Float, q: Float) {
            val a = 10f.pow(gainDb / 40f)
            val w0 = 2f * PI.toFloat() * freq / sampleRate
            val alpha = sin(w0) / (2f * q)
            val a0 = 1f + alpha / a
            b0 = (1f + alpha * a) / a0; b1 = (-2f * cos(w0)) / a0; b2 = (1f - alpha * a) / a0
            a1 = (-2f * cos(w0)) / a0; a2 = (1f - alpha / a) / a0
        }
    }

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
        }.apply { name = "TireSquealThread"; priority = Thread.NORM_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        try { thread?.join(500) } catch (e: Exception) {}
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun update(slipRatio: Float, speedKmh: Float, isBraking: Boolean) {
        this.targetSlipRatio = slipRatio
        this.targetSpeedKmh = speedKmh
        this.targetIsBraking = isBraking
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isTireSquealEnabled) {
            samples.fill(0)
            return
        }

        // スリップ量またはブレーキ状態に基づいて音量を決定
        val brakeEffect = if (targetIsBraking && targetSpeedKmh > 30f) 0.3f else 0f
        val volTarget = if (targetSpeedKmh > 10f) {
            (max(targetSlipRatio, brakeEffect) * 0.7f).coerceIn(0f, 0.7f)
        } else 0f
        
        val volStep = (volTarget - renderedVolume) / samples.size

        // 周波数はスリップ量とブレーキの両方を考慮
        val effectiveRatio = max(targetSlipRatio, brakeEffect)
        val freq = (1800f + effectiveRatio * 1500f).coerceIn(1000f, 4000f)
        val q = (2.0f + effectiveRatio * 10f).coerceIn(1.0f, 12.0f)
        squealFilter.setPeaking(freq, sampleRate.toFloat(), 30f, q)

        for (i in samples.indices) {
            renderedVolume += volStep

            if (renderedVolume < 0.001f) {
                samples[i] = 0
                continue
            }
            
            val whiteNoise = noise.nextFloat() * 2f - 1f
            val signal = squealFilter.process(whiteNoise)
            val distortedSignal = tanh(signal * 1.5f)
            
            val pcmValue = (distortedSignal * renderedVolume * GameSettings.ENGINE_SOUND_VOLUME * 0.5f).coerceIn(-1f, 1f)
            samples[i] = (pcmValue * 32767f).toInt().toShort()
        }
    }
}
