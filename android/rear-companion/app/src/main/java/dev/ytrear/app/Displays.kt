package dev.ytrear.app

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.DisplayMetrics
import android.view.Display

/** Finding, describing, and choosing the secondary (rear) display. */
object Displays {

    data class Info(
        val id: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val densityDpi: Int,
        val flags: Int,
        val state: Int
    ) {
        val isDefault get() = id == Display.DEFAULT_DISPLAY

        fun flagsText(): String = buildList {
            if (flags and Display.FLAG_PRESENTATION != 0) add("PRESENTATION")
            if (flags and Display.FLAG_PRIVATE != 0) add("PRIVATE")
            if (flags and Display.FLAG_SECURE != 0) add("SECURE")
            if (flags and Display.FLAG_ROUND != 0) add("ROUND")
        }.joinToString("|").ifEmpty { "none" }

        fun stateText(): String = when (state) {
            Display.STATE_OFF -> "off"
            Display.STATE_ON -> "on"
            Display.STATE_DOZE -> "doze"
            Display.STATE_DOZE_SUSPEND -> "doze_suspend"
            Display.STATE_UNKNOWN -> "unknown"
            else -> "state=$state"
        }

        override fun toString() =
            "id=$id  ${width}x$height  ${densityDpi}dpi  ${stateText()}  [${flagsText()}]\n$name"
    }

    fun manager(context: Context): DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    fun all(context: Context): List<Info> =
        manager(context).displays.map { describe(it) }

    fun describe(d: Display): Info {
        val m = DisplayMetrics().also { @Suppress("DEPRECATION") d.getRealMetrics(it) }
        return Info(d.displayId, d.name ?: "", m.widthPixels, m.heightPixels, m.densityDpi, d.flags, d.state)
    }

    /**
     * The rear panel is the small non-default display. Prefer ones the framework already
     * advertises as presentation targets; otherwise take the smallest non-default display,
     * which on a 17 Pro is the ~904x572 back screen rather than the main panel.
     */
    fun pickRear(context: Context, preferredId: Int = -1): Display? {
        val dm = manager(context)
        if (preferredId >= 0) {
            dm.getDisplay(preferredId)?.let { return it }
        }
        val presentation = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .filter { it.displayId != Display.DEFAULT_DISPLAY }
        val candidates = presentation.ifEmpty {
            dm.displays.filter { it.displayId != Display.DEFAULT_DISPLAY }
        }
        return candidates.minByOrNull { val i = describe(it); i.width.toLong() * i.height }
    }
}
