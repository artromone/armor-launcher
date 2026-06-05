package com.armor.launcher.platform

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.widget.TextView
import com.armor.launcher.R

/**
 * Subscribes to ACTION_BATTERY_CHANGED and pushes the percentage into the
 * standard `@id/tv_battery_pct` TextView, so every screen with the unified
 * status bar gets a live battery readout for free.
 *
 * ACTION_BATTERY_CHANGED is a sticky broadcast, so registerReceiver() yields
 * the current state immediately — no need for the activity to also poll.
 */
internal class BatteryWatcher(private val activity: Activity) {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = render(i)
    }

    fun onStart() {
        val sticky = activity.registerReceiver(
            receiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        render(sticky)
    }

    fun onStop() {
        try { activity.unregisterReceiver(receiver) } catch (_: Exception) {}
    }

    private fun render(intent: Intent?) {
        val tv = activity.findViewById<TextView?>(R.id.tv_battery_pct) ?: return
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        tv.text = if (level >= 0 && scale > 0) "${level * 100 / scale}%" else "--%"
    }
}
