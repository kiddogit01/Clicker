package com.kiddo.autotapper

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnBattery: Button
    private lateinit var btnContinue: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvProgressLabel: TextView
    private lateinit var progressBar: ProgressBar

    private lateinit var badges: Array<TextView>
    private lateinit var cards: Array<android.view.View>
    private var continueEnabled = false
    private var uiInitialized = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Retour dans l'app avec l'onboarding déjà validé → direct à la config.
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        if (prefs.getBoolean("onboarding_done", false) && AppChecks.requiredGranted(this)) {
            goToConfig(animate = false)
            return
        }

        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btnOverlayPermission)
        btnAccessibility = findViewById(R.id.btnAccessibilityPermission)
        btnBattery = findViewById(R.id.btnBatteryOptimization)
        btnContinue = findViewById(R.id.btnContinue)
        tvStatus = findViewById(R.id.tvStatus)
        tvProgressLabel = findViewById(R.id.tvProgressLabel)
        progressBar = findViewById(R.id.progressBar)
        badges = arrayOf(
            findViewById(R.id.tvStep1Number),
            findViewById(R.id.tvStep2Number),
            findViewById(R.id.tvStep3Number)
        )
        cards = arrayOf(
            findViewById(R.id.cardStep1),
            findViewById(R.id.cardStep2),
            findViewById(R.id.cardStep3)
        )

        listOf(btnOverlay, btnAccessibility, btnBattery, btnContinue).forEach {
            (it as Button).clearTint()
            AnimKit.press(it)
        }

        setupListeners()
        playEntranceAnimation()
    }

    override fun onResume() {
        super.onResume()
        if (!::btnContinue.isInitialized) return
        refreshUi()
    }

    private fun setupListeners() {
        btnOverlay.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnAccessibility.setOnClickListener {
            // API 30+ : ouvre DIRECTEMENT la page de nos services d'accessibilité
            // au lieu de la liste générale (beaucoup plus simple pour l'utilisateur).
            // L'action est testée à l'exécution (constante absente du SDK 34) avec
            // repli sur la liste classique si le chemin n'existe pas.
            val deepLink = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
                data = Uri.parse("package:$packageName")
            }
            try {
                if (Build.VERSION.SDK_INT >= 30) startActivity(deepLink)
                else startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
        btnBattery.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:$packageName")
                )
            )
        }
        btnContinue.setOnClickListener {
            getSharedPreferences(PREFS, MODE_PRIVATE)
                .edit().putBoolean("onboarding_done", true).apply()
            goToConfig(animate = true)
        }
    }

    private fun playEntranceAnimation() {
        AnimKit.popLogo(findViewById<ImageView>(R.id.imgLogo))
        AnimKit.fadeIn(findViewById(R.id.titleRow), 120)
        AnimKit.fadeIn(findViewById(R.id.tvSubtitle), 190)
        AnimKit.enter(findViewById(R.id.progressBar), 250, 24f)
        AnimKit.fadeIn(findViewById(R.id.tvProgressLabel), 330)
        cards.forEachIndexed { i, card -> AnimKit.enter(card, 340 + i * 110L) }
        // btnContinue n'est PAS animé à l'entrée : son alpha est piloté
        // uniquement par refreshUi() (aucun conflit d'animations).
        AnimKit.fadeIn(tvStatus, 700)
    }

    private fun refreshUi() {
        val overlay = AppChecks.overlayGranted(this)
        val a11y = AppChecks.accessibilityEnabled(this)
        val battery = AppChecks.batteryIgnored(this)

        refreshStep(overlay, btnOverlay, badges[0], cards[0], "1", !uiInitialized)
        refreshStep(a11y, btnAccessibility, badges[1], cards[1], "2", !uiInitialized)
        refreshStep(battery, btnBattery, badges[2], cards[2], "3", !uiInitialized)

        uiInitialized = true

        val done = (if (overlay) 1 else 0) + (if (a11y) 1 else 0)
        AnimKit.setProgress(progressBar, done)
        tvProgressLabel.text = if (done == 2) "Prêt ! Autorisations requises accordées."
        else "$done / 2 autorisations requises"

        val canContinue = overlay && a11y
        if (canContinue != continueEnabled) {
            continueEnabled = canContinue
            btnContinue.isEnabled = canContinue
            if (canContinue) {
                btnContinue.scaleX = 0.94f
                btnContinue.scaleY = 0.94f
            }
            btnContinue.animate().cancel()
            btnContinue.animate()
                .alpha(if (canContinue) 1f else 0.35f)
                .scaleX(1f).scaleY(1f)
                .setDuration(300)
                .setInterpolator(android.view.animation.OvershootInterpolator(1.8f))
                .start()
        }

        tvStatus.text = if (canContinue)
            "Tout est prêt → appuie sur Continuer."
        else
            "Complète les 2 autorisations requises pour continuer."
    }

    private fun refreshStep(
        done: Boolean,
        button: Button,
        badge: TextView,
        card: android.view.View,
        number: String,
        silent: Boolean
    ) {
        val wasDone = button.tag == "done"
        button.clearTint()
        if (done) {
            button.text = "✓ Activé"
            button.isEnabled = false
            button.setBackgroundResource(R.drawable.btn_done_bg)
            button.setTextColor(getColor(R.color.kiddo_green))
            badge.text = "✓"
            badge.setBackgroundResource(R.drawable.step_number_done_bg)
            badge.setTextColor(getColor(R.color.kiddo_bg))
            card.setBackgroundResource(R.drawable.card_done_bg)
            if (!wasDone && !silent) {
                AnimKit.pop(badge)
                card.animate().alpha(0.55f).setDuration(120)
                    .withEndAction {
                        card.animate().alpha(1f).setDuration(220).start()
                    }.start()
            }
            button.tag = "done"
        } else {
            button.text = "Activer"
            button.isEnabled = true
            button.setBackgroundResource(R.drawable.btn_pill_cyan)
            button.setTextColor(getColor(R.color.kiddo_bg))
            badge.text = number
            badge.setBackgroundResource(R.drawable.step_number_bg)
            badge.setTextColor(getColor(R.color.kiddo_white))
            card.setBackgroundResource(R.drawable.step_card_background)
            button.tag = null
        }
    }

    private fun goToConfig(animate: Boolean) {
        startActivity(Intent(this, ConfigActivity::class.java))
        if (animate) {
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
            finish()
            overridePendingTransition(R.anim.slide_in_right, R.anim.fade_out)
        } else {
            finish()
        }
    }

    companion object {
        const val PREFS = "kiddo_autotapper"
    }
}
