package com.armor.launcher.platform

import android.content.Context
import android.content.Intent

/**
 * Common Intent patterns. `clearTopHome(...)` and friends are repeated all
 * over the codebase — centralize them so the flag combinations are uniform.
 */
internal object Intents {
    /**
     * NEW_TASK + CLEAR_TASK — what every "drop back to the disguise" call
     * site uses. Use for navigating from a one-shot screen (lock, panic,
     * emergency) back into Armor's main flow.
     */
    fun clearTopHome(c: Context, target: Class<*>): Intent =
        Intent(c, target).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
}
