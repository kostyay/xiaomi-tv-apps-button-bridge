plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
}

android {
    namespace = "com.kostyay.xiaomiappsbridge"
    compileSdk = 36

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "com.kostyay.xiaomiappsbridge"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "2.0.0"
    }
}

ktlint {
    version.set("1.8.0")
    android.set(true)
    outputToConsole.set(true)
}

dependencies {
    implementation("com.flyfishxu:kadb:2.1.1")
}
