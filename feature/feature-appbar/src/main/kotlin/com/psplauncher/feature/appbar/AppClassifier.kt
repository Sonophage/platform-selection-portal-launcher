package com.psplauncher.feature.appbar

import android.content.pm.ApplicationInfo
import javax.inject.Inject
import javax.inject.Singleton

// Category ids these match the seeded built-in categories in CategoryRepositoryImpl.
object AppCategoryIds {
    const val PHOTO     = "photos"
    const val MUSIC     = "music"
    const val VIDEO     = "videos"
    const val NETWORK   = "network"
}

// Produces the DEFAULT category placement for an installed app. This is only a starting
// point — once the user customizes an app's placement, AppCategoryRepository ignores this.
//
// Resolution order: curated package database → Android system category → label keywords.
@Singleton
class AppClassifier @Inject constructor() {

    fun defaultCategories(app: InstalledApp): Set<String> {
        // 1) Curated database — exact or prefix match wins.
        curatedCategory(app.packageName)?.let { return setOf(it) }

        // 2) Android-declared application category.
        systemCategory(app.systemCategory)?.let { return setOf(it) }

        // 3) Label keyword fallback (e.g. generic "Browser").
        labelCategory(app.label)?.let { return setOf(it) }

        return emptySet()   // unclassified — remains reachable via the App Drawer
    }

    private fun curatedCategory(pkg: String): String? {
        val p = pkg.lowercase()
        CURATED.forEach { (categoryId, prefixes) ->
            if (prefixes.any { p == it || p.startsWith("$it.") || p.startsWith(it) }) return categoryId
        }
        return null
    }

    private fun systemCategory(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_VIDEO  -> AppCategoryIds.VIDEO
        ApplicationInfo.CATEGORY_AUDIO  -> AppCategoryIds.MUSIC
        ApplicationInfo.CATEGORY_IMAGE  -> AppCategoryIds.PHOTO
        else                            -> null
    }

    private fun labelCategory(label: String): String? {
        val l = label.lowercase()
        return when {
            l.contains("browser")                      -> AppCategoryIds.NETWORK
            else                                       -> null
        }
    }

    private companion object {
        // categoryId → known package-name prefixes
        val CURATED: Map<String, List<String>> = mapOf(
            AppCategoryIds.VIDEO to listOf(
                "com.google.android.youtube", "com.google.android.apps.youtube",
                "com.netflix.mediaclient", "com.hulu.plus", "com.disney.disneyplus",
                "com.amazon.avod", "com.plexapp.android", "org.videolan.vlc",
                "org.xbmc.kodi", "com.mxtech.videoplayer", "com.crunchyroll.crunchyroid",
                "tv.twitch.android.app", "com.hbo.hbonow", "com.wbd.stream",
                "com.google.android.videos", "com.spotify.tv.android",
            ),
            AppCategoryIds.NETWORK to listOf(
                "com.android.chrome", "com.google.android.apps.chrome",
                "org.mozilla.firefox", "org.mozilla.fenix", "com.brave.browser",
                "com.microsoft.emmx", "com.opera.browser", "com.opera.mini.native",
                "com.duckduckgo.mobile.android", "com.sec.android.app.sbrowser",
                "com.UCMobile.intl", "com.kiwibrowser.browser", "org.torproject.torbrowser",
                "mark.via", "com.android.browser",
                // ── Remote play ──────────────────────────────────────────────────────────
                // Streaming clients belong in Network, not Game: they need a connection before
                // they need a controller, and they run nothing on this device. A PSP owner looks
                // under Network for "things that talk to something else".
                //
                // Verified installed on the test tablet: com.limelight (which by prefix also
                // covers com.limelight.noir) and com.boosteroid.streaming. The rest are taken
                // from each app's published id and have NOT been seen on a device here -- a wrong
                // prefix classifies nothing and fails silently, so they are listed one per line
                // with the app named, and AppClassifierTest pins each one.
                "com.limelight",                    // Moonlight (and Moonlight Noir)
                "com.boosteroid.streaming",         // Boosteroid
                "com.metallic.chiaki",              // Chiaki (PS4/PS5 remote play)
                "com.valvesoftware.steamlink",      // Steam Link
                "com.nvidia.geforcenow",            // GeForce NOW
                "com.microsoft.xcloud",             // Xbox Cloud Gaming
                "com.parsecgaming.parsec",          // Parsec
            ),
            AppCategoryIds.MUSIC to listOf(
                "com.spotify.music", "com.google.android.apps.youtube.music",
                "com.amazon.mp3", "com.apple.android.music", "deezer.android.app",
                "com.soundcloud.android", "com.pandora.android", "tunein.player",
                "com.maxmpz.audioplayer", "org.videolan.vlc.music",
            ),
            AppCategoryIds.PHOTO to listOf(
                "com.google.android.apps.photos", "com.sec.android.gallery3d",
                "com.instagram.android", "com.pinterest", "com.adobe.lrmobile",
                "com.google.android.GoogleCamera", "net.sourceforge.opencamera",
            ),
        )
    }
}
