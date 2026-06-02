package com.armor.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Common scaffolding shared by every disguise screen:
 *  - fullscreen / immersive
 *  - clock in title bar auto-updates on TIME_TICK
 *  - panic combo (5× '*' in 3s) → disarm
 *  - swallow home/recents keys (back is handled per subclass)
 */
abstract class BaseDisguiseActivity : Activity() {

    private val starPresses = ArrayDeque<Long>()
    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)

    private val timeTick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            if (PinManager.forPin(this@BaseDisguiseActivity).isSet()) {
                getSharedPreferences(PREFS_LOCK_STATE, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_LOCKED, true)
                    .apply()
            }
        }
    }

    /** Activities that ARE the lock screen itself opt-out (LockActivity). */
    protected open val isLockScreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        @Suppress("DEPRECATION")
        window.decorView.setOnSystemUiVisibilityChangeListener { vis ->
            if (vis and View.SYSTEM_UI_FLAG_FULLSCREEN == 0) hideSystemBars()
        }
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(timeTick, IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        })
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        updateClock()
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(timeTick) } catch (_: Exception) {}
        try { unregisterReceiver(screenOff) } catch (_: Exception) {}
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        if (!isLockScreen && shouldShowLockScreen()) {
            val needsLock = getSharedPreferences(PREFS_LOCK_STATE, Context.MODE_PRIVATE)
                .getBoolean(KEY_LOCKED, false)
            if (needsLock) {
                getSharedPreferences(PREFS_LOCK_STATE, Context.MODE_PRIVATE)
                    .edit().putBoolean(KEY_LOCKED, false).apply()
                startActivity(Intent(this, LockActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                })
                finish()
            }
        }
    }

    private fun shouldShowLockScreen(): Boolean = PinManager.forPin(this).isSet()

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    /** Override to receive minute-tick clock updates (default updates @id/tv_clock). */
    protected open fun updateClock() {
        findViewById<TextView?>(R.id.tv_clock)?.text = clockFmt.format(Date())
    }

    protected fun hideSystemBars() {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
        val c = WindowInsetsControllerCompat(window, window.decorView)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Helper: bind a soft-key label and click handler. */
    protected fun bindSoftKey(id: Int, label: String, onClick: () -> Unit) {
        findViewById<TextView>(id).apply {
            text = label
            setOnClickListener { onClick() }
        }
    }

    /** Helper: bind the title text. */
    protected fun setTitleText(text: String) {
        findViewById<TextView?>(R.id.tv_title)?.text = text
    }

    // ---------- Panic combo + key handling -----------------------------------

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_STAR) {
            if (handleStarPress()) return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    /** Returns true if this press completed the panic sequence (consume). */
    private fun handleStarPress(): Boolean {
        val now = SystemClock.uptimeMillis()
        starPresses.addLast(now)
        while (starPresses.isNotEmpty() && now - starPresses.first() > PANIC_WINDOW_MS) {
            starPresses.removeFirst()
        }
        if (starPresses.size >= PANIC_PRESSES) {
            starPresses.clear()
            val report = disarm()
            Toast.makeText(this, "PANIC: $report", Toast.LENGTH_LONG).show()
            finishAndRemoveTask()
            return true
        }
        return false // let star propagate normally if it wasn't the trigger
    }

    private fun disarm(): String {
        val dpm = getSystemService(DevicePolicyManager::class.java) ?: return "no DPM"
        val admin = DeviceAdmin.componentName(this)
        val log = StringBuilder()
        try { stopLockTask() } catch (_: Exception) {}
        if (dpm.isDeviceOwnerApp(packageName)) {
            try { HiddenAppsManager(this).showAll(); log.append("shown • ") }
            catch (e: Exception) { log.append("show err • ") }
            try { dpm.setKeyguardDisabled(admin, false); log.append("KG • ") }
            catch (e: Exception) { log.append("KG err • ") }
            try {
                dpm.clearPackagePersistentPreferredActivities(admin, packageName)
                log.append("HOME • ")
            } catch (e: Exception) { log.append("HOME err • ") }
            try {
                @Suppress("DEPRECATION")
                dpm.clearDeviceOwnerApp(packageName); log.append("DO • ")
            } catch (e: Exception) { log.append("DO err • ") }
        }
        if (dpm.isAdminActive(admin)) {
            try { dpm.removeActiveAdmin(admin); log.append("admin") }
            catch (e: Exception) { log.append("adm err") }
        }
        return log.toString()
    }

    companion object {
        private const val PANIC_PRESSES = 5
        private const val PANIC_WINDOW_MS = 3_000L
        private const val PREFS_LOCK_STATE = "armor_lock_state"
        private const val KEY_LOCKED = "needs_lock"
    }
}
