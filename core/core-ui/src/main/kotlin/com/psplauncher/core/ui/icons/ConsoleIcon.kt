package com.psplauncher.core.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
fun ConsoleIcon(
    platformId: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
) {
    val key = "sysicon_${platformId ?: "default"}"
    val icon = LocalCustomIcons.current[key] ?: LocalXmbIconOverrides.current[key]
    if (icon != null) {
        CustomIconSurface(icon, contentDescription, modifier)
        return
    }
    PortalIcon(
        painter = painterResource(systemIconRes(platformId)),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
