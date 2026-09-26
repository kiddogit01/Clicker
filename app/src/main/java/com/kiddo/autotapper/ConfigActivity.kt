package com.kiddo.autotapper

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class ConfigActivity : AppCompatActivity() {

    private lateinit var etInterval: EditText
    private lateinit var btnLaunch: Button
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        etInterval = findViewById(R.id.etInterval)
        btnLaunch = findViewById(R.id.btnToggleTapper)
        tvStatus = findViewById(R.id.tvConfigStatus)
        btnLaunch.text = "🎯 Lancer la cible et les contrôles"

        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnLaunch.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "⚠️ Autorisation manquante, reviens à l'étape précédente.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val interval = etInterval.text.toString().toLongOrNull()?.coerceAtLeast(20L) ?: 50L
            val intent = Intent(this, OverlayService::class.java).apply {
                putExtra("interval", interval)
            }
            startService(intent)
            tvStatus.text = "Reviens sur l'écran d'accueil : une cible et une barre de contrôle sont apparues. Glisse la cible où tu veux taper, puis appuie sur Démarrer sur la barre."
        }
    }
}
