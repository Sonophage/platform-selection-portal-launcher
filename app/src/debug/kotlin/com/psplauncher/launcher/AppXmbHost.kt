package com.psplauncher.launcher

import androidx.compose.runtime.Composable
import com.psplauncher.feature.xmb.ui.XMBShellContainer
import com.psplauncher.launcher.debug.DebugAwareXMBHost

// Debug variant: wraps the shell so long-pressing Settings opens the debug menu.
@Composable
fun AppXmbHost() {
    DebugAwareXMBHost { onSettingsLongPress ->
        XMBShellContainer(onSettingsLongPress = onSettingsLongPress)
    }
}
