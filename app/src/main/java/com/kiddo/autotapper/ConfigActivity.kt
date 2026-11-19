package com.kiddo.autotapper

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ConfigActivity : AppCompatActivity() {

    private lateinit var seekInterval: SeekBar
    private lateinit var tvIntervalValue: TextView
    private lateinit var tvBatteryTitle: TextView
    private lateinit var tvBatteryDesc: TextView
    private lateinit var btnBattery: Button
    private lateinit var tvConfigStatus: TextView

    private lateinit var chips: Map<TextView, Int> // vue -> valeur ms

    private val prefs get() = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        seekInterval = findViewById(R.id.seekInterval)
        tvIntervalValue = findViewById(R.id.tvIntervalValue)
        tvBatteryTitle = findViewById(R.id.tvBatteryTitle)
        tvBatteryDesc = findViewById(R.id.tvBatteryDesc)
        btnBattery = findViewById(R.id.btnBattery)
        tvConfigStatus = findViewById(R.id.tvConfigStatus)

        listOf<Button>(findViewById(R.id.btnToggleTapper), btnBattery, findViewById(R.id.btnSettings))
            .forEach { it.clearTint(); AnimKit.press(it) }

        // --- Interval : valeur mémorisée (plus besoin de re-taper à chaque lancement) ---
        val savedMs = prefs.getLong("interval_ms", 100L).toInt().coerceIn(MIN_MS, MAX_MS)
        seekInterval.progress = savedMs - MIN_MS

        chips = mapOf(
            findViewById<TextView>(R.id.chip50) to 50,
            findViewById<TextView>(R.id.chip100) to 100,
            findViewById<TextView>(R.id.chip250) to 250,
            findViewById<TextView>(R.id.chip500) to 500,
            findViewById<TextView>(R.id.chip1000) to 1000
        )
        chips.forEach { (view, value) ->
            AnimKit.press(view, 0.93f)
            view.setOnClickListener {
                seekInterval.progress = value - MIN_MS
                syncIntervalUi(pop = true)
            }
        }

        seekInterval.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                syncIntervalUi(pop = false)
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {
                syncIntervalUi(pop = true)
            }
        })
        syncIntervalUi(pop = false)

        // ===== Suivez-nous (liens de gamelvup.com) =====
        setupSocialLinks()

        btnBattery.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    android.net.Uri.parse("package:$packageName")
                )
            )
        }
        findViewById<Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        }

        findViewById<Button>(R.id.btnToggleTapper).setOnClickListener { launchOverlays() }

        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        refreshBatteryCard()
    }

    private fun currentIntervalMs(): Int = seekInterval.progress + MIN_MS

    private fun syncIntervalUi(pop: Boolean) {
        val value = currentIntervalMs()
        tvIntervalValue.text = "$value ms"
        prefs.edit().putLong("interval_ms", value.toLong()).apply()
        chips.forEach { (view, v) ->
            val selected = v == value
            view.isSelected = selected
            view.setTextColor(getColor(if (selected) R.color.kiddo_bg else R.color.kiddo_white))
        }
        if (pop) AnimKit.pop(tvIntervalValue, 0.86f)
    }

    private fun refreshBatteryCard() {
        if (AppChecks.batteryIgnored(this)) {
            tvBatteryTitle.text = "Batterie : protégée ✓"
            tvBatteryTitle.setTextColor(getColor(R.color.kiddo_green))
            tvBatteryDesc.text = "La bulle ne sera pas arrêtée en arrière-plan"
            btnBattery.visibility = android.view.View.GONE
        } else {
            tvBatteryTitle.text = "Batterie : non protégée"
            tvBatteryTitle.setTextColor(getColor(R.color.kiddo_yellow))
            tvBatteryDesc.text = "L'OS peut arrêter la bulle en arrière-plan"
            btnBattery.visibility = android.view.View.VISIBLE
        }
    }

    private fun launchOverlays() {
        if (!AppChecks.overlayGranted(this)) {
            toast("⚠️ Autorisation manquante, reviens à l'étape précédente.")
            return
        }
        if (!AppChecks.accessibilityEnabled(this)) {
            toast("⚠️ Active d'abord le service d'accessibilité (étape 2).")
            return
        }

        val interval = currentIntervalMs().toLong()
        val intent = Intent(this, OverlayService::class.java).apply {
            putExtra("interval", interval)
        }
        startService(intent)

        tvConfigStatus.text =
            "Cible et contrôles lancés ! Reviens sur l'écran d'accueil : " +
                "glisse la cible où tu veux taper, puis appuie sur Démarrer."
        AnimKit.pop(findViewById(R.id.btnToggleTapper), 0.95f)
    }

    private fun setupSocialLinks() {
        val links = mapOf(
            R.id.socialTikTok to "https://www.tiktok.com/@gamelvup",
            R.id.socialYouTube to "https://youtube.com/@gamelvup",
            R.id.socialFacebook to "https://www.facebook.com/share/1J7gQ8uUoX/",
            R.id.socialWhatsApp to "https://whatsapp.com/channel/0029VbDwIGTDp2Q1EuJ0131j"
        )
        links.forEach { (viewId, url) ->
            val view = findViewById<LinearLayout>(viewId)
            AnimKit.press(view, 0.94f)
            view.setOnClickListener { openUrl(url) }
        }
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            toast("⚠️ Impossible d'ouvrir le lien")
        }
    }

    private fun playEntranceAnimation() {
        AnimKit.enter(findViewById(R.id.topBar), 0, -24f)
        AnimKit.enter(findViewById(R.id.cardInterval), 110)
        AnimKit.enter(findViewById(R.id.cardBattery), 230)
        AnimKit.enter(findViewById(R.id.btnToggleTapper), 350, 32f)
        AnimKit.fadeIn(tvConfigStatus, 450)
        AnimKit.fadeIn(findViewById(R.id.btnSettings), 520)
        AnimKit.fadeIn(findViewById(R.id.tvIntervalValue), 260)
        AnimKit.fadeIn(findViewById(R.id.suivezTitle), 580)
        AnimKit.enter(findViewById(R.id.suivezRow), 640, 28f)
    }

    private fun toast(msg: String) =
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_LONG).show()

    companion object {
        const val MIN_MS = 50
        const val MAX_MS = 1000
    }
}
