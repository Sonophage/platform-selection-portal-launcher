package com.psplauncher.feature.launcher

import com.psplauncher.core.data.repository.MemoryCardRepository
import com.psplauncher.core.data.repository.RomRootRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

enum class SetupGap(val repairScreenId: String, val message: String) {
    NO_ROM_ROOT(
        repairScreenId = "settings_library",
        message = "Add a ROM folder to start your library",
    ),
    NO_CONSOLES(
        repairScreenId = "settings_library",
        message = "Add a console Memory Card to scan into",
    ),
    NO_EMULATORS(
        repairScreenId = "settings_emulators",
        message = "Install an emulator to play your games",
    ),

    NONE(repairScreenId = "", message = ""),
}

data class SetupState(
    val hasRomRoot: Boolean = false,
    val hasConsoleCard: Boolean = false,
    val hasEmulator: Boolean = false,
) {
    val firstGap: SetupGap
        get() = when {
            !hasRomRoot      -> SetupGap.NO_ROM_ROOT
            !hasConsoleCard  -> SetupGap.NO_CONSOLES
            !hasEmulator     -> SetupGap.NO_EMULATORS
            else             -> SetupGap.NONE
        }

    val isPlayable: Boolean get() = firstGap == SetupGap.NONE
}

@Singleton
class SetupStateProvider @Inject constructor(
    private val romRootRepository: RomRootRepository,
    private val memoryCardRepository: MemoryCardRepository,
    private val emulatorProfileRepository: EmulatorProfileRepository,
) {
    suspend fun current(): SetupState {
        val romRoots = runCatching { romRootRepository.roots.first() }.getOrDefault(emptyList())
        val cards = runCatching { memoryCardRepository.getAll() }.getOrDefault(emptyList())
        val emulators = runCatching { emulatorProfileRepository.getInstalledProfiles() }
            .getOrDefault(emptyList())
        return SetupState(
            hasRomRoot = romRoots.isNotEmpty(),
            hasConsoleCard = cards.isNotEmpty(),
            hasEmulator = emulators.isNotEmpty(),
        )
    }

    fun observe(): Flow<SetupState> = combine(
        romRootRepository.roots,
        memoryCardRepository.observeAll(),
        emulatorProfileRepository.profiles,
    ) { romRoots, cards, emulators ->
        SetupState(
            hasRomRoot = romRoots.isNotEmpty(),
            hasConsoleCard = cards.isNotEmpty(),
            hasEmulator = emulators.isNotEmpty(),
        )
    }
}
