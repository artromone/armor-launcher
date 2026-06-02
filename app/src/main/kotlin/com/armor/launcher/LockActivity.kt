package com.armor.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView

/**
 * Feature-phone style PIN unlock. Replaces the system keyguard
 * (which we disabled via DPM). Shown:
 *  - At boot, if a PIN is configured (BootReceiver hand-off).
 *  - On wake from screen-off (BaseDisguiseActivity hand-off).
 *
 * Exits:
 *  - Correct PIN → DisguiseActivity, this finishes.
 *  - Panic 5×* (handled in BaseDisguiseActivity) → full disarm + bail.
 *  - Otherwise unkillable: BACK/HOME swallowed.
 */
class LockActivity : BaseDisguiseActivity() {

    override val isLockScreen: Boolean = true

    private val pinManager by lazy { PinManager(this) }
    private val entered = StringBuilder()

    private lateinit var pinView: TextView
    private lateinit var statusView: TextView
    private lateinit var promptView: TextView

    private val tickHandler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            renderLockoutCountdown()
            tickHandler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_lock)
        setTitleText("Locked")

        pinView = findViewById(R.id.lock_pin)
        statusView = findViewById(R.id.lock_status)
        promptView = findViewById(R.id.lock_prompt)

        bindSoftKey(R.id.btn_left, "Clear") { entered.clear(); render() }
        bindSoftKey(R.id.btn_right, "") {}

        // Edge-case safety: if there's no PIN at all, just go to home.
        if (!pinManager.isSet()) {
            launchHome()
            return
        }
        render()
    }

    override fun onResume() {
        super.onResume()
        tickHandler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        tickHandler.removeCallbacks(tick)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (pinManager.isLockedOut()) {
            // Ignore digit input while locked out.
            renderLockoutCountdown()
            return true
        }
        val d = digitOf(keyCode)
        if (d != null) {
            if (entered.length < pinManager.pinLength()) entered.append(d)
            if (entered.length == pinManager.pinLength()) tryUnlock()
            render()
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_BACK -> {
                if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                render()
                return true
            }
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> return true // swallow
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun digitOf(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'
        KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

    private fun tryUnlock() {
        val ok = pinManager.verify(entered.toString())
        entered.clear()
        if (ok) {
            launchHome()
        } else {
            render()
            if (pinManager.isLockedOut()) {
                renderLockoutCountdown()
            } else {
                val remaining = (5 - pinManager.failedAttempts()).coerceAtLeast(0)
                statusView.text = "Incorrect PIN — $remaining left"
            }
        }
    }

    private fun launchHome() {
        startActivity(Intent(this, DisguiseActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            putExtra(DisguiseActivity.EXTRA_FROM_LOCK, true)
        })
        finishAndRemoveTask()
    }

    private fun render() {
        val total = pinManager.pinLength()
        val filled = entered.length
        // ● = entered, ○ = empty
        pinView.text = buildString {
            repeat(filled) { append('●') }
            repeat(total - filled) { append('○') }
        }
        if (!pinManager.isLockedOut() && statusView.text.isNullOrEmpty().not() && filled == 0) {
            // keep error visible until the user starts typing again
        }
        promptView.text = "Enter PIN"
    }

    private fun renderLockoutCountdown() {
        val s = pinManager.lockoutSecondsRemaining()
        if (s > 0) {
            promptView.text = "Locked"
            statusView.text = "Too many attempts — wait ${s}s"
            pinView.text = "—".repeat(pinManager.pinLength())
        } else {
            statusView.text = ""
            promptView.text = "Enter PIN"
            render()
        }
    }
}
