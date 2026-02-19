package com.example.autorun.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.LoudnessEnhancer
import android.net.Uri
import android.provider.MediaStore
import com.example.autorun.config.GameSettings
import java.util.Calendar

/**
 * 音楽・ラジオ再生プレイヤー
 */
class MusicPlayer(private val context: Context) {

    private sealed class PlayItem {
        data class Resource(val resId: Int, val name: String) : PlayItem()
        data class DeviceFile(val uri: Uri, val name: String) : PlayItem()
        data class Stream(val url: String, val name: String) : PlayItem()
    }

    private var mediaPlayer: MediaPlayer? = null
    private var reverb: EnvironmentalReverb? = null
    private val playlist = mutableListOf<PlayItem>()
    private var currentTrackIndex = 0
    private var isPlaylistLoaded = false
    private var isRadioMode = false
    var isPlaying = false
        private set

    // -0.1dB相当のリニアゲイン係数 (10^(-0.1/20) ≒ 0.9885)
    private val normalizedVolumeFactor = 0.9885f

    fun getCurrentTrackName(): String {
        if (isRadioMode) return "LIVE STREAMING"
        if (!isPlaylistLoaded) return "Press Play to Load"
        if (playlist.isEmpty()) return "No Track"
        return when (val item = playlist[currentTrackIndex]) {
            is PlayItem.Resource -> item.name
            is PlayItem.DeviceFile -> item.name
            is PlayItem.Stream -> item.name
        }
    }

    fun refreshPlaylist() {
        playlist.clear()
        loadResourceBgms()
        scanDeviceAudioFiles()
        isPlaylistLoaded = true
    }

    private fun loadResourceBgms() {
        for (i in 2..10) {
            val name = "bgm$i"
            val resId = context.resources.getIdentifier(name, "raw", context.packageName)
            if (resId != 0) playlist.add(PlayItem.Resource(resId, name.uppercase()))
        }
    }

    private fun scanDeviceAudioFiles() {
        try {
            val projection = arrayOf(MediaStore.Audio.Media._ID, MediaStore.Audio.Media.DISPLAY_NAME)
            val query = context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, "${MediaStore.Audio.Media.DURATION} >= 30000", null, "${MediaStore.Audio.Media.DISPLAY_NAME} ASC")
            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    playlist.add(PlayItem.DeviceFile(android.content.ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cursor.getLong(idColumn)), cursor.getString(nameColumn) ?: "Unknown"))
                }
            }
        } catch (e: SecurityException) {}
    }

    fun playStream(url: String, band: String? = null) {
        stop()
        isRadioMode = true
        try {
            val mp = MediaPlayer()
            mp.setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            mp.setDataSource(url)
            mp.setOnPreparedListener { 
                applyInCarEffect(it.audioSessionId)
                // FMラジオ以外は正規化と音量微調整
                if (band != "FM") {
                    applyNormalizer(it.audioSessionId)
                    val vol = GameSettings.MUSIC_VOLUME * normalizedVolumeFactor
                    it.setVolume(vol, vol)
                } else {
                    it.setVolume(GameSettings.MUSIC_VOLUME, GameSettings.MUSIC_VOLUME)
                }
                it.start()
                isPlaying = true
            }
            mp.setOnErrorListener { _, _, _ -> 
                stop()
                false 
            }
            mp.prepareAsync()
            mediaPlayer = mp
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun playRawResource(resId: Int, band: String? = null) {
        stop()
        isRadioMode = true
        try {
            val mp = MediaPlayer()
            val afd = context.resources.openRawResourceFd(resId)
            mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            afd.close()
            mp.prepare()

            applyInCarEffect(mp.audioSessionId)

            // FMラジオ以外は0dB正規化 & 音量調整 (-0.1dB)
            if (band != "FM") {
                applyNormalizer(mp.audioSessionId)
                val vol = GameSettings.MUSIC_VOLUME * normalizedVolumeFactor
                mp.setVolume(vol, vol)
            } else {
                mp.setVolume(GameSettings.MUSIC_VOLUME, GameSettings.MUSIC_VOLUME)
            }

            // PMラジオ: 0:00からの経過時間で同期再生位置を計算
            if (band == "PM") {
                val duration = mp.duration
                if (duration > 0) {
                    val calendar = Calendar.getInstance()
                    val secondsOfDay = calendar.get(Calendar.HOUR_OF_DAY) * 3600 +
                                       calendar.get(Calendar.MINUTE) * 60 +
                                       calendar.get(Calendar.SECOND)
                    val seekTo = (secondsOfDay * 1000L) % duration
                    mp.seekTo(seekTo.toInt())
                }
            }
            
            mp.isLooping = true
            mp.start()
            mediaPlayer = mp
            isPlaying = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun start() {
        if (isRadioMode) return
        if (!isPlaylistLoaded) refreshPlaylist()
        if (playlist.isEmpty()) return
        stop() 
        try {
            val item = playlist[currentTrackIndex]
            val mp = MediaPlayer()
            when (item) {
                is PlayItem.Resource -> { val afd = context.resources.openRawResourceFd(item.resId); mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length); afd.close() }
                is PlayItem.DeviceFile -> mp.setDataSource(context, item.uri)
                else -> {}
            }
            mp.prepare()

            applyInCarEffect(mp.audioSessionId)

            // BGM再生時は常に正規化 & 音量微調整
            applyNormalizer(mp.audioSessionId)
            val vol = GameSettings.MUSIC_VOLUME * normalizedVolumeFactor
            mp.setVolume(vol, vol)

            mp.setOnCompletionListener { if (!isRadioMode) skipToNext() }
            mp.start()
            mediaPlayer = mp
            isPlaying = true
        } catch (e: Exception) { e.printStackTrace() }
    }

    private fun applyNormalizer(audioSessionId: Int) {
        try {
            val enhancer = LoudnessEnhancer(audioSessionId)
            enhancer.setTargetGain(0)
            enhancer.enabled = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 車内のような音響効果（リバーブ）を適用
     */
    private fun applyInCarEffect(audioSessionId: Int) {
        try {
            reverb?.release()
            reverb = EnvironmentalReverb(0, audioSessionId).apply {
                // 車内（狭い空間）をシミュレート
                roomLevel = -1000
                roomHFLevel = -100
                decayTime = 500
                decayHFRatio = 500
                reverbLevel = -1000
                reverbDelay = 30
                diffusion = 1000
                density = 1000
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stop() {
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null
        reverb?.release()
        reverb = null
        isPlaying = false
        isRadioMode = false
    }

    fun togglePlayPause() {
        if (isRadioMode) { stop(); return }
        if (!isPlaylistLoaded) refreshPlaylist()
        if (playlist.isEmpty()) return
        if (mediaPlayer == null) { start(); return }
        if (isPlaying) { mediaPlayer?.pause(); isPlaying = false }
        else { mediaPlayer?.start(); isPlaying = true }
    }

    fun skipToNext() {
        if (isRadioMode) return
        if (!isPlaylistLoaded) refreshPlaylist()
        if (playlist.isEmpty()) return
        currentTrackIndex = (currentTrackIndex + 1) % playlist.size
        start()
    }

    fun skipToPrevious() {
        if (isRadioMode) return
        if (!isPlaylistLoaded) refreshPlaylist()
        if (playlist.isEmpty()) return
        currentTrackIndex = if (currentTrackIndex > 0) currentTrackIndex - 1 else playlist.size - 1
        start()
    }
}
