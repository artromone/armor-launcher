package com.armor.launcher.platform

import android.app.Activity
import android.view.KeyEvent
import android.widget.TextView
import com.armor.launcher.R

/**
 * Qin F22 has two physical soft keys above Call / End. They surface as
 * KEYCODE_SOFT_LEFT or KEYCODE_MENU (left) and KEYCODE_SOFT_RIGHT or
 * KEYCODE_BACK (right). This object centralizes the mapping so subclasses
 * that need different BACK semantics can intercept earlier.
 */
internal object SoftKeys {

    fun bind(activity: Activity, id: Int, label: String, onClick: () -> Unit) {
        activity.findViewById<TextView>(id).apply {
            text = label
            setOnClickListener { onClick() }
        }
    }

    /**
     * Try to dispatch a key event as a soft-key click. Returns true if
     * we clicked a bound button.
     */
    fun dispatch(activity: Activity, keyCode: Int): Boolean = when (keyCode) {
        KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU ->
            click(activity, R.id.btn_left)
        KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_BACK ->
            click(activity, R.id.btn_right)
        else -> false
    }

    private fun click(activity: Activity, id: Int): Boolean {
        val v = activity.findViewById<TextView?>(id) ?: return false
        if (v.text.isNullOrBlank()) return false
        return v.performClick()
    }
}
