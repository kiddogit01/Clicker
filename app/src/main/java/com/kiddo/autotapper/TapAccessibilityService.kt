package com.kiddo.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité : c'est le SEUL moyen autorisé par Android pour simuler
 * un appui (tap) dans une autre application.
 *
 * Point important documenté par Android lui-même : dispatchGesture() annule
 * TOUT geste en cours au moment où il est appelé — y compris un geste de
 * l'utilisateur (glisser la barre de contrôle, par exemple). C'est pour ça
 * qu'on met le tapotage en pause via setUserInteracting() pendant que
 * l'utilisateur touche un des éléments flottants : aucun tap n'est envoyé
 * pendant ce court instant, donc son geste n'est jamais coupé.
 */
class TapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTapping = false
    private var isDispatching = false
    private var userInteracting = false
    private var tapX = 0f
    private var tapY = 0f
    private var intervalMs = 100L

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!isTapping) return
            if (!isDispatching && !userInteracting) performTap(tapX, tapY)
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
        // En dessous de 50ms, ça devient instable sur pas mal d'apps (dont TikTok) :
        // 50ms mini, 100ms recommandé, comme la plupart des auto-clickers du marché.
        intervalMs = interval.coerceAtLeast(MIN_INTERVAL_MS)
        if (isTapping) return
        isTapping = true
        handler.post(tapRunnable)
    }

    fun stopTapping() {
        isTapping = false
        isDispatching = false
        handler.removeCallbacks(tapRunnable)
    }

    fun updatePosition(x: Float, y: Float) {
        tapX = x
        tapY = y
    }

    /** À appeler pendant que l'utilisateur touche un élément flottant (drag en cours). */
    fun setUserInteracting(interacting: Boolean) {
        userInteracting = interacting
    }

    fun isRunning(): Boolean = isTapping

    private fun performTap(x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0L, 30L)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()

        isDispatching = true
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                isDispatching = false
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                isDispatching = false
            }
        }, null)
        if (!accepted) isDispatching = false
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    companion object {
        const val MIN_INTERVAL_MS = 50L
        const val RECOMMENDED_INTERVAL_MS = 100L
        var instance: TapAccessibilityService? = null
    }
}
