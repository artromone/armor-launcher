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
 *  - swallow home/recents keys (back is handled per subclass)
 *  - hardware soft-key dispatch to btn_left / btn_right
 *
 * No in-app disarm/panic. The only escape is the ADB rescue broadcast
 * (RescueReceiver), kept as a dev-side safety net.
 */
abstract class BaseDisguiseActivity : Activity() {

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)

    private val timeTick = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) = updateClock()
    }

    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            RealMode.lock()
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
        // KEEP_SCREEN_ON disables the *system* screen-off timer so only our
        // own idleHandler controls when the screen dims/sleeps. Without this
        // the system would honor Settings.System.SCREEN_OFF_TIMEOUT (often
        // 30 s on Qin), which raced our timer and produced apparently-random
        // lock prompts. Our own timer still fires goToSleep() → dpm.lockNow()
        // when the user really has been idle for offAfterMs.
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
        // Disguise = keypad-only. Swallow touches everywhere except on the
        // lock screen (PIN entry stays tappable) and once Real mode is on.
        if (!isLockScreen && !RealMode.unlocked) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event != null) trackEmergencyLock(event)
        if (event?.action == KeyEvent.ACTION_DOWN) {
            onUserInteraction()
            // Real-mode unlock combo runs pre-routing so it works on every
            // disguise screen even when the subclass would consume the key
            // (LockActivity / PinSetup / Calculator all eat digits).
            if (handleSecretCombo(event.keyCode, event)) return true
        }
        // Swallow KEYCODE_POUND when we're in the middle of the emergency
        // hold so it doesn't also reach Subclass.onKeyDown as a normal '#'.
        if (event?.keyCode == KeyEvent.KEYCODE_POUND) return true
        return super.dispatchKeyEvent(event)
    }

    // ---------- Emergency lock: hold '#' for 2s -----------------------------
    private val emergencyHandler = Handler(Looper.getMainLooper())
    private val emergencyRunnable = Runnable {
        RealMode.lock()
        startActivity(Intent(this, DisguiseActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        })
    }

    private fun trackEmergencyLock(event: KeyEvent) {
        if (event.keyCode != KeyEvent.KEYCODE_POUND) return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                emergencyHandler.removeCallbacks(emergencyRunnable)
                emergencyHandler.postDelayed(emergencyRunnable, EMERGENCY_HOLD_MS)
            }
            KeyEvent.ACTION_UP -> emergencyHandler.removeCallbacks(emergencyRunnable)
        }
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
        RealMode.lock()
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
        // No swipe-to-reveal peek; SHOW_BARS_BY_TOUCH is the legacy alias for
        // BEHAVIOR_DEFAULT in the androidx.core version we're pinned to (1.6.0).
        // Do NOT "fix" this back to BEHAVIOR_DEFAULT — it's not in this release.
        @Suppress("DEPRECATION")
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_BARS_BY_TOUCH
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

    // ---------- Key handling -------------------------------------------------

    private val secretHandler = Handler(Looper.getMainLooper())
    private var awaitingSecret = false
    private val secretBuf = StringBuilder()
    private val starPresses = ArrayDeque<Long>()
    private val secretTimeout = Runnable {
        awaitingSecret = false
        secretBuf.clear()
        starPresses.clear()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Qin F22 physical soft keys (the two above Call/End):
        //   left → SOFT_LEFT or MENU   → click btn_left
        //   right → SOFT_RIGHT or BACK → click btn_right
        // Subclasses that handle BACK themselves (LockActivity, PinSetup,
        // Menu, Calculator, Stub) return true before super and never reach
        // this — so we don't clobber backspace/finish semantics there.
        when (keyCode) {
            KeyEvent.KEYCODE_SOFT_LEFT, KeyEvent.KEYCODE_MENU ->
                if (clickSoftKey(R.id.btn_left)) return true
            KeyEvent.KEYCODE_SOFT_RIGHT, KeyEvent.KEYCODE_BACK ->
                if (clickSoftKey(R.id.btn_right)) return true
        }
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun handleSecretCombo(keyCode: Int, event: KeyEvent?): Boolean {
        if (awaitingSecret) {
            val d = digitOf(keyCode) ?: return false
            secretBuf.append(d)
            secretHandler.removeCallbacks(secretTimeout)
            secretHandler.postDelayed(secretTimeout, SECRET_WINDOW_MS)
            val mgr = PinManager.forSecret(this)
            if (secretBuf.length >= mgr.pinLength()) {
                val ok = mgr.verify(secretBuf.toString())
                awaitingSecret = false
                secretBuf.clear()
                secretHandler.removeCallbacks(secretTimeout)
                if (ok) {
                    RealMode.unlock()
                    startActivity(Intent(this, RealLauncherActivity::class.java))
                }
            }
            return true
        }
        if (keyCode == KeyEvent.KEYCODE_STAR && event?.repeatCount == 0) {
            val now = SystemClock.uptimeMillis()
            starPresses.addLast(now)
            while (starPresses.isNotEmpty() && now - starPresses.first() > STAR_WINDOW_MS) {
                starPresses.removeFirst()
            }
            if (starPresses.size >= STAR_PRESSES) {
                starPresses.clear()
                awaitingSecret = true
                secretBuf.clear()
                secretHandler.removeCallbacks(secretTimeout)
                secretHandler.postDelayed(secretTimeout, SECRET_WINDOW_MS)
            }
            return true
        }
        return false
    }

    private fun digitOf(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'
        KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

    private fun clickSoftKey(id: Int): Boolean {
        val v = findViewById<TextView?>(id) ?: return false
        if (v.text.isNullOrBlank()) return false
        return v.performClick()
    }

    companion object {
        private const val PREFS_LOCK_STATE = "armor_lock_state"
        private const val KEY_LOCKED = "needs_lock"
        private const val DIM_LEAD_MS = 5_000L
        private const val STAR_PRESSES = 5
        private const val STAR_WINDOW_MS = 3_000L
        private const val SECRET_WINDOW_MS = 5_000L
        private const val EMERGENCY_HOLD_MS = 2_000L
    }
}
