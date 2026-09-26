package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnownEmulatorPackagesTest {
    @Test
    fun `real emulator packages the old prefix list missed are tagged`() {
        listOf(
            "io.mgba",
            "com.github.stenzek.duckstation",
            "xyz.aethersx2.android",
            "com.armsx2",
            "com.nanodata.armsx",
            "com.armsx3",
            "com.izzy2lost.x1box",
            "net.rpcsx",
            "org.scummvm.scummvm",
            "org.azahar_emu.azahar",
        ).forEach { assertTrue(KnownEmulatorPackages.isEmulator(it), it) }
    }

    @Test
    fun `families match their variants on a dot boundary only`() {
        assertTrue(KnownEmulatorPackages.isEmulator("com.retroarch"))
        assertTrue(KnownEmulatorPackages.isEmulator("com.retroarch.aarch64"))
        assertTrue(KnownEmulatorPackages.isEmulator("com.winlator.cmod"))
        assertTrue(KnownEmulatorPackages.isEmulator("xyz.aethersx2.tturnip"))

        assertFalse(KnownEmulatorPackages.isEmulator("com.retroarchive.reader"))
    }

    @Test
    fun `every PC runtime is tagged as an emulator, by name and by family`() {
        PcRuntimes.PACKAGES.keys.forEach {
            assertTrue(KnownEmulatorPackages.isEmulator(it), it)
        }
        assertTrue(KnownEmulatorPackages.isEmulator(PcRuntimes.WINLATOR_FAMILY))
        assertTrue(KnownEmulatorPackages.isEmulator("com.winlator.cmod"))
        assertFalse(PcRuntimes.WINLATOR_FAMILY in PcRuntimes.PACKAGES)
    }

    @Test
    fun `every PC runtime has a name to show`() {
        PcRuntimes.PACKAGES.forEach { (pkg, name) ->
            assertTrue(name.isNotBlank(), "$pkg has no display name")
        }
    }

    @Test
    fun `streaming clients and frontends are not emulators`() {
        listOf(
            "com.limelight",
            "com.limelight.noir",
            "com.magneticchen.daijishou",
            "org.es_de.frontend",
            "com.android.chrome",
        ).forEach { assertFalse(KnownEmulatorPackages.isEmulator(it), it) }
    }
}
