package com.psplauncher.feature.settings.ui

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.psplauncher.core.domain.model.GamepadAction
import kotlinx.coroutines.launch

@Composable
fun CreditsSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Pure info screen — no interactive rows for the scaffold's focus navigation to walk, so
    // Up/Down scroll the column directly instead.
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val stepPx = with(LocalDensity.current) { 120.dp.toPx() }

    SettingsScaffold(
        title = "Settings",
        subtitle = "Credits",
        onBack = onBack,
        modifier = modifier,
        onInterceptAction = { action ->
            when (action) {
                GamepadAction.NAVIGATE_UP   -> { scope.launch { scrollState.animateScrollBy(-stepPx) }; true }
                GamepadAction.NAVIGATE_DOWN -> { scope.launch { scrollState.animateScrollBy(stepPx) }; true }
                else -> false
            }
        },
    ) {
        // Credits has no focusable rows — it scrolls as a whole — so this registration is purely
        // what lets the scaffold's header and footer drag it. The screen keeps owning the state
        // itself because onInterceptAction above animates the same one for UP/DOWN.
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 48.dp),
        ) {
            SettingsGroup("XMB Design — Sony")

            CreditParagraph(
                "The look and feel of this launcher is inspired by the XMB (XrossMediaBar), the " +
                    "interface Sony created for the PlayStation Portable and PlayStation 3 — the " +
                    "cross-bar layout, the flowing wave, and the navigation model are all homages to it."
            )
            CreditParagraph(
                "\"XrossMediaBar\", \"XMB\", \"PSP\" and \"PlayStation\" are trademarks of Sony " +
                    "Interactive Entertainment Inc. PSPLauncher is an independent, non-commercial " +
                    "fan project — not affiliated with, endorsed by, or sponsored by Sony. Bundled UI " +
                    "artwork comes from the community \"XMB Menu for ES-DE\" theme (credited below) " +
                    "and remains the property of its respective authors. No Sony audio is bundled: " +
                    "every menu sound is credited separately below."
            )

            Spacer(Modifier.height(16.dp))
            SettingsGroup("The Wave")

            CreditParagraph(
                "The animated wave behind the crossbar is ported from Mart's PlayStation-3-XMB, a " +
                    "web recreation of the PS3 wave. The strand model and its motion are theirs; the " +
                    "AGSL shader, the colour cascade and the wallpaper tinting are this project's."
            )
            CreditLine("Project", "PlayStation-3-XMB")
            CreditLine("Author", "Mart — github.com/linkev/PlayStation-3-XMB")
            CreditLine("License", "MIT — Copyright (c) 2025 Mart")
            CreditParagraph(
                "The MIT licence requires its copyright notice to travel with the software, so the " +
                    "full text ships in the app's source at LICENSES/PlayStation-3-XMB-MIT.txt."
            )

            Spacer(Modifier.height(16.dp))
            SettingsGroup("System & Console Artwork")

            CreditParagraph(
                "The system, console and category icons used throughout this launcher are from " +
                    "the \"XMB Menu for ES-DE\" theme — a community recreation of the PSP XMB."
            )
            CreditParagraph(
                "All rights to this artwork belong to its creators — Anthony Caccese, building on the " +
                    "original work by InitialDin. The icons are used here with gratitude and remain the " +
                    "property of their respective authors."
            )
            CreditLine("Project", "XMB Menu for ES-DE")
            CreditLine("Authors", "Anthony Caccese · InitialDin")
            CreditLine("Source", "github.com/anthonycaccese/xmb-menu-es-de")

            Spacer(Modifier.height(16.dp))
            SettingsGroup("Controller Button Icons")

            CreditParagraph(
                "Every on-screen button prompt — the PlayStation, Xbox and Nintendo face buttons, " +
                    "D-pads, bumpers, triggers, sticks and system buttons — is drawn from Zacksly's " +
                    "button icon packs, used under the Creative Commons Attribution 3.0 license."
            )
            CreditParagraph(
                "The artwork is unmodified: only the file names were changed to Android resource " +
                    "names, and just the icons the launcher renders are bundled."
            )
            CreditLine("Author", "Zacksly")
            CreditLine("Website", "zacksly.itch.io")
            CreditLine("Support", "patreon.com/zacksly")
            CreditLine(
                "Packs",
                "PS5 Button Icons and Controls · Xbox Series Button Icons and Controls · " +
                    "Switch 2 Button Icons and Controls"
            )
            CreditLine("License", "CC BY 3.0 — creativecommons.org/licenses/by/3.0")

            Spacer(Modifier.height(16.dp))
            SettingsGroup("Menu Sounds")

            CreditParagraph(
                "The bundled menu sounds — navigation, back, confirm, error, launch, notification " +
                    "and the boot chime — are built from royalty-free audio published on Pixabay, " +
                    "edited for the launcher: trimmed, re-pitched, re-levelled and converted. Thanks " +
                    "to the creators whose work the set is built from."
            )
            CreditParagraph(
                "The Pixabay Content License does not require attribution; it is given here with " +
                    "thanks anyway. If you are one of these creators and would like the credit " +
                    "changed or an asset removed, please reach out."
            )
            CreditLine("Source", "Pixabay — pixabay.com")
            CreditLine("License", "Pixabay Content License — pixabay.com/service/license-summary")
            CreditLine("Luca di Alessandro", "pixabay.com/users/lucadialessandro-25927643")
            CreditLine("SoundReality", "pixabay.com/users/soundreality-31074404")
            CreditLine("Musheran", "pixabay.com/users/musheran-40634446")
            CreditLine("Universfield", "pixabay.com/users/universfield-28281460")

            Spacer(Modifier.height(16.dp))
            SettingsGroup("Game Artwork & Metadata")

            CreditParagraph(
                "Box art, 3D boxes, cartridge/disc shots, hero banners, logos, icons, manuals, " +
                    "video snaps and game metadata are fetched at your request from third-party " +
                    "providers and remain the property of their respective owners."
            )
            CreditLine("Primary scraper", "ScreenScraper — screenscraper.fr, community-maintained game media database")
            CreditLine("Artwork", "SteamGridDB — steamgriddb.com")
            CreditLine("Metadata", "IGDB")
            CreditLine("Film posters", "TMDB — themoviedb.org")
            CreditParagraph(
                "This product uses the TMDB API but is not endorsed or certified by TMDB."
            )


            Spacer(Modifier.height(16.dp))
            SettingsGroup("Design Influence")
            CreditParagraph(
                "Several screens were designed by studying NeoStation: the Last Played home page, " +
                    "the paged detail panel, and the Artwork Studio's scraping panel all owe it their " +
                    "shape. No code and no artwork were taken — the influence is on layout and " +
                    "interaction, and it is named here because it earned the mention."
            )
            CreditLine("Project", "NeoStation")

            Spacer(Modifier.height(16.dp))
            SettingsGroup("Notes")
            CreditParagraph(
                "If you are a rights holder and would like attribution changed or any asset removed, " +
                    "please reach out and it will be addressed promptly."
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun CreditParagraph(text: String) {
    Text(
        text = text,
        color = SettingsText,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        modifier = Modifier.padding(vertical = 8.dp),
    )
}

@Composable
private fun CreditLine(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 6.dp)) {
        Text(text = label.uppercase(), color = SettingsAccent, fontSize = 10.sp)
        Text(text = value, color = SettingsText, fontSize = 14.sp)
    }
}
