package com.psplauncher.core.data.repository

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RemapCoordinator @Inject constructor() {
    var captureNextKey: ((keyCode: Int) -> Unit)? = null
}
