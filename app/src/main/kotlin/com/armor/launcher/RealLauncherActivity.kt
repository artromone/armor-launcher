package com.armor.launcher

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Real-mode launcher — shows every launchable app on the device, including
 * the ones that are normally hidden.
 *
 * Entering this activity calls HiddenAppsManager.showAll() so the hidden
 * apps become launchable for this session. Leaving (Back) re-applies the
 * stored hidden state so the disguise returns intact.
 */
class RealLauncherActivity : BaseDisguiseActivity() {

    private val hidden by lazy { HiddenAppsManager(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_apps)  // reuse the scrollable list layout
        setTitleText("All Apps")

        // Temporarily reveal hidden apps so we (and the user) can launch them.
        Dpm.asOwner(this, "unhide for RealLauncher") { dpm, admin ->
            for (pkg in hidden.hiddenPackages()) {
                try { dpm.setApplicationHidden(admin, pkg, false) } catch (_: Exception) {}
            }
        }

        val container = findViewById<LinearLayout>(R.id.list_container)
        val inflater = LayoutInflater.from(this)
        val items = collectApps()

        for (item in items) {
            val row = inflater.inflate(R.layout.item_app_toggle, container, false)
            row.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
            row.findViewById<TextView>(R.id.app_label).text = item.label
            row.findViewById<TextView>(R.id.app_pkg).text = item.pkg
            row.findViewById<TextView>(R.id.app_status).text = ""

            row.isFocusable = true
            row.isFocusableInTouchMode = false
            row.setOnClickListener { launchApp(item.pkg) }
            row.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
            }
            container.addView(row)
        }

        bindSoftKey(R.id.btn_left, "Manage") {
            startActivity(Intent(this, HiddenAppsActivity::class.java))
        }
        bindSoftKey(R.id.btn_right, "Back") { finish() }

        if (container.childCount > 0) {
            container.getChildAt(0).post { container.getChildAt(0).requestFocus() }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Re-hide everything per the saved set when leaving Real mode.
        hidden.applyAll()
    }

    private data class AppItem(
        val pkg: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    private fun collectApps(): List<AppItem> {
        val pm = packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val pkgs = pm.queryIntentActivities(mainIntent, 0)
            .map { it.activityInfo.packageName }
            .distinct()
        return pkgs.mapNotNull { pkg ->
            if (pkg == packageName) return@mapNotNull null
            try {
                val info = pm.getApplicationInfo(pkg, 0)
                AppItem(
                    pkg = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                )
            } catch (_: Exception) { null }
        }.sortedBy { it.label.lowercase() }
    }

    private fun launchApp(pkg: String) {
        val intent = packageManager.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }
}
