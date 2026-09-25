package com.kiddo.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité : c'est le SEUL moyen autorisé par Android pour simuler
 * un appui (tap) dans une autre application. Une simple fenêtre flottante ne peut
 * pas transmettre de tap à l'app en dessous — c'est une restriction de sécurité
 * du système, pas une limite de cette app.
 */
class TapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTapping = false
    private var tapX = 0f
    private var tapY = 0f
    private var intervalMs = 50L

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!isTapping) return
            performTap(tapX, tapY)
            handler.postDelayed(this, intervalMs)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        stopTapping()
        instance = null
    }

    fun startTapping(x: Float, y: Float, interval: Long) {
        tapX = x
        tapY = y
        // En dessous de ~20ms, Android fusionne ou ignore les gestes : ce n'est pas fiable.
        intervalMs = interval.coerceAtLeast(MIN_INTERVAL_MS)
        if (isTapping) return
        isTapping = true
        // Premier geste immédiatement, sans attendre un cycle d'intervalle.
        performTap(tapX, tapY)
        handler.removeCallbacks(tapRunnable)
        handler.postDelayed(tapRunnable, intervalMs)
    }

    fun stopTapping() {
        isTapping = false
        handler.removeCallbacks(tapRunnable)
    }

    fun updatePosition(x: Float, y: Float) {
        tapX = x
        tapY = y
    }

    fun isRunning(): Boolean = isTapping

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 40L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Non utilisé : ce service sert uniquement à dispatcher des gestes.
    }

    override fun onInterrupt() {}

    companion object {
        const val MIN_INTERVAL_MS = 20L
        var instance: TapAccessibilityService? = null
    }
}
