buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        // Kotlin compiler used by the Android plugin's built-in Kotlin support.
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.21")
    }
}

plugins {
    id("com.android.application") version "9.5.1" apply false
}
