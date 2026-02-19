package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.*
import kotlin.random.Random

/**
 * 【EngineSoundRenderer: 高精度・低負荷版】
 * LUT(Lookup Table)による高速合成と順次点火ロジックを実装
 */
class EngineSoundRenderer {

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096) * 4 // バッファに余裕を持たせる

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    @Volatile private var targetRPM = 800f
    @Volatile private var targetThrottle = 0f
    @Volatile private var isTurboActive = false

    private var renderedRPM = 800f
    private var renderedThrottle = 0f
    private var cyclePhase = 0f
    private var ghostPhase = 0f

    private var lpfPrev = 0f
    private val ghostDelayBufferSize = 8192
    private val ghostDelayBuffer = FloatArray(ghostDelayBufferSize)
    private var ghostWriteIdx = 0

    private var currentJitter = 1.0f
    private var targetJitter = 1.0f
    private var jitterTimer = 0f

    // --- 高速化のためのルックアップテーブル ---
    private val pulseTableSize = 2048
    private val pulseTable = FloatArray(pulseTableSize)

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

    private val eqFilters = Array(10) { BiquadFilter() }
    
    // デバッグ画面 (HDUDebugOverlay) からアクセスされるため public に変更
    val eqFreqs = floatArrayOf(31.5f, 63f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val eqGains = floatArrayOf(0f, 3f, 2f, 0f, -2f, -4f, -6f, -3f, 0f, 0f)

    private val visualBuffer = FloatArray(1024)
    private var visualIndex = 0

    private val TWO_PI_F = 2f * PI.toFloat()
    private val cylinderCount = 6
    // 点火順序 (I6: 1-5-3-6-2-4 等を模した位相オフセット)
    private val firingOrder = floatArrayOf(0f, 0.66f, 0.33f, 0.83f, 0.16f, 0.5f)

    init {
        // パルス形状の事前計算 (爆発音のカーブ)
        for (i in 0 until pulseTableSize) {
            val x = i.toFloat() / pulseTableSize
            // 爆発の衝撃波と残留振動をシミュレート
            var v = exp(-25f * x) * sin(TWO_PI_F * 2.2f * x)
            v += exp(-50f * x) * sin(TWO_PI_F * 8.5f * x) * 0.2f
            pulseTable[i] = v
        }
        updateFilters()
    }

    fun updateFilters() {
        for (i in 0 until 10) {
            eqFilters[i].setPeaking(eqFreqs[i], sampleRate.toFloat(), eqGains[i], 1.2f)
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sampleRate).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
        thread = Thread {
            val samples = ShortArray(1024) // 安定した書き込み単位
            while (isRunning) {
                generateSamples(samples)
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply { name = "AudioRenderThread"; priority = Thread.MAX_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false; try { thread?.join(500) } catch (e: Exception) {}
        audioTrack?.stop(); audioTrack?.release(); audioTrack = null
    }

    fun update(rpm: Float, throttle: Float, turbo: Boolean) {
        this.targetRPM = rpm; this.targetThrottle = throttle; this.isTurboActive = turbo
    }

    fun getVisualBuffer(): FloatArray {
        synchronized(visualBuffer) {
            val result = FloatArray(visualBuffer.size)
            val startIdx = visualIndex
            for (i in 0 until visualBuffer.size) {
                result[i] = visualBuffer[(startIdx + i) % visualBuffer.size]
            }
            return result
        }
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isEngineSoundEnabled) {
            samples.fill(0); return
        }
        val outDt = 1f / sampleRate
        val oversampling = 4 // 高回転時のエイリアシング抑制
        val internalDt = outDt / oversampling
        
        val rpmStep = (targetRPM - renderedRPM) / samples.size
        val throttleStep = (targetThrottle - renderedThrottle) / samples.size

        for (i in samples.indices) {
            renderedRPM += rpmStep
            renderedThrottle += throttleStep
            
            // ジッターの平滑化
            if (DeveloperSettings.isEngineJitterEnabled) {
                jitterTimer -= outDt
                if (jitterTimer <= 0) {
                    targetJitter = 1.0f + (Random.nextFloat() - 0.5f) * 0.015f
                    jitterTimer = 0.03f + Random.nextFloat() * 0.05f
                }
                currentJitter += (targetJitter - currentJitter) * 0.1f 
            } else {
                currentJitter = 1.0f
            }

            var accumSignal = 0f
            val mainRPM = renderedRPM * currentJitter
            val mainCycleFreq = (mainRPM / 60f) * 0.5f // 4ストロークなので 1/2 Hz

            for (j in 0 until oversampling) {
                // メイン
                val mainSig = renderSequentialCylinders(cyclePhase)
                
                // ゴースト (負荷が高いため設定時のみ)
                var ghostSig = 0f
                if (DeveloperSettings.isEngineGhostEnabled) {
                    ghostSig = renderSequentialCylinders(ghostPhase)
                    ghostDelayBuffer[ghostWriteIdx] = ghostSig
                    val readIdx = (ghostWriteIdx - 1200 + ghostDelayBufferSize) % ghostDelayBufferSize
                    ghostSig = ghostDelayBuffer[readIdx] * 0.15f
                    ghostWriteIdx = (ghostWriteIdx + 1) % ghostDelayBufferSize
                    ghostPhase = (ghostPhase + (mainCycleFreq * 1.01f) * internalDt) % 1f
                }

                val noise = if (renderedThrottle > 0.1f) (Random.nextFloat() * 2f - 1f) * 0.02f * renderedThrottle else 0f
                accumSignal += (mainSig * 0.5f) + ghostSig + noise
                cyclePhase = (cyclePhase + mainCycleFreq * internalDt) % 1f
            }

            var finalSignal = accumSignal / oversampling

            // LPF
            val cutoff = 1500f + (renderedRPM - 800f) * 0.8f
            val alpha = (TWO_PI_F * cutoff * outDt) / (TWO_PI_F * cutoff * outDt + 1f)
            lpfPrev += alpha * (finalSignal - lpfPrev)
            finalSignal = lpfPrev

            // EQ
            for (filter in eqFilters) {
                finalSignal = filter.process(finalSignal)
            }

            // Master
            val masterVol = (0.7f + (renderedThrottle * 0.3f)) * GameSettings.ENGINE_SOUND_VOLUME
            val pcmValue = (finalSignal * masterVol * (1.1f + renderedThrottle * 0.4f)).coerceIn(-1f, 1f)
            
            if (i % 16 == 0) {
                synchronized(visualBuffer) {
                    visualBuffer[visualIndex] = pcmValue
                    visualIndex = (visualIndex + 1) % visualBuffer.size
                }
            }
            samples[i] = (pcmValue * 32767f).toInt().toShort()
        }
    }

    /**
     * 各シリンダーを適切なタイミングで発火させる
     */
    private fun renderSequentialCylinders(phase: Float): Float {
        var sig = 0f
        for (cylIdx in 0 until cylinderCount) {
            var cylPhase = (phase - firingOrder[cylIdx])
            if (cylPhase < 0f) cylPhase += 1f
            
            // シリンダー1周(0..1)のうち、最初の 1/cylinderCount 程度で減衰するようにLUTを参照
            // ここでは簡易的に全域を使うが、LUT側で減衰させている
            val tableIdx = (cylPhase * (pulseTableSize - 1)).toInt()
            sig += pulseTable[tableIdx]
        }
        return sig
    }
}
