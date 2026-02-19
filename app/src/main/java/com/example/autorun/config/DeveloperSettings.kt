package com.example.autorun.config

import android.content.Context
import com.example.autorun.R

object DeveloperSettings {
    var showRightGuardrail = true
    var showGuardrailPosts = true
    
    // 音要素のオンオフ設定
    var isEngineSoundEnabled = true
    var isExhaustSoundEnabled = true
    var isTurboSoundEnabled = true
    var isWindSoundEnabled = true
    var isBrakeSoundEnabled = true
    var isTireSquealEnabled = true

    // エンジン音の追加効果
    var isEngineJitterEnabled = true
    var isEngineGhostEnabled = false // 起動時は負荷軽減のためオフ

    fun init(context: Context) {
        showRightGuardrail = context.getString(R.string.show_right_guardrail).toBoolean()
        showGuardrailPosts = context.getString(R.string.show_guardrail_posts).toBoolean()
        
        isEngineSoundEnabled = true
        isExhaustSoundEnabled = true
        isTurboSoundEnabled = true
        isWindSoundEnabled = true
        isBrakeSoundEnabled = true
        isTireSquealEnabled = true
        
        isEngineJitterEnabled = true
        isEngineGhostEnabled = false // 起動時は負荷軽減のためオフ
    }
}
