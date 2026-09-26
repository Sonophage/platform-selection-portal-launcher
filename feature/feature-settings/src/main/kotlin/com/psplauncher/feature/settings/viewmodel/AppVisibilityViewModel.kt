package com.psplauncher.feature.settings.viewmodel

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.psplauncher.core.data.database.dao.AppOverrideDao
import com.psplauncher.core.data.database.dao.HiddenPlacementDao
import com.psplauncher.core.domain.model.HideLocationType
import com.psplauncher.core.domain.model.HiddenPlacement
import com.psplauncher.feature.appbar.AppCategoryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HiddenEntry(
    val itemKey: String,
    val locationType: HideLocationType,
    val locationId: String,
    val locationLabel: String,
)

data class HiddenItemGroup(
    val itemKey: String,
    val label: String,
    val icon: Drawable?,
    val entries: List<HiddenEntry>,
)

data class AppVisibilityUiState(
    val loading: Boolean = true,
    val groups: List<HiddenItemGroup> = emptyList(),
) {
    val totalHidden: Int get() = groups.size
}

@HiltViewModel
class AppVisibilityViewModel @Inject constructor(
    private val appCategoryRepository: AppCategoryRepository,
    private val appOverrideDao: AppOverrideDao,
    private val hiddenPlacementDao: HiddenPlacementDao,
) : ViewModel() {
    private val installedInfo = MutableStateFlow<Map<String, Pair<String, Drawable>>>(emptyMap())
    private val loading = MutableStateFlow(true)

    init {
        viewModelScope.launch {
            appCategoryRepository.ensureLoaded()
            installedInfo.value = appCategoryRepository.allInstalledApps()
                .associate { it.packageName to (it.label to it.icon) }
            loading.value = false
        }
    }

    val uiState: StateFlow<AppVisibilityUiState> = combine(
        hiddenPlacementDao.observeAll(),
        appOverrideDao.observeAll(),
        installedInfo,
        loading,
    ) { placements, overrides, info, isLoading ->

        val byItem = linkedMapOf<String, MutableList<HiddenEntry>>()
        val labels = hashMapOf<String, String>()

        overrides.filter { it.isHidden }.forEach { ov ->
            val itemKey = HiddenPlacement.appKey(ov.packageName)
            val label = info[ov.packageName]?.first ?: ov.customLabel ?: ov.packageName
            labels[itemKey] = label
            byItem.getOrPut(itemKey) { mutableListOf() }.add(
                HiddenEntry(itemKey, HideLocationType.GLOBAL, "", "Everywhere")
            )
        }

        placements.forEach { p ->
            val type = runCatching { HideLocationType.valueOf(p.locationType) }.getOrDefault(HideLocationType.GLOBAL)
            labels.putIfAbsent(p.itemKey, p.itemLabel)
            byItem.getOrPut(p.itemKey) { mutableListOf() }.add(
                HiddenEntry(p.itemKey, type, p.locationId, p.locationLabel)
            )
        }

        val groups = byItem.entries
            .map { (itemKey, entries) ->
                val pkg = itemKey.removePrefix("app:").takeIf { itemKey.startsWith("app:") }
                HiddenItemGroup(
                    itemKey = itemKey,
                    label = labels[itemKey] ?: itemKey,
                    icon = pkg?.let { info[it]?.second },
                    entries = entries.sortedBy { it.locationLabel.lowercase() },
                )
            }
            .sortedBy { it.label.lowercase() }

        AppVisibilityUiState(loading = isLoading, groups = groups)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppVisibilityUiState())

    fun unhide(entry: HiddenEntry) {
        viewModelScope.launch {
            if (entry.locationType == HideLocationType.GLOBAL) {
                val pkg = entry.itemKey.removePrefix("app:")
                appOverrideDao.setHidden(pkg, false)
            } else {
                hiddenPlacementDao.delete(entry.itemKey, entry.locationType.name, entry.locationId)
            }
        }
    }

    fun unhideAll(group: HiddenItemGroup) {
        viewModelScope.launch {
            if (group.itemKey.startsWith("app:")) {
                appOverrideDao.setHidden(group.itemKey.removePrefix("app:"), false)
            }
            hiddenPlacementDao.deleteAllForItem(group.itemKey)
        }
    }
}
