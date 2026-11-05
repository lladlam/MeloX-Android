package com.lladlam.melox.core.plugin

/** SemVer precedence without integer overflow; build metadata is not part of manifest v1. */
object PluginVersion {
    private val pattern = Regex("(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:-([0-9A-Za-z-]+(?:\\.[0-9A-Za-z-]+)*))?")

    fun valid(value: String): Boolean {
        val match = pattern.matchEntire(value) ?: return false
        return match.groupValues[4].split('.').none { it.length > 1 && it.all(Char::isDigit) && it.startsWith('0') }
    }

    fun compare(left: String, right: String): Int {
        require(valid(left) && valid(right)) { "invalid semantic version" }
        val a = pattern.matchEntire(left)!!.groupValues
        val b = pattern.matchEntire(right)!!.groupValues
        for (index in 1..3) number(a[index], b[index]).let { if (it != 0) return it }
        if (a[4].isEmpty() || b[4].isEmpty()) return when {
            a[4] == b[4] -> 0
            a[4].isEmpty() -> 1
            else -> -1
        }
        val ap = a[4].split('.')
        val bp = b[4].split('.')
        for (index in 0 until minOf(ap.size, bp.size)) {
            val x = ap[index]
            val y = bp[index]
            val xn = x.all(Char::isDigit)
            val yn = y.all(Char::isDigit)
            val comparison = when {
                xn && yn -> number(x, y)
                xn -> -1
                yn -> 1
                else -> x.compareTo(y)
            }
            if (comparison != 0) return comparison
        }
        return ap.size.compareTo(bp.size)
    }

    private fun number(a: String, b: String): Int = a.length.compareTo(b.length).takeIf { it != 0 } ?: a.compareTo(b)
}
