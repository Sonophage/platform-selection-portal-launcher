package com.psplauncher.feature.settings.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.domain.model.EmulatorProfile
import com.psplauncher.core.domain.model.MemoryCard
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.feature.launcher.AutoCoreMemory
import com.psplauncher.feature.launcher.EmulatorLaunchResolver
import com.psplauncher.feature.launcher.EmulatorProfileRepository
import com.psplauncher.feature.launcher.LaunchSource
import com.psplauncher.feature.launcher.byLaunchPreference
import com.psplauncher.feature.launcher.stabilizeCore
import com.psplauncher.feature.launcher.supportsPlatform
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

private val NON_EMULATOR_PLATFORMS = setOf("android", "windows")

private const val MEMORY_CARD_SUFFIX = " Memory Card"

data class PlatformEmulatorCandidate(
    val profile: EmulatorProfile,

    val isRecommended: Boolean,

    val isDefault: Boolean,
)

data class PlatformAssignRow(
    val platformId: String,
    val platformName: String,
    val gameCount: Int,
    val overrideCount: Int,

    val storedDefaultId: String? = null,

    val storedDefaultName: String? = null,

    val platformDefaultId: String? = null,

    val resolvedProfile: EmulatorProfile? = null,
    val resolvedCoreName: String? = null,

    val isMissingCore: Boolean = false,

    val source: LaunchSource? = null,

    val candidates: List<PlatformEmulatorCandidate> = emptyList(),
) {
    val resolvedProfileName: String? get() = resolvedProfile?.name

    val isAutomatic: Boolean get() = source == LaunchSource.CATALOG_DEFAULT

    val defaultDisplayName: String?
        get() = resolvedProfile?.name
            ?: storedDefaultName
            ?: platformDefaultId
}

data class EmulatorAssignmentUiState(
    val platforms: List<PlatformAssignRow> = emptyList(),
    val detailPlatformId: String? = null,

    val returnFocusKey: String? = null,
    val confirmClearPlatformId: String? = null,
    val message: String? = null,
) {
    val detailRow: PlatformAssignRow?
        get() = platforms.firstOrNull { it.platformId == detailPlatformId }
}

@HiltViewModel
class EmulatorAssignmentViewModel @Inject constructor(
    private val memoryCardRepository: MemoryCardRepository,
    private val platformDao: PlatformDao,
    private val gameRepository: GameRepository,
    private val profileRepository: EmulatorProfileRepository,
    private val autoCoreMemory: AutoCoreMemory,
) : ViewModel() {
    private val _uiState = MutableStateFlow(EmulatorAssignmentUiState())
    val uiState: StateFlow<EmulatorAssignmentUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                memoryCardRepository.observeAll(),
                platformDao.observeAll(),
                gameRepository.observeAllGames(),
                profileRepository.profiles,
            ) { cards, platforms, games, allProfiles ->
                buildRows(cards, platforms, games, allProfiles)
            }.collect { rows ->
                _uiState.update { it.copy(platforms = rows) }
            }
        }
    }

    fun openDetail(platformId: String) = _uiState.update {
        it.copy(detailPlatformId = platformId, returnFocusKey = platformId, confirmClearPlatformId = null, message = null)
    }

    fun closeDetail() = _uiState.update {
        it.copy(detailPlatformId = null, confirmClearPlatformId = null)
    }

    fun onBack(): Boolean {
        if (_uiState.value.detailPlatformId == null) return false
        closeDetail()
        return true
    }

    fun dismissMessage() = _uiState.update { it.copy(message = null) }

    fun selectDefault(platformId: String, profileId: String) {
        val row = _uiState.value.platforms.firstOrNull { it.platformId == platformId } ?: return
        val name = row.candidates.firstOrNull { it.profile.id == profileId }?.profile?.name
            ?: row.defaultDisplayName
        viewModelScope.launch {
            memoryCardRepository.setEmulator(platformId, profileId)
            _uiState.update {
                it.copy(message = "${row.platformName}: default emulator set to ${name ?: profileId}")
            }
        }
    }

    fun useAutomaticDefault(platformId: String) {
        val row = _uiState.value.platforms.firstOrNull { it.platformId == platformId } ?: return
        viewModelScope.launch {
            memoryCardRepository.setEmulator(platformId, null)
            _uiState.update {
                it.copy(message = "${row.platformName}: using the recommended emulator")
            }
        }
    }

    fun requestClearOverrides() {
        val platformId = _uiState.value.detailPlatformId ?: return
        _uiState.update { it.copy(confirmClearPlatformId = platformId) }
    }

    fun cancelClearOverrides() = _uiState.update { it.copy(confirmClearPlatformId = null) }

    fun confirmClearOverrides() {
        val platformId = _uiState.value.confirmClearPlatformId ?: return
        val row = _uiState.value.platforms.firstOrNull { it.platformId == platformId }
        viewModelScope.launch {
            gameRepository.clearPreferredEmulatorForPlatform(platformId)
            _uiState.update {
                it.copy(
                    confirmClearPlatformId = null,
                    message = "${row?.platformName ?: platformId}: cleared ${row?.overrideCount ?: 0} " +
                        "per-game override(s) — those games now follow the platform default",
                )
            }
        }
    }

    private suspend fun buildRows(
        cards: List<MemoryCard>,
        platforms: List<com.psplauncher.core.data.database.entity.PlatformEntity>,
        games: List<com.psplauncher.core.domain.model.Game>,
        allProfiles: List<EmulatorProfile>,
    ): List<PlatformAssignRow> {
        val rememberedCores = autoCoreMemory.rememberedIds()

        val installed = profileRepository.getInstalledProfiles()
        val namesById = allProfiles.associate { it.id to it.name }
        val namesByPackage = allProfiles.associate { it.packageName to it.name }
        val platformById = platforms.associateBy { it.id }
        val cardByPlatform = cards.associateBy { it.platformId }
        val gamesByPlatform = games.groupBy { it.platformId }

        return gamesByPlatform
            .filterKeys { it !in NON_EMULATOR_PLATFORMS }
            .mapNotNull { (platformId, platformGames) ->
                if (platformGames.isEmpty()) return@mapNotNull null
                val card = cardByPlatform[platformId]
                val platformEntity = platformById[platformId]
                val platformName = platformEntity?.name
                    ?: card?.displayName?.removeSuffix(MEMORY_CARD_SUFFIX)
                    ?: platformId.uppercase()

                val installedForPlatform =
                    installed.filter { it.isAvailable && it.supportsPlatform(platformId) }

                val platformProfiles =
                    installedForPlatform.byLaunchPreference().stabilizeCore(rememberedCores[platformId])
                val stored = card?.emulatorId?.takeIf { it.isNotBlank() }
                val platformPref =
                    platformEntity?.preferredEmulatorPackage?.takeIf { it.isNotBlank() }
                val resolved = EmulatorLaunchResolver.resolve(
                    platformId = platformId,
                    installedProfiles = installed,
                    platformProfiles = platformProfiles,
                    memoryCardEmulatorId = stored,
                    platformDefault = platformPref,
                ).getOrNull()
                val recommendedId = platformProfiles.firstOrNull()?.id

                PlatformAssignRow(
                    platformId = platformId,
                    platformName = platformName,
                    gameCount = platformGames.size,
                    overrideCount = platformGames.count { !it.emulatorPackage.isNullOrBlank() },
                    storedDefaultId = stored,
                    storedDefaultName = stored?.let { namesById[it] ?: namesByPackage[it] },
                    platformDefaultId = platformPref,
                    resolvedProfile = resolved?.profile,
                    resolvedCoreName = resolved?.coreName,
                    isMissingCore = resolved?.isMissingCore == true,
                    source = resolved?.source,
                    candidates = platformProfiles.map { profile ->
                        PlatformEmulatorCandidate(
                            profile = profile,
                            isRecommended = profile.id == recommendedId,
                            isDefault = resolved != null &&
                                (resolved.profile.id == profile.id ||
                                    resolved.profile.packageName == profile.packageName),
                        )
                    },
                )
            }
            .sortedBy { it.platformName.lowercase() }
    }
}
