package com.kiddo.autotapper

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences("kiddo_autotapper", MODE_PRIVATE)
        val optionFinger = findViewById<LinearLayout>(R.id.optionFinger)
        val optionCircle = findViewById<LinearLayout>(R.id.optionCircle)
        val checkFinger = findViewById<TextView>(R.id.checkFinger)
        val checkCircle = findViewById<TextView>(R.id.checkCircle)

        fun refresh() {
            val style = prefs.getString("target_style", "finger")
            checkFinger.text = if (style == "finger") "●" else "○"
            checkFinger.setTextColor(getColor(if (style == "finger") R.color.kiddo_pink else R.color.kiddo_muted))
            checkCircle.text = if (style == "circle") "●" else "○"
            checkCircle.setTextColor(getColor(if (style == "circle") R.color.kiddo_pink else R.color.kiddo_muted))
        }

        optionFinger.setOnClickListener {
            prefs.edit().putString("target_style", "finger").apply()
            refresh()
        }
        optionCircle.setOnClickListener {
            prefs.edit().putString("target_style", "circle").apply()
            refresh()
        }
        refresh()
    }
}
