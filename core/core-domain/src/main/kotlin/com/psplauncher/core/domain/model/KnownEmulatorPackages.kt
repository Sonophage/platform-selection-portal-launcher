package com.psplauncher.core.domain.model

object PcRuntimes {
    val PACKAGES: Map<String, String> = mapOf(
        "app.gamenative"      to "GameNative",
        "gamehub.lite"        to "GameHub Lite",
        "banner.hub"          to "BannerHub",
        "com.xiaoji.egggame"  to "EggNS",
        "org.force9.starboard" to "Starboard",
    )

    const val WINLATOR_FAMILY = "com.winlator"
}

object KnownEmulatorPackages {
    private val families = setOf(
        "com.retroarch",
        PcRuntimes.WINLATOR_FAMILY,
        "xyz.aethersx2",
        "org.ppsspp",
    )

    private val packages = setOf(

        "org.vita3k.emulator", "org.vita3k.emulator.ikhoeyZX", "com.sbro.emucorev",
        "com.github.stenzek.duckstation", "com.epsxe.ePSXe", "com.emulator.fpse",
        "com.emulator.fpse64", "com.nanodata.armsx",
        "com.armsx2", "come.nanodata.armsx2", "come.nanodata.armsx2.debug",
        "com.virtualapplications.play", "com.sbro.emucorex",
        "aenu.aps3e", "aenu.aps3e.premium", "com.armsx3", "net.rpcsx",

        "me.magnum.melonds", "me.magnum.melonds.nightly", "me.magnum.melondualds",
        "com.dsemu.drastic", "com.hydra.noods", "com.sky.SkyEmu",
        "org.dolphinemu.dolphinemu", "org.mm.jr", "org.dolphinemu.mmjr",
        "org.dolphinemu.primehack", "org.shiiion.primehack",
        "info.cemu.cemu", "info.cemu.Cemu",
        "org.azahar_emu.azahar", "io.github.azaharplus.android", "org.citra.citra_emu",
        "org.citra.citra_emu.canary", "org.citra.emu", "io.github.lime3ds.android",
        "io.github.mandarine3ds.mandarine", "io.github.borked3ds.android", "com.panda3ds.pandroid",
        "dev.eden.eden_emulator", "dev.eden.eden_emulator.nightly", "dev.legacy.eden_emulator",
        "org.yuzu.yuzu_emu", "org.yuzu.yuzu_emu.ea", "org.sudachi.sudachi_emu",
        "org.sudachi.sudachi_emu.ea", "org.citron.citron_emu", "org.citron.citron_emu.ea",
        "com.sumi.SumiEmulator", "org.uzuy.uzuy_emu", "org.uzuy.uzuy_emu.ea",
        "org.uzuy.uzuy_emu.mmjr", "org.suyu.suyu_emu", "org.kenjinx.android",
        "org.benjisc.android", "skyline.emu",
        "io.mgba", "com.mgba.mgba", "com.fastemulator.gba", "com.fastemulator.gbafree",
        "com.fastemulator.gbc", "it.dbtecno.pizzaboygbapro", "it.dbtecno.pizzaboygba",
        "it.dbtecno.pizzaboypro", "it.dbtecno.pizzaboy", "com.pixelrespawn.linkboy",
        "org.mupen64plusae.v3.fzurita.pro", "org.mupen64plusae.v3.fzurita",
        "org.mupen64plusae.v3.fzurita.amazon", "org.mupen64plusae.v3.alpha",
        "com.fms.ines.free", "com.simongellis.vvb",

        "it.dbtecno.pizzaboyscpro", "it.dbtecno.pizzaboyscbasic", "com.fms.mg",
        "org.devmiyax.yabasanshioro2.pro", "org.devmiyax.yabasanshioro2",
        "com.flycast.emulator", "com.flycast.emulator.gles2", "io.recompiled.redream",
        "com.seleuco.mame4d2024", "com.seleuco.mame4droid",

        "com.explusalpha.GbaEmu", "com.explusalpha.GbcEmu", "com.explusalpha.NesEmu",
        "com.explusalpha.Snes9xPlus", "com.explusalpha.MdEmu", "com.explusalpha.SaturnEmu",
        "com.PceEmu", "com.explusalpha.NeoEmu", "com.explusalpha.NgpEmu",
        "com.explusalpha.SwanEmu", "com.explusalpha.LynxEmu", "com.explusalpha.A2600Emu",
        "com.explusalpha.C64Emu",

        "com.izzy2lost.x1box", "emu.x360.mobile", "aenu.ax360e", "aenu.ax360e.free",

        "com.github.eka2l1", "org.scummvm.scummvm", "io.wip.pico8", "com.rfandango.haku_x",
    ) + PcRuntimes.PACKAGES.keys

    fun isEmulator(packageName: String): Boolean =
        packageName in packages ||
            families.any { packageName == it || packageName.startsWith("$it.") }
}
