package com.armor.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import com.armor.launcher.domain.PinManager
import com.armor.launcher.platform.KeyCodes

/**
 * Three-step flow:
 *  1. If a PIN is already set → ask for current PIN first.
 *  2. Ask for new PIN. Press # to confirm length (4-8 digits).
 *  3. Ask to re-enter the new PIN. On match → save and return.
 *
 * Soft-keys: left = "Clear", right = "Back" (cancels without saving).
 */
class PinSetupActivity : BaseDisguiseActivity() {

    private val isSecret by lazy { intent.getBooleanExtra(EXTRA_SECRET, false) }
    private val pinManager by lazy {
        if (isSecret) PinManager.forSecret(this) else PinManager.forPin(this)
    }
    private val label: String get() = if (isSecret) "Secret Code" else "PIN"

    private val entered = StringBuilder()

    private lateinit var promptView: TextView
    private lateinit var pinView: TextView
    private lateinit var statusView: TextView

    private enum class Stage { VERIFY_OLD, ENTER_NEW, CONFIRM_NEW }
    private var stage = Stage.ENTER_NEW
    private var newPin = ""
    @Volatile private var busy = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_setup)
        setTitleText(if (pinManager.isSet()) "Change $label" else "Set $label")

        promptView = findViewById(R.id.setup_prompt)
        pinView = findViewById(R.id.setup_pin)
        statusView = findViewById(R.id.setup_status)

        stage = if (pinManager.isSet()) Stage.VERIFY_OLD else Stage.ENTER_NEW
        renderPrompt()

        bindSoftKey(R.id.btn_left, "Clear") { entered.clear(); render() }
        bindSoftKey(R.id.btn_right, "Back") { finish() }
        render()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Drop input while a background PBKDF2 verify/save is still running.
        if (busy) return true
        val d = digitOf(keyCode)
        if (d != null) {
            if (entered.length < PinManager.MAX_LEN) entered.append(d)
            // Paint the last dot before kicking off any PBKDF2 work, so the
            // user sees their final keystroke land.
            render()
            if (stage == Stage.VERIFY_OLD && entered.length == pinManager.pinLength()) {
                checkOldPin()
            } else if (stage == Stage.CONFIRM_NEW && entered.length == newPin.length) {
                confirm()
            }
            return true
        }
        when (keyCode) {
            KeyEvent.KEYCODE_POUND -> {
                onPound()
                return true
            }
            KeyEvent.KEYCODE_DEL,
            KeyEvent.KEYCODE_FORWARD_DEL,
            KeyEvent.KEYCODE_BACK -> {
                if (entered.isNotEmpty()) entered.deleteCharAt(entered.length - 1)
                render()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun digitOf(keyCode: Int): Char? = KeyCodes.digitOf(keyCode)

    private fun onPound() {
        when (stage) {
            Stage.ENTER_NEW -> {
                if (entered.length in PinManager.MIN_LEN..PinManager.MAX_LEN) {
                    newPin = entered.toString()
                    entered.clear()
                    stage = Stage.CONFIRM_NEW
                    renderPrompt()
                    render()
                } else {
                    statusView.text = "PIN must be 4-8 digits"
                }
            }
            Stage.CONFIRM_NEW -> {
                if (entered.length == newPin.length) confirm()
            }
            Stage.VERIFY_OLD -> {
                if (entered.length == pinManager.pinLength()) checkOldPin()
            }
        }
    }

    private fun checkOldPin() {
        busy = true
        val candidate = entered.toString()
        entered.clear()
        statusView.text = "Checking…"
        Thread {
            val ok = pinManager.verify(candidate)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                if (ok) {
                    stage = Stage.ENTER_NEW
                    statusView.text = ""
                    renderPrompt()
                } else {
                    statusView.text = "Wrong PIN"
                }
                render()
            }
        }.start()
    }

    private fun confirm() {
        if (entered.toString() != newPin) {
            entered.clear()
            statusView.text = "Doesn't match — try again"
            render()
            return
        }
        busy = true
        val pin = newPin
        statusView.text = "Saving…"
        Thread {
            pinManager.setPin(pin)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                busy = false
                statusView.text = "Saved"
                statusView.postDelayed({ if (!isFinishing) finish() }, 600)
            }
        }.start()
    }

    private fun renderPrompt() {
        promptView.text = when (stage) {
            Stage.VERIFY_OLD -> "Enter current $label"
            Stage.ENTER_NEW -> "Enter new $label (4-8 digits, # to confirm)"
            Stage.CONFIRM_NEW -> "Re-enter new $label"
        }
    }

    private fun render() {
        val total = when (stage) {
            Stage.VERIFY_OLD -> pinManager.pinLength().coerceAtLeast(entered.length)
            Stage.ENTER_NEW -> entered.length.coerceAtLeast(PinManager.MIN_LEN)
            Stage.CONFIRM_NEW -> newPin.length
        }
        val filled = entered.length
        pinView.text = buildString {
            repeat(filled) { append('●') }
            repeat((total - filled).coerceAtLeast(0)) { append('○') }
        }
    }

    companion object {
        const val EXTRA_SECRET = "is_secret"
    }
}
