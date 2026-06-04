package com.armor.launcher

import android.view.KeyEvent

/** Shared key-code helpers. T9 keypad has these on every disguise screen. */
internal object KeyCodes {
    fun digitOf(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'
        KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

    fun isBackspace(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DEL || keyCode == KeyEvent.KEYCODE_FORWARD_DEL
}
