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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.psplauncher.feature.settings.viewmodel.CredentialField
import com.psplauncher.feature.settings.viewmodel.ArtworkSettingsViewModel

/**
 * Which half of this screen to show. Artwork was 33 rows across eleven groups doing two unrelated
 * jobs: managing the art you have, and holding API credentials for four scraping services that are
 * entered once and then never touched. Splitting the entry points keeps both short.
 *
 * Null renders the whole screen, as with [DisplaySection].
 */
enum class ArtworkSection { ARTWORK, SOURCES }

@Composable
fun ArtworkSettingsScreen(
    onBack: () -> Unit,
    section: ArtworkSection? = null,
    modifier: Modifier = Modifier,
    viewModel: ArtworkSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()

    // Artwork Folder & Import lives on its own sub-screen (same pattern as Library Manager's
    // internal sections) — BACK returns here.
    var showImport by remember { mutableStateOf(false) }
    if (showImport) {
        ArtworkImportScreen(onBack = { showImport = false }, modifier = modifier)
        return
    }

    // Read from the ViewModel, not remembered here: the keyboard covers the field below the one
    // you are filling on this screen's height, so a two-part credential means dismissing it and
    // scrolling, and a back press that left the pane used to take the half-typed pair with it.
    // Three of these were also keyed on the stored value, so a store emission could blank the box
    // you were typing in.
    val sgdbKeyDraft = state.drafts.sgdbKey
    val tgdbKeyDraft = state.drafts.tgdbKey
    val igdbClientIdDraft = state.drafts.igdbClientId
    val igdbClientSecretDraft = state.drafts.igdbClientSecret
    val ssUsernameDraft = state.drafts.ssUsername
    val ssPasswordDraft = state.drafts.ssPassword

    // Debug builds only. Any MIME type: pickers often report .properties files as octet-stream.
    val credentialsFilePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.loadDebugCredentials(it) }
    }

    SettingsScaffold(
        title    = "Settings",
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

            // ── Debug: credentials file ───────────────────────────────────────
            // Never in release: BuildConfig.DEBUG is a compile-time false there, so the row is
            // stripped and the ViewModel refuses the load as well.
            if (com.psplauncher.feature.settings.BuildConfig.DEBUG && state.debugCredentialsAvailable) {
                SettingsGroup("Debug")

                SettingsRow(
                    label    = "Load Credentials File",
                    sublabel = "Fill every artwork and achievement credential from a .properties file " +
                        "(steamgriddb.apiKey, thegamesdb.apiKey, igdb.*, screenscraper.*)",
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

            // ── Artwork library / import ──────────────────────────────────────
            if (section == null || section == ArtworkSection.ARTWORK) {
                SettingsGroup("Artwork Library")

                SettingsRow(
                    label    = if (state.artworkFolderGrantDead) "Artwork Folder & Import  ⚠" else "Artwork Folder & Import",
                    sublabel = if (state.artworkFolderGrantDead)
                        "Access to your artwork folder was lost — open to re-link it"
                    else "Choose where artwork is stored and import existing artwork from ES-DE",
                    onClick  = { showImport = true },
                )

                // ── Artwork status ────────────────────────────────────────────────
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

                // ── Scraping ──────────────────────────────────────────────────────
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

                // ── Source priority ───────────────────────────────────────────────
            }
            if (section == null || section == ArtworkSection.SOURCES) {
                SettingsGroup("Source Priority")

                if (state.ssEnabled) {
                    SettingsValueRow(label = "Primary",    value = "ScreenScraper (ROM hash)")
                    SettingsValueRow(label = "Fallback 1", value = "TheGamesDB")
                    SettingsValueRow(label = "Fallback 2", value = "IGDB")
                    SettingsValueRow(label = "Fallback 3", value = "SteamGridDB (artwork)")
                } else {
                    SettingsValueRow(label = "Primary",    value = "TheGamesDB")
                    SettingsValueRow(label = "Fallback 1", value = "IGDB")
                    SettingsValueRow(label = "Fallback 2", value = "SteamGridDB (artwork)")
                }

                // ── Art preferences ───────────────────────────────────────────────
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

                // ── SteamGridDB API key ───────────────────────────────────────────
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

                // ── TheGamesDB API key ────────────────────────────────────────────
                // Stored encrypted like the SteamGridDB key. Without one TheGamesDB is skipped by the
                // scraper and not offered in the Artwork Studio.
                SettingsGroup("TheGamesDB API")

                SettingsTextFieldRow(
                    label         = if (state.hasTgdbKey) "API Key (saved)" else "API Key",
                    value         = tgdbKeyDraft,
                    onValueChange = { viewModel.setDraft(CredentialField.TGDB_KEY, it) },
                    placeholder   = if (state.hasTgdbKey) "••••••••  (tap to replace)" else "Paste your TheGamesDB key",
                    isPassword    = true,
                    helper        = "Request a key from TheGamesDB at thegamesdb.net",
                )

                if (tgdbKeyDraft.isNotBlank()) {
                    SettingsRow(
                        label   = "Save TheGamesDB Key",
                        onClick = {
                            viewModel.saveTgdbKey(tgdbKeyDraft)
                        },
                    )
                }

                if (state.hasTgdbKey) {
                    SettingsRow(
                        label    = "Remove TheGamesDB Key",
                        sublabel = "TheGamesDB will be skipped as a source",
                        onClick  = { viewModel.clearTgdbKey() },
                    )
                }

                // ── IGDB credentials (optional) ───────────────────────────────────
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

                // ── ScreenScraper provider status ────────────────────────────────
                // The WebAPI rejects every call without a devid/devpassword pair. This build ships an
                // obfuscated built-in pair (there is no user-entered override), so the provider works
                // out of the box; the optional user account below only raises the rate limit/quota.
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

                // ── ScreenScraper user account (optional) ─────────────────────────
                // Only raises the rate limit and daily quota; scraping works without it.
                SettingsGroup("ScreenScraper Account (Optional)")

                // A stored username is shown whether or not the account is complete, and the
                // two are now separate questions: a restore from another device keeps the
                // username and drops the password, so "a username is saved" and "the account
                // works" stopped being the same thing.
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

                // The re-prompt the restore path's log line already promises. BackupManager logs
                // "Dropped un-decryptable credential on restore ... (re-prompt)" and nothing was
                // ever prompting, so the account sat half-saved and the scrape quietly ran at
                // anonymous limits.
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

                // Offered whenever ANYTHING is stored, so a half-restored account can be cleared
                // rather than stranded.
                if (ssUsernameStored || state.hasSsCredentials) {
                    SettingsRow(
                        label    = "Clear ScreenScraper Account",
                        sublabel = "Scraping continues at anonymous rate limits",
                        onClick  = { viewModel.clearSsCredentials() },
                    )
                }

                // ── Cache ─────────────────────────────────────────────────────────
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
