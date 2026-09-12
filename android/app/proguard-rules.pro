# JavaScript bridges are invoked by name from bundled WebView assets.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# JSON/domain objects are decoded explicitly; retain enum names used in persisted settings.
-keepclassmembers enum com.lladlam.melox.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Kotlin file-level static functions are resolved by R8 as *Kt classes.
# Retain the ones that are referenced across module boundaries or via companion objects.
-keep class com.lladlam.melox.core.account.NeteaseSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.qqmusic.QQMusicSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.kugou.KugouSessionStoreKt { *; }
-keep class com.lladlam.melox.core.provider.qqmusic.QQMusicApiClientKt { *; }
-keep class com.lladlam.melox.core.network.NeteaseSearchClientKt { *; }

# Shizuku and optional vendor bridges are discovered through Android framework metadata.
-keep class rikka.shizuku.** { *; }
-dontwarn rikka.shizuku.**
-dontwarn com.apple.android.music.**

## Rules for NewPipeExtractor
-keep class org.mozilla.javascript.** { *; }
-keep class org.mozilla.classfile.ClassFileWriter
-dontwarn org.mozilla.javascript.tools.**
