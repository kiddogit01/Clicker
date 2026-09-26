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

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var targetView: View
    private lateinit var controlView: View
    private lateinit var targetParams: WindowManager.LayoutParams
    private lateinit var controlParams: WindowManager.LayoutParams
    private var startInterval: Long = 50L
    private var collapsed = false
    private var controlWidthExpanded = 0
    private var controlWidthCollapsed = 0

    // Position du point de tap dans la cible (ratio largeur/hauteur de la vue).
    // Doigt : proche du haut (bout du doigt). Cercle : exactement au centre.
    private var fingertipXRatio = 0.5f
    private var fingertipYRatio = 0.22f

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
            setupTarget()
            setupControl()
            Toast.makeText(this, "👆 Glisse la cible, puis appuie sur Démarrer", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ Erreur : " + (e.message ?: e.toString()), Toast.LENGTH_LONG).show()
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        TapAccessibilityService.instance?.stopTapping()
        if (::targetView.isInitialized) { try { windowManager.removeView(targetView) } catch (_: Exception) {} }
        if (::controlView.isInitialized) { try { windowManager.removeView(controlView) } catch (_: Exception) {} }
    }

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun fingertipX() = targetParams.x + targetView.width * fingertipXRatio
    private fun fingertipY() = targetParams.y + targetView.height * fingertipYRatio

    // ---------- Cible ----------

    private fun setupTarget() {
        val prefs = getSharedPreferences("kiddo_autotapper", MODE_PRIVATE)
        val useCircle = prefs.getString("target_style", "finger") == "circle"

        targetView = LayoutInflater.from(this).inflate(
            if (useCircle) R.layout.overlay_target_circle else R.layout.overlay_target, null
        )
        fingertipXRatio = 0.5f
        fingertipYRatio = if (useCircle) 0.5f else 0.22f

        val metrics = resources.displayMetrics
        targetParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = metrics.widthPixels / 2 - 100
            y = metrics.heightPixels / 2 - 200
        }

        windowManager.addView(targetView, targetParams)

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f

        targetView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = targetParams.x; initialY = targetParams.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    targetParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    targetParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(targetView, targetParams)
                    TapAccessibilityService.instance?.updatePosition(fingertipX(), fingertipY())
                    true
                }
                else -> false
            }
        }
    }

    // ---------- Barre de contrôle (glissable, repli en bordure) ----------

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

        val toolbar = controlView as DraggableToolbar
        val expandedRow = controlView.findViewById<View>(R.id.expandedRow)
        val collapsedStrip = controlView.findViewById<TextView>(R.id.collapsedStrip)
        val btnToggle = controlView.findViewById<Button>(R.id.btnToggle)
        val btnQuit = controlView.findViewById<Button>(R.id.btnQuit)

        // Mesure la largeur réelle une fois posée, pour un repli précis en bordure.
        controlView.post {
            controlWidthExpanded = expandedRow.width
            controlWidthCollapsed = collapsedStrip.layoutParams.width
        }

        fun expand() {
            collapsed = false
            expandedRow.visibility = View.VISIBLE
            collapsedStrip.visibility = View.GONE
        }

        fun collapseToEdge(rightSide: Boolean) {
            collapsed = true
            expandedRow.visibility = View.GONE
            collapsedStrip.visibility = View.VISIBLE
            val w = if (controlWidthCollapsed > 0) controlWidthCollapsed else 60
            controlParams.x = if (rightSide) metrics.widthPixels - w else 0
            windowManager.updateViewLayout(controlView, controlParams)
        }

        collapsedStrip.setOnClickListener { expand() }

        toolbar.onDrag = { dx, dy ->
            controlParams.x += dx
            controlParams.y += dy
            windowManager.updateViewLayout(controlView, controlParams)
        }
        toolbar.onDragEnd = {
            if (!collapsed) {
                val w = if (controlWidthExpanded > 0) controlWidthExpanded else 200
                val edgeMargin = 60
                val centerX = controlParams.x + w / 2
                if (centerX < metrics.widthPixels / 2 && controlParams.x < edgeMargin) {
                    collapseToEdge(rightSide = false)
                } else if (centerX >= metrics.widthPixels / 2 &&
                    controlParams.x + w > metrics.widthPixels - edgeMargin
                ) {
                    collapseToEdge(rightSide = true)
                }
            }
        }

        fun refreshToggle() {
            val running = TapAccessibilityService.instance?.isRunning() == true
            btnToggle.text = if (running) "⏸ Arrêter" else "▶ Démarrer"
            btnToggle.backgroundTintList = android.content.res.ColorStateList.valueOf(
                resources.getColor(if (running) R.color.kiddo_pink else R.color.kiddo_green, theme)
            )
        }
        refreshToggle()

        btnToggle.setOnClickListener {
            val service = TapAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "⚠️ Service d'accessibilité non actif", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (service.isRunning()) service.stopTapping()
            else service.startTapping(fingertipX(), fingertipY(), startInterval)
            refreshToggle()
        }

        btnQuit.setOnClickListener {
            TapAccessibilityService.instance?.stopTapping()
            stopSelf()
        }

        windowManager.addView(controlView, controlParams)
    }
}
