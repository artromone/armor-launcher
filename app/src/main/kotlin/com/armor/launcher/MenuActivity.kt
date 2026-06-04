package com.armor.launcher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.GridLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.armor.launcher.domain.AppCatalog
import com.armor.launcher.domain.AppEntry

class MenuActivity : BaseDisguiseActivity() {

    private val cells = mutableListOf<View>()
    private var selected = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        setTitleText("Menu")

        val grid = findViewById<GridLayout>(R.id.grid)
        AppCatalog.MENU.forEachIndexed { i, entry ->
            val cell = LayoutInflater.from(this).inflate(R.layout.item_menu_cell, grid, false)
            cell.findViewById<ImageView>(R.id.icon).setImageResource(entry.iconRes)
            cell.findViewById<TextView>(R.id.label).text = entry.label

            // Selection is driven by *focus*. Android's D-pad navigation moves
            // focus between focusable views; we just listen for those changes
            // and update both the highlight and our `selected` index.
            cell.isFocusable = true
            // Keep focus across touch-mode switches. ViewRootImpl flips into
            // touch-mode the instant any touch reaches the window (before our
            // dispatchTouchEvent can swallow it) and would otherwise clear
            // focus from views with focusableInTouchMode=false — meaning a
            // stray finger on the screen made the D-pad highlight disappear.
            cell.isFocusableInTouchMode = true
            cell.setOnClickListener { open(entry) }
            cell.setOnFocusChangeListener { v, hasFocus ->
                v.setBackgroundResource(if (hasFocus) R.drawable.bg_item_selected else 0)
                if (hasFocus) selected = cells.indexOf(v)
            }

            val params = GridLayout.LayoutParams().apply {
                width = 0
                height = GridLayout.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(i % 3, 1f)
                rowSpec = GridLayout.spec(i / 3, 1f)
            }
            grid.addView(cell, params)
            cells.add(cell)
        }

        // Initial focus on first cell (also paints its highlight).
        cells.first().post { cells.first().requestFocus() }

        bindSoftKey(R.id.btn_left, "Select") { open(AppCatalog.MENU[selected]) }
        bindSoftKey(R.id.btn_right, "Back") { finish() }
    }

    private fun open(entry: AppEntry) {
        try {
            when {
                entry.systemSettingsAction != null -> {
                    startActivity(Intent(entry.systemSettingsAction).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                entry.launchPackage != null -> {
                    val intent = packageManager.getLaunchIntentForPackage(entry.launchPackage)
                    if (intent != null) {
                        startActivity(intent)
                    } else {
                        openStub(entry, "App not installed:\n${entry.launchPackage}")
                    }
                }
                entry.launchClass != null -> {
                    startActivity(Intent(this, entry.launchClass).apply {
                        putExtra(EXTRA_KEY, entry.key)
                        putExtra(EXTRA_LABEL, entry.label)
                        putExtra(EXTRA_ICON, entry.iconRes)
                    })
                }
                else -> openStub(entry, "No data")
            }
        } catch (e: ActivityNotFoundException) {
            openStub(entry, "Cannot open")
        } catch (e: SecurityException) {
            Toast.makeText(this, "Blocked by Lock Task: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun openStub(entry: AppEntry, message: String) {
        startActivity(Intent(this, StubActivity::class.java).apply {
            putExtra(EXTRA_KEY, entry.key)
            putExtra(EXTRA_LABEL, entry.label)
            putExtra(EXTRA_ICON, entry.iconRes)
            putExtra(EXTRA_MESSAGE, message)
        })
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Let Android focus navigation handle D-pad arrows; we only intercept
        // BACK and pass everything else (including STAR for the panic combo).
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }

    companion object {
        const val EXTRA_KEY = "key"
        const val EXTRA_LABEL = "label"
        const val EXTRA_ICON = "icon"
        const val EXTRA_MESSAGE = "message"
    }
}
