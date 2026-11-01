plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.adam.youder"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.adam.youder"
        minSdk = 29 // Android 10+
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
        // Hanya HP 64-bit modern; menghemat ± 30 MB
        ndk { abiFilters += "arm64-v8a" }
    }

    signingConfigs {
        // shortcut: password ditulis di sini karena APK hanya untuk pemakaian pribadi
        create("release") {
            storeFile = file("../youder.jks")
            storePassword = "youder123"
            keyAlias = "youder"
            keyPassword = "youder123"
        }
    }

    buildTypes {
        release {
            // R8 dimatikan: youtubedl-android memakai refleksi (Jackson), dan ukuran APK didominasi native lib
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    // youtubedl-android menjalankan Python/FFmpeg dari file .so yang harus diekstrak
    packaging { jniLibs.useLegacyPackaging = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("io.github.junkfood02.youtubedl-android:library:0.18.1")
    implementation("io.github.junkfood02.youtubedl-android:ffmpeg:0.18.1")
}
