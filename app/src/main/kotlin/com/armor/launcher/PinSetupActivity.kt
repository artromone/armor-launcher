package com.armor.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView

/**
 * Three-step flow:
 *  1. If a PIN is already set → ask for current PIN first.
 *  2. Ask for new PIN. Press # to confirm length (4-8 digits).
 *  3. Ask to re-enter the new PIN. On match → save and return.
 *
 * Soft-keys: left = "Clear", right = "Back" (cancels without saving).
 */
class PinSetupActivity : BaseDisguiseActivity() {

    private val pinManager by lazy { PinManager(this) }
    private val entered = StringBuilder()

    private lateinit var promptView: TextView
    private lateinit var pinView: TextView
    private lateinit var statusView: TextView

    private enum class Stage { VERIFY_OLD, ENTER_NEW, CONFIRM_NEW }
    private var stage = Stage.ENTER_NEW
    private var newPin = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_pin_setup)
        setTitleText(if (pinManager.isSet()) "Change PIN" else "Set PIN")

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
        val d = digitOf(keyCode)
        if (d != null) {
            if (entered.length < PinManager.MAX_LEN) entered.append(d)
            // Auto-verify when length matches stored PIN length (verify-old stage)
            if (stage == Stage.VERIFY_OLD && entered.length == pinManager.pinLength()) {
                checkOldPin()
            } else if (stage == Stage.CONFIRM_NEW && entered.length == newPin.length) {
                confirm()
            }
            render()
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

    private fun digitOf(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'
        KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

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
        val ok = pinManager.verify(entered.toString())
        entered.clear()
        if (ok) {
            stage = Stage.ENTER_NEW
            statusView.text = ""
            renderPrompt()
            render()
        } else {
            statusView.text = "Wrong PIN"
            render()
        }
    }

    private fun confirm() {
        if (entered.toString() == newPin) {
            pinManager.setPin(newPin)
            statusView.text = "Saved"
            statusView.postDelayed({ finish() }, 600)
        } else {
            entered.clear()
            statusView.text = "Doesn't match — try again"
            render()
        }
    }

    private fun renderPrompt() {
        promptView.text = when (stage) {
            Stage.VERIFY_OLD -> "Enter current PIN"
            Stage.ENTER_NEW -> "Enter new PIN (4-8 digits, # to confirm)"
            Stage.CONFIRM_NEW -> "Re-enter new PIN"
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
}
