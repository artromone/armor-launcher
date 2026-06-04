package com.armor.launcher

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent

/**
 * Real-mode unlock combo. Two-stage state machine driven by KEY_DOWN events:
 *   1. Five KEYCODE_STAR within STAR_WINDOW_MS → enter "awaiting secret" mode
 *   2. Digits collected until length matches stored secret length → verify
 *
 * If the secret matches, [onUnlock] fires (synchronously). The detector resets
 * after a successful verify or after SECRET_WINDOW_MS of idleness.
 *
 * The combo is dispatched from `dispatchKeyEvent` (not onKeyDown) so it works
 * on every disguise screen including ones whose subclass eats digit keys.
 */
internal class SecretComboDetector(
    private val context: Context,
    private val onUnlock: () -> Unit,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var awaitingSecret = false
    private val secretBuf = StringBuilder()
    private val starPresses = ArrayDeque<Long>()
    private val timeout = Runnable { reset() }

    /** Returns true if the event was consumed by the combo. */
    fun handle(event: KeyEvent): Boolean {
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (awaitingSecret) return collectDigit(event.keyCode)
        return collectStar(event.keyCode, event)
    }

    private fun collectDigit(keyCode: Int): Boolean {
        val d = KeyCodes.digitOf(keyCode) ?: return false
        secretBuf.append(d)
        rescheduleTimeout()
        val mgr = PinManager.forSecret(context)
        if (secretBuf.length >= mgr.pinLength()) {
            val ok = mgr.verify(secretBuf.toString())
            reset()
            if (ok) onUnlock()
        }
        return true
    }

    private fun collectStar(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode != KeyEvent.KEYCODE_STAR || event.repeatCount != 0) return false
        val now = SystemClock.uptimeMillis()
        starPresses.addLast(now)
        while (starPresses.isNotEmpty() && now - starPresses.first() > STAR_WINDOW_MS) {
            starPresses.removeFirst()
        }
        if (starPresses.size >= STAR_PRESSES) {
            starPresses.clear()
            awaitingSecret = true
            secretBuf.clear()
            rescheduleTimeout()
        }
        return true
    }

    private fun rescheduleTimeout() {
        handler.removeCallbacks(timeout)
        handler.postDelayed(timeout, SECRET_WINDOW_MS)
    }

    private fun reset() {
        awaitingSecret = false
        secretBuf.clear()
        starPresses.clear()
        handler.removeCallbacks(timeout)
    }

    companion object {
        private const val STAR_PRESSES = 5
        private const val STAR_WINDOW_MS = 3_000L
        private const val SECRET_WINDOW_MS = 5_000L
    }
}
