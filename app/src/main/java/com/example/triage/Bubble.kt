package com.example.triage

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.TextView

/**
 * Petite bulle flottante FIXE, affichee en haut a gauche par-dessus les autres
 * apps (dont WhatsApp), a l'emplacement du bouton retour. Un tap ramene Triage
 * au premier plan. Elle ne se deplace pas.
 *
 * Necessite l'autorisation "Afficher au-dessus des autres apps"
 * (Settings.canDrawOverlays). Sans elle, show() ne fait rien.
 */
object Bubble {

    private var view: View? = null

    fun show(context: Context) {
        val ctx = context.applicationContext
        if (!Settings.canDrawOverlays(ctx)) return
        if (view != null) return

        val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val density = ctx.resources.displayMetrics.density
        val size = (44 * density).toInt()

        val bubble = TextView(ctx).apply {
            text = "\u21A9"            // ↩
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#008069"))
            }
            elevation = 8f
            alpha = 0.95f
            setOnClickListener { bringAppToFront(ctx) }
        }

        // Hauteur de la barre de statut : la bulle se pose juste en dessous,
        // au niveau du bouton retour de l'app en avant-plan.
        var statusBar = (24 * density).toInt()
        val resId = ctx.resources.getIdentifier("status_bar_height", "dimen", "android")
        if (resId > 0) statusBar = ctx.resources.getDimensionPixelSize(resId)

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (6 * density).toInt()
            y = statusBar + (6 * density).toInt()
        }

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
