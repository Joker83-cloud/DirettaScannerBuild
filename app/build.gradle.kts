plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.joker.direttascannerbuild"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.joker.direttascannerbuild"
        minSdk = 24
        targetSdk = 35
        versionCode = 139
        versionName = "0.13.9-snai"
    }

    signingConfigs {
        create("stableDebug") {
            storeFile = rootProject.file("stable-debug.keystore")
            storePassword = "android"
            keyAlias = "direttascanner"
            keyPassword = "android"
        }
    }

    buildTypes {
        getByName("debug") {
            signingConfig = signingConfigs.getByName("stableDebug")
        }
        release { isMinifyEnabled = false }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.0")
}
