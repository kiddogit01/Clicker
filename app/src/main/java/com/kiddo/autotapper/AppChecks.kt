package com.kiddo.autotapper

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils

/**
 * Vérifications d'autorisations partagées entre MainActivity et ConfigActivity.
 */
object AppChecks {

    fun overlayGranted(ctx: Context): Boolean = Settings.canDrawOverlays(ctx)

    fun batteryIgnored(ctx: Context): Boolean {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(ctx.packageName)
    }

    fun accessibilityEnabled(ctx: Context): Boolean {
        val expectedComponent =
            "${ctx.packageName}/${TapAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabledServices)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expectedComponent, ignoreCase = true)) return true
        }
        return false
    }

    /** Les deux autorisations SANS lesquelles l'app ne peut pas fonctionner. */
    fun requiredGranted(ctx: Context): Boolean =
        overlayGranted(ctx) && accessibilityEnabled(ctx)
}
