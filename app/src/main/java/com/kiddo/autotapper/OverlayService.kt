package com.kiddo.autotapper

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.Button
import android.widget.TextView
import android.widget.Toast

/**
 * Fenêtres flottantes : la cible à glisser + la barre de contrôle.
 *
 * Nouveautés / corrections :
 *  - nettoyage robuste : si un des deux overlays échoue à s'ajouter, l'autre
 *    est quand même retiré (plus de bulle fantôme bloquée à l'écran)
 *  - toutes les positions sont en dp (plus de décalages selon la densité)
 *  - entrée des bulles animée (ressort), aimantation aux bords ANIMÉE
 *  - barre de contrôle : expansion/repli animés, pulse sur le bouton quand ça
 *    tourne, affichage de l'intervalle, retour haptique au drag
 *  - position de la cible mémorisée
 */
class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private var targetView: View? = null
    private var controlView: View? = null
    private var targetParams: WindowManager.LayoutParams? = null
    private var controlParams: WindowManager.LayoutParams? = null

    private var startInterval: Long = TapAccessibilityService.RECOMMENDED_INTERVAL_MS
    private var collapsed = false
    private var controlWidthExpanded = 0
    private var controlWidthCollapsed = 0
    private var overlaysReady = false

    private var fingertipXRatio = 0.5f
    private var fingertipYRatio = 0.22f

    private val handler = Handler(Looper.getMainLooper())
    private var togglePulse: ValueAnimator? = null
    private var snapAnimator: ValueAnimator? = null

    private val prefs get() = getSharedPreferences("kiddo_autotapper", MODE_PRIVATE)

    private val refreshRunnable = object : Runnable {
        override fun run() {
            if (!overlaysReady) return
            refreshToggle()
            handler.postDelayed(this, REFRESH_PERIOD_MS)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val fromIntent = intent?.getLongExtra("interval", -1L) ?: -1L
        startInterval = if (fromIntent > 0) {
            prefs.edit().putLong("interval_ms", fromIntent).apply()
            fromIntent
        } else {
            // Redémarrage sticky (intent nul) : on reprend la dernière valeur.
            prefs.getLong("interval_ms", TapAccessibilityService.RECOMMENDED_INTERVAL_MS)
        }

        // On repart toujours d'une base propre : si l'utilisateur a changé le
        // style de cible dans les réglages entre-temps, ça doit se voir
        // immédiatement au prochain lancement.
        teardownOverlays()
        initOverlay()
        return START_STICKY
    }

    private fun initOverlay() {
        try {
            windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            setupTarget()
            setupControl()
            overlaysReady = true
            animateEntrance()
            handler.postDelayed(refreshRunnable, REFRESH_PERIOD_MS)
            Toast.makeText(
                this, "👆 Glisse la cible, puis appuie sur Démarrer",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            // Correction fuite : on retire TOUT ce qui a pu être ajouté avant
            // l'échec (avant, la cible restait bloquée à l'écran pour toujours).
            teardownOverlays()
            Toast.makeText(
                this, "❌ Erreur : " + (e.message ?: e.toString()),
                Toast.LENGTH_LONG
            ).show()
            stopSelf()
        }
    }

    private fun teardownOverlays() {
        handler.removeCallbacks(refreshRunnable)
        togglePulse?.cancel(); togglePulse = null
        snapAnimator?.cancel(); snapAnimator = null

        if (overlaysReady) TapAccessibilityService.instance?.stopTapping()
        // Toujours détacher le listener (même en cas d'échec partiel du setup)
        // pour ne pas figer une référence vers ce Service.
        TapAccessibilityService.instance?.onRunningChanged = null

        targetView?.let { v ->
            v.animate().cancel()
            try { windowManager.removeView(v) } catch (_: Exception) {}
        }
        controlView?.let { v ->
            v.animate().cancel()
            try { windowManager.removeView(v) } catch (_: Exception) {}
        }
        targetView = null
        controlView = null
        targetParams = null
        controlParams = null
        overlaysReady = false
    }

    override fun onDestroy() {
        super.onDestroy()
        teardownOverlays()
    }

    // ---------- Outils ----------

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun overlayType(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE

    private fun fingertipX(): Float =
        (targetParams?.x ?: 0) + (targetView?.width ?: 0) * fingertipXRatio

    private fun fingertipY(): Float =
        (targetParams?.y ?: 0) + (targetView?.height ?: 0) * fingertipYRatio

    private fun animateEntrance() {
        targetView?.let {
            it.alpha = 0f; it.scaleX = 0.55f; it.scaleY = 0.55f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(480)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
        }
        controlView?.let {
            it.alpha = 0f; it.scaleX = 0.6f; it.scaleY = 0.6f
            it.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setStartDelay(120)
                .setDuration(460)
                .setInterpolator(OvershootInterpolator(1.8f))
                .start()
        }
    }

    /** Translation horizontale animée de la barre (aimantation aux bords). */
    private fun animateControlX(toX: Int) {
        val params = controlParams ?: return
        val view = controlView ?: return
        val from = params.x
        if (from == toX) return
        snapAnimator?.cancel()
        val anim = ValueAnimator.ofInt(from, toX).apply {
            duration = 280
            interpolator = OvershootInterpolator(1.15f)
            addUpdateListener { a ->
                params.x = a.animatedValue as Int
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
            }
        }
        snapAnimator = anim
        anim.start()
    }

    // ---------- Cible ----------

    private fun setupTarget() {
        val useCircle = prefs.getString("target_style", "finger") == "circle"

        val view = LayoutInflater.from(this).inflate(
            if (useCircle) R.layout.overlay_target_circle else R.layout.overlay_target, null
        )
        targetView = view
        fingertipXRatio = 0.5f
        fingertipYRatio = if (useCircle) 0.5f else 0.22f

        val metrics = resources.displayMetrics
        val defaultX = metrics.widthPixels / 2 - dp(36)
        val defaultY = metrics.heightPixels / 2 - dp(90)
        val savedX = prefs.getInt("target_x", defaultX)
            .coerceIn(-dp(30), metrics.widthPixels - dp(30))
        val savedY = prefs.getInt("target_y", defaultY)
            .coerceIn(-dp(30), metrics.heightPixels - dp(30))

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }
        targetParams = params
        windowManager.addView(view, params)

        var initialX = 0; var initialY = 0
        var initialTouchX = 0f; var initialTouchY = 0f
        var activePointerId = MotionEvent.INVALID_POINTER_ID

        view.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    // Coupe le tapotage le temps du glissement : dispatchGesture()
                    // annule sinon ce geste en cours de route (comportement
                    // documenté par Android, pas un bug de l'app).
                    TapAccessibilityService.instance?.setUserInteracting(true)
                    activePointerId = event.getPointerId(0)
                    initialX = params.x; initialY = params.y
                    initialTouchX = event.rawX; initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val idx = event.findPointerIndex(activePointerId)
                    if (idx < 0) return@setOnTouchListener true
                    // Glissement sur les DEUX axes (X et Y), puis on garde la
                    // cible entièrement visible à l'écran.
                    val newX = initialX + (event.rawX - initialTouchX).toInt()
                    val newY = initialY + (event.rawY - initialTouchY).toInt()
                    val vw = if (view.width > 0) view.width else dp(76)
                    val vh = if (view.height > 0) view.height else dp(76)
                    params.x = newX.coerceIn(-vw / 2, metrics.widthPixels - vw / 2)
                    params.y = newY.coerceIn(-vh / 2, metrics.heightPixels - vh / 2)
                    try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                    TapAccessibilityService.instance?.updatePosition(fingertipX(), fingertipY())
                    true
                }
                MotionEvent.ACTION_POINTER_DOWN -> true // 2e doigt : on ignore
                MotionEvent.ACTION_POINTER_UP -> {
                    // Le doigt qui track se lève → on termine proprement
                    if (event.getPointerId(event.actionIndex) == activePointerId) {
                        activePointerId = MotionEvent.INVALID_POINTER_ID
                        TapAccessibilityService.instance?.setUserInteracting(false)
                        prefs.edit()
                            .putInt("target_x", params.x)
                            .putInt("target_y", params.y)
                            .apply()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    activePointerId = MotionEvent.INVALID_POINTER_ID
                    TapAccessibilityService.instance?.setUserInteracting(false)
                    // Mémorise la position pour le prochain lancement
                    prefs.edit()
                        .putInt("target_x", params.x)
                        .putInt("target_y", params.y)
                        .apply()
                    true
                }
                else -> true
            }
        }
    }

    // ---------- Barre de contrôle (glissable, repli en bordure animé) ----------

    private fun setupControl() {
        val view = LayoutInflater.from(this).inflate(R.layout.overlay_control, null)
        controlView = view

        val metrics = resources.displayMetrics
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = metrics.heightPixels - dp(260)
        }
        controlParams = params

        val toolbar = view as DraggableToolbar
        val expandedRow = view.findViewById<View>(R.id.expandedRow)
        val collapsedStrip = view.findViewById<TextView>(R.id.collapsedStrip)
        val btnToggle = view.findViewById<Button>(R.id.btnToggle)
        val btnQuit = view.findViewById<Button>(R.id.btnQuit)
        val tvInterval = view.findViewById<TextView>(R.id.tvInterval)

        btnToggle.clearTint()
        btnQuit.clearTint()
        tvInterval.text = "$startInterval ms"

        view.post {
            controlWidthExpanded = expandedRow.width
            controlWidthCollapsed = collapsedStrip.layoutParams.width
        }

        fun expand() {
            collapsed = false
            expandedRow.visibility = View.VISIBLE
            expandedRow.alpha = 0f
            expandedRow.scaleX = 0.8f; expandedRow.scaleY = 0.8f
            expandedRow.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(2f))
                .start()
            collapsedStrip.animate().alpha(0f).scaleX(0.75f).scaleY(0.75f)
                .setDuration(130)
                .withEndAction {
                    collapsedStrip.visibility = View.GONE
                    // Remet la barre entière dans l'écran si elle en déborde
                    view.post {
                        val w = if (controlWidthExpanded > 0) controlWidthExpanded else view.width
                        val p = controlParams ?: return@post
                        if (p.x + w > metrics.widthPixels) p.x = metrics.widthPixels - w
                        if (p.x < 0) p.x = 0
                        try { windowManager.updateViewLayout(view, p) } catch (_: Exception) {}
                    }
                }
                .start()
        }

        fun collapseToEdge(rightSide: Boolean) {
            collapsed = true
            expandedRow.animate().alpha(0f).scaleX(0.8f).scaleY(0.8f)
                .setDuration(130)
                .withEndAction {
                    expandedRow.visibility = View.GONE
                    collapsedStrip.visibility = View.VISIBLE
                    collapsedStrip.alpha = 0f
                    collapsedStrip.scaleX = 0.75f; collapsedStrip.scaleY = 0.75f
                    collapsedStrip.animate().alpha(1f).scaleX(1f).scaleY(1f)
                        .setDuration(220)
                        .setInterpolator(OvershootInterpolator(2.2f))
                        .start()
                }
                .start()
            val w = if (controlWidthCollapsed > 0) controlWidthCollapsed else dp(40)
            animateControlX(if (rightSide) metrics.widthPixels - w else 0)
        }

        collapsedStrip.setOnClickListener { expand() }

        // Les trois petits points (⋮) : un tap replie la barre dans le bord
        // le plus proche (glisser depuis la poignée continue de marcher).
        view.findViewById<View>(R.id.dragHandle).setOnClickListener {
            val w = if (controlWidthExpanded > 0) controlWidthExpanded else view.width
            val centerX = (controlParams?.x ?: 0) + w / 2
            collapseToEdge(centerX < metrics.widthPixels / 2)
        }

        // Même principe que pour la cible : pas de tap auto pendant le glissement.
        toolbar.onDragStart = {
            snapAnimator?.cancel()
            TapAccessibilityService.instance?.setUserInteracting(true)
            view.animate().scaleX(0.965f).scaleY(0.965f)
                .setDuration(130).start()
        }
        toolbar.onDrag = { dx, dy ->
            controlParams?.let { p ->
                p.x += dx
                p.y += dy
                try { windowManager.updateViewLayout(view, p) } catch (_: Exception) {}
            }
        }
        toolbar.onDragEnd = {
            TapAccessibilityService.instance?.setUserInteracting(false)
            view.animate().scaleX(1f).scaleY(1f)
                .setDuration(220)
                .setInterpolator(OvershootInterpolator(2.2f))
                .start()
            if (!collapsed) {
                val w = if (controlWidthExpanded > 0) controlWidthExpanded else view.width
                val edgeMargin = dp(8)
                controlParams?.let { p ->
                    val centerX = p.x + w / 2
                    when {
                        centerX < metrics.widthPixels / 2 && p.x < edgeMargin ->
                            animateControlX(0)
                        centerX >= metrics.widthPixels / 2 &&
                            p.x + w > metrics.widthPixels - edgeMargin ->
                            animateControlX(metrics.widthPixels - w)
                    }
                }
            }
        }

        // Rafraîchit l'état du bouton : démarré / arrêté (aussi si le service
        // d'accessibilité est désactivé depuis les réglages système).
        TapAccessibilityService.instance?.onRunningChanged = {
            handler.post { if (overlaysReady) refreshToggle() }
        }

        btnToggle.setOnClickListener {
            val service = TapAccessibilityService.instance
            if (service == null) {
                Toast.makeText(this, "⚠️ Service d'accessibilité non actif", Toast.LENGTH_SHORT).show()
                refreshToggle()
                return@setOnClickListener
            }
            if (service.isRunning()) service.stopTapping()
            else service.startTapping(fingertipX(), fingertipY(), startInterval)
            refreshToggle()
        }

        btnQuit.setOnClickListener {
            TapAccessibilityService.instance?.stopTapping()
            // Sortie animée avant de tuer le service
            targetView?.animate()?.alpha(0f)?.scaleX(0.7f)?.scaleY(0.7f)
                ?.setDuration(170)?.start()
            view.animate().alpha(0f).scaleX(0.7f).scaleY(0.7f)
                .setDuration(170)
                .withEndAction { stopSelf() }
                .start()
        }

        windowManager.addView(view, params)
        refreshToggle()
    }

    /** Petit "battement" du bouton quand le tap tourne. */
    private fun startTogglePulse(btn: Button) {
        if (togglePulse?.isStarted == true) return
        val anim = ValueAnimator.ofFloat(1f, 1.05f).apply {
            duration = 550
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { a ->
                val s = a.animatedValue as Float
                btn.scaleX = s; btn.scaleY = s
            }
        }
        togglePulse = anim
        anim.start()
    }

    private fun stopTogglePulse(btn: Button) {
        togglePulse?.cancel()
        togglePulse = null
        btn.animate().scaleX(1f).scaleY(1f).setDuration(150).start()
    }

    private fun refreshToggle() {
        // Utilisé par la boucle de refresh périodique.
        val view = controlView ?: return
        val btn = view.findViewById<Button>(R.id.btnToggle) ?: return
        val running = TapAccessibilityService.instance?.isRunning() == true
        btn.text = if (running) "⏸ Arrêter" else "▶ Démarrer"
        btn.setBackgroundResource(
            if (running) R.drawable.btn_pill_pink else R.drawable.btn_pill_green
        )
        if (running) startTogglePulse(btn) else stopTogglePulse(btn)
    }

    companion object {
        private const val REFRESH_PERIOD_MS = 1200L
    }
}
