package com.example.autorun.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import com.example.autorun.config.DeveloperSettings
import com.example.autorun.config.GameSettings
import kotlin.math.PI
import kotlin.math.pow
import kotlin.random.Random

class WindSoundRenderer {

    private val sampleRate = 44100
    private val bufferSize = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    ).coerceAtLeast(4096)

    private var audioTrack: AudioTrack? = null
    private var isRunning = false
    private var thread: Thread? = null

    @Volatile private var targetSpeedKmh = 0f

    private var renderedSpeedKmh = 0f
    private var lpfPrev = 0f
    private val noise = Random(System.currentTimeMillis())

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
            val samples = ShortArray(1024)
            while (isRunning) {
                generateSamples(samples)
                audioTrack?.write(samples, 0, samples.size)
            }
        }.apply { name = "WindAudioThread"; priority = Thread.NORM_PRIORITY }
        thread?.start()
    }

    fun stop() {
        isRunning = false
        try { thread?.join(500) } catch (e: Exception) {}
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }

    fun update(speedKmh: Float) {
        this.targetSpeedKmh = speedKmh
    }

    private fun generateSamples(samples: ShortArray) {
        if (!DeveloperSettings.isWindSoundEnabled) {
            samples.fill(0)
            return
        }

        val speedStep = (targetSpeedKmh - renderedSpeedKmh) / samples.size

        for (i in samples.indices) {
            renderedSpeedKmh += speedStep

            val speedRatio = (renderedSpeedKmh / 300f).coerceIn(0f, 1f)

            // Generate white noise
            val rawNoise = noise.nextFloat() * 2f - 1f

            // Simple low-pass filter
            val cutoff = 400f + 5000f * speedRatio.pow(2)
            val dt = 1f / sampleRate
            val alpha = (TWO_PI_F * dt * cutoff) / (TWO_PI_F * dt * cutoff + 1f)
            lpfPrev += alpha * (rawNoise - lpfPrev)
            val filteredNoise = lpfPrev

            // Volume based on speed
            val volume = speedRatio.pow(1.5f) * 0.7f * GameSettings.WIND_SOUND_VOLUME
            val pcmValue = (filteredNoise * volume).coerceIn(-1f, 1f)

            samples[i] = (pcmValue * 32767f).toInt().toShort()
        }
    }
}
