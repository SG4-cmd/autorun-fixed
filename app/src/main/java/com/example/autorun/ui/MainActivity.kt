package com.example.autorun.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.autorun.R
import com.example.autorun.config.GameSettings

/**
 * 【MainActivity: アプリのエントリポイント】
 */
class MainActivity : AppCompatActivity() {

    private var lastBackTime = 0L

    // 権限リクエスト用のランチャー
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            // 権限が許可されたら、必要に応じて音楽プレイヤーをリフレッシュするなどの処理
            Toast.makeText(this, "音楽ライブラリへのアクセスが許可されました", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "端末内の音楽を再生するには権限が必要です", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            GameSettings.init(this)
            HDU.init(this)
        } catch (e: Exception) {
            Log.e("Autorun", "Failed to initialize GameSettings/HDU: ${e.message}")
            e.printStackTrace()
        }
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 音楽再生のための権限チェック
        checkMusicPermission()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackTime < 2000) {
                    finish()
                } else {
                    lastBackTime = currentTime
                    Toast.makeText(this@MainActivity, "もう一度押すと終了します", Toast.LENGTH_SHORT).show()
                }
            }
        })

        setupSystemUI()
    }

    private fun checkMusicPermission() {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        when {
            ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED -> {
                // 既に許可されている
            }
            else -> {
                // 権限をリクエスト
                requestPermissionLauncher.launch(permission)
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            setupSystemUI()
        }
    }

    private fun setupSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
