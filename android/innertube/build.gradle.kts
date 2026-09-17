plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.metrolist.innertube"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    buildTypes {
        // The app builds `dev` as well as debug and release, and a library that
        // does not declare it has no variant Gradle can match against — see
        // the app's own buildTypes for what `dev` is for. Nothing is configured
        // here: a library has no shrinker or signing of its own to set.
        create("dev") {
            initWith(getByName("release"))
        }
    }

    compileOptions {
        // The module reads java.time; on API 26 most of it exists, but the
        // upstream sources assume the desugared library and this is not the
        // place to find out which calls do not.
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }
}

dependencies {
    // Exposed rather than hidden: this module's public functions return
    // `Result<HttpResponse>`, so anything calling them needs the type on its
    // own classpath.
    api(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.ktor.client.encoding)
    implementation(libs.brotli)
    api(libs.newpipe.extractor) {
        exclude(group = "com.google.protobuf")
    }
    implementation(libs.timber)

    coreLibraryDesugaring(libs.desugaring)
}
