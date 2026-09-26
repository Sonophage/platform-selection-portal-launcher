package com.psplauncher.feature.settings.ui

import com.psplauncher.core.domain.model.VideoSnapPlacement
import com.psplauncher.core.domain.model.IconDisplayMode
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.feature.settings.viewmodel.CredentialField
import com.psplauncher.feature.settings.viewmodel.ArtworkSettingsViewModel

enum class ArtworkSection { ARTWORK, SOURCES }

@Composable
fun ArtworkSettingsScreen(
    onBack: () -> Unit,
    section: ArtworkSection? = null,
    modifier: Modifier = Modifier,
    viewModel: ArtworkSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    var showImport by remember { mutableStateOf(false) }
    if (showImport) {
        ArtworkImportScreen(onBack = { showImport = false }, modifier = modifier)
        return
    }

    val sgdbKeyDraft = state.drafts.sgdbKey
    val igdbClientIdDraft = state.drafts.igdbClientId
    val igdbClientSecretDraft = state.drafts.igdbClientSecret
    val ssUsernameDraft = state.drafts.ssUsername
    val ssPasswordDraft = state.drafts.ssPassword

    val credentialsFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadDebugCredentials(it) }
    }

    SettingsPageScaffold(
        subtitle = when (section) {
            ArtworkSection.SOURCES -> "Scraping Sources"
            else                   -> "Artwork"
        },
        onBack   = onBack,
        modifier = modifier,
    ) {
        val scrollState = rememberScrollState()
        LocalSettingsScrollStateRegistrar.current(scrollState)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            if (com.psplauncher.feature.settings.BuildConfig.DEBUG && state.debugCredentialsAvailable) {
                SettingsGroup("Debug")

                SettingsRow(
                    label    = "Load Credentials File",
                    sublabel = "Fill every artwork and achievement credential from a .properties file " +
                        "(steamgriddb.apiKey, igdb.*, screenscraper.*)",
                    onClick  = { credentialsFilePicker.launch(arrayOf("*/*")) },
                )

                state.debugCredentialsStatus?.let {
                    SettingsRow(
                        label    = it,
                        sublabel = "Tap to dismiss",
                        onClick  = { viewModel.dismissDebugCredentialsStatus() },
                    )
                }
            }

            if (section == null || section == ArtworkSection.ARTWORK) {
                SettingsGroup("Artwork Library")

                SettingsRow(
                    label    = if (state.artworkFolderGrantDead) "Artwork Folder & Import  ⚠" else "Artwork Folder & Import",
                    sublabel = if (state.artworkFolderGrantDead)
                        "Access to your artwork folder was lost — open to re-link it"
                    else "Choose where artwork is stored and import existing artwork from ES-DE",
                    onClick  = { showImport = true },
                )

                SettingsGroup("Library Artwork Status")

                SettingsValueRow(label = "Total Games",      value = state.status.total.toString())
                SettingsValueRow(label = "Complete",         value = state.status.complete.toString())
                SettingsValueRow(label = "Missing",          value = state.status.missing.toString())
                SettingsValueRow(label = "Stale / Invalid",  value = state.status.stale.toString())
                SettingsRow(
                    label    = "Refresh Status",
                    sublabel = if (state.isLoadingStatus) "Checking files…" else "Re-check artwork on disk",
                    onClick  = if (state.isLoadingStatus) null else ({ viewModel.refreshStatus() }),
                )

                SettingsRow(
                    label    = "Repair Background Links",
                    sublabel = if (state.isRepairingLinks) "Checking every game's background…"
                               else "Point each game's XMB background at art that is actually there",
                    onClick  = if (state.isRepairingLinks) null else ({ viewModel.repairArtworkLinks() }),
                )

                SettingsGroup("Scrape Artwork")

                if (state.isScraping) {
                    Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 10.dp)) {
                        LinearProgressIndicator(
                            progress = {
                                if (state.scrapeTotal > 0) state.scrapeCurrent.toFloat() / state.scrapeTotal else 0f
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = SettingsAccent,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text  = "${state.scrapeCurrent} / ${state.scrapeTotal}  —  ${state.scrapeTitle}",
                            color = SettingsText,
                        )
                        if (state.scrapeSource.isNotEmpty() || state.scrapeAsset.isNotEmpty()) {
                            Text(
                                text = buildString {
                                    if (state.scrapeSource.isNotEmpty()) append(state.scrapeSource)
                                    if (state.scrapeSource.isNotEmpty() && state.scrapeAsset.isNotEmpty()) append(" › ")
                                    if (state.scrapeAsset.isNotEmpty()) append(state.scrapeAsset)
                                },
                                color = SettingsAccent,
                            )
                        }
                        Text(
                            text  = "${state.scrapeSucceeded} succeeded · ${state.scrapeFailed} failed",
                            color = SettingsSubtext,
                        )
                        Text(
                            text  = "Runs in the background — you can leave this screen.",
                            color = SettingsSubtext,
                        )
                    }
                    SettingsRow(
                        label    = "Cancel Scrape",
                        sublabel = "Stops after the current game — artwork fetched so far is kept",
                        onClick  = { viewModel.cancelScrape() },
                    )
                } else {
                    SettingsRow(
                        label    = "Re-Scrape All Games",
                        sublabel = "Clears and re-fetches artwork for every game",
                        onClick  = { viewModel.requestRescrapeAll() },
                    )
                    SettingsRow(
                        label    = "Scrape Missing Games Only",
                        sublabel = "Fetches only games with missing or invalid artwork — keeps valid art",
                        onClick  = { viewModel.scrapeMissingOnly() },
                    )
                }

                state.summary?.let { summary ->
                    SettingsRow(
                        label    = summary,
                        sublabel = "Tap to dismiss",
                        onClick  = { viewModel.dismissSummary() },
                    )
                }
            }
            if (section == null || section == ArtworkSection.SOURCES) {
                SettingsGroup("Source Priority")

                if (state.ssEnabled) {
                    SettingsValueRow(label = "Primary",    value = "ScreenScraper (ROM hash)")
                    SettingsValueRow(label = "Fallback 1", value = "IGDB")
                    SettingsValueRow(label = "Fallback 2", value = "SteamGridDB (artwork)")
                } else {
                    SettingsValueRow(label = "Primary",    value = "IGDB")
                    SettingsValueRow(label = "Fallback 1", value = "SteamGridDB (artwork)")
                }
            }
            if (section == null || section == ArtworkSection.ARTWORK) {
                SettingsGroup("Art Preferences")

                SettingsPickerRow(
                    label    = "Game Icon Display",
                    sublabel = "Default for every console — override per console or per game from their Options menus",
                    options  = IconDisplayMode.entries.map { SettingsPickerOption(it.label) },
                    selectedIndex = IconDisplayMode.entries.indexOf(state.iconDisplayMode),
                    onPick   = { viewModel.setIconDisplayMode(IconDisplayMode.entries[it]) },
                )

                SettingsToggleRow(
                    label    = "Backdrop & Tint",
                    sublabel = "The focused item's artwork fills the background and its colour " +
                        "tints the wave. Off keeps your theme's own colour and wallpaper.",
                    checked  = state.itemBackdrop,
                    onToggle = { viewModel.setItemBackdrop(it) },
                )

                SettingsToggleRow(
                    label    = "Game Info Line",
                    sublabel = "Show the focused game's year, genre, developer and players under its logo",
                    checked  = state.gameMetadata,
                    onToggle = { viewModel.setGameMetadata(it) },
                )

                SettingsToggleRow(
                    label    = "Animated Icons",
                    sublabel = "Play a game's video snap after resting on it (skipped on low battery)",
                    checked  = state.animatedIcons,
                    onToggle = { viewModel.setAnimatedIcons(it) },
                )

                SettingsPickerRow(
                    label    = "Video Snap Placement",
                    sublabel = "Where a game's video snap plays once you rest on it",
                    options  = listOf(
                        SettingsPickerOption(
                            VideoSnapPlacement.ICON.label,
                            "Inside the 144x80 tile over the static icon. Needs Custom Icon mode.",
                        ),
                        SettingsPickerOption(
                            VideoSnapPlacement.BACKGROUND.label,
                            "Full-screen behind the crossbar. Plays in any icon mode.",
                        ),
                    ),
                    selectedIndex = VideoSnapPlacement.entries.indexOf(state.snapPlacement),
                    onPick   = { viewModel.setSnapPlacement(VideoSnapPlacement.entries[it]) },
                    enabled  = state.animatedIcons,
                )

                SettingsSliderRow(
                    label     = "Video Snap Delay",
                    sublabel  = "Rest on a game ${formatSnapDelay(state.icon1LingerDelaySeconds)} " +
                        "before its video snap plays (1–5 seconds) — higher delays avoid pops while browsing",
                    value     = state.icon1LingerDelaySeconds,
                    onValueChange = viewModel::setIcon1LingerDelaySeconds,
                    valueRange = 1f..5f,
                    steps     = 7,
                    enabled  = state.animatedIcons,
                    valueFormatter = { formatSnapDelay(it) },
                )

                SettingsToggleRow(
                    label    = "Crop Preview",
                    sublabel = "Show the finished tile in the corner while cropping artwork " +
                        "(Ⓨ toggles it inside the crop editor too)",
                    checked  = state.cropPreviewEnabled,
                    onToggle = { viewModel.setCropPreviewEnabled(it) },
                )

                SettingsToggleRow(
                    label    = "Prefer SteamGridDB Heroes",
                    sublabel = "Try SteamGridDB first for hero/banner art",
                    checked  = state.preferSteamGridDbHeroes,
                    onToggle = { viewModel.setPreferSteamGridDbHeroes(it) },
                )

                SettingsToggleRow(
                    label    = "Download Hero Images",
                    sublabel = "Wide banner art shown in game detail view",
                    checked  = state.downloadHeroes,
                    onToggle = { viewModel.setDownloadHeroes(it) },
                )

                SettingsToggleRow(
                    label    = "Download Clear Logos",
                    sublabel = "Transparent logo PNGs overlaid on hero art",
                    checked  = state.downloadLogos,
                    onToggle = { viewModel.setDownloadLogos(it) },
                )

                if (state.ssEnabled) {
                    SettingsToggleRow(
                        label    = "Download Game Manuals",
                        sublabel = "PDF manuals from ScreenScraper — opens from the game's Options menu",
                        checked  = state.downloadManuals,
                        onToggle = { viewModel.setDownloadManuals(it) },
                    )
                    SettingsToggleRow(
                        label    = "Download Video Snaps",
                        sublabel = "Short gameplay clips from ScreenScraper — large files",
                        checked  = state.downloadVideoSnaps,
                        onToggle = { viewModel.setDownloadVideoSnaps(it) },
                    )
                }
            }
            if (section == null || section == ArtworkSection.SOURCES) {
                SettingsGroup("SteamGridDB API")

                SettingsTextFieldRow(
                    label         = if (state.hasApiKey) "API Key (saved)" else "API Key",
                    value         = sgdbKeyDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.SGDB_KEY, it) },
                    placeholder   = if (state.hasApiKey) "••••••••  (tap to replace)" else "Paste your SteamGridDB key",
                    isPassword    = true,
                    helper        = "Get a free key at steamgriddb.com/api",
                )

                if (sgdbKeyDraft.isNotBlank()) {
                    SettingsRow(
                        label   = "Save API Key",
                        onClick = {
                            viewModel.saveApiKey(sgdbKeyDraft)
                        },
                    )
                }

                if (state.hasApiKey) {
                    SettingsRow(
                        label    = "Remove API Key",
                        sublabel = "SteamGridDB artwork will be disabled",
                        onClick  = { viewModel.clearApiKey() },
                    )
                }

                SettingsGroup("IGDB Credentials (Optional)")

                SettingsTextFieldRow(
                    label         = if (state.hasIgdbCredentials) "Client ID (saved)" else "Client ID",
                    value         = igdbClientIdDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.IGDB_CLIENT_ID, it) },
                    placeholder   = if (state.hasIgdbCredentials) "••••••••" else "Twitch Client ID",
                )
                SettingsTextFieldRow(
                    label         = "Client Secret",
                    value         = igdbClientSecretDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.IGDB_CLIENT_SECRET, it) },
                    placeholder   = "Twitch Client Secret",
                    isPassword    = true,
                    helper        = "Create app at dev.twitch.tv — improves fallback coverage for modern games",
                )

                state.igdbCredentialStatus?.let {
                    SettingsRow(label = it, sublabel = "Tap to dismiss", onClick = { viewModel.dismissCredentialStatus() })
                }

                if (igdbClientIdDraft.isNotBlank() && igdbClientSecretDraft.isNotBlank()) {
                    SettingsRow(
                        label   = "Test Credentials",
                        onClick = { viewModel.testIgdbCredentials(igdbClientIdDraft, igdbClientSecretDraft) },
                    )
                    SettingsRow(
                        label   = "Save Credentials",
                        onClick = {
                            viewModel.saveIgdbCredentials(igdbClientIdDraft, igdbClientSecretDraft)
                        },
                    )
                }

                if (state.hasIgdbCredentials) {
                    SettingsRow(
                        label    = "Clear IGDB Credentials",
                        sublabel = "IGDB will be skipped as a fallback source",
                        onClick  = { viewModel.clearIgdbCredentials() },
                    )
                }

                SettingsGroup("ScreenScraper")

                SettingsValueRow(
                    label    = "Status",
                    sublabel = if (state.ssEnabled) {
                        "Hash-based scraping is active (built-in developer pair)"
                    } else {
                        "ScreenScraper is unavailable in this build — no developer pair was bundled"
                    },
                    value    = if (state.ssEnabled) "Active" else "Inactive",
                )

                state.unprotectedSecretWarning?.let {
                    SettingsRow(
                        label    = it,
                        sublabel = "Tap to dismiss",
                        onClick  = { viewModel.dismissUnprotectedSecretWarning() },
                    )
                }

                SettingsGroup("ScreenScraper Account (Optional)")

                val ssUsernameStored = state.ssUsername.isNotBlank()
                SettingsTextFieldRow(
                    label         = if (ssUsernameStored) "Username (saved: ${state.ssUsername})" else "Username",
                    value         = ssUsernameDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.SS_USERNAME, it) },
                    placeholder   = if (ssUsernameStored) "Tap to replace" else "ScreenScraper username",
                )
                SettingsTextFieldRow(
                    label         = "Password",
                    value         = ssPasswordDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.SS_PASSWORD, it) },
                    placeholder   = if (state.hasSsCredentials) "••••••••  (tap to replace)" else "ScreenScraper password",
                    isPassword    = true,
                    helper        = "Free account at screenscraper.fr — raises the scrape rate limit and daily quota. " +
                        "Stored encrypted on this device (Android Keystore).",
                )

                if (ssUsernameStored && !state.hasSsCredentials) {
                    SettingsRow(
                        label    = "Password needed to use this account",
                        sublabel = "The saved password did not survive a restore from another device. " +
                            "Enter it again to leave anonymous rate limits.",
                    )
                }

                state.ssCredentialStatus?.let {
                    SettingsRow(label = it, sublabel = "Tap to dismiss", onClick = { viewModel.dismissSsCredentialStatus() })
                }

                if (ssUsernameDraft.isNotBlank() && ssPasswordDraft.isNotBlank()) {
                    if (state.ssEnabled) {
                        SettingsRow(
                            label   = "Test Account",
                            onClick = { viewModel.testSsCredentials(ssUsernameDraft, ssPasswordDraft) },
                        )
                    }
                    SettingsRow(
                        label   = "Save Account",
                        onClick = {
                            viewModel.saveSsCredentials(ssUsernameDraft, ssPasswordDraft)
                        },
                    )
                }

                if (ssUsernameStored || state.hasSsCredentials) {
                    SettingsRow(
                        label    = "Clear ScreenScraper Account",
                        sublabel = "Scraping continues at anonymous rate limits",
                        onClick  = { viewModel.clearSsCredentials() },
                    )
                }
            }
            if (section == null || section == ArtworkSection.ARTWORK) {
                SettingsGroup("Cache")

                SettingsValueRow(
                    label    = "Stored Artwork Size",
                    sublabel = "Image cache + artwork stored on this device (your artwork folder isn't counted)",
                    value    = state.diskCacheSizeMb,
                )

                SettingsRow(
                    label    = "Clear ScreenScraper URL Cache",
                    sublabel = "Forget cached artwork URLs — the next scrape re-asks ScreenScraper per game. Harmless; never deletes artwork",
                    onClick  = { viewModel.clearSsUrlCache() },
                )

                SettingsRow(
                    label    = "Clear All Artwork",
                    sublabel = "Fresh start: removes cached images, stored artwork, and every game's art links. Files in your artwork folder are kept — Relink or re-scrape to restore",
                    onClick  = { viewModel.clearCache() },
                )
            }
        }
    }

    if (state.confirmRescrapeAll) {
        SettingsConfirmOverlay(
            title = "Re-Scrape All Games?",
            message = "This will clear and re-scrape artwork for all ${state.status.total} games. " +
                "Existing artwork will be replaced.",
            confirmLabel = "Re-Scrape All",
            onConfirm = { viewModel.confirmRescrapeAll() },
            onCancel = { viewModel.cancelRescrapeAll() },
        )
    }
}

private fun formatSnapDelay(seconds: Float): String =
    if (seconds % 1f == 0f) "${seconds.toInt()}s" else "${seconds}s"

@Composable
private fun credentialFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor   = SettingsAccent,
    unfocusedBorderColor = SettingsDivider,
    focusedTextColor     = SettingsText,
    unfocusedTextColor   = SettingsText,
    cursorColor          = SettingsAccent,
)
