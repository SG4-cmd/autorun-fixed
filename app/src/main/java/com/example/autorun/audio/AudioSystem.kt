package com.example.autorun.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.example.autorun.config.GameSettings

/**
 * システム全体のオーディオ再生の基盤。
 * 効果音(SE)の管理や、オーディオフォーカス、基本設定などを担当。
 */
object AudioSystem {
    private var soundPool: SoundPool? = null
    private val soundMap = mutableMapOf<Int, Int>()

    fun init(context: Context) {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        soundPool = SoundPool.Builder()
            .setMaxStreams(10)
            .setAudioAttributes(attrs)
            .build()
    }

    fun loadSound(context: Context, resId: Int): Int {
        val soundId = soundPool?.load(context, resId, 1) ?: 0
        soundMap[resId] = soundId
        return soundId
    }

    fun playSound(resId: Int, volume: Float = 1.0f) {
        val soundId = soundMap[resId] ?: return
        soundPool?.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    fun release() {
        soundPool?.release()
        soundPool = null
        soundMap.clear()
    }
}
