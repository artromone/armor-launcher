package com.armor.launcher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Fires when the device finishes booting (also LOCKED_BOOT_COMPLETED for
 * direct-boot stage). We re-launch DisguiseActivity so the user is never
 * dropped onto the system UI even briefly.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val launch = Intent(context, DisguiseActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_NO_ANIMATION
            )
        }
        context.startActivity(launch)
    }
}
