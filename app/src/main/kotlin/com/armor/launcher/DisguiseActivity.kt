package com.armor.launcher

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import com.armor.launcher.domain.AppCatalog
import com.armor.launcher.domain.HiddenAppsManager
import com.armor.launcher.domain.PinManager
import com.armor.launcher.platform.Dpm
import com.armor.launcher.platform.Intents
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
            startActivity(Intents.clearTopHome(this, LockActivity::class.java))
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

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                startActivity(Intent(this, MenuActivity::class.java)); return true
            }
            // BACK falls through to Base → clicks btn_right ("Contacts").
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun tryStartLockTask() {
        Dpm.asOwner(this, "tryStartLockTask") { dpm, admin ->
            dpm.addPersistentPreferredActivity(
                admin,
                IntentFilter(Intent.ACTION_MAIN).apply {
                    addCategory(Intent.CATEGORY_HOME)
                    addCategory(Intent.CATEGORY_DEFAULT)
                },
                ComponentName(this, DisguiseActivity::class.java)
            )

            // Kill the system keyguard so there's no PIN screen and no
            // swipe-down status bar gap between power-on and Armor. Fails
            // silently if the user still has a PIN/pattern/password set —
            // they must remove it in Settings → Security → Screen lock first.
            runCatching { dpm.setKeyguardDisabled(admin, true) }
                .onFailure { Log.w(TAG, "setKeyguardDisabled failed", it) }

            dpm.setLockTaskPackages(admin, AppCatalog.LOCK_TASK_WHITELIST)
            dpm.setLockTaskFeatures(admin, 0)
            startLockTask()

            // Kill the status-bar peek (swipe-down sliver). Requires Device
            // Owner + active Lock Task.
            runCatching { dpm.setStatusBarDisabled(admin, true) }
                .onFailure { Log.w(TAG, "setStatusBarDisabled failed", it) }
        }
    }

    companion object {
        private const val TAG = "ArmorDisguise"
        const val EXTRA_FROM_LOCK = "from_lock"
    }
}
