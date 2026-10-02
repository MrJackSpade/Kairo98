plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

apply(from = rootProject.file("shared/gradle/android-module.gradle"))

android {
    androidResources { noCompress += listOf("json", "idx") }
    namespace = "com.mrjackspade.kairo98"

    defaultConfig {
        applicationId = providers.gradleProperty("kairo98ApplicationId").orNull ?: "com.loxifi.kairo98"
        versionCode = providers.gradleProperty("kairo98VersionCode").orNull?.toInt() ?: 907
        versionName = providers.gradleProperty("kairo98VersionName").orNull ?: "0.9.7"
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_static"
                providers.gradleProperty("kairo98PgoMode").orNull?.let {
                    arguments += "-DKAIRO98_PGO_MODE=$it"
                }
                providers.gradleProperty("kairo98PgoProfile").orNull?.let {
                    arguments += "-DKAIRO98_PGO_PROFILE=$it"
                }
                if (providers.gradleProperty("kairo98EgcVerify").orNull == "true") {
                    arguments += "-DKAIRO98_EGC_VERIFY=ON"
                }
                if (providers.gradleProperty("kairo98GpuVerify").orNull == "true") {
                    arguments += "-DKAIRO98_GPU_VERIFY=ON"
                }
                if (providers.gradleProperty("kairo98SynthVerify").orNull == "true") {
                    arguments += "-DKAIRO98_SYNTH_VERIFY=ON"
                }
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
            if (!providers.environmentVariable("KAIRO98_BETA_KEYSTORE").orNull.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("beta")
            }
        }
    }

    flavorDimensions += "artwork"
    productFlavors {
        create("withImages") {
            dimension = "artwork"
            resValue("bool", "catalog_art_download_enabled", "true")
        }
        create("withoutImages") {
            dimension = "artwork"
            resValue("bool", "catalog_art_download_enabled", "true")
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
        }
    }
}

android.sourceSets.getByName("main").assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/catalog-assets")))
android.sourceSets.getByName("withImages").assets.setSrcDirs(listOf(rootProject.file("catalog/artwork")))
val catalogAssets by tasks.registering(Sync::class) {
    from("src/main/assets") { exclude("catalog/**") }
    from(rootProject.file("catalog/parts")) {
        into("catalog")
        if (providers.gradleProperty("kairoDistribution").orNull == "play") exclude("art.nsfw.*")
    }
    into(layout.buildDirectory.dir("generated/catalog-assets"))
}
tasks.named("preBuild") { dependsOn(catalogAssets) }

dependencies {
    implementation(project(":frontend"))
}
