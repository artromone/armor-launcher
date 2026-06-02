package com.armor.launcher

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.WindowManager
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DisguiseActivity : BaseDisguiseActivity() {

    private var tvTime: TextView? = null
    private var tvAmPm: TextView? = null
    private var tvDate: TextView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over keyguard / turn on screen so we cover system UI as early
        // as possible when coming back from sleep or after boot.
        window.addFlags(
            WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        )
        setContentView(R.layout.activity_disguise)
        tvTime = findViewById(R.id.tv_time)
        tvAmPm = findViewById(R.id.tv_ampm)
        tvDate = findViewById(R.id.tv_date)

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
        val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
        val minute = now.get(Calendar.MINUTE)
        tvTime?.text = String.format(Locale.US, "%02d:%02d", hour12, minute)
        tvAmPm?.text = if (hour24 < 12) "AM" else "PM"

        val dateFmt = SimpleDateFormat("EEE dd/MM/yyyy", Locale.US)
        tvDate?.text = dateFmt.format(now.time)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                startActivity(Intent(this, MenuActivity::class.java)); return true
            }
            KeyEvent.KEYCODE_BACK -> return true // swallow on home
        }
        return super.onKeyDown(keyCode, event)
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
        } catch (e: Exception) {
            Log.w(TAG, "Lock task start failed", e)
        }
    }

    companion object { private const val TAG = "ArmorDisguise" }
}
