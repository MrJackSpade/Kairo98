plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.mrjackspade.kairo98"
    compileSdk = 36
    ndkVersion = "28.2.13676358"

    defaultConfig {
        applicationId = "com.mrjackspade.kairo98"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("kairo98VersionCode").orNull?.toInt() ?: 1
        versionName = providers.gradleProperty("kairo98VersionName").orNull ?: "0.1.0-dev"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    signingConfigs {
        create("beta") {
            val keystore = providers.environmentVariable("KAIRO98_BETA_KEYSTORE").orNull
            val password = providers.environmentVariable("KAIRO98_BETA_PASSWORD").orNull
            if (!keystore.isNullOrBlank() && !password.isNullOrBlank()) {
                storeFile = file(keystore)
                storePassword = password
                keyAlias = "kairo98-beta"
                keyPassword = password
            }
        }
    }

    buildTypes {
        getByName("debug") {
            if (!providers.environmentVariable("KAIRO98_BETA_KEYSTORE").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("beta")
            }
        }
        release {
            isMinifyEnabled = false
        }
    }

    flavorDimensions += "artwork"
    productFlavors {
        create("withImages") {
            dimension = "artwork"
            resValue("bool", "catalog_art_download_enabled", "false")
        }
        create("withoutImages") {
            dimension = "artwork"
            resValue("bool", "catalog_art_download_enabled", "true")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

kotlin {
    jvmToolchain(17)
}
