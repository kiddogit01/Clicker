package com.kiddo.autotapper

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import kotlin.math.abs

/**
 * Toolbar flottante entièrement déplaçable, tout en gardant les boutons à
 * l'intérieur parfaitement cliquables.
 *
 * - le seuil de glissement est en dp (plus de déclenchement parasite sur
 *   écrans haute densité)
 * - un retour haptique marque le début du glissement
 * - un léger "enfoncement" visuel accompagne le drag
 */
class DraggableToolbar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    var onDragStart: (() -> Unit)? = null
    var onDrag: ((dx: Int, dy: Int) -> Unit)? = null
    var onDragEnd: (() -> Unit)? = null

    private var downRawX = 0f
    private var downRawY = 0f
    private var lastRawX = 0f
    private var lastRawY = 0f
    private var dragging = false
    private val dragThresholdPx = (DRAG_THRESHOLD_DP * resources.displayMetrics.density)

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = ev.rawX; downRawY = ev.rawY
                lastRawX = ev.rawX; lastRawY = ev.rawY
                dragging = false
                return false // laisse d'abord la chance au bouton en dessous
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = ev.rawX - downRawX
                val dy = ev.rawY - downRawY
                if (abs(dx) > dragThresholdPx || abs(dy) > dragThresholdPx) {
                    dragging = true
                    performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK)
                    onDragStart?.invoke()
                    return true // à partir d'ici, c'est un glissement : on prend la main
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                if (dragging) {
                    val dx = (event.rawX - lastRawX).toInt()
                    val dy = (event.rawY - lastRawY).toInt()
                    lastRawX = event.rawX
                    lastRawY = event.rawY
                    onDrag?.invoke(dx, dy)
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) {
                    dragging = false
                    onDragEnd?.invoke()
                }
            }
        }
        return true
    }

    companion object {
        private const val DRAG_THRESHOLD_DP = 8f
    }
}
