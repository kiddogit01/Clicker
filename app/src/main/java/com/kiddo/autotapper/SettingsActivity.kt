package com.kiddo.autotapper

import android.os.Bundle
import android.widget.Button
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        val optionFinger = findViewById<LinearLayout>(R.id.optionFinger)
        val optionCircle = findViewById<LinearLayout>(R.id.optionCircle)
        val checkFinger = findViewById<TextView>(R.id.checkFinger)
        val checkCircle = findViewById<TextView>(R.id.checkCircle)

        AnimKit.press(findViewById(R.id.btnBack))
        AnimKit.press(optionFinger, 0.97f)
        AnimKit.press(optionCircle, 0.97f)

        fun refresh() {
            val style = prefs.getString("target_style", "finger")
            val fingerOn = style == "finger"

            optionFinger.setBackgroundResource(
                if (fingerOn) R.drawable.card_selected_bg else R.drawable.step_card_background
            )
            optionCircle.setBackgroundResource(
                if (fingerOn) R.drawable.step_card_background else R.drawable.card_selected_bg
            )
            checkFinger.text = if (fingerOn) "● Sélectionné" else "○ Choisir"
            checkFinger.setTextColor(getColor(if (fingerOn) R.color.kiddo_cyan else R.color.kiddo_muted))
            checkCircle.text = if (fingerOn) "○ Choisir" else "● Sélectionné"
            checkCircle.setTextColor(getColor(if (fingerOn) R.color.kiddo_muted else R.color.kiddo_cyan))
        }

        optionFinger.setOnClickListener {
            prefs.edit().putString("target_style", "finger").apply()
            refresh()
            AnimKit.pop(checkFinger)
        }
        optionCircle.setOnClickListener {
            prefs.edit().putString("target_style", "circle").apply()
            refresh()
            AnimKit.pop(checkCircle)
        }
        refresh()

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.fade_in, R.anim.slide_out_left)
        }

        // Entrées en cascade
        AnimKit.enter(findViewById(R.id.topBar), 0, -24f)
        AnimKit.fadeIn(findViewById(R.id.tvSettingsDesc), 90)
        AnimKit.enter(optionFinger, 180)
        AnimKit.enter(optionCircle, 300)
    }
}
