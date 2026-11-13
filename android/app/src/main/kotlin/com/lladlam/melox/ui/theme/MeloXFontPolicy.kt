package com.lladlam.melox.ui.theme

/**
 * Font scope policy.
 *
 * The system family is the global default; the optional MiSans family is only
 * used when the user explicitly picks a scope that needs it, and only after
 * the font has been provisioned by [MeloXOnDemandFont].
 */
internal object MeloXFontPolicy {
    /** MiSans everywhere. */
    const val SCOPE_MI_SANS = "off"

    /** System font everywhere except the lyrics. */
    const val SCOPE_SYSTEM_EXCEPT_LYRICS = "nonlyrics"

    /** System font everywhere (default). */
    const val SCOPE_SYSTEM = "all"

    /** Default scope for fresh installs and for reset-to-defaults. */
    const val DEFAULT_SCOPE = SCOPE_SYSTEM

    val scopes: List<String> = listOf(SCOPE_MI_SANS, SCOPE_SYSTEM_EXCEPT_LYRICS, SCOPE_SYSTEM)

    /** Maps a persisted (possibly stale or corrupt) value onto a known scope. */
    fun normalize(scope: String?): String = scope?.takeIf { it in scopes } ?: DEFAULT_SCOPE

    /** True when the regular UI renders with the system family. */
    fun usesSystemUiFont(scope: String): Boolean = normalize(scope) != SCOPE_MI_SANS

    /** True when the lyrics render with the system family. */
    fun usesSystemLyricFont(scope: String): Boolean = normalize(scope) == SCOPE_SYSTEM

    /** True when the scope needs the on-demand MiSans family to be present. */
    fun requiresMiSans(scope: String): Boolean = !usesSystemLyricFont(scope)
}
