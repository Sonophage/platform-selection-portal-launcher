package com.psplauncher.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.psplauncher.core.data.repository.ControllerLayoutRepository
import com.psplauncher.core.data.repository.ControllerMappingRepository
import com.psplauncher.core.domain.model.ControllerLayoutPrefs
import com.psplauncher.core.domain.model.GamepadMappings
import com.psplauncher.core.ui.components.ControllerPromptStyle
import com.psplauncher.core.ui.components.LocalControllerPromptStyle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.flow.combine

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ControllerPromptsEntryPoint {
    fun controllerLayoutRepository(): ControllerLayoutRepository
    fun controllerMappingRepository(): ControllerMappingRepository
}

@Composable
fun ProvideControllerPrompts(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ControllerPromptsEntryPoint::class.java,
        )
    }
    val styleFlow = remember(entryPoint) {
        combine(
            entryPoint.controllerLayoutRepository().prefs,
            entryPoint.controllerMappingRepository().mappings,
        ) { prefs: ControllerLayoutPrefs, mappings: GamepadMappings ->
            ControllerPromptStyle(family = prefs.displayType, mappings = mappings)
        }
    }

    val style by styleFlow.collectAsStateWithLifecycle(initialValue = ControllerPromptStyle())

    CompositionLocalProvider(LocalControllerPromptStyle provides style) {
        content()
    }
}
