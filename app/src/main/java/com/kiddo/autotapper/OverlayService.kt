package com.kiddo.autotapper

import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.graphics.Color
import android.content.SharedPreferences
import android.view.ViewGroup

/**
 * Deux fenêtres flottantes bien séparées, comme dans les vraies apps
 * d'auto-tap (ex: eci99/Clicker) :
 *  - targetView  : l'émoji 👆 — indique OÙ ça va taper (au bout du doigt).
 *    Se déplace au doigt.
 *  - controlView : Démarrer/Arrêter + Fermer. Se déplace via sa poignée (✥),
 *    jamais à l'endroit où les taps automatiques tombent — donc toujours
 *    cliquable, même pendant que ça tapote.
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var targetView: View
    private lateinit var controlView: View
    private lateinit var targetParams: WindowManager.LayoutParams
    private lateinit var controlParams: WindowManager.LayoutParams
    private var startInterval: Long = 50L
    private lateinit var prefs: SharedPreferences
    private var controlCollapsed = false

    // Position approximative du bout du doigt dans l'émoji 👆 (ratio de la
    // largeur/hauteur de la vue). Le rendu de l'émoji varie légèrement d'un
    // téléphone à l'autre : si le point réel tape un peu à côté, ajuste ces
    // deux valeurs (0.5 = centre horizontal, 0.22 = proche du haut).
    private val fingertipXRatio = 0.5f
    private val fingertipYRatio = 0.22f

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startInterval = intent?.getLongExtra("interval", 50L) ?: 50L
        if (!::targetView.isInitialized) {
            initOverlay()
        }
        return START_STICKY
    }

    private fun initOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            prefs = getSharedPreferences("settings", MODE_PRIVATE)
            setupTarget()
            setupControl()
            Toast.makeText(
                this,
                "👆 Glisse le doigt sur la cible, puis appuie sur Démarrer",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erreur : " + (e.message ?: e.toString()), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TapAccessibilityService.instance?.stopTapping()
        if (::targetView.isInitialized) {
            try { windowManager.removeView(targetView) } catch (_: Exception) {}
        }
        if (::controlView.isInitialized) {
            try { windowManager.removeView(controlView) } catch (_: Exception) {}
        }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    /** Point exact où le tap automatique doit tomber : le bout du doigt de l'émoji. */
    private fun fingertipX() = targetParams.x + targetView.width * 0.5f
    private fun fingertipY() = targetParams.y + targetView.height * if (prefs.getBoolean("circle_target", false)) 0.5f else fingertipYRatio

    // ---------- Cible (déplaçable, purement visuelle) ----------

    private fun setupTarget() {
        targetView = LayoutInflater.from(this).inflate(R.layout.overlay_target, null)
        val targetText = targetView.findViewById<TextView>(R.id.targetText)
        val circleMode = prefs.getBoolean("circle_target", false)
        targetText.text = if (circleMode) "⊕" else "👆🏽"
        targetText.textSize = if (circleMode) 52f else 52f

        val metrics = resources.displayMetrics
        val defaultX = metrics.widthPixels / 2 - 100
        val defaultY = metrics.heightPixels / 2 - 200

        targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = defaultX
            y = defaultY
        }

        windowManager.addView(targetView, targetParams)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        targetView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = targetParams.x
                    initialY = targetParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    targetParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    targetParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(targetView, targetParams)
                    // Le tap suit la cible en temps réel, même pendant le tapotage
                    TapAccessibilityService.instance?.updatePosition(fingertipX(), fingertipY())
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Barre de contrôle (déplaçable via sa poignée ✥) ----------

    private fun setupControl() {
        controlView = LayoutInflater.from(this).inflate(R.layout.overlay_control, null)

        val metrics = resources.displayMetrics
        controlParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 40
            y = metrics.heightPixels - 300
        }

        val dragHandle = controlView.findViewById<TextView>(R.id.dragHandle)
        val btnToggle = controlView.findViewById<Button>(R.id.btnToggle)
        val btnQuit = controlView.findViewById<Button>(R.id.btnQuit)
        val panelButtons = controlView.findViewById<View>(R.id.panelButtons)

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var moved = false

        fun collapseToEdge() {
            val screenWidth = resources.displayMetrics.widthPixels
            val edgeThreshold = (screenWidth * 0.08f).toInt()
            val currentWidth = controlView.width.coerceAtLeast(48)
            val nearLeft = controlParams.x <= edgeThreshold
            val nearRight = controlParams.x + currentWidth >= screenWidth - edgeThreshold
            if (!nearLeft && !nearRight) return

            controlCollapsed = true
            panelButtons.visibility = View.GONE
            dragHandle.text = "‹"
            dragHandle.contentDescription = "Ouvrir le panneau de contrôle"
            val collapsedWidth = (48 * resources.displayMetrics.density).toInt()
            controlParams.width = collapsedWidth
            controlParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            controlParams.x = if (nearLeft) 0 else screenWidth - collapsedWidth
            windowManager.updateViewLayout(controlView, controlParams)
        }

        fun expandPanel() {
            controlCollapsed = false
            panelButtons.visibility = View.VISIBLE
            dragHandle.text = "✥"
            dragHandle.contentDescription = "Déplacer le panneau de contrôle"
            controlParams.width = WindowManager.LayoutParams.WRAP_CONTENT
            controlParams.height = WindowManager.LayoutParams.WRAP_CONTENT
            val screenWidth = resources.displayMetrics.widthPixels
            val desiredWidth = controlView.measuredWidth.coerceAtLeast(150)
            if (controlParams.x > screenWidth - desiredWidth) controlParams.x = screenWidth - desiredWidth
            windowManager.updateViewLayout(controlView, controlParams)
        }

        dragHandle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = controlParams.x
                    initialY = controlParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (kotlin.math.abs(dx) > 4 || kotlin.math.abs(dy) > 4) moved = true
                    controlParams.x = initialX + dx.toInt()
                    controlParams.y = (initialY + dy.toInt()).coerceAtLeast(0)
                    windowManager.updateViewLayout(controlView, controlParams)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved && controlCollapsed) {
                        expandPanel()
                    } else if (moved && !controlCollapsed) {
                        collapseToEdge()
                    }
                    true
                }
                else -> true
            }
        }

        fun refreshToggle() {
            val running = TapAccessibilityService.instance?.isRunning() == true
            if (running) {
                btnToggle.text = "⏸ Arrêter"
                btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(255, 0, 127))
                btnToggle.setTextColor(Color.WHITE)
            } else {
                btnToggle.text = "▶ Démarrer"
                btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(0, 240, 255))
                btnToggle.setTextColor(Color.rgb(13, 14, 21))
            }
        }
        refreshToggle()

        btnToggle.setOnClickListener {
            val service = TapAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "⚠️ Service d'accessibilité non actif", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (service.isRunning()) {
                service.stopTapping()
            } else {
                service.startTapping(fingertipX(), fingertipY(), startInterval)
            }
            refreshToggle()
        }

        btnQuit.setOnClickListener {
            TapAccessibilityService.instance?.stopTapping()
            stopSelf()
        }

        windowManager.addView(controlView, controlParams)
    }

}
