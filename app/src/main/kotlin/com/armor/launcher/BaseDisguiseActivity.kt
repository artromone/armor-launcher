package com.armor.launcher

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.core.content.edit

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

    // ---------- Idle dim / screen-off ----------------------------------------
    private val idleHandler = Handler(Looper.getMainLooper())
    private val dimRunnable = Runnable { applyDim() }
    private val offRunnable = Runnable { goToSleep() }
    private var isDimmed = false
    private var dimOverlay: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Don't keep the screen on unconditionally — we want it to dim/sleep
        // when the user idles. We manage that ourselves via idleHandler.
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
        restoreBrightness()
        resetIdleTimers()
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

    override fun onPause() {
        super.onPause()
        idleHandler.removeCallbacks(dimRunnable)
        idleHandler.removeCallbacks(offRunnable)
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) onUserInteraction()
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event?.action == KeyEvent.ACTION_DOWN) onUserInteraction()
        return super.dispatchKeyEvent(event)
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        restoreBrightness()
        resetIdleTimers()
    }

    private fun resetIdleTimers() {
        idleHandler.removeCallbacks(dimRunnable)
        idleHandler.removeCallbacks(offRunnable)
        val off = PowerPrefs(this).offAfterMs
        if (off != PowerPrefs.NEVER) {
            // Dim always fires 5s before off (or immediately if off ≤ 5s).
            val dim = (off - DIM_LEAD_MS).coerceAtLeast(0L)
            idleHandler.postDelayed(dimRunnable, dim)
            idleHandler.postDelayed(offRunnable, off)
        }
    }

    private fun applyDim() {
        isDimmed = true
        val lp = window.attributes
        lp.screenBrightness = 0.01f
        window.attributes = lp
        // Fallback for ROMs that ignore window brightness override (Qin F22):
        // overlay a near-opaque black View. Non-clickable so touches pass
        // through to the activity's dispatchTouchEvent and wake us.
        ensureDimOverlay().apply {
            bringToFront()
            visibility = View.VISIBLE
        }
    }

    private fun restoreBrightness() {
        if (!isDimmed) return
        isDimmed = false
        val lp = window.attributes
        lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        dimOverlay?.visibility = View.GONE
    }

    private fun ensureDimOverlay(): View {
        dimOverlay?.let { return it }
        val v = View(this).apply {
            setBackgroundColor(0xFF000000.toInt())
            alpha = 0.85f
            isClickable = false
            isFocusable = false
            visibility = View.GONE
        }
        val root = window.decorView as ViewGroup
        root.addView(
            v,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        dimOverlay = v
        return v
    }

    private fun goToSleep() {
        // Restore brightness first so when the screen turns back on it's not stuck dim.
        restoreBrightness()
        // Set the lock flag directly before lockNow(). Don't rely on
        // ACTION_SCREEN_OFF — onStop() may unregister the receiver before the
        // broadcast is delivered, which would leave us unlocked on next wake.
        if (PinManager.forPin(this).isSet()) {
            getSharedPreferences(PREFS_LOCK_STATE, Context.MODE_PRIVATE)
                .edit {
                    putBoolean(KEY_LOCKED, true)
                }
        }
        try {
            val dpm = getSystemService(DevicePolicyManager::class.java)
            if (dpm?.isAdminActive(DeviceAdmin.componentName(this)) == true) {
                dpm.lockNow()
            }
        } catch (_: Exception) {}
    }

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
        // BEHAVIOR_DEFAULT — no swipe-to-reveal peek; combined with
        // setStatusBarDisabled() in Lock Task this fully suppresses the shade.
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
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
            try { dpm.setStatusBarDisabled(admin, false); log.append("SB • ") }
            catch (e: Exception) { log.append("SB err • ") }
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
        private const val DIM_LEAD_MS = 5_000L
    }
}
