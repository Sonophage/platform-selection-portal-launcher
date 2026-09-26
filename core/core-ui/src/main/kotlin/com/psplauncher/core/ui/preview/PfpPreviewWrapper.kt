package com.psplauncher.core.ui.preview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.psplauncher.core.ui.theme.DefaultPFPColors
import com.psplauncher.core.ui.theme.PFPColors
import com.psplauncher.core.ui.theme.PFPTheme

@Composable
fun PfpPreview(
    colors: PFPColors = DefaultPFPColors,
    content: @Composable () -> Unit,
) {
    PFPTheme(colors = colors) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}

@Composable
fun PfpScreenPreview(
    colors: PFPColors = DefaultPFPColors,
    content: @Composable () -> Unit,
) {
    PFPTheme(colors = colors) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            content = content,
        )
    }
}
