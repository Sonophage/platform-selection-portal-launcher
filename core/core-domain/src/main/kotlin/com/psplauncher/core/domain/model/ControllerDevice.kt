package com.psplauncher.core.domain.model

data class ControllerDevice(
    val deviceId: Int,
    val name: String,
    val vendorId: Int,
    val productId: Int,

    val isGamepad: Boolean,

    val isJoystick: Boolean,

    val connectedAtEpochMs: Long,
)
