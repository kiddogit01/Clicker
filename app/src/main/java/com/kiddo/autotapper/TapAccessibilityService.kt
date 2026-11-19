package com.kiddo.autotapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent

/**
 * Service d'accessibilité : c'est le SEUL moyen autorisé par Android pour simuler
 * un appui (tap) dans une autre application.
 *
 * Point important documenté par Android lui-même : dispatchGesture() annule
 * TOUT geste en cours au moment où il est appelé — y compris un geste de
 * l'utilisateur (glisser la barre de contrôle). C'est pour ça qu'on met le
 * tapotage en pause via setUserInteracting() pendant que l'utilisateur touche
 * un des éléments flottants : aucun tap n'est envoyé pendant ce court instant,
 * donc son geste n'est jamais coupé.
 *
 * Robustesse : si dispatchGesture ne rappelle jamais (bug OS rare), un
 * watchdog remet isDispatching à false après 1,5 s — les taps repartent tout
 * seuls au lieu de se figer définitivement.
 */
class TapAccessibilityService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var isTapping = false
    private var isDispatching = false
    private var dispatchStartedAt = 0L
    private var userInteracting = false
    private var tapX = 0f
    private var tapY = 0f
    private var intervalMs = 100L

    /** Notifie la barre flottante que l'état démarré/arrêté a changé. */
    var onRunningChanged: ((Boolean) -> Unit)? = null

    private val tapRunnable = object : Runnable {
        override fun run() {
            if (!isTapping) return
            if (isDispatching &&
                SystemClock.uptimeMillis() - dispatchStartedAt > DISPATCH_TIMEOUT_MS
            ) {
                isDispatching = false // watchdog
            }
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
        onRunningChanged?.invoke(true)
    }

    fun stopTapping() {
        val was = isTapping
        isTapping = false
        isDispatching = false
        handler.removeCallbacks(tapRunnable)
        if (was) onRunningChanged?.invoke(false)
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
        dispatchStartedAt = SystemClock.uptimeMillis()
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
        private const val DISPATCH_TIMEOUT_MS = 1500L
        var instance: TapAccessibilityService? = null
    }
}
