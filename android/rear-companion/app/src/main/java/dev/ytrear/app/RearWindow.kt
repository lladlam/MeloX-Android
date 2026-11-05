package dev.ytrear.app

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

/**
 * Adds a view straight onto a secondary display.
 *
 * We can't use [android.app.Presentation] here: it builds its window context as
 * TYPE_PRESENTATION (2037), which HyperOS denies to ordinary apps. Instead we build our own
 * window context for the target display typed as TYPE_APPLICATION_OVERLAY (2038), which the
 * SYSTEM_ALERT_WINDOW permission covers.
 */
class RearWindow(private val context: Context) {

    private var wm: WindowManager? = null
    private var root: View? = null

    val isShowing get() = root != null

    fun show(display: Display): Boolean {
        hide()
        val info = Displays.describe(display)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Probe.log("FAILED on display ${info.id} -- window contexts require Android 12+")
            return false
        }
        return try {
            val windowContext = context.createDisplayContext(display)
                .createWindowContext(display, WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
            val manager = windowContext.getSystemService(WindowManager::class.java)

            val view = buildProbeView(windowContext, info)
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                @Suppress("DEPRECATION")
                flags = flags or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            }

            manager.addView(view, params)
            wm = manager
            root = view
            Probe.log("OK -- window added on display ${info.id} (${info.width}x${info.height})")

            view.post {
                val cutout = view.rootWindowInsets?.displayCutout
                Probe.log(
                    "attached size=${view.width}x${view.height} " +
                        if (cutout != null) {
                            "cutout L=${cutout.safeInsetLeft} T=${cutout.safeInsetTop} " +
                                "R=${cutout.safeInsetRight} B=${cutout.safeInsetBottom}"
                        } else "cutout=none"
                )
            }
            true
        } catch (e: Throwable) {
            Probe.log("FAILED on display ${info.id} -- ${e.javaClass.name}: ${e.message}")
            false
        }
    }

    fun hide() {
        val v = root ?: return
        runCatching { wm?.removeViewImmediate(v) }
            .onFailure { Probe.log("remove failed: ${it.message}") }
        root = null
        wm = null
        Probe.log("window removed")
    }

    private fun buildProbeView(ctx: Context, info: Displays.Info): View =
        FrameLayout(ctx).apply {
            setBackgroundColor(Color.MAGENTA)
            addView(
                TextView(ctx).apply {
                    setBackgroundColor(Color.parseColor("#1B5E20"))
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    gravity = Gravity.CENTER
                    text = "YTREAR\n${info.width}x${info.height}\n@${info.densityDpi}dpi"
                },
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
}
