package com.armor.launcher.platform

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * Subscribes to ACTION_TIME_TICK (+ TIME/TIMEZONE_CHANGED) and fires [onTick]
 * once per minute. Owner is responsible for the actual clock rendering —
 * BaseDisguiseActivity defaults to writing HH:mm into `@id/tv_clock`.
 */
internal class ClockTicker(
    private val context: Context,
    private val onTick: () -> Unit,
) {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = onTick()
    }

    fun onStart() {
        context.registerReceiver(receiver, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
    }

    fun onStop() {
        try { context.unregisterReceiver(receiver) } catch (_: Exception) {}
    }
}
