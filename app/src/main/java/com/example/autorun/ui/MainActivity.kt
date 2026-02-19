package com.example.autorun.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.autorun.R
import com.example.autorun.config.GameSettings

class MainActivity : AppCompatActivity() {

    private var lastBackTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        GameSettings.init(this)
        HDU.init(this)
        
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        // 3Dエンジンの状態を2D UIレイヤーに共有
        val gameView = findViewById<GameView>(R.id.game_view)
        val hduOverlay = findViewById<HDUOverlayView>(R.id.hdu_overlay)
        hduOverlay.setGameState(gameView.getGameState())

        setupSystemUI()
        
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastBackTime < 2000) finish()
                else {
                    lastBackTime = currentTime
                    Toast.makeText(this@MainActivity, "もう一度押すと終了します", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) setupSystemUI()
    }

    private fun setupSystemUI() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
