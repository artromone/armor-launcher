package com.armor.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.armor.launcher.domain.HiddenAppsManager
import com.armor.launcher.domain.MruTracker
import com.armor.launcher.domain.PinnedAppsManager
import com.armor.launcher.platform.InstalledApp
import com.armor.launcher.platform.InstalledApps

/**
 * Disguise app drawer. Lists every launchable installed app minus the ones
 * the user has hidden, sorted by MRU (most-recently-launched first), with
 * alphabetical fallback for ties.
 *
 * Interaction:
 *   • Up/Down — D-pad focus traversal (built-in for focusable views in a
 *     vertical LinearLayout).
 *   • Tap / Center — launch the focused app, bump its MRU timestamp.
 *   • Long-press Center — toggle pinned state for the focused app. Pinned
 *     apps appear on the disguise home screen (see DisguiseActivity).
 *   • Back / soft-right — return to home.
 */
class MenuActivity : BaseDisguiseActivity() {

    private lateinit var pinned: PinnedAppsManager
    private lateinit var mru: MruTracker

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        setTitleText("Menu")

        pinned = PinnedAppsManager(this)
        mru = MruTracker(this)

        val container = findViewById<LinearLayout>(R.id.list_container)
        val inflater = LayoutInflater.from(this)

        val hidden = HiddenAppsManager(this).hiddenPackages()
        val items = InstalledApps.list(this)
            .filter { it.pkg !in hidden }
            .sortedWith(
                compareByDescending<InstalledApp> { mru.lastUsed(it.pkg) }
                    .thenBy { it.label.lowercase() }
            )

        for (item in items) {
            container.addView(buildRow(inflater, container, item))
        }

        bindSoftKey(R.id.btn_left, "Select") {
            container.findFocus()?.performClick()
        }
        bindSoftKey(R.id.btn_right, "Back") { finish() }

        if (container.childCount > 0) {
            container.getChildAt(0).post { container.getChildAt(0).requestFocus() }
        }
    }

    private fun buildRow(
        inflater: LayoutInflater,
        container: LinearLayout,
        item: InstalledApp,
    ) = inflater.inflate(R.layout.item_app_toggle, container, false).apply {
        findViewById<ImageView>(R.id.app_icon).setImageDrawable(item.icon)
        findViewById<TextView>(R.id.app_label).text = item.label
        findViewById<TextView>(R.id.app_pkg).text = item.pkg
        val status = findViewById<TextView>(R.id.app_status)
        status.text = if (pinned.isPinned(item.pkg)) "★" else ""

        isFocusable = true
        isFocusableInTouchMode = true
        setOnClickListener { launch(item) }
        setOnLongClickListener { togglePin(item, status); true }
        setOnFocusChangeListener { v, hasFocus ->
            v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
        }
    }

    private fun launch(item: InstalledApp) {
        if (InstalledApps.launch(this, item.pkg)) {
            mru.recordLaunch(item.pkg)
        } else {
            Toast.makeText(this, "Can't open ${item.label}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun togglePin(item: InstalledApp, statusView: TextView) {
        val msg = when (pinned.toggle(item.pkg)) {
            PinnedAppsManager.ToggleResult.PINNED -> {
                statusView.text = "★"; "Pinned ${item.label}"
            }
            PinnedAppsManager.ToggleResult.UNPINNED -> {
                statusView.text = ""; "Unpinned ${item.label}"
            }
            PinnedAppsManager.ToggleResult.LIMIT_REACHED ->
                "Pin limit (${PinnedAppsManager.LIMIT}) reached"
        }
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
