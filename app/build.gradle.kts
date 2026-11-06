plugins {
    id("com.android.application")
}

android {
    namespace = "com.dnsorder.wififix"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dnsorder.wififix"
        minSdk = 31
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Debug-key signing so the release APK installs without a keystore.
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // Provided by the framework (Vector) at runtime.
    compileOnly("io.github.libxposed:api:102.0.0")
}
