package dev.ytrear.app

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.edit
import java.util.Locale

/** Persists the app whose MediaSession YTRear should mirror. */
object PlayerSelection {

    const val YOUTUBE_MUSIC = "com.google.android.apps.youtube.music"
    private const val SPOTIFY = "com.spotify.music"
    private val NATIVE_REAR_PLAYERS = setOf(SPOTIFY)

    data class AppEntry(
        val packageName: String,
        val label: String
    )

    private const val PREFS = "player_selection"
    private const val KEY_PACKAGE = "package"
    private const val KEY_LABEL = "label"
    private const val KEY_PENDING_RESTORE_PACKAGES = "pending_restore_packages"

    fun selectedPackage(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_PACKAGE, null)
            ?.takeIf { it.isNotBlank() }
            ?.takeUnless { it in NATIVE_REAR_PLAYERS }

    fun selectedLabel(context: Context): String? {
        if (selectedPackage(context) == null) return null
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_LABEL, null)
            ?.takeIf { it.isNotBlank() }
    }

    fun select(context: Context, app: AppEntry) {
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val previousPackage = preferences.getString(KEY_PACKAGE, null)
            ?.takeIf { it.isNotBlank() }
        val pendingRestores = preferences
            .getStringSet(KEY_PENDING_RESTORE_PACKAGES, emptySet())
            .orEmpty()
            .toMutableSet()
        if (previousPackage != null && previousPackage != app.packageName) {
            pendingRestores += previousPackage
        }

        preferences.edit {
            putString(KEY_PACKAGE, app.packageName)
            putString(KEY_LABEL, app.label)
            putStringSet(KEY_PENDING_RESTORE_PACKAGES, pendingRestores)
        }
        Probe.log("PLAYER: selected ${app.label} (${app.packageName})")
    }

    /** Packages whose YTRear-managed media notification should return after a player switch. */
    fun pendingRestorePackages(context: Context): Set<String> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_PENDING_RESTORE_PACKAGES, emptySet())
            .orEmpty()
            .toSet()

    fun markRestoresHandled(context: Context, packages: Set<String>) {
        if (packages.isEmpty()) return
        val preferences = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val remaining = preferences
            .getStringSet(KEY_PENDING_RESTORE_PACKAGES, emptySet())
            .orEmpty()
            .toMutableSet()
        if (!remaining.removeAll(packages)) return
        preferences.edit { putStringSet(KEY_PENDING_RESTORE_PACKAGES, remaining) }
    }

    /**
     * Returns every enabled app with a launcher activity. Headless services are intentionally
     * omitted: there is nothing useful to open when the proxy notification is tapped.
     */
    fun launchableApps(context: Context): List<AppEntry> {
        val pm = context.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val resolved = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.queryIntentActivities(launcher, PackageManager.ResolveInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION")
            pm.queryIntentActivities(launcher, 0)
        }

        return resolved.mapNotNull { info ->
            val application = info.activityInfo?.applicationInfo ?: return@mapNotNull null
            val packageName = application.packageName
            if (packageName == context.packageName || packageName in NATIVE_REAR_PLAYERS) {
                return@mapNotNull null
            }
            val label = application.loadLabel(pm)?.toString()?.trim().orEmpty()
                .ifBlank { packageName }
            AppEntry(packageName, label)
        }
            .distinctBy { it.packageName }
            .sortedWith(
                compareBy<AppEntry>(
                    { if (it.packageName == "com.lladlam.melox.android" || it.packageName == "com.lladlam.melox.android.dev") 0 else 1 },
                    { it.label.lowercase(Locale.getDefault()) },
                    { it.packageName }
                )
            )
    }
}
