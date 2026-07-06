package com.nathanb.lock.service

import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.nathanb.lock.R

/**
 * Manages a full-screen overlay displayed when the user opens a blocked app.
 * Uses the standard Android View system (not Compose) because overlays run
 * from the Accessibility Service context which has no ComponentActivity.
 */
class BlockOverlayManager(private val context: Context) {

    private val localizedContext: Context = resolveLocalizedContext(context)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var overlayView: View? = null

    private val layoutParams = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    )

    fun show(packageName: String, isNoEscape: Boolean = false) {
        if (overlayView != null) return

        val appLabel = resolveAppLabel(packageName)

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF111111.toInt())
            setPadding(dp(32), dp(48), dp(32), dp(48))
        }

        // "LOCK" label
        val lockLabel = TextView(context).apply {
            text = "LOCK"
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 14f
            typeface = Typeface.DEFAULT_BOLD
            letterSpacing = 0.3f
            gravity = Gravity.CENTER
        }
        layout.addView(lockLabel)

        // Triangle logo
        val icon = ImageView(context).apply {
            setImageResource(R.mipmap.ic_launcher_foreground)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val iconParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, dp(32), 0, dp(32))
        }
        layout.addView(icon, iconParams)

        // Main title
        val title = TextView(context).apply {
            text = localizedContext.getString(R.string.overlay_title)
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 28f
            typeface = Typeface.create(Typeface.DEFAULT, 700, false)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
        }
        layout.addView(title)

        // Subtitle — no-escape sessions can't be unlocked with a tag
        val subtitleRes = if (isNoEscape) R.string.overlay_subtitle_no_escape else R.string.overlay_subtitle
        val subtitle = TextView(context).apply {
            text = localizedContext.getString(subtitleRes, appLabel)
            setTextColor(0xFFAAAAAA.toInt())
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), dp(48))
            setLineSpacing(dp(4).toFloat(), 1f)
        }
        layout.addView(subtitle)

        // OK button
        val button = Button(context).apply {
            text = localizedContext.getString(R.string.overlay_button)
            setTextColor(0xFF111111.toInt())
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setBackgroundColor(0xFFFFFFFF.toInt())
            setPadding(dp(24), dp(14), dp(24), dp(14))
            minimumWidth = dp(200)

            // Rounded corners via a GradientDrawable
            val bg = android.graphics.drawable.GradientDrawable().apply {
                setColor(0xFFFFFFFF.toInt())
                cornerRadius = dp(28).toFloat()
            }
            background = bg

            setOnClickListener {
                dismiss()
            }
        }
        val buttonParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply {
            setMargins(dp(24), 0, dp(24), 0)
        }
        layout.addView(button, buttonParams)

        overlayView = layout
        windowManager.addView(layout, layoutParams)
    }

    fun dismiss() {
        overlayView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {}
            overlayView = null
        }
    }

    val isShowing: Boolean get() = overlayView != null

    private fun resolveAppLabel(packageName: String): String {
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            appInfo.loadLabel(pm).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName
        }
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            context.resources.displayMetrics,
        ).toInt()
    }

    companion object {
        private fun resolveLocalizedContext(context: Context): Context {
            val localeManager = context.getSystemService(LocaleManager::class.java)
            val appLocales = localeManager.applicationLocales
            if (appLocales.isEmpty) return context
            val locale = appLocales[0] ?: return context
            val config = Configuration(context.resources.configuration).apply {
                setLocale(locale)
            }
            return context.createConfigurationContext(config)
        }
    }
}
