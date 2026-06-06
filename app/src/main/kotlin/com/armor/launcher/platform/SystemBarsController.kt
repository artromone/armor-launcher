package com.armor.launcher.platform

import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Immersive sticky + status/nav-bar hiding for fake (disguise) mode. While
 * [RealMode.unlocked] is true, [hide] delegates to [show] so the real system
 * status bar + notification shade are available everywhere inside Armor.
 *
 * Theme.Armor.NoBars sets windowFullscreen=true (FLAG_FULLSCREEN), which the
 * platform applies at attach time before this controller runs. [show] clears
 * that flag at runtime so the status bar can actually appear.
 */
internal class SystemBarsController(private val activity: Activity) {

    fun install() {
        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
        @Suppress("DEPRECATION")
        activity.window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (RealMode.unlocked) return@setOnSystemUiVisibilityChangeListener
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) hide()
        }
    }

    fun hide() {
        if (RealMode.unlocked) { show(); return }
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

    fun show() {
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        val c = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
        c.show(WindowInsetsCompat.Type.systemBars())
    }
}
