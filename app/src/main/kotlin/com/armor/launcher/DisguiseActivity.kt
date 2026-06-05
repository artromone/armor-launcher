package com.armor.launcher

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.armor.launcher.domain.AppCatalog
import com.armor.launcher.domain.HiddenAppsManager
import com.armor.launcher.domain.MruTracker
import com.armor.launcher.domain.PinManager
import com.armor.launcher.domain.PinnedAppsManager
import com.armor.launcher.platform.Dpm
import com.armor.launcher.platform.InstalledApps
import com.armor.launcher.platform.Intents
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DisguiseActivity : BaseDisguiseActivity() {

    private var tvTime: TextView? = null
    private var tvDate: TextView? = null

    private lateinit var pinned: PinnedAppsManager
    private lateinit var mru: MruTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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
        pinned = PinnedAppsManager(this)
        mru = MruTracker(this)

        bindSoftKey(R.id.btn_left, "Menu") {
            startActivity(Intent(this, MenuActivity::class.java))
        }
        bindSoftKey(R.id.btn_right, "Settings") {
            startActivity(Intent(this, ArmorSettingsActivity::class.java))
        }

        updateClock()
        HiddenAppsManager(this).applyAll()
        tryStartLockTask()
        rebuildPinnedList()
    }

    override fun onResume() {
        super.onResume()
        tryStartLockTask()
        rebuildPinnedList()
    }

    private fun rebuildPinnedList() {
        val container = findViewById<LinearLayout>(R.id.pinned_list) ?: return
        val emptyHint = findViewById<TextView>(R.id.pinned_empty)
        container.removeAllViews()

        val installed = InstalledApps.list(this).associateBy { it.pkg }
        pinned.prune(installed.keys)
        val items = pinned.list().mapNotNull { installed[it] }

        if (items.isEmpty()) {
            emptyHint?.visibility = View.VISIBLE
            return
        }
        emptyHint?.visibility = View.GONE

        val inflater = LayoutInflater.from(this)
        for (item in items) {
            val cell = inflater.inflate(R.layout.item_pinned_app, container, false) as ImageView
            cell.setImageDrawable(item.icon)
            cell.contentDescription = item.label
            cell.isFocusable = true
            cell.isFocusableInTouchMode = true
            cell.setOnClickListener {
                if (InstalledApps.launch(this, item.pkg)) mru.recordLaunch(item.pkg)
            }
            cell.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
            }
            container.addView(cell)
        }
        container.getChildAt(0).post { container.getChildAt(0).requestFocus() }
    }

    override fun updateClock() {
        // Status-bar tv_clock is handled by super; we additionally render the
        // big home-screen clock + date below it.
        super.updateClock()
        val now = Calendar.getInstance()
        tvTime?.text = String.format(
            Locale.US, "%02d:%02d",
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE)
        )
        tvDate?.text = SimpleDateFormat("EEE dd.MM.yyyy", Locale.US).format(now.time)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // D-pad center / Enter: if a pinned cell has focus, let it click;
        // otherwise fall through to opening Menu. Left/Right navigate
        // naturally between the focusable pinned icons.
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            val focus = currentFocus
            if (focus != null && focus.id != R.id.btn_left && focus.id != R.id.btn_right) {
                focus.performClick(); return true
            }
            startActivity(Intent(this, MenuActivity::class.java)); return true
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

            runCatching { dpm.setKeyguardDisabled(admin, true) }
                .onFailure { Log.w(TAG, "setKeyguardDisabled failed", it) }

            // Whitelist must include every pinned package — otherwise launching
            // a pinned app while in Lock Task throws SecurityException.
            val whitelist = (AppCatalog.LOCK_TASK_WHITELIST.toSet() + pinned.list()).toTypedArray()
            dpm.setLockTaskPackages(admin, whitelist)
            dpm.setLockTaskFeatures(admin, 0)
            startLockTask()

            runCatching { dpm.setStatusBarDisabled(admin, true) }
                .onFailure { Log.w(TAG, "setStatusBarDisabled failed", it) }
        }
    }

    companion object {
        private const val TAG = "ArmorDisguise"
        const val EXTRA_FROM_LOCK = "from_lock"
    }
}
