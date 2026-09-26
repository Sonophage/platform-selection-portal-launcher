package com.psplauncher.core.data.repository

import android.content.Context
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.view.InputDevice
import com.psplauncher.core.domain.model.ControllerDevice
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ControllerRegistry @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val _connectedControllers = MutableStateFlow<List<ControllerDevice>>(emptyList())
    val connectedControllers: StateFlow<List<ControllerDevice>> = _connectedControllers.asStateFlow()

    private val _lastActiveController = MutableStateFlow<ControllerDevice?>(null)
    val lastActiveController: StateFlow<ControllerDevice?> = _lastActiveController.asStateFlow()

    init {
        runCatching {
            val inputManager = context.getSystemService(Context.INPUT_SERVICE) as? InputManager
                ?: return@runCatching
            inputManager.registerInputDeviceListener(
                object : InputManager.InputDeviceListener {
                    override fun onInputDeviceAdded(deviceId: Int) = refreshDevice(deviceId)
                    override fun onInputDeviceRemoved(deviceId: Int) = removeDevice(deviceId)
                    override fun onInputDeviceChanged(deviceId: Int) = refreshDevice(deviceId)
                },
                Handler(Looper.getMainLooper()),
            )
        }
    }

    fun markActive(deviceId: Int) {
        val device = _connectedControllers.value.firstOrNull { it.deviceId == deviceId } ?: return
        if (_lastActiveController.value?.deviceId != deviceId) {
            _lastActiveController.value = device
            Timber.v("Controller registry: active device ${device.name}")
        }
    }

    fun onDeviceAdded(device: ControllerDevice) {
        _connectedControllers.update { current ->
            if (current.any { it.deviceId == device.deviceId }) current else current + device
        }
    }

    fun onDeviceRemoved(deviceId: Int) {
        _connectedControllers.update { list -> list.filterNot { it.deviceId == deviceId } }
        if (_lastActiveController.value?.deviceId == deviceId) _lastActiveController.value = null
    }

    private fun refreshDevice(deviceId: Int) {
        val snapshot = snapshotDevice(deviceId) ?: return
        onDeviceAdded(snapshot)
    }

    private fun removeDevice(deviceId: Int) = onDeviceRemoved(deviceId)

    private fun snapshotDevice(deviceId: Int): ControllerDevice? {
        val device = InputDevice.getDevice(deviceId) ?: return null
        val sources = device.sources
        return ControllerDevice(
            deviceId = device.id,
            name = device.name ?: "Controller",
            vendorId = device.vendorId,
            productId = device.productId,
            isGamepad = sources and InputDevice.SOURCE_GAMEPAD != 0,
            isJoystick = sources and InputDevice.SOURCE_JOYSTICK != 0,
            connectedAtEpochMs = System.currentTimeMillis(),
        )
    }
}
