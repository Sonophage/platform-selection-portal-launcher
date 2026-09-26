package com.psplauncher.launcher

import androidx.compose.runtime.Composable
import com.psplauncher.feature.xmb.ui.XMBShellContainer
import com.psplauncher.launcher.debug.DebugAwareXMBHost

@Composable
fun AppXmbHost() {
    DebugAwareXMBHost { onSettingsLongPress ->
        XMBShellContainer(onSettingsLongPress = onSettingsLongPress)
    }
}
