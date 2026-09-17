package com.example.triage

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs

/**
 * Petite bulle flottante affichee par-dessus les autres apps (dont WhatsApp).
 * Un tap ramene Triage au premier plan ; on peut la deplacer par glisser.
 *
 * Necessite l'autorisation "Afficher au-dessus des autres apps"
 * (Settings.canDrawOverlays). Sans elle, show() ne fait rien.
 *
 * La vue est ajoutee via le WindowManager avec le contexte applicatif :
 * elle survit a la mise en arriere-plan de l'activite tant que le
 * processus vit (le service de notifications le maintient en vie).
 */
object Bubble {

    private var view: View? = null

    fun show(context: Context) {
        val ctx = context.applicationContext
        if (!Settings.canDrawOverlays(ctx)) return
        if (view != null) return

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = ctx.resources.displayMetrics.density
        val size = (56 * density).toInt()

        val bubble = TextView(ctx).apply {
            text = "\u21A9"            // ↩
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#008069"))
            }
            elevation = 8f
        }

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (16 * density).toInt()
            y = (140 * density).toInt()
        }

        bubble.setOnTouchListener(object : View.OnTouchListener {
            var downX = 0f; var downY = 0f
            var startX = 0; var startY = 0
            var moved = false
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> {
                        downX = e.rawX; downY = e.rawY
                        startX = params.x; startY = params.y
                        moved = false
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (e.rawX - downX).toInt()
                        val dy = (e.rawY - downY).toInt()
                        if (abs(dx) > 8 || abs(dy) > 8) moved = true
                        params.x = startX + dx
                        params.y = startY + dy
                        try { wm.updateViewLayout(v, params) } catch (ignored: Exception) {}
                        return true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (!moved) bringAppToFront(ctx)
                        return true
                    }
                }
                return false
            }
        })

        try {
            wm.addView(bubble, params)
            view = bubble
        } catch (e: Exception) {
            view = null
        }
    }

    fun hide(context: Context) {
        val v = view ?: return
        try {
            val wm = context.applicationContext
                .getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.removeView(v)
        } catch (ignored: Exception) {}
        view = null
    }

    private fun bringAppToFront(ctx: Context) {
        try {
            val i = Intent(ctx, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            ctx.startActivity(i)
        } catch (ignored: Exception) {}
        hide(ctx)
    }
}
