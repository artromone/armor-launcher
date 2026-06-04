package com.armor.launcher

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DisguiseActivity : BaseDisguiseActivity() {

    private var tvTime: TextView? = null
    private var tvDate: TextView? = null
    private var tvBatteryPct: TextView? = null

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            val level = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = i?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            if (level >= 0 && scale > 0) {
                tvBatteryPct?.text = "${level * 100 / scale}%"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // If a PIN is set and this is a cold start (no savedInstanceState),
        // jump to the lock screen first.
        if (savedInstanceState == null && PinManager.forPin(this).isSet()
            && !intent.getBooleanExtra(EXTRA_FROM_LOCK, false)
        ) {
            startActivity(Intent(this, LockActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            })
            finish()
            return
        }
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_disguise)
        tvTime = findViewById(R.id.tv_time)
        tvDate = findViewById(R.id.tv_date)
        tvBatteryPct = findViewById(R.id.tv_battery_pct)

        bindSoftKey(R.id.btn_left, "Menu") {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        bindSoftKey(R.id.btn_right, "Contacts") {
            val entry = AppCatalog.MENU.first { it.key == "contacts" }
            val pkgIntent = entry.launchPackage?.let {
                packageManager.getLaunchIntentForPackage(it)
            }
            if (pkgIntent != null) startActivity(pkgIntent)
        }

        updateClock()
        tryStartLockTask()
        HiddenAppsManager(this).applyAll()
    }

    override fun onResume() {
        super.onResume()
        // Re-arm Lock Task in case the OS dropped it during pause (e.g. user
        // navigated to system Settings and came back).
        tryStartLockTask()
    }

    override fun updateClock() {
        val now = Calendar.getInstance()
        val hour24 = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)
        tvTime?.text = String.format(Locale.US, "%02d:%02d", hour24, minute)

        val dateFmt = SimpleDateFormat("EEE dd.MM.yyyy", Locale.US)
        tvDate?.text = dateFmt.format(now.time)
    }

    override fun onStart() {
        super.onStart()
        // sticky broadcast — registering yields the current battery state immediately.
        val sticky = registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        if (sticky != null) {
            val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (level >= 0 && scale > 0) {
                tvBatteryPct?.text = "${level * 100 / scale}%"
            }
        }
    }

    override fun onStop() {
        super.onStop()
        try { unregisterReceiver(batteryReceiver) } catch (_: Exception) {}
    }

    // ---------- Secret combo: long-press 5 → digits → Real mode --------------

    private val handler = Handler(Looper.getMainLooper())
    private var awaitingSecret = false
    private val secretBuf = StringBuilder()
    private val longPress5Trigger = Runnable {
        awaitingSecret = true
        // Reset window
        handler.removeCallbacks(secretTimeout)
        handler.postDelayed(secretTimeout, SECRET_WINDOW_MS)
        secretBuf.clear()
        // No visible UI feedback — stays plausibly indistinguishable from a stuck key.
    }
    private val secretTimeout = Runnable {
        awaitingSecret = false
        secretBuf.clear()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Secret-input mode swallows digits and watches for a match.
        if (awaitingSecret) {
            val d = digitOf(keyCode)
            if (d != null) {
                secretBuf.append(d)
                // Slide the timeout window forward on each press
                handler.removeCallbacks(secretTimeout)
                handler.postDelayed(secretTimeout, SECRET_WINDOW_MS)

                val secretMgr = PinManager.forSecret(this)
                if (secretMgr.isSet() && secretBuf.length >= secretMgr.pinLength()) {
                    val attempt = secretBuf.toString()
                    val ok = secretMgr.verify(attempt)
                    awaitingSecret = false
                    secretBuf.clear()
                    handler.removeCallbacks(secretTimeout)
                    if (ok) {
                        startActivity(Intent(this, RealLauncherActivity::class.java))
                    }
                }
                return true
            }
        }

        // Long-press '5' starts secret-input mode after 3 s.
        if (keyCode == KeyEvent.KEYCODE_5 && event?.repeatCount == 0 && !awaitingSecret) {
            handler.removeCallbacks(longPress5Trigger)
            handler.postDelayed(longPress5Trigger, LONG_PRESS_MS)
        }

        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                startActivity(Intent(this, MenuActivity::class.java)); return true
            }
            // BACK falls through to Base → clicks btn_right ("Contacts").
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_5) {
            handler.removeCallbacks(longPress5Trigger)
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun digitOf(keyCode: Int): Char? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> '0'; KeyEvent.KEYCODE_1 -> '1'
        KeyEvent.KEYCODE_2 -> '2'; KeyEvent.KEYCODE_3 -> '3'
        KeyEvent.KEYCODE_4 -> '4'; KeyEvent.KEYCODE_5 -> '5'
        KeyEvent.KEYCODE_6 -> '6'; KeyEvent.KEYCODE_7 -> '7'
        KeyEvent.KEYCODE_8 -> '8'; KeyEvent.KEYCODE_9 -> '9'
        else -> null
    }

    private fun tryStartLockTask() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (!dpm.isDeviceOwnerApp(packageName)) return
        val admin = DeviceAdmin.componentName(this)
        try {
            dpm.addPersistentPreferredActivity(
                admin,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                ComponentName(this, DisguiseActivity::class.java)
            )

            // Kill the system keyguard entirely so there's no PIN screen and
            // no swipe-down status bar gap between power-on and Armor.
            // Fails silently if user still has a PIN/pattern/password set —
            // they must remove it in Settings → Security → Screen lock → None
            // first.
            try {
                val ok = dpm.setKeyguardDisabled(admin, true)
                Log.i(TAG, "setKeyguardDisabled(true) returned $ok")
            } catch (e: Exception) {
                Log.w(TAG, "setKeyguardDisabled failed", e)
            }

            dpm.setLockTaskPackages(admin, AppCatalog.LOCK_TASK_WHITELIST)
            dpm.setLockTaskFeatures(admin, 0)
            startLockTask()

            // Kill the status-bar peek (swipe-down sliver). Requires Device
            // Owner + active Lock Task. Returns true if SystemUI accepted.
            try {
                val ok = dpm.setStatusBarDisabled(admin, true)
                Log.i(TAG, "setStatusBarDisabled(true) returned $ok")
            } catch (e: Exception) {
                Log.w(TAG, "setStatusBarDisabled failed", e)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Lock task start failed", e)
        }
    }

    companion object {
        private const val TAG = "ArmorDisguise"
        const val EXTRA_FROM_LOCK = "from_lock"
        private const val LONG_PRESS_MS = 3_000L
        private const val SECRET_WINDOW_MS = 5_000L
    }
}
