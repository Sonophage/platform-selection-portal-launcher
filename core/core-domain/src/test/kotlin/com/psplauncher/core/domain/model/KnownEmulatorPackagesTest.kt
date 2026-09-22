package com.psplauncher.core.domain.model

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class KnownEmulatorPackagesTest {

    @Test
    fun `real emulator packages the old prefix list missed are tagged`() {
        listOf(
            "io.mgba",                          // was "com.mgba"
            "com.github.stenzek.duckstation",   // was "com.duckstation"
            "xyz.aethersx2.android",            // NetherSX2 — was "com.nethersx2"
            "com.armsx2",
            "com.nanodata.armsx",               // ARMSX1
            "com.armsx3",
            "com.izzy2lost.x1box",              // X1 BOX (xemu)
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
        // A shared stem is not a family member.
        assertFalse(KnownEmulatorPackages.isEmulator("com.retroarchive.reader"))
    }

    @Test
    fun `every PC runtime is tagged as an emulator, by name and by family`() {
        // PcRuntimes is the one list; KnownEmulatorPackages folds it in. They were the same
        // literals written twice, and the drawer's EMU badge is what silently went missing when
        // one gained an entry. Winlator is deliberately not in the map -- every fork renames
        // itself, so it needs the prefix rule, which the map cannot express.
        PcRuntimes.PACKAGES.keys.forEach {
            assertTrue(KnownEmulatorPackages.isEmulator(it), it)
        }
        assertTrue(KnownEmulatorPackages.isEmulator(PcRuntimes.WINLATOR_FAMILY))
        assertTrue(KnownEmulatorPackages.isEmulator("com.winlator.cmod"))
        assertFalse(PcRuntimes.WINLATOR_FAMILY in PcRuntimes.PACKAGES)
    }

    @Test
    fun `every PC runtime has a name to show`() {
        // The Emulators screen prints these. A blank one is a row that says nothing.
        PcRuntimes.PACKAGES.forEach { (pkg, name) ->
            assertTrue(name.isNotBlank(), "$pkg has no display name")
        }
    }

    @Test
    fun `streaming clients and frontends are not emulators`() {
        listOf(
            "com.limelight",                    // Moonlight
            "com.limelight.noir",               // Artemis
            "com.magneticchen.daijishou",
            "org.es_de.frontend",
            "com.android.chrome",
        ).forEach { assertFalse(KnownEmulatorPackages.isEmulator(it), it) }
    }
}
