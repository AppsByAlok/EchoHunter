package com.appsbyalok.echohunter

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.window.OnBackInvokedDispatcher
import com.appsbyalok.echohunter.data.SaveManager
import com.appsbyalok.echohunter.data.UpgradeSystem
import com.appsbyalok.echohunter.utils.EchoAudioManager
import com.appsbyalok.echohunter.view.GameView

class MainActivity : Activity() {
    private lateinit var gameView: GameView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        SaveManager.init(this)
        UpgradeSystem.init(this)

        gameView = GameView(this)
        setContentView(gameView)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Notch / Safe Area Insets Handling
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            gameView.setOnApplyWindowInsetsListener { _, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                val displayCutout = insets.getInsets(WindowInsets.Type.displayCutout())
                
                gameView.gs.hudLayout.safeInsetTop = maxOf(bars.top, displayCutout.top).toFloat()
                gameView.gs.hudLayout.safeInsetBottom = maxOf(bars.bottom, displayCutout.bottom).toFloat()
                gameView.gs.hudLayout.safeInsetLeft = maxOf(bars.left, displayCutout.left).toFloat()
                gameView.gs.hudLayout.safeInsetRight = maxOf(bars.right, displayCutout.right).toFloat()
                
                insets
            }
        }

        savedInstanceState?.let { gameView.restoreState(it) }
        applyOrientation()

        // Android 13+ Back Navigation
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_DEFAULT
            ) {
                if (!gameView.handleBackPressed()) finish()
            }
        }

        // Immersive Mode
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN)
        }
    }

    override fun onPause() {
        super.onPause()
        gameView.onPause()
    }

    override fun onResume() {
        super.onResume()
        gameView.onResume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        gameView.saveState(outState)
    }

    @Deprecated("Deprecated in Java")
    @SuppressLint("GestureBackNavigation")
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!gameView.handleBackPressed()) {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        EchoAudioManager.release() // Releasing audio resources
    }

    fun applyOrientation() {
        requestedOrientation = when (SaveManager.screenOrientation) {
            1 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            2 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            0 -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
            else -> android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}