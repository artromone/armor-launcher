package com.armor.launcher

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

/**
 * Manages the hidden-apps allowlist. Lists every launchable user app
 * (plus our currently-tracked hidden ones, since DPM-hidden apps don't
 * appear in queryIntentActivities) and lets the user toggle each.
 */
class HiddenAppsActivity : BaseDisguiseActivity() {

    private val manager by lazy { HiddenAppsManager(this) }

    private data class AppItem(
        val pkg: String,
        val label: String,
        val icon: android.graphics.drawable.Drawable?,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_apps)
        setTitleText("Hidden Apps")

        val items = collectApps()
        val container = findViewById<LinearLayout>(R.id.list_container)
        val inflater = LayoutInflater.from(this)

        for (item in items) {
            val row = inflater.inflate(R.layout.item_app_toggle, container, false)
            row.findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
            row.findViewById<TextView>(R.id.app_label).text = item.label
            row.findViewById<TextView>(R.id.app_pkg).text = item.pkg
            val status = row.findViewById<TextView>(R.id.app_status)
            updateStatus(status, manager.isHidden(item.pkg))

            row.isFocusable = true
            row.isFocusableInTouchMode = false
            row.setOnClickListener {
                val newState = !manager.isHidden(item.pkg)
                val ok = manager.setHidden(item.pkg, newState)
                if (!ok) {
                    Toast.makeText(this,
                        "Cannot ${if (newState) "hide" else "show"} ${item.label}",
                        Toast.LENGTH_SHORT).show()
                }
                updateStatus(status, manager.isHidden(item.pkg))
            }
            row.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
            }
            container.addView(row)
        }

        bindSoftKey(R.id.btn_left, "Show all") {
            manager.showAll()
            recreate()
        }
        bindSoftKey(R.id.btn_right, "Back") { finish() }

        if (container.childCount > 0) {
            container.getChildAt(0).post { container.getChildAt(0).requestFocus() }
        }
    }

    private fun updateStatus(view: TextView, hidden: Boolean) {
        view.text = if (hidden) "HIDDEN" else "visible"
        view.setTextColor(if (hidden) 0xFFE06060.toInt() else 0xFF888888.toInt())
    }

    private fun collectApps(): List<AppItem> {
        val pm = packageManager
        // visible launchable apps
        val mainIntent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val visible = pm.queryIntentActivities(mainIntent, 0)
            .map { ri -> ri.activityInfo.packageName }
            .toSet()

        // plus packages we know we hid (DPM hides them from queryIntentActivities)
        val all = (visible + manager.hiddenPackages()).distinct()

        return all.mapNotNull { pkg ->
            if (pkg == packageName) return@mapNotNull null  // exclude self
            try {
                val info = pm.getApplicationInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                AppItem(
                    pkg = pkg,
                    label = pm.getApplicationLabel(info).toString(),
                    icon = runCatching { pm.getApplicationIcon(info) }.getOrNull()
                )
            } catch (_: Exception) { null }
        }.sortedBy { it.label.lowercase() }
    }
}
