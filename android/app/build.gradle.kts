plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
}

android {
    namespace = "com.lladlam.melox"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.lladlam.melox.android"
        minSdk = 26
        targetSdk = 37
        versionCode = 7
        versionName = "0.3.4-Dev"
    }

    // Release credentials are supplied from the command line or CI secrets;
    // passwords and the external keystore are deliberately not committed.
    val meloxReleaseSigning = signingConfigs.create("meloxRelease") {
        val keystorePath = providers.gradleProperty("meloxReleaseStoreFile").orNull
        val keystorePassword = providers.gradleProperty("meloxReleaseStorePassword").orNull
        val keyAliasValue = providers.gradleProperty("meloxReleaseKeyAlias").orNull
        val keyPasswordValue = providers.gradleProperty("meloxReleaseKeyPassword").orNull
        if (keystorePath != null && keystorePassword != null && keyAliasValue != null && keyPasswordValue != null) {
            storeFile = file(keystorePath)
            storePassword = keystorePassword
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
        }
    }

    buildTypes {
        getByName("release") {
            signingConfig = meloxReleaseSigning
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
    }
}

dependencies {
    // Optional official Apple MusicKit for Android AARs. Download them from
    // Apple Developer and place only these two files in app/libs/:
    // musickitauth-release-*.aar and mediaplayback-release-*.aar.
    // The app remains catalog-capable when the optional files are absent.
    implementation(fileTree("libs") {
        include("musickitauth-release-*.aar", "mediaplayback-release-*.aar")
    })

    val composeBom = platform("androidx.compose:compose-bom:2026.06.00")
    implementation(composeBom)

    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.navigation:navigation-compose:2.9.8")

    // Kyant0/AndroidLiquidGlass (Backdrop). The high-level MeloX controls in
    // ui/glass are adapted from the project's official LiquidButton and
    // LiquidBottomTabs examples while preserving MeloX's iOS geometry.
    implementation("io.github.kyant0:backdrop:2.0.0")
    implementation("io.github.kyant0:shapes:1.2.0")
    implementation("io.github.kyant0:capsule:2.1.3")

    implementation("androidx.media3:media3-common:1.10.1")
    implementation("androidx.media3:media3-datasource:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.4")

    // External lyric integrations.
    implementation("io.github.proify.lyricon:provider:0.1.70")
    // HyperOS Focus / Super Island notification payload builder.
    implementation("com.xzakota.hyper.notification:focus-api:1.4")
    // Optional non-root HyperOS compatibility path. If Shizuku is unavailable or
    // permission is denied, MeloX continues publishing Focus notifications directly.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    // Framework Binder signatures used only at compile time. Android supplies the
    // real hidden interfaces at runtime; this module is never packaged in the APK.
    compileOnly(project(":hidden-api"))

    // Hilt DI
    implementation("com.google.dagger:hilt-android:2.60.1")
    ksp("com.google.dagger:hilt-android-compiler:2.60.1")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")

    // Room + DataStore
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")

    // Glance (AppWidget with Compose)
    implementation("androidx.glance:glance-appwidget:1.1.1")
    implementation("androidx.glance:glance-material3:1.1.1")

    testImplementation("junit:junit:4.13.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
