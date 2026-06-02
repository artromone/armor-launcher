package com.armor.launcher

import android.os.Bundle
import android.view.KeyEvent
import android.widget.ImageView
import android.widget.TextView

class StubActivity : BaseDisguiseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_stub)

        val label = intent.getStringExtra(MenuActivity.EXTRA_LABEL) ?: ""
        val iconRes = intent.getIntExtra(MenuActivity.EXTRA_ICON, 0)
        val message = intent.getStringExtra(MenuActivity.EXTRA_MESSAGE) ?: "No data"

        setTitleText(label)
        if (iconRes != 0) findViewById<ImageView>(R.id.stub_icon).setImageResource(iconRes)
        findViewById<TextView>(R.id.stub_message).text = message

        bindSoftKey(R.id.btn_left, "") {}
        bindSoftKey(R.id.btn_right, "Back") { finish() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) { finish(); return true }
        return super.onKeyDown(keyCode, event)
    }
}
