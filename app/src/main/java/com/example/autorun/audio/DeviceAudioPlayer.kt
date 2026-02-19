package com.example.autorun.audio

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.autorun.config.GameSettings

/**
 * プレイ端末本体に保存されているオーディオファイルを再生するためのクラス。
 * URIを指定して再生を制御します。
 */
class DeviceAudioPlayer(private val context: Context) {
    private var mediaPlayer: MediaPlayer? = null

    fun play(uri: Uri) {
        stop()
        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(context, uri)
                prepare()
                isLooping = true
                setVolume(GameSettings.MUSIC_VOLUME, GameSettings.MUSIC_VOLUME)
                start()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        mediaPlayer?.start()
    }

    fun setVolume(volume: Float) {
        mediaPlayer?.setVolume(volume, volume)
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
}
