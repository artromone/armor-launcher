package com.armor.launcher

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent

/**
 * Hold KEYCODE_POUND for [HOLD_MS] → [onTrigger] fires.
 *
 * Originally this was a 2+5 simultaneous combo, but the Qin F22 keypad
 * ghosts adjacent keys; single-key long-press is the only reliable form.
 *
 * The owner also swallows '#' key events globally (see BaseDisguiseActivity
 * dispatchKeyEvent) so a tap doesn't also reach subclass onKeyDown as a
 * literal '#'.
 */
internal class EmergencyLockDetector(
    private val onTrigger: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private val runnable = Runnable { onTrigger() }

    fun handle(event: KeyEvent) {
        if (event.keyCode != KeyEvent.KEYCODE_POUND) return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                handler.removeCallbacks(runnable)
                handler.postDelayed(runnable, HOLD_MS)
            }
            KeyEvent.ACTION_UP -> handler.removeCallbacks(runnable)
        }
    }

    companion object {
        private const val HOLD_MS = 2_000L
    }
}
