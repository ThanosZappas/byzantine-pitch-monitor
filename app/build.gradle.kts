plugins {
    id("com.android.application")
}

android {
    namespace = "app.pitchmonitor.byz"
    compileSdk = 37

    defaultConfig {
        applicationId = "app.pitchmonitor.byz"
        minSdk = 29
        targetSdk = 36
        versionCode = 37
        versionName = "1.6.0"
    }

    buildTypes {
        debug {
            // Installs next to the 1.6.0 APK on the phone so both can be compared.
            applicationIdSuffix = ".dev"
            resValue("string", "app_label", "Byzantine Pitch Monitor (dev)")
        }
        release {
            isMinifyEnabled = false
            resValue("string", "app_label", "Byzantine Pitch Monitor")
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        resValues = true
    }
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.8.0")
    implementation("com.google.android.material:material:1.12.0")
    testImplementation("junit:junit:4.13.2")
}
