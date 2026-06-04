package com.armor.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.widget.TextView
import com.armor.launcher.platform.KeyCodes

class CalculatorActivity : BaseDisguiseActivity() {

    private lateinit var display: TextView
    private lateinit var history: TextView

    private var acc: Double = 0.0
    private var pending: Char? = null
    private var input: String = "0"
    private var justEvaluated = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_calculator)
        setTitleText("Calculator")
        display = findViewById(R.id.calc_display)
        history = findViewById(R.id.calc_history)
        bindSoftKey(R.id.btn_left, "×") { applyOp('*') }
        bindSoftKey(R.id.btn_right, "Back") { finish() }
        render()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        KeyCodes.digitOf(keyCode)?.let { pushDigit(it); return true }
        when (keyCode) {
            KeyEvent.KEYCODE_POUND -> { applyOp('+'); return true }
            KeyEvent.KEYCODE_STAR -> {
                // Star is reserved for the Real-mode unlock combo — let base handle it.
                return super.onKeyDown(keyCode, event)
            }
            KeyEvent.KEYCODE_DPAD_UP -> { applyOp('/'); return true }
            KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_CENTER -> { evaluate(); return true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { applyOp('-'); return true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { applyOp('+'); return true }
            KeyEvent.KEYCODE_DEL, KeyEvent.KEYCODE_FORWARD_DEL -> { backspace(); return true }
            KeyEvent.KEYCODE_BACK -> { finish(); return true }
        }
        return super.onKeyDown(keyCode, event)
    }

    // KEYCODE_STAR is reserved for the global Real-mode unlock combo, so
    // minus is exposed via D-pad LEFT instead of '*'.

    private fun pushDigit(d: Char) {
        if (justEvaluated) { input = "0"; justEvaluated = false }
        input = if (input == "0") d.toString() else input + d
        render()
    }

    private fun applyOp(op: Char) {
        val current = input.toDoubleOrNull() ?: 0.0
        if (pending == null) acc = current
        else acc = doMath(acc, current, pending!!)
        pending = op
        input = "0"
        history.text = "${trim(acc)} $op"
        justEvaluated = false
        render()
    }

    private fun evaluate() {
        val p = pending ?: return
        val current = input.toDoubleOrNull() ?: 0.0
        val result = doMath(acc, current, p)
        history.text = "${trim(acc)} $p ${trim(current)} ="
        acc = result
        pending = null
        input = trim(result)
        justEvaluated = true
        render()
    }

    private fun backspace() {
        if (justEvaluated) {
            input = "0"; justEvaluated = false
        } else {
            input = if (input.length > 1) input.dropLast(1) else "0"
        }
        render()
    }

    private fun doMath(a: Double, b: Double, op: Char): Double = when (op) {
        '+' -> a + b
        '-' -> a - b
        '*' -> a * b
        '/' -> if (b == 0.0) Double.NaN else a / b
        else -> b
    }

    private fun trim(d: Double): String =
        if (d.isNaN()) "Error"
        else if (d == d.toLong().toDouble()) d.toLong().toString()
        else d.toString()

    private fun render() { display.text = input }
}
