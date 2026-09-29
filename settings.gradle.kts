apply(from = "shared/gradle/android-settings.gradle")

rootProject.name = "Kairo98"
include(":kairo98", ":frontend")
project(":frontend").projectDir = file("shared/frontend")
