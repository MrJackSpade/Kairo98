package com.mrjackspade.kairo98

import com.mrjackspade.kairo.LaunchTablesTest
import java.io.File

/** What Kairo98 builds as it launches (Kairo#125, Kairo98#118). */
class Pc98LaunchTablesTest : LaunchTablesTest() {
    override fun tables() = LaunchTables(listOf(Pc98Media.format), listOf(Pc98KeyboardLayout.value),
        listOf(Pc98Firmware.set(File("firmware"), {}) { null }),
        mapOf("key names" to { Pc98KeyNames }, "controller bindings" to { ControllerBindings }))
}
