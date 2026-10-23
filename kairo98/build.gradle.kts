plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

apply(from = rootProject.file("shared/gradle/android-module.gradle"))
apply(from = rootProject.file("shared/gradle/catalog-binary.gradle"))
@Suppress("UNCHECKED_CAST")
val compileCatalogParts = extra["compileCatalogParts"] as groovy.lang.Closure<Unit>

android {
    androidResources { noCompress += listOf("json", "idx", "bin") }
    namespace = "com.mrjackspade.kairo98"

    defaultConfig {
        applicationId = providers.gradleProperty("kairo98ApplicationId").orNull ?: "com.loxifi.kairo98"
        versionCode = providers.gradleProperty("kairo98VersionCode").orNull?.toInt() ?: 915
        versionName = providers.gradleProperty("kairo98VersionName").orNull ?: "0.9.15"
        manifestPlaceholders["kairoLabel"] = "Kairo98"
        // Google Play updates its builds itself, so they never offer GitHub releases (shared AppUpdateCheck).
        if (providers.gradleProperty("kairoDistribution").orNull == "play") resValue("bool", "kairo_update_check", "false")
        ndk {
            abiFilters += "arm64-v8a"
        }
        externalNativeBuild {
            cmake {
                arguments += "-DANDROID_STL=c++_shared"
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
        include("*.idx")
        if (providers.gradleProperty("kairoDistribution").orNull == "play") exclude("*.nsfw.*")
    }
    inputs.dir(rootProject.file("catalog/parts"))
    into(layout.buildDirectory.dir("generated/catalog-assets"))
    doLast {
        compileCatalogParts.call(rootProject.file("catalog/parts"),
            layout.buildDirectory.dir("generated/catalog-assets/catalog").get().asFile)
    }
}
tasks.named("preBuild") { dependsOn(catalogAssets) }

dependencies {
    implementation(project(":frontend"))
}

extra["kairoLabel"] = "Kairo98"
apply(from = rootProject.file("shared/gradle/kairo-uitest.gradle"))
