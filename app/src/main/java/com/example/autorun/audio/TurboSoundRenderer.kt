package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.*
import kotlin.random.Random

/**
 * 【TurboSoundRenderer: ターボ音（過給音・バッグタービン）合成】
 * 吸気側のタービン回転音と、アクセルオフ時のバックタービン音（コンプレッサーサージ）を生成します。
 */
class TurboSoundRenderer {

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
    @Volatile private var targetBoost = 0f // 0.0 to 1.0 (相対的なブースト圧)

    private var renderedRPM = 800f
    private var renderedThrottle = 0f
    private var currentBoost = 0f
    
    private var turbinePhase = 0f
    
    // バックタービン（サージ）音用
    private var surgeActive = false
    private var surgePhase = 0f
    private var surgeLevel = 0f
    private var surgeTimer = 0f

    private val TWO_PI_F = 2f * PI.toFloat()

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
            val samples = ShortArray(bufferSize / 2)
            while (isRunning) {
                generateSamples(samples)
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply { name = "TurboRenderThread"; priority = Thread.MAX_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false; thread?.join(); audioTrack?.stop(); audioTrack?.release(); audioTrack = null
    }

    fun update(rpm: Float, throttle: Float, boost: Float) {
        // アクセルオフ時にバックタービン発動判定
        // 条件：高ブースト状態から急激にアクセルを戻した時
        if (targetThrottle > 0.4f && throttle < 0.15f && currentBoost > 0.3f) {
            triggerSurge(currentBoost)
        }
        this.targetRPM = rpm
        this.targetThrottle = throttle
        this.targetBoost = boost
    }

    private fun triggerSurge(level: Float) {
        surgeActive = true
        surgeTimer = 0f
        surgePhase = 0f
        surgeLevel = level
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isTurboSoundEnabled) {
            samples.fill(0)
            return
        }
        val dt = 1f / sampleRate
        val rpmStep = (targetRPM - renderedRPM) / samples.size
        val throttleStep = (targetThrottle - renderedThrottle) / samples.size
        val boostStep = (targetBoost - currentBoost) / samples.size

        for (i in samples.indices) {
            renderedRPM += rpmStep
            renderedThrottle += throttleStep
            currentBoost += boostStep

            // 1. タービン回転音 (キーンという高周波)
            val turbineFreq = 1800f + (currentBoost * 4500f) + (renderedRPM * 0.4f)
            val turbineSignal = sin(TWO_PI_F * turbineFreq * turbinePhase) * (currentBoost * 0.12f)
            
            // 2. 吸気ノイズ (シューー)
            val intakeNoise = (Random.nextFloat() * 2f - 1f) * (currentBoost * 0.08f)

            // 3. バックタービン音 (シュルシュルシュル...！)
            var surgeSignal = 0f
            if (surgeActive) {
                // サージ（バックタービン）特有の断続的な「パタパタ」音をシミュレート
                // 周期的な振幅変調(LFO)をかける
                val flutterFreq = 12f + (1.0f - surgeTimer) * 8f // 時間とともに少しずつ遅くなる
                val flutterEnv = (sin(TWO_PI_F * flutterFreq * surgeTimer) * 0.5f + 0.5f)
                
                // 指数関数的な全体減衰
                val totalEnv = exp(-3.5f * surgeTimer) * surgeLevel
                
                // ホワイトノイズと少しの低周波を混ぜる
                val noisePart = (Random.nextFloat() * 2f - 1f) * 0.7f
                val throbPart = sin(TWO_PI_F * 60f * surgeTimer) * 0.3f
                
                surgeSignal = (noisePart + throbPart) * flutterEnv * totalEnv * 1.2f
                
                surgeTimer += dt
                if (surgeTimer > 1.2f) { // 約1.2秒で終了
                    surgeActive = false
                }
            }

            var finalSignal = turbineSignal + intakeNoise + surgeSignal
            
            // マスターボリューム調整
            val masterVol = GameSettings.ENGINE_SOUND_VOLUME * 0.7f
            val pcmValue = (finalSignal * masterVol).coerceIn(-1f, 1f)
            
            samples[i] = (pcmValue * 32767f).toInt().toShort()
            
            turbinePhase = (turbinePhase + dt) % 1.0f
        }
    }
}
