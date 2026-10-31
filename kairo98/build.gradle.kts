plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

extra["kairoProduct"] = "kairo98"
extra["kairoLabel"] = "Kairo98"
extra["kairoCatalogRules"] = "com.mrjackspade.kairo98.Pc98Catalog"
extra["kairoChd"] = true
apply(from = rootProject.file("shared/gradle/kairo-app.gradle"))

android {
    defaultConfig {
        externalNativeBuild {
            cmake {
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
}
