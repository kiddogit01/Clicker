package com.kiddo.autotapper

import android.animation.ValueAnimator
import android.view.MotionEvent
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.ProgressBar

/**
 * Petites animations partagées : pressions, pops, entrées en cascade.
 * Toutes sont en framework pur (aucune dépendance externe).
 */
object AnimKit {

    /** Feedback "pressé" sur un bouton (le clic continue de fonctionner). */
    fun press(v: View, scale: Float = 0.96f) {
        v.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN ->
                    view.animate().scaleX(scale).scaleY(scale)
                        .setDuration(90).setInterpolator(DecelerateInterpolator()).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                    view.animate().scaleX(1f).scaleY(1f)
                        .setDuration(160).setInterpolator(OvershootInterpolator(2f)).start()
            }
            false
        }
    }

    /** Petit "pop" ressort (validation, changement de valeur…). */
    fun pop(v: View, from: Float = 0.78f) {
        v.animate().cancel()
        v.scaleX = from
        v.scaleY = from
        v.animate().scaleX(1f).scaleY(1f)
            .setDuration(320)
            .setInterpolator(OvershootInterpolator(2.4f))
            .start()
    }

    /** Entrée en cascade : glissement vers le haut + fondu. */
    fun enter(v: View, delayMs: Long = 0L, translation: Float = 44f) {
        v.alpha = 0f
        v.translationY = translation
        v.animate().alpha(1f).translationY(0f)
            .setStartDelay(delayMs)
            .setDuration(420)
            .setInterpolator(DecelerateInterpolator(1.6f))
            .start()
    }

    /** Fondu simple avec délai. */
    fun fadeIn(v: View, delayMs: Long = 0L) {
        v.alpha = 0f
        v.animate().alpha(1f)
            .setStartDelay(delayMs)
            .setDuration(340)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    /** Logo : apparition ressort. */
    fun popLogo(v: View, delayMs: Long = 0L) {
        v.alpha = 0f
        v.scaleX = 0.6f
        v.scaleY = 0.6f
        v.animate().alpha(1f).scaleX(1f).scaleY(1f)
            .setStartDelay(delayMs)
            .setDuration(520)
            .setInterpolator(OvershootInterpolator(1.8f))
            .start()
    }

    /** Progression animée de la barre d'étapes. */
    fun setProgress(bar: ProgressBar, target: Int) {
        if (bar.progress == target) return
        val anim = ValueAnimator.ofInt(bar.progress, target)
        anim.duration = 350
        anim.interpolator = AccelerateDecelerateInterpolator()
        anim.addUpdateListener { bar.progress = it.animatedValue as Int }
        anim.start()
    }
}

/** Retire le tint par défaut d'un bouton pour laisser vivre nos drawables. */
fun Button.clearTint() {
    backgroundTintList = null
}
