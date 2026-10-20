plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.doz.minihome"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.doz.minihome"
        minSdk = 22
        targetSdk = 30
        versionCode = 1
        versionName = "1.0"
    }
    // Fixed debug key so CI builds can be installed over each other.
    signingConfigs {
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
