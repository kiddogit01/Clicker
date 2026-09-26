package com.kiddo.autotapper

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var btnOverlay: Button
    private lateinit var btnAccessibility: Button
    private lateinit var btnBattery: Button
    private lateinit var btnContinue: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStep1: TextView
    private lateinit var tvStep2: TextView
    private lateinit var tvStep3: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Si tout est déjà activé (ouvertures suivantes de l'app), on saute
        // directement cet écran — pas besoin de re-cliquer sur "Continuer".
        if (allPermissionsGranted()) {
            goToConfig()
            return
        }

        setContentView(R.layout.activity_main)

        btnOverlay = findViewById(R.id.btnOverlayPermission)
        btnAccessibility = findViewById(R.id.btnAccessibilityPermission)
        btnBattery = findViewById(R.id.btnBatteryOptimization)
        btnContinue = findViewById(R.id.btnContinue)
        tvStatus = findViewById(R.id.tvStatus)
        tvStep1 = findViewById(R.id.tvStep1Number)
        tvStep2 = findViewById(R.id.tvStep2Number)
        tvStep3 = findViewById(R.id.tvStep3Number)

        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            }
        }

        btnOverlay.setOnClickListener {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        btnAccessibility.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        btnBattery.setOnClickListener {
            startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
        }
        btnContinue.setOnClickListener { goToConfig() }
    }

    override fun onResume() {
        super.onResume()
        if (!::btnContinue.isInitialized) return // écran déjà quitté (redirection depuis onCreate)

        if (allPermissionsGranted()) {
            goToConfig()
            return
        }

        refreshStepUi(overlayGranted(), btnOverlay, tvStep1)
        refreshStepUi(isAccessibilityServiceEnabled(), btnAccessibility, tvStep2)
        refreshStepUi(batteryOptimizationIgnored(), btnBattery, tvStep3)
        tvStatus.text = "Complète les étapes ci-dessus pour continuer."
    }

    private fun goToConfig() {
        startActivity(Intent(this, ConfigActivity::class.java))
        finish()
    }

    private fun allPermissionsGranted(): Boolean =
        overlayGranted() && isAccessibilityServiceEnabled() && batteryOptimizationIgnored()

    private fun refreshStepUi(done: Boolean, button: Button, numberView: TextView) {
        if (done) {
            button.text = "✓ Activé"
            button.isEnabled = false
            button.backgroundTintList = null
            button.setBackgroundResource(R.drawable.btn_done_bg)
            button.setTextColor(ContextCompat.getColor(this, R.color.kiddo_white))
            numberView.text = "✓"
            numberView.setBackgroundResource(R.drawable.step_number_done_bg)
        } else {
            button.text = "Activer"
            button.isEnabled = true
            button.backgroundTintList = null
            button.setBackgroundColor(ContextCompat.getColor(this, R.color.kiddo_cyan))
            button.setTextColor(ContextCompat.getColor(this, R.color.kiddo_bg))
            numberView.setBackgroundResource(R.drawable.step_number_bg)
        }
    }

    private fun overlayGranted(): Boolean = Settings.canDrawOverlays(this)

    private fun batteryOptimizationIgnored(): Boolean {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedComponent = "$packageName/${TapAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }
}
