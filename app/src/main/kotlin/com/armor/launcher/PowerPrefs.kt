package com.armor.launcher

import android.content.Context

/**
 * Per-launcher screen-timeout settings. Stored separately from system
 * Settings.System.SCREEN_OFF_TIMEOUT so the disguise can have its own dim
 * curve independent of any "stock launcher" settings the user might still
 * see if they peek into system Settings.
 */
class PowerPrefs(context: Context) {
    private val prefs = context.getSharedPreferences("armor_power", Context.MODE_PRIVATE)

    var dimAfterMs: Long
        get() = prefs.getLong(KEY_DIM, DEFAULT_DIM_MS)
        set(v) { prefs.edit().putLong(KEY_DIM, v).apply() }

    var offAfterMs: Long
        get() = prefs.getLong(KEY_OFF, DEFAULT_OFF_MS)
        set(v) { prefs.edit().putLong(KEY_OFF, v).apply() }

    companion object {
        private const val KEY_DIM = "dim_after_ms"
        private const val KEY_OFF = "off_after_ms"

        private const val DEFAULT_DIM_MS = 30_000L
        private const val DEFAULT_OFF_MS = 60_000L

        const val NEVER: Long = Long.MAX_VALUE

        /** Sorted ascending — picker cycles through these. */
        val DIM_OPTIONS: List<Long> = listOf(
            10_000L, 15_000L, 20_000L, 30_000L, 60_000L, 120_000L, NEVER
        )
        val OFF_OPTIONS: List<Long> = listOf(
            15_000L, 30_000L, 60_000L, 120_000L, 300_000L, 600_000L, NEVER
        )

        fun label(ms: Long): String = when {
            ms == NEVER -> "Never"
            ms < 60_000L -> "${ms / 1000}s"
            ms < 3_600_000L -> "${ms / 60_000}m"
            else -> "${ms / 3_600_000}h"
        }

        /** Pick the next value in `opts` after `current` (cycles back to first). */
        fun next(current: Long, opts: List<Long>): Long {
            val i = opts.indexOf(current)
            return if (i < 0 || i == opts.lastIndex) opts.first() else opts[i + 1]
        }
    }
}
