package com.armor.launcher

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import com.armor.launcher.domain.PinManager
import com.armor.launcher.platform.Intents
import com.armor.launcher.platform.KeyCodes

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

    private val pinManager by lazy { PinManager.forPin(this) }
    private val entered = StringBuilder()
    @Volatile private var verifying = false

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
        // Eat all keys while the background PBKDF2 verify is still running,
        // otherwise queued digits would land in the next attempt.
        if (verifying) return true
        if (pinManager.isLockedOut()) {
            renderLockoutCountdown()
            return true
        }
        val d = digitOf(keyCode)
        if (d != null) {
            if (entered.isEmpty()) statusView.text = ""
            if (entered.length < pinManager.pinLength()) entered.append(d)
            // Render BEFORE kicking off verify so the last dot is painted on
            // screen before we move to the "Checking…" state.
            render()
            if (entered.length == pinManager.pinLength()) tryUnlock()
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

    private fun digitOf(keyCode: Int): Char? = KeyCodes.digitOf(keyCode)

    private fun tryUnlock() {
        // PBKDF2-HMAC-SHA256 at 50k iterations takes ~1-2 s on MT6739 — way
        // too long to block the UI thread. Run it on a worker, show a brief
        // "Checking…" hint, ignore further keys until done.
        verifying = true
        val candidate = entered.toString()
        entered.clear()
        promptView.text = "Checking…"
        Thread {
            val ok = pinManager.verify(candidate)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                verifying = false
                if (ok) {
                    launchHome()
                    return@runOnUiThread
                }
                promptView.text = "Enter PIN"
                render()
                if (pinManager.isLockedOut()) {
                    renderLockoutCountdown()
                } else {
                    val remaining = (5 - pinManager.failedAttempts()).coerceAtLeast(0)
                    statusView.text = "Incorrect PIN — $remaining left"
                }
            }
        }.start()
    }

    private fun launchHome() {
        startActivity(Intents.clearTopHome(this, DisguiseActivity::class.java).apply {
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
