package com.sundwaeji.jarvis.overlay

import android.app.Service
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Build
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.min

class JarvisSubtitleOverlayService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val subtitle = intent?.getStringExtra(EXTRA_SUBTITLE)?.trim().orEmpty()
        if (subtitle.isEmpty() || !Settings.canDrawOverlays(this)) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        show(subtitle)
        val readingTime = min(9_000L, 3_500L + subtitle.length * 70L)
        handler.removeCallbacksAndMessages(null)
        handler.postDelayed({ removeOverlay(); stopSelf() }, readingTime)
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        removeOverlay()
        super.onDestroy()
    }

    private fun show(subtitle: String) {
        removeOverlay()
        val density = resources.displayMetrics.density
        val background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 14f * density
            setColor(Color.argb(226, 5, 15, 24))
            setStroke((1f * density).toInt().coerceAtLeast(1), Color.rgb(85, 231, 255))
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding((18 * density).toInt(), (12 * density).toInt(), (18 * density).toInt(), (14 * density).toInt())
            this.background = background
            addView(label("JARVIS // KO SUBTITLE", 10f, Color.rgb(85, 231, 255), true))
            addView(label(subtitle, 16f, Color.rgb(217, 250, 255), false).apply {
                setPadding(0, (6 * density).toInt(), 0, 0)
                maxLines = 4
            })
        }
        @Suppress("DEPRECATION")
        val overlayType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            x = 0
            y = (56 * density).toInt()
            horizontalMargin = 0.055f
        }
        overlayView = layout
        windowManager.addView(layout, params)
    }

    private fun label(text: String, sizeSp: Float, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text
        textSize = sizeSp
        setTextColor(color)
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private fun removeOverlay() {
        overlayView?.let { runCatching { windowManager.removeView(it) } }
        overlayView = null
    }

    companion object {
        const val EXTRA_SUBTITLE = "korean_subtitle"
    }
}
