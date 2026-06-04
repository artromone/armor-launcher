package com.armor.launcher

import android.app.Activity
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Immersive sticky + status/nav-bar hiding. Status bar peek (swipe-down) is
 * additionally killed at DPM level by DisguiseActivity; this class handles
 * the activity-window-local hiding so non-DO screens still look stock.
 */
internal class SystemBarsController(private val activity: Activity) {

    fun install() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        @Suppress("DEPRECATION")
        activity.window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) hide()
        }
    }

    fun hide() {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        // No swipe-to-reveal peek; SHOW_BARS_BY_TOUCH is the legacy alias for
        // BEHAVIOR_DEFAULT in the androidx.core version we're pinned to (1.6.0).
        // Do NOT "fix" this back to BEHAVIOR_DEFAULT — it's not in this release.
        @Suppress("DEPRECATION")
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
        c.hide(WindowInsetsCompat.Type.systemBars())
    }
}
