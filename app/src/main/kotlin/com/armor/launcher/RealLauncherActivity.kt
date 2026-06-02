package com.armor.launcher

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.widget.TextView

class RealLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply {
            text = "Real mode (iteration 1 stub)"
            textSize = 22f
            setPadding(48, 96, 48, 48)
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.WHITE)
        })
    }
}
