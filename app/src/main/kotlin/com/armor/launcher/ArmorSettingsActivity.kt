package com.armor.launcher

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import com.armor.launcher.domain.HiddenAppsManager
import com.armor.launcher.domain.PinManager
import com.armor.launcher.domain.PowerPrefs
import com.armor.launcher.platform.RealMode

class ArmorSettingsActivity : BaseDisguiseActivity() {

    private val rows = mutableListOf<View>()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_armor_settings)
        setTitleText("Settings")

        val container = findViewById<LinearLayout>(R.id.rows_container)

        // ----- Always visible: looks like a stock feature phone -----
        addRow(container, pinRowLabel()) {
            startActivity(Intent(this, PinSetupActivity::class.java))
        }
        if (PinManager.forPin(this).isSet()) {
            addRow(container, "Remove PIN") {
                PinManager.forPin(this).clearPin()
                recreate()
            }
        }
        val power = PowerPrefs(this)
        addRow(container, "Screen off after: ${PowerPrefs.label(power.offAfterMs)}") {
            power.offAfterMs = PowerPrefs.next(power.offAfterMs, PowerPrefs.OFF_OPTIONS)
            recreate()
        }

        // ----- Real-mode-only: revealed after 5×'*' + secret code -----
        if (RealMode.unlocked) {
            addRow(container, "Change Secret Code") {
                startActivity(Intent(this, PinSetupActivity::class.java).apply {
                    putExtra(PinSetupActivity.EXTRA_SECRET, true)
                })
            }
            addRow(container, "Reset Secret Code to default") {
                PinManager.forSecret(this).clearPin()
                // forSecret() re-seeds "1234" on next access.
                PinManager.forSecret(this)
                android.widget.Toast.makeText(
                    this, "Secret reset to 1234", android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            addRow(container, "Hidden Apps") {
                startActivity(Intent(this, HiddenAppsActivity::class.java))
            }
            addRow(container, "Hide recommended set") {
                val applied = HiddenAppsManager(this).hideRecommended()
                android.widget.Toast.makeText(
                    this,
                    "Hidden ${applied.size} package(s)",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
            addRow(container, "Open Real Mode") {
                startActivity(Intent(this, RealLauncherActivity::class.java))
            }
            addRow(container, "System Settings") {
                startActivity(Intent(Settings.ACTION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
            addRow(container, "Lock Real Mode now") {
                RealMode.lock()
                recreate()
            }
        }
        if (rows.isNotEmpty()) rows.first().post { rows.first().requestFocus() }

        bindSoftKey(R.id.btn_left, "") {}
        bindSoftKey(R.id.btn_right, "Back") { finish() }
    }

    private fun pinRowLabel(): String =
        if (PinManager.forPin(this).isSet()) "Change PIN" else "Set PIN"

    private fun addRow(container: LinearLayout, label: String, onClick: () -> Unit) {
        val tv = TextView(this).apply {
            text = label
            textSize = 18f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(24, 24, 24, 24)
            gravity = Gravity.CENTER_VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            setOnClickListener { onClick() }
            setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
                if (hasFocus) selected = rows.indexOf(v)
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        container.addView(tv)
        rows.add(tv)
    }
}
