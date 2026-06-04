package com.armor.launcher.platform

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import com.armor.launcher.domain.PowerPrefs

/**
 * Owns the idle-screen lifecycle: dim → off → lockNow.
 *
 *  - dim fires `DIM_LEAD_MS` before off (or immediately if off ≤ lead)
 *  - off fires `PowerPrefs.offAfterMs` after the last interaction
 *  - dim is rendered both as a brightness override AND as an opaque-ish
 *    overlay View, because some Qin ROMs ignore window brightness
 *
 * Owner calls onCreate / onResume / onPause and onUserInteraction.
 */
internal class IdleSleepController(
    private val activity: Activity,
    private val onSleep: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable { applyDim() }
    private val offRunnable = Runnable { goToSleep() }
    private var isDimmed = false
    private var dimOverlay: View? = null

    /** Disable the system screen-off timer so we control timing alone. */
    fun install() {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    fun onResume() {
        restoreBrightness()
        reset()
    }

    fun onPause() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(offRunnable)
    }

    fun onUserInteraction() {
        restoreBrightness()
        reset()
    }

    fun reset() {
        handler.removeCallbacks(dimRunnable)
        handler.removeCallbacks(offRunnable)
        val off = PowerPrefs(activity).offAfterMs
        if (off == PowerPrefs.NEVER) return
        val dim = (off - DIM_LEAD_MS).coerceAtLeast(0L)
        handler.postDelayed(dimRunnable, dim)
        handler.postDelayed(offRunnable, off)
    }

    private fun applyDim() {
        isDimmed = true
        val lp = activity.window.attributes
        lp.screenBrightness = 0.01f
        activity.window.attributes = lp
        ensureDimOverlay().apply {
            bringToFront()
            visibility = View.VISIBLE
        }
    }

    private fun restoreBrightness() {
        if (!isDimmed) return
        isDimmed = false
        val lp = activity.window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = lp
        dimOverlay?.visibility = View.GONE
    }

    private fun ensureDimOverlay(): View {
        dimOverlay?.let { return it }
        val v = View(activity).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0.85f
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val root = activity.window.decorView as ViewGroup
        root.addView(
            v,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        dimOverlay = v
        return v
    }

    private fun goToSleep() {
        restoreBrightness()
        onSleep()
        Dpm.lockNow(activity)
    }

    companion object {
        private const val DIM_LEAD_MS = 5_000L
    }
}
