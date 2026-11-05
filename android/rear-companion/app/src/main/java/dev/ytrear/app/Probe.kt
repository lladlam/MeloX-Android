package dev.ytrear.app

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Shared scratch log so the probe's findings show up both in logcat and on screen. */
object Probe {
    const val TAG = "YTRearProbe"

    fun interface Sink { fun onLog(full: String) }

    private val lines = ArrayList<String>()
    private val stamp = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Volatile
    var sink: Sink? = null

    fun log(msg: String) {
        Log.i(TAG, msg)
        synchronized(lines) {
            lines.add("${stamp.format(Date())}  $msg")
            if (lines.size > 200) lines.removeAt(0)
        }
        sink?.onLog(text())
    }

    fun text(): String = synchronized(lines) { lines.joinToString("\n") }
}
