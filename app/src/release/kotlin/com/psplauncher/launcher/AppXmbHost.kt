package com.psplauncher.launcher

import androidx.compose.runtime.Composable
import com.psplauncher.feature.xmb.ui.XMBShellContainer

// Release variant: no debug code — go straight to the shell.
@Composable
fun AppXmbHost() {
    XMBShellContainer()
}
