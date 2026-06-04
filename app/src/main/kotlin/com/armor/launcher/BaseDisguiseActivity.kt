package com.armor.launcher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.TextView
import com.armor.launcher.platform.ClockTicker
import com.armor.launcher.platform.EmergencyLockDetector
import com.armor.launcher.platform.IdleSleepController
import com.armor.launcher.platform.Intents
import com.armor.launcher.platform.LockGate
import com.armor.launcher.platform.RealMode
import com.armor.launcher.platform.SecretComboDetector
import com.armor.launcher.platform.SoftKeys
import com.armor.launcher.platform.SystemBarsController
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Common scaffolding shared by every disguise screen. This class is a thin
 * orchestrator — actual behavior lives in focused controllers:
 *   - [SystemBarsController]   immersive sticky + hide status/nav
 *   - [ClockTicker]            TIME_TICK → updateClock()
 *   - [LockGate]               screen-off → needs-lock pref → LockActivity
 *   - [IdleSleepController]    dim → off → lockNow
 *   - [SecretComboDetector]    5×'*' + secret code → enter Real mode
 *   - [EmergencyLockDetector]  hold '#' 2s → drop Real mode and go home
 *   - [SoftKeys]               hardware soft-key → btn_left/btn_right click
 *
 * Subclasses override `onKeyDown` for screen-specific keys; if they don't
 * consume BACK/MENU here, the soft-key dispatch wires those to the on-screen
 * buttons via `super.onKeyDown`.
 *
 * No in-app disarm/panic. The only escape is the ADB rescue broadcast
 * (RescueReceiver), kept as a dev-side safety net.
 */
abstract class BaseDisguiseActivity : Activity() {

    private val clockFmt = SimpleDateFormat("HH:mm", Locale.US)

    private val systemBars = SystemBarsController(this)
    private val clockTicker = ClockTicker(this) { updateClock() }
    private val lockGate = LockGate(this)
    private val idleSleep = IdleSleepController(this) { lockGate.markLocked() }
    private val secretCombo = SecretComboDetector(this) {
        RealMode.unlock()
        startActivity(Intent(this, RealLauncherActivity::class.java))
    }
    private val emergencyLock = EmergencyLockDetector {
        RealMode.lock()
        startActivity(Intents.clearTopHome(this, DisguiseActivity::class.java))
    }

    /** Activities that ARE the lock screen itself opt-out (LockActivity). */
    protected open val isLockScreen: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        idleSleep.install()
        systemBars.install()
    }

    override fun onStart() {
        super.onStart()
        clockTicker.onStart()
        lockGate.onStart()
        updateClock()
    }

    override fun onStop() {
        super.onStop()
        clockTicker.onStop()
        lockGate.onStop()
    }

    override fun onResume() {
        super.onResume()
        systemBars.hide()
        idleSleep.onResume()
        if (!isLockScreen && lockGate.maybeRedirectToLock()) return
    }

    override fun onPause() {
        super.onPause()
        idleSleep.onPause()
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        idleSleep.onUserInteraction()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) systemBars.hide()
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (ev?.action == MotionEvent.ACTION_DOWN) onUserInteraction()
        // Disguise = keypad-only. Swallow touches everywhere except on the
        // lock screen (PIN entry stays tappable) and once Real mode is on.
        if (!isLockScreen && !RealMode.unlocked) return true
        return super.dispatchTouchEvent(ev)
    }

    override fun dispatchKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return super.dispatchKeyEvent(event)
        emergencyLock.handle(event)
        if (event.action == KeyEvent.ACTION_DOWN) {
            onUserInteraction()
            // Real-mode combo runs pre-routing so it works on every disguise
            // screen even when the subclass would consume the key
            // (LockActivity / PinSetup / Calculator all eat digits).
            if (secretCombo.handle(event)) return true
        }
        // Swallow KEYCODE_POUND so a tap doesn't also reach subclass onKeyDown
        // as a literal '#' while emergency hold is being tracked.
        if (event.keyCode == KeyEvent.KEYCODE_POUND) return true
        return super.dispatchKeyEvent(event)
    }

    /** Override to receive minute-tick clock updates (default updates @id/tv_clock). */
    protected open fun updateClock() {
        findViewById<TextView?>(R.id.tv_clock)?.text = clockFmt.format(Date())
    }

    /** Helper: bind a soft-key label and click handler. */
    protected fun bindSoftKey(id: Int, label: String, onClick: () -> Unit) =
        SoftKeys.bind(this, id, label, onClick)

    /** Helper: bind the title text. */
    protected fun setTitleText(text: String) {
        findViewById<TextView?>(R.id.tv_title)?.text = text
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Hardware soft keys → tap the on-screen button. Subclasses that
        // handle BACK themselves (Lock, PinSetup, Menu, Calculator, Stub)
        // return true before super and never reach this.
        if (SoftKeys.dispatch(this, keyCode)) return true
        return when (keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_MENU -> true
            else -> super.onKeyDown(keyCode, event)
        }
    }
}
