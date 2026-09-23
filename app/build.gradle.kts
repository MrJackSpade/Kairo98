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
        versionCode = 1
        versionName = "0.1.0-dev"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
