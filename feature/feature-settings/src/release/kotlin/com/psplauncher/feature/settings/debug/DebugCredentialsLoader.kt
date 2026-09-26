package com.psplauncher.feature.settings.debug

import javax.inject.Inject

class DebugCredentialsLoader @Inject constructor() {
    suspend fun load(text: String): DebugCredentialsResult =
        DebugCredentialsResult("Credentials files can only be loaded in debug builds", anyUnprotected = false)
}
