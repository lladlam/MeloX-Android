import java.net.URI
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
    id("androidx.baselineprofile")
}

fun String.asKotlinStringLiteral(): String = buildString {
    append('"')
    this@asKotlinStringLiteral.forEach { character ->
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(character)
        }
    }
    append('"')
}

fun currentGitSha(): String {
    fun exec(vararg args: String): String = try {
        val process = ProcessBuilder(*args).redirectErrorStream(true).start()
        process.waitFor()
        process.inputStream.bufferedReader().readText().trim()
    } catch (_: Throwable) {
        ""
    }
    return listOf("git", "rev-parse", "HEAD").let { exec(*it.toTypedArray()) }
        .ifBlank { "" }
        .take(40)
}

// ───────────────────────────────────────────────────────────────────────────
// ONNX Runtime
//
// Upstream publishes no Android artifact on GitHub Releases (the release
// assets only cover Linux/macOS/Windows), so the Android runtime is fetched
// as a pinned archive whose SHA-256 is verified before it is used. The build
// can be pointed at a GitHub release asset:
//
//   ./gradlew :app:assembleRelease \
//     -PmeloxOnnxRuntimeUrl="https://github.com/<owner>/<repo>/releases/download/<tag>/<file>" \
//     -PmeloxOnnxRuntimeSha256="<sha256>"
//
// The Java classes come from the archive while the native libraries are
// staged separately, so the reduced runtime produced by
// `app/onnxruntime/build-onnxruntime.sh` can replace the upstream ones.
// ───────────────────────────────────────────────────────────────────────────
val onnxRuntimeVersion = "1.31.0"
val onnxRuntimeAarUrl: String = (project.findProperty("meloxOnnxRuntimeUrl") as String?)
    ?: "https://repo1.maven.org/maven2/com/microsoft/onnxruntime/onnxruntime-android/" +
        "$onnxRuntimeVersion/onnxruntime-android-$onnxRuntimeVersion.aar"
val onnxRuntimeAarSha256: String = (project.findProperty("meloxOnnxRuntimeSha256") as String?)
    ?: "72683787a80d0608a3121b967d8e1239aef48286826661c953211626690540a7"

val onnxRuntimeStageDir = layout.buildDirectory.dir("onnxruntime")
val onnxRuntimeClassesJar = onnxRuntimeStageDir.map { it.file("classes.jar") }
val onnxRuntimeJniLibsDir = layout.buildDirectory.dir("onnxruntime/jniLibs").get().asFile
val onnxRuntimeCacheDir = File(gradle.gradleUserHomeDir, "melox/onnxruntime")
val onnxRuntimePrebuiltDir = file("onnxruntime/prebuilt")

val downloadOnnxRuntime by tasks.registering {
    group = "build"
    description = "Downloads the ONNX Runtime Android archive and verifies its SHA-256."
    val url = onnxRuntimeAarUrl
    val sha256 = onnxRuntimeAarSha256
    val cacheDir = onnxRuntimeCacheDir
    val stageDir = onnxRuntimeStageDir
    val prebuilt = onnxRuntimePrebuiltDir
    inputs.property("url", url)
    inputs.property("sha256", sha256)
    // A locally built reduced runtime (app/onnxruntime/prebuilt/<abi>/*.so) may
    // appear after this task has already been cached. The file tree is
    // snapshotted when the task runs, so a new or replaced reduced library
    // invalidates the task and gets staged into the APK.
    inputs.files(fileTree(prebuilt) { include("**/*.so") })
    outputs.dir(stageDir)
    doLast {
        fun sha256Of(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1 shl 16)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    digest.update(buffer, 0, read)
                }
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val archive = File(cacheDir, "onnxruntime-android-$onnxRuntimeVersion.aar")
        if (!archive.isFile || sha256Of(archive) != sha256) {
            cacheDir.mkdirs()
            val part = File(cacheDir, "${archive.name}.part")
            URI(url).toURL().openStream().use { input ->
                part.outputStream().use { output -> input.copyTo(output) }
            }
            val actual = sha256Of(part)
            if (actual != sha256) {
                part.delete()
                throw GradleException("ONNX Runtime checksum mismatch: expected $sha256 but got $actual")
            }
            if (!part.renameTo(archive)) {
                part.copyTo(archive, overwrite = true)
                part.delete()
            }
        }

        val stage = stageDir.get().asFile
        stage.deleteRecursively()
        stage.mkdirs()
        ZipFile(archive).use { zip ->
            val classes = zip.getEntry("classes.jar")
                ?: throw GradleException("ONNX Runtime archive does not contain classes.jar")
            zip.getInputStream(classes).use { input ->
                File(stage, "classes.jar").outputStream().use { output -> input.copyTo(output) }
            }
            for (abi in listOf("arm64-v8a", "armeabi-v7a")) {
                val target = File(stage, "jniLibs/$abi")
                target.mkdirs()
                for (name in listOf("libonnxruntime.so", "libonnxruntime4j_jni.so")) {
                    val entry = zip.getEntry("jni/$abi/$name") ?: continue
                    val reduced = File(prebuilt, "$abi/$name")
                    if (reduced.isFile) {
                        reduced.copyTo(File(target, name), overwrite = true)
                    } else {
                        zip.getInputStream(entry).use { input ->
                            File(target, name).outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                }
            }
        }
    }
}

tasks.named("preBuild").configure { dependsOn(downloadOnnxRuntime) }

android {
    namespace = "com.lladlam.melox"
    compileSdk = 37

    // OpenJTalk G2P frontend (Japanese AutoSync). Sources are vendored under
    // app/src/main/cpp/third_party/open_jtalk (Modified BSD).
    ndkVersion = "29.0.14206865"

    // Publish one APK per CPU architecture so a phone downloads only its own
    // native libraries instead of carrying both ONNX/FFmpeg copies.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a")
            isUniversalApk = false
        }
    }

    defaultConfig {
        applicationId = "com.lladlam.melox.android"
        minSdk = 24
        targetSdk = 37
        versionCode = 27
        versionName = "0.7.2"
        testInstrumentationRunner = providers.gradleProperty("meloxTestInstrumentationRunner")
            .getOrElse("com.lladlam.melox.core.autosync.AutoSyncDeviceRunner")
        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_static")
            }
        }
        buildConfigField(
            "String",
            "SPOTIFY_CLIENT_ID",
            providers.gradleProperty("meloxSpotifyClientId").orNull.orEmpty().asKotlinStringLiteral(),
        )
        buildConfigField(
            "String",
            "GIT_SHA",
            currentGitSha().asKotlinStringLiteral(),
        )
        // 方向锁总开关（默认 false = 锁竖屏，与 MeloX 主线一致）。
        // true 的变体（目前只有 debugPerf）会解除应用内横竖屏限制：
        //   · Manifest 的 MainActivity 不再写 portrait（改由 src/debugPerf/AndroidManifest.xml 覆盖）
        //   · MeloXApp 里那段「按播放页可见性切 SENSOR/PORTRAIT」的代码整个跳过
        // ⚠ 改这里会让 release/debug 跟着解锁 ⇒ 只允许在 buildType 里覆写，不要动这一行。
        buildConfigField("boolean", "UNLOCK_ORIENTATION", "false")
    }

    // ── Release signing ─────────────────────────────────────────────────────
    // Resolution order (first hit wins) for each value:
    //   1. -PmeloxRelease*  or  $GRADLE_USER_HOME/gradle.properties
    //   2. android/keystore.properties   (in-repo; copy keystore/ to build machines)
    //
    // Every release APK must be signed by exactly one certificate:
    //   CN=MeloX, OU=Development, O=lladlam, C=CN
    //   SHA-256 dfcca9865b87a402d341985a48eb132bd8679dfa6d9d502f365dd16210a5ebe9
    // Android refuses to *update* an installed app whose new APK carries a
    // different key (INSTALL_FAILED_UPDATE_INCOMPATIBLE). Mixing a throwaway
    // debug key with a real release key — as this project used to — makes every
    // install fail until the old package is uninstalled. Never swap this key.
    val meloxKeystoreProperties = rootProject.file("keystore.properties")
        .takeIf { it.isFile }
        ?.let { file -> Properties().apply { file.inputStream().use { load(it) } } }

    fun meloxSigningValue(key: String): String? =
        providers.gradleProperty(key).orNull?.takeIf { it.isNotBlank() }
            ?: meloxKeystoreProperties?.getProperty(key)?.takeIf { it.isNotBlank() }

    val keystorePath = meloxSigningValue("meloxReleaseStoreFile")
    val keystorePassword = meloxSigningValue("meloxReleaseStorePassword")
    val keyAliasValue = meloxSigningValue("meloxReleaseKeyAlias")
    val keyPasswordValue = meloxSigningValue("meloxReleaseKeyPassword")
    val meloxReleaseSigning = if (
        keystorePath != null && keystorePassword != null && keyAliasValue != null && keyPasswordValue != null
    ) {
        signingConfigs.create("meloxRelease") {
            storeFile = rootProject.file(keystorePath)
            storePassword = keystorePassword
            keyAlias = keyAliasValue
            keyPassword = keyPasswordValue
            // minSdk is 26, so every device this app can run on understands APK
            // Signature Scheme v2 — AGP therefore never emits a v1 (JAR)
            // signature and silently ignores enableV1Signing. Spell out v2 + v3
            // explicitly anyway: v3 is what a hand-signed APK would carry and it
            // keeps key rotation possible. v4 (fs-verity) stays off — it needs a
            // matching .idsig and buys nothing for adb installs.
            enableV2Signing = true
            enableV3Signing = true
            enableV4Signing = false
        }
    } else null

    buildTypes {
        getByName("debug") {
            // 本地调试包必须能与「官方 MeloX 安卓版」同时安装。
            // 官方占 com.lladlam.melox.android（release key 签名），本工作区的
            // debug key 包因此落到 com.lladlam.melox.android.dev。
            // ⚠ 不要删这个后缀：同名不同签名会直接
            //   INSTALL_FAILED_UPDATE_INCOMPATIBLE，两个包只能留一个。
            // Manifest 的 authorities 全是 ${applicationId}.xxx 模板（.androidx-startup
            // / .fileprovider / .shizuku），换 id 自动错开，无需额外改动。
            // Override for same-phone LAN regression with a second debug install.
            applicationIdSuffix = providers.gradleProperty("meloxDebugApplicationIdSuffix").getOrElse(".dev")
        }

        // ── debugPerf：不卡的 debug ──────────────────────────────────────────
        // 名字带 debug 前缀：它本质是 debug 变体的优化版，不是第三种发布形态。
        // 用户实测「debug 明显比 release 卡」。原因不是代码，是变体配置：
        //   ① debuggable=true ⇒ ART 关闭 JIT/AOT 与 profile-guided 优化，全程走
        //      解释器友好但慢的路径。Compose 全是小方法，受影响最大。
        //   ② debug 没有 R8 ⇒ 无内联、无死代码消除、保留全部 Compose 诊断开销。
        // release 两条都占了，所以流畅。
        // debugPerf 把这两条都打开，同时沿用 debug 的包名与 debug key ⇒ 可以直接
        // 覆盖安装替换掉卡顿的 debug 包，不用卸载、不丢数据。
        //   构建：./gradlew :app:assembleDebugPerf --offline --no-daemon
        //   装机：adb install -r app/build/outputs/apk/debugPerf/app-debugPerf.apk
        // 桌面应用名：沿用 @string/app_name（多语言资源）。
        // ⚠ AGP sourceSet 不随 initWith 继承，目录名就是 buildType 名。
        create("debugPerf") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".dev"
            // ── 解除横竖屏限制（2026-10-08 用户要求，仅此一版）────────────────
            // 对齐 MeloX 原版：不锁竖屏，方向完全交给系统 / 传感器。
            // 生效的两处：① src/debugPerf/AndroidManifest.xml 把 MainActivity 的
            // screenOrientation 从 portrait 改成 unspecified
            //            ② MeloXApp.kt 跳过运行时 SENSOR/PORTRAIT 强制切换
            // ⛔ 不要把这一行挪到 debug / release —— 只这一版解锁。
            buildConfigField("boolean", "UNLOCK_ORIENTATION", "true")
            // ⚠ 核心开关：ART 恢复优化 + 手写 Baseline Profile 才会被真正用于 AOT。
            isDebuggable = false
            isMinifyEnabled = true
            // :baselineprofile（com.android.test）只暴露 dev / release 两种变体，
            // 新增的 debugPerf 必须声明回退，否则 baselineProfile 依赖解析直接失败。
            matchingFallbacks += listOf("dev", "debug")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }

        getByName("release") {
            meloxReleaseSigning?.let { signingConfig = it }

            // ── Package id ──────────────────────────────────────────────────
            // Published releases MUST keep com.lladlam.melox.android: previous
            // MeloX Android releases (0.6.5 and earlier) use that id and the same
            // MeloX signing certificate, so a suffix would install a second app
            // and existing users would never receive the update.
            // For a side-by-side test build pass
            // -PmeloxReleaseIdSuffix=.fork explicitly.
            val releaseIdSuffix = providers.gradleProperty("meloxReleaseIdSuffix").orNull.orEmpty()
            if (releaseIdSuffix.isNotBlank()) {
                applicationIdSuffix = releaseIdSuffix
            }

            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        resources.excludes += "/google/protobuf/**"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    // The bottom-chrome animation runs a per-frame Compose semantics diff
    // whenever a third-party accessibility service is bound. Device-driven
    // BaselineProfileGenerator is unusable on the target vivo (instrumentation
    // crashes, logcat blocked), so a hand-authored profile covering the
    // animation / recomposition / semantics / glass-shader hot path is
    // shipped instead. Regenerate with `tmp/gen_baseline_profile.py`.
    baselineProfile {
        saveInSrc = true
    }

    // Feed the authored profile into every variant that ships to a device.
    sourceSets {
        getByName("main") {
            resources.srcDir("src/main/baselineProfiles")
            // Translations live outside `res/` so a contributor can add one folder
            // per language without touching code or the default strings.
            // See `android/app/i18n/README.md`.
            res.srcDir("i18n")
            // ONNX Runtime native libraries, staged by the `downloadOnnxRuntime`
            // task (see the ONNX Runtime block at the top of this script).
            jniLibs.srcDir(onnxRuntimeJniLibsDir)
        }
    }

    // ── Never emit an unsigned release APK ──────────────────────────────────
    // Without a signing config AGP still reports success and writes
    // `app-release-unsigned.apk`, which can never be installed on any device.
    // That silent downgrade is exactly what made release installs fail.
    // Fail loudly and early instead, with instructions on how to supply the key.
    val meloxSigningHint = """
        Release build requested but no signing material is available.
        Provide one of the following:
          - android/keystore.properties (copy it plus the keystore/ directory to this machine)
          - -PmeloxReleaseStoreFile=... -PmeloxReleaseStorePassword=... -PmeloxReleaseKeyAlias=... -PmeloxReleaseKeyPassword=...
          - the same four keys in ${'$'}GRADLE_USER_HOME/gradle.properties
        Refusing to build an unsigned release APK.
    """.trimIndent()

    val meloxReleaseTaskRequested = gradle.startParameter.taskNames.any { task ->
        val name = task.substringAfterLast(':').lowercase()
        name.contains("release") || name == "build" || name == "bundle" ||
            name == "install" || name == "publish"
    }

    if (meloxReleaseSigning == null && meloxReleaseTaskRequested) {
        throw GradleException(meloxSigningHint)
    }

    // Belt and braces: the configure-time check above is skipped by tooling that
    // does not populate startParameter (notably IDE sync). Anchor a guard on the
    // release packaging tasks themselves.
    tasks.matching {
        it.name == "preReleaseBuild" || it.name == "assembleRelease" ||
            it.name == "bundleRelease" || it.name == "packageRelease"
    }.configureEach {
        doFirst {
            if (meloxReleaseSigning == null) throw GradleException(meloxSigningHint)
        }
    }
}

kotlin {
        compilerOptions {
            optIn.add("androidx.compose.material3.ExperimentalMaterial3Api")
        }
    }

dependencies {
    implementation("xyz.gianlu.librespot:librespot-lib:1.6.5")
    implementation(project(":innertube"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs_nio:2.1.5")

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
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
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
    implementation("androidx.media3:media3-datasource-okhttp:1.10.1")
    implementation("androidx.media3:media3-exoplayer:1.10.1")
    implementation("androidx.media3:media3-session:1.10.1")
    // Prebuilt Media3 FFmpeg audio decoder by Jellyfin (GPL-3.0); software FLAC fallback
    // for devices whose platform FLAC decoder is broken (QtiFlacDecoderWorkaround).
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.9.0+1")

    // AutoSync word-level lyric alignment. Inference is fully local (ONNX
    // Runtime on CPU); weights are supplied per language, never bundled here.
    // The runtime archive is fetched and checksum-verified by the
    // `downloadOnnxRuntime` task instead of being resolved as a Maven artifact.
    implementation(files(onnxRuntimeClassesJar).builtBy(downloadOnnxRuntime))

    implementation("io.coil-kt.coil3:coil-compose:3.5.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.5.0")
    implementation("com.squareup.okhttp3:okhttp:5.3.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:5.3.0")
    // LX Music-compatible user source scripts run in an isolated QuickJS context.
    implementation("wang.harlon.quickjs:wrapper-android:3.2.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.4")

    // External lyric integrations.
    implementation("io.github.proify.lyricon:provider:0.1.70")
    implementation("com.github.HChenX:SuperLyricApi:3.4")
    // HyperOS Focus / Super Island notification payload builder.
    implementation("com.xzakota.hyper.notification:focus-api:1.4")
    // Optional non-root HyperOS compatibility path. If Shizuku is unavailable or
    // permission is denied, MeloX continues publishing Focus notifications directly.
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
    implementation("org.lsposed.hiddenapibypass:hiddenapibypass:6.1")
    // bzip2 decoder for the BSDIFF40 incremental-update patches.
    implementation("org.apache.commons:commons-compress:1.27.1")
    // Framework Binder signatures used only at compile time. Android supplies the
    // real hidden interfaces at runtime; this module is never packaged in the APK.
    compileOnly(project(":hidden-api"))

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")

    debugImplementation("androidx.compose.ui:ui-tooling")
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    baselineProfile(project(":baselineprofile"))
}
