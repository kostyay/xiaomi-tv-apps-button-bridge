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
        versionCode = providers.gradleProperty("VERSION_CODE").getOrElse("2").toInt()
        versionName = providers.gradleProperty("VERSION_NAME").getOrElse("2.0.0")
    }

    val releaseKeystore = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
    if (releaseKeystore != null) {
        signingConfigs {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("RELEASE_STORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").get()
            }
        }
        buildTypes.getByName("release") {
            signingConfig = signingConfigs.getByName("release")
        }
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
