package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.*
import kotlin.random.Random

/**
 * 【ExhaustSoundRenderer: 排気音（マフラーサウンド）合成】
 * バブリング（デセレーション・ポップ）機能搭載版。
 */
class ExhaustSoundRenderer {

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    @Volatile private var targetRPM = 800f
    @Volatile private var targetThrottle = 0f

    private var renderedRPM = 800f
    private var renderedThrottle = 0f
    private var cyclePhase = 0f

    private var lpfPrev = 0f
    private var hpfPrevX = 0f
    private var hpfPrevY = 0f

    // バブリング制御用
    private var bubblingIntensity = 0f
    private var lastThrottle = 0f

    private val cylinderCount = 6
    private val firingOrder = floatArrayOf(0f, 4/6f, 2/6f, 5/6f, 1/6f, 3/6f)
    private val TWO_PI_F = 2f * PI.toFloat()

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
            val samples = ShortArray(bufferSize / 2)
            while (isRunning) {
                generateSamples(samples)
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply { name = "ExhaustRenderThread"; priority = Thread.MAX_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false; thread?.join(); audioTrack?.stop(); audioTrack?.release(); audioTrack = null
    }

    fun update(rpm: Float, throttle: Float) {
        // アクセルOFF時のバブリングトリガー判定
        // 条件を少し緩和：3000rpm以上で0.3以上のスロットルから急に戻した場合
        if (lastThrottle > 0.3f && throttle < 0.1f && rpm > 2500f) {
            // RPMが高いほど、また直前のスロットルが大きいほど激しくなる
            bubblingIntensity = (rpm / 7000f * lastThrottle).coerceIn(0.4f, 1.5f)
        }
        lastThrottle = throttle
        this.targetRPM = rpm; this.targetThrottle = throttle
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isExhaustSoundEnabled) {
            samples.fill(0)
            return
        }
        val dt = 1f / sampleRate
        val rpmStep = (targetRPM - renderedRPM) / samples.size
        val throttleStep = (targetThrottle - renderedThrottle) / samples.size

        for (i in samples.indices) {
            renderedRPM += rpmStep
            renderedThrottle += throttleStep

            val crankFreq = renderedRPM / 60f
            val cycleFreq = crankFreq * 0.5f

            var exhaustSignal = 0f
            for (cylIdx in 0 until cylinderCount) {
                var cylPhase = (cyclePhase + firingOrder[cylIdx])
                while (cylPhase >= 1f) cylPhase -= 1f
                
                val localCylPhase = (cylPhase * 6f) % 1f
                
                // 排気パルス
                val pulse = exp(-12f * localCylPhase) * sin(TWO_PI_F * localCylPhase)
                
                // 加速時の破裂音
                val powerPop = if (localCylPhase < 0.04f) {
                    (Random.nextFloat() * 2f - 1f) * renderedThrottle * 0.4f
                } else 0f

                exhaustSignal += pulse + powerPop
            }

            // --- バブリング音 (Deceleration Pop) ---
            var bubblingPop = 0f
            if (bubblingIntensity > 0.01f) {
                // 不規則にパルスを発生させる。RPMが高いほど頻度が増す。
                val triggerChance = 0.003f * bubblingIntensity * (renderedRPM / 2000f)
                if (Random.nextFloat() < triggerChance) {
                    // 鋭いパルス音。音の強さにもランダム性を持たせる。
                    val popScale = if (Random.nextFloat() > 0.8f) 2.0f else 1.0f 
                    bubblingPop = (Random.nextFloat() * 2f - 1f) * bubblingIntensity * popScale * 2.0f
                }
                // 時間経過で減衰。減衰速度も少しランダムに。
                bubblingIntensity -= dt * (1.2f + Random.nextFloat() * 0.5f)
            }

            exhaustSignal += bubblingPop

            // ローパスフィルタ
            val cutoff = 180f + (renderedRPM / 9000f) * 700f
            val alphaLPF = (TWO_PI_F * cutoff * dt) / (TWO_PI_F * cutoff * dt + 1f)
            lpfPrev += alphaLPF * (exhaustSignal - lpfPrev)
            var finalSignal = lpfPrev

            // ハイパスフィルタ
            val hpfCutoff = 40f
            val alphaHPF = 1f / (1f + TWO_PI_F * hpfCutoff * dt)
            val outHPF = alphaHPF * (hpfPrevY + finalSignal - hpfPrevX)
            hpfPrevX = finalSignal
            hpfPrevY = outHPF
            finalSignal = outHPF

            // 歪み (排圧サチュレーション)
            val distortion = 1.2f + renderedThrottle * 2.5f
            finalSignal = (finalSignal * distortion).coerceIn(-1.3f, 1.3f)

            val masterVol = (0.5f + (renderedThrottle * 0.5f)) * GameSettings.ENGINE_SOUND_VOLUME
            val pcmValue = (finalSignal * masterVol).coerceIn(-1f, 1f)
            
            samples[i] = (pcmValue * 32767f).toInt().toShort()
            
            cyclePhase = (cyclePhase + cycleFreq * dt) % 1f
        }
    }
}
