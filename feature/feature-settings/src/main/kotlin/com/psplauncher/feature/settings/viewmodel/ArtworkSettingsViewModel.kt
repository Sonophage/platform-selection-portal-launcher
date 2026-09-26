package com.psplauncher.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.common.security.SecretProtection
import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import com.psplauncher.feature.artwork.api.ArtworkRepository
import com.psplauncher.feature.artwork.api.ArtworkScrapePreferences
import com.psplauncher.feature.artwork.api.ArtworkStatus
import com.psplauncher.feature.artwork.api.IgdbApi
import com.psplauncher.feature.artwork.api.MetadataScrapeWorker
import com.psplauncher.feature.artwork.api.ScreenScraperApi
import com.psplauncher.feature.artwork.api.SgdbApiKeyProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtworkSettingsUiState(
    val hasApiKey: Boolean = false,
    val apiKeyMasked: String = "",

    val cropPreviewEnabled: Boolean =
        com.psplauncher.core.data.repository.CropPreviewPreferences.DEFAULT_ENABLED,
    val hasIgdbCredentials: Boolean = false,
    val igdbClientId: String = "",
    val igdbCredentialStatus: String? = null,

    val ssEnabled: Boolean = false,
    val hasSsCredentials: Boolean = false,
    val ssUsername: String = "",
    val ssCredentialStatus: String? = null,

    val unprotectedSecretWarning: String? = null,

    val drafts: CredentialDrafts = CredentialDrafts(),
    val status: ArtworkStatus = ArtworkStatus(),
    val isLoadingStatus: Boolean = false,
    val isScraping: Boolean = false,
    val scrapeCurrent: Int = 0,
    val scrapeTotal: Int = 0,
    val scrapeSucceeded: Int = 0,
    val scrapeFailed: Int = 0,
    val scrapeTitle: String = "",
    val scrapeSource: String = "",
    val scrapeAsset: String = "",
    val summary: String? = null,
    val isRepairingLinks: Boolean = false,
    val confirmRescrapeAll: Boolean = false,
    val diskCacheSizeMb: String = "0 MB",

    val iconDisplayMode: com.psplauncher.core.domain.model.IconDisplayMode =
        com.psplauncher.core.domain.model.IconDisplayMode.DEFAULT,

    val animatedIcons: Boolean = true,

    val snapPlacement: com.psplauncher.core.domain.model.VideoSnapPlacement =
        com.psplauncher.core.domain.model.VideoSnapPlacement.DEFAULT,

    val gameMetadata: Boolean = true,
    val itemBackdrop: Boolean = true,

    val icon1LingerDelaySeconds: Float = 1.5f,
    val downloadHeroes: Boolean = true,
    val downloadLogos: Boolean = true,
    val downloadManuals: Boolean = true,
    val downloadVideoSnaps: Boolean = false,
    val preferSteamGridDbHeroes: Boolean = false,

    val artworkFolderGrantDead: Boolean = false,

    val debugCredentialsAvailable: Boolean = com.psplauncher.feature.settings.BuildConfig.DEBUG,
    val debugCredentialsStatus: String? = null,
)

enum class CredentialField { SGDB_KEY, IGDB_CLIENT_ID, IGDB_CLIENT_SECRET, SS_USERNAME, SS_PASSWORD }

@androidx.compose.runtime.Immutable
data class CredentialDrafts(
    val sgdbKey: String = "",
    val igdbClientId: String = "",
    val igdbClientSecret: String = "",
    val ssUsername: String = "",
    val ssPassword: String = "",
) {
    operator fun get(field: CredentialField): String = when (field) {
        CredentialField.SGDB_KEY -> sgdbKey
        CredentialField.IGDB_CLIENT_ID -> igdbClientId
        CredentialField.IGDB_CLIENT_SECRET -> igdbClientSecret
        CredentialField.SS_USERNAME -> ssUsername
        CredentialField.SS_PASSWORD -> ssPassword
    }

    fun with(field: CredentialField, value: String): CredentialDrafts = when (field) {
        CredentialField.SGDB_KEY -> copy(sgdbKey = value)
        CredentialField.IGDB_CLIENT_ID -> copy(igdbClientId = value)
        CredentialField.IGDB_CLIENT_SECRET -> copy(igdbClientSecret = value)
        CredentialField.SS_USERNAME -> copy(ssUsername = value)
        CredentialField.SS_PASSWORD -> copy(ssPassword = value)
    }
}

@HiltViewModel
class ArtworkSettingsViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context,
    private val sgdbKeyProvider: SgdbApiKeyProvider,
    private val metadataKeyProvider: MetadataApiKeyProvider,
    private val artworkLinkRepair: com.psplauncher.core.data.repository.ArtworkLinkRepair,
    private val artworkRepository: ArtworkRepository,
    private val scrapePreferences: ArtworkScrapePreferences,
    private val igdbApi: IgdbApi,
    private val screenScraperApi: ScreenScraperApi,
    private val artworkFolderRepository: com.psplauncher.core.data.repository.ArtworkFolderRepository,
    private val iconDisplayPreferences: com.psplauncher.core.data.repository.IconDisplayPreferences,
    private val cropPreviewPreferences: com.psplauncher.core.data.repository.CropPreviewPreferences,
    private val debugCredentialsLoader: com.psplauncher.feature.settings.debug.DebugCredentialsLoader,
) : ViewModel() {
    private val _extra = MutableStateFlow(ArtworkSettingsUiState())

    init {
        viewModelScope.launch {
            iconDisplayPreferences.modeFlow.collect { mode ->
                _extra.update { it.copy(iconDisplayMode = mode) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.animatedIconsFlow.collect { enabled ->
                _extra.update { it.copy(animatedIcons = enabled) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.snapPlacementFlow.collect { placement ->
                _extra.update { it.copy(snapPlacement = placement) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.itemBackdropFlow.collect { on ->
                _extra.update { it.copy(itemBackdrop = on) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.gameMetadataFlow.collect { visible ->
                _extra.update { it.copy(gameMetadata = visible) }
            }
        }
        viewModelScope.launch {
            iconDisplayPreferences.lingerDelaySecondsFlow.collect { seconds ->
                _extra.update { it.copy(icon1LingerDelaySeconds = seconds) }
            }
        }

        viewModelScope.launch {
            cropPreviewPreferences.enabledFlow.collect { enabled ->
                _extra.update { it.copy(cropPreviewEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            val configured = artworkFolderRepository.getTreeUri() != null
            val dead = configured && !artworkFolderRepository.hasLiveGrant()
            _extra.update { it.copy(artworkFolderGrantDead = dead) }
        }

        runCatching { androidx.work.WorkManager.getInstance(context) }.getOrNull()?.let { wm ->
            viewModelScope.launch {
                wm.getWorkInfosForUniqueWorkFlow(MetadataScrapeWorker.UNIQUE_NAME)
                    .collect { infos -> onScrapeWorkInfos(infos) }
            }
        }
    }

    private fun onScrapeWorkInfos(infos: List<androidx.work.WorkInfo>) {
        val active = infos.firstOrNull { !it.state.isFinished }
        if (active != null) {
            val p = active.progress
            _extra.update {
                it.copy(
                    isScraping      = true,
                    summary         = null,
                    scrapeCurrent   = p.getInt(MetadataScrapeWorker.KEY_CURRENT, 0),
                    scrapeTotal     = p.getInt(MetadataScrapeWorker.KEY_TOTAL, 0),
                    scrapeSucceeded = p.getInt(MetadataScrapeWorker.KEY_SUCCEEDED, 0),
                    scrapeFailed    = p.getInt(MetadataScrapeWorker.KEY_FAILED, 0),
                    scrapeTitle     = p.getString(MetadataScrapeWorker.KEY_TITLE) ?: "Starting…",
                    scrapeSource    = p.getString(MetadataScrapeWorker.KEY_SOURCE) ?: "",
                    scrapeAsset     = p.getString(MetadataScrapeWorker.KEY_ASSET) ?: "",
                )
            }
            return
        }
        if (!_extra.value.isScraping) return

        val finished = infos.maxByOrNull { it.state.ordinal }
        val summary = when (finished?.state) {
            androidx.work.WorkInfo.State.SUCCEEDED -> {
                val out = finished.outputData
                val label = if (out.getString(MetadataScrapeWorker.KEY_MODE) == MetadataScrapeWorker.MODE_ALL)
                    "Re-scraped all games" else "Scraped missing games"
                val counts = "$label: ${out.getInt(MetadataScrapeWorker.KEY_SUCCEEDED, 0)} succeeded, " +
                    "${out.getInt(MetadataScrapeWorker.KEY_FAILED, 0)} failed of " +
                    "${out.getInt(MetadataScrapeWorker.KEY_TOTAL, 0)}"

                out.getString(MetadataScrapeWorker.KEY_STOPPED_REASON)
                    ?.let { "$counts. $it" }
                    ?: counts
            }
            androidx.work.WorkInfo.State.CANCELLED -> "Scrape cancelled — artwork fetched so far is kept"
            else -> "Scrape failed: ${finished?.outputData?.getString(MetadataScrapeWorker.KEY_ERROR) ?: "unknown error"}"
        }
        _extra.update {
            it.copy(isScraping = false, scrapeTitle = "", scrapeSource = "", scrapeAsset = "", summary = summary)
        }
        refreshStatus()
    }

    private val ssAccounts = combine(
        metadataKeyProvider.ssUsernameFlow,
        screenScraperApi.isEnabledFlow,

        metadataKeyProvider.hasSsCredentialsFlow,
    ) { username, enabled, hasBoth -> Triple(username, enabled, hasBoth) }

    private val igdb = combine(
        metadataKeyProvider.igdbClientIdFlow,
        metadataKeyProvider.hasIgdbCredentialsFlow,
    ) { clientId, hasBoth -> clientId to hasBoth }

    val uiState: StateFlow<ArtworkSettingsUiState> = combine(
        sgdbKeyProvider.apiKeyFlow,
        igdb,
        ssAccounts,
        scrapePreferences.preferSteamGridDbHeroesFlow,
        _extra,
    ) { sgdbKey, igdbPair, ss, preferSgdbHeroes, extra ->
        val (igdbClientId, hasIgdb) = igdbPair
        val (ssUsername, ssEnabled, hasSs) = ss
        extra.copy(
            hasApiKey             = !sgdbKey.isNullOrBlank(),
            apiKeyMasked          = if (!sgdbKey.isNullOrBlank()) "••••••" else "",
            hasIgdbCredentials    = hasIgdb,
            igdbClientId          = igdbClientId ?: "",
            ssEnabled             = ssEnabled,
            hasSsCredentials      = hasSs,
            ssUsername            = ssUsername ?: "",
            preferSteamGridDbHeroes = preferSgdbHeroes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ArtworkSettingsUiState())

    init {
        refreshStatus()
        loadScrapePreferences()
    }

    private fun loadScrapePreferences() {
        viewModelScope.launch {
            val opts = scrapePreferences.getOptions()
            _extra.update {
                it.copy(
                    downloadHeroes     = opts.downloadHeroes,
                    downloadLogos      = opts.downloadClearLogos,
                    downloadManuals    = opts.downloadManuals,
                    downloadVideoSnaps = opts.downloadVideoSnaps,
                )
            }
        }
    }

    fun repairArtworkLinks() {
        viewModelScope.launch {
            _extra.update { it.copy(isRepairingLinks = true, summary = null) }
            val report = artworkLinkRepair.run()
            _extra.update { it.copy(isRepairingLinks = false, summary = report.message()) }
        }
    }

    fun refreshStatus() {
        viewModelScope.launch {
            _extra.update { it.copy(isLoadingStatus = true) }
            val status = artworkRepository.computeStatus()
            val cacheMb = artworkRepository.cacheSizeBytes() / (1024.0 * 1024.0)
            _extra.update { it.copy(
                status = status,
                isLoadingStatus = false,
                diskCacheSizeMb = String.format(java.util.Locale.US, "%.1f MB", cacheMb),
            ) }
        }
    }

    fun saveApiKey(key: String) {
        viewModelScope.launch {
            val protection = sgdbKeyProvider.saveKey(key.trim())
            clearDrafts(CredentialField.SGDB_KEY)
            warnIfUnprotected("SteamGridDB key", protection)
        }
    }

    fun clearApiKey() {
        viewModelScope.launch { sgdbKeyProvider.clearKey() }
    }

    fun setCropPreviewEnabled(enabled: Boolean) {
        viewModelScope.launch { cropPreviewPreferences.setEnabled(enabled) }
    }

    fun setDraft(field: CredentialField, value: String) {
        _extra.update { it.copy(drafts = it.drafts.with(field, value)) }
    }

    private fun clearDrafts(vararg fields: CredentialField) {
        _extra.update { s -> s.copy(drafts = fields.fold(s.drafts) { d, f -> d.with(f, "") }) }
    }

    fun saveIgdbCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            val protection = metadataKeyProvider.saveIgdbCredentials(clientId.trim(), clientSecret.trim())
            _extra.update { it.copy(igdbCredentialStatus = null) }
            clearDrafts(CredentialField.IGDB_CLIENT_ID, CredentialField.IGDB_CLIENT_SECRET)
            warnIfUnprotected("IGDB client secret", protection)
        }
    }

    fun clearIgdbCredentials() {
        viewModelScope.launch {
            metadataKeyProvider.clearIgdbCredentials()
            _extra.update { it.copy(igdbCredentialStatus = null) }
        }
    }

    fun testIgdbCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            _extra.update { it.copy(igdbCredentialStatus = "Testing…") }
            val status = ServiceConnectors.testIgdb(igdbApi, clientId, clientSecret)
            _extra.update { it.copy(igdbCredentialStatus = status) }
        }
    }

    fun dismissCredentialStatus() {
        _extra.update { it.copy(igdbCredentialStatus = null) }
    }

    fun saveSsCredentials(username: String, password: String) {
        viewModelScope.launch {
            val protection = metadataKeyProvider.saveSsCredentials(username.trim(), password.trim())
            _extra.update { it.copy(ssCredentialStatus = null) }
            clearDrafts(CredentialField.SS_USERNAME, CredentialField.SS_PASSWORD)
            warnIfUnprotected("ScreenScraper password", protection)
        }
    }

    fun clearSsCredentials() {
        viewModelScope.launch {
            metadataKeyProvider.clearSsCredentials()
            _extra.update { it.copy(ssCredentialStatus = null) }
        }
    }

    fun testSsCredentials(username: String, password: String) {
        viewModelScope.launch {
            _extra.update { it.copy(ssCredentialStatus = "Testing…") }
            val status = ServiceConnectors.testScreenScraper(screenScraperApi, username, password)
            _extra.update { it.copy(ssCredentialStatus = status) }
        }
    }

    fun dismissSsCredentialStatus() {
        _extra.update { it.copy(ssCredentialStatus = null) }
    }

    fun dismissUnprotectedSecretWarning() {
        _extra.update { it.copy(unprotectedSecretWarning = null) }
    }

    private fun warnIfUnprotected(what: String, protection: SecretProtection) {
        if (protection == SecretProtection.PROTECTED) return
        _extra.update {
            it.copy(
                unprotectedSecretWarning =
                    "$what was saved, but this device's secure keystore was unavailable, so it is " +
                        "stored unencrypted. Clearing and re-entering it later will try again.",
            )
        }
    }

    fun loadDebugCredentials(uri: android.net.Uri) {
        if (!com.psplauncher.feature.settings.BuildConfig.DEBUG) return
        viewModelScope.launch {
            val read = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri) ?: error("No stream") }
                    .mapCatching { stream ->
                        stream.use { com.psplauncher.feature.settings.debug.readCredentialsText(it) }
                    }
            }
            val text = read.getOrElse { _ ->
                _extra.update { it.copy(debugCredentialsStatus = "Couldn't read that file") }
                return@launch
            }
            if (text == null) {
                _extra.update { it.copy(debugCredentialsStatus = "That file is too large to be a credentials file") }
                return@launch
            }
            loadDebugCredentialsText(text)
        }
    }

    internal fun loadDebugCredentialsText(text: String) {
        if (!com.psplauncher.feature.settings.BuildConfig.DEBUG) return
        viewModelScope.launch {
            val result = debugCredentialsLoader.load(text)
            _extra.update { it.copy(debugCredentialsStatus = result.status) }
            if (result.anyUnprotected) {
                _extra.update {
                    it.copy(
                        unprotectedSecretWarning =
                            "Some credentials were saved, but this device's secure keystore was unavailable, " +
                                "so they are stored unencrypted.",
                    )
                }
            }
        }
    }

    fun dismissDebugCredentialsStatus() = _extra.update { it.copy(debugCredentialsStatus = null) }

    fun requestRescrapeAll() = _extra.update { it.copy(confirmRescrapeAll = true) }
    fun cancelRescrapeAll()  = _extra.update { it.copy(confirmRescrapeAll = false) }

    fun confirmRescrapeAll() {
        _extra.update { it.copy(confirmRescrapeAll = false) }
        startScrape(MetadataScrapeWorker.MODE_ALL)
    }

    fun scrapeMissingOnly() = startScrape(MetadataScrapeWorker.MODE_MISSING)

    fun cancelScrape() = MetadataScrapeWorker.cancel(context)

    private fun startScrape(mode: String) {
        if (_extra.value.isScraping) return
        _extra.update {
            it.copy(
                isScraping = true, summary = null, scrapeCurrent = 0, scrapeTotal = 0,
                scrapeSucceeded = 0, scrapeFailed = 0, scrapeTitle = "Starting…",
                scrapeSource = "", scrapeAsset = "",
            )
        }
        MetadataScrapeWorker.enqueue(context, mode)
    }

    fun dismissSummary() = _extra.update { it.copy(summary = null) }

    fun clearSsUrlCache() {
        viewModelScope.launch {
            artworkRepository.clearSsMediaCache()
            _extra.update { it.copy(summary = "ScreenScraper URL cache cleared") }
        }
    }

    fun clearCache() {
        viewModelScope.launch {
            artworkRepository.clearCache()
            _extra.update { it.copy(diskCacheSizeMb = "0.0 MB") }
            refreshStatus()
        }
    }

    fun setIconDisplayMode(mode: com.psplauncher.core.domain.model.IconDisplayMode) {
        viewModelScope.launch { iconDisplayPreferences.setMode(mode) }
    }

    fun setAnimatedIcons(enabled: Boolean) {
        _extra.update { it.copy(animatedIcons = enabled) }
        viewModelScope.launch { iconDisplayPreferences.setAnimatedIcons(enabled) }
    }

    fun setSnapPlacement(placement: com.psplauncher.core.domain.model.VideoSnapPlacement) {
        viewModelScope.launch { iconDisplayPreferences.setSnapPlacement(placement) }
    }

    fun setItemBackdrop(enabled: Boolean) {
        _extra.update { it.copy(itemBackdrop = enabled) }
        viewModelScope.launch { iconDisplayPreferences.setItemBackdrop(enabled) }
    }

    fun setGameMetadata(enabled: Boolean) {
        _extra.update { it.copy(gameMetadata = enabled) }
        viewModelScope.launch { iconDisplayPreferences.setGameMetadata(enabled) }
    }

    fun setIcon1LingerDelaySeconds(seconds: Float) {
        val clamped = seconds.coerceIn(1f, 5f)
        _extra.update { it.copy(icon1LingerDelaySeconds = clamped) }
        viewModelScope.launch { iconDisplayPreferences.setLingerDelaySeconds(clamped) }
    }

    fun setDownloadHeroes(enabled: Boolean) {
        _extra.update { it.copy(downloadHeroes = enabled) }
        viewModelScope.launch { scrapePreferences.setDownloadHeroes(enabled) }
    }

    fun setDownloadLogos(enabled: Boolean) {
        _extra.update { it.copy(downloadLogos = enabled) }
        viewModelScope.launch { scrapePreferences.setDownloadClearLogos(enabled) }
    }

    fun setDownloadManuals(enabled: Boolean) {
        _extra.update { it.copy(downloadManuals = enabled) }
        viewModelScope.launch { scrapePreferences.setDownloadManuals(enabled) }
    }

    fun setDownloadVideoSnaps(enabled: Boolean) {
        _extra.update { it.copy(downloadVideoSnaps = enabled) }
        viewModelScope.launch { scrapePreferences.setDownloadVideoSnaps(enabled) }
    }

    fun setPreferSteamGridDbHeroes(enabled: Boolean) {
        viewModelScope.launch { scrapePreferences.setPreferSteamGridDbHeroes(enabled) }
    }
}
