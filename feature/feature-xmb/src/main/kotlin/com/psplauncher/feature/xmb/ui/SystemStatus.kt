package com.psplauncher.feature.xmb.ui

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.input.InputManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.telephony.PhoneStateListener
import android.telephony.SignalStrength
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.view.InputDevice
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.getSystemService
import timber.log.Timber

data class SystemStatus(
    val bluetoothOn: Boolean = false,
    val wifiLevel: Int? = null,
    val cellularLevel: Int? = null,
    val controllerConnected: Boolean = false,
)

const val SIGNAL_MAX_LEVEL = 4

@Composable
fun rememberSystemStatus(): SystemStatus {
    val context = LocalContext.current
    var status by remember { mutableStateOf(SystemStatus()) }

    DisposableEffect(Unit) {
        val adapter = context.getSystemService<BluetoothManager>()?.adapter
        fun push() { status = status.copy(bluetoothOn = adapter?.isEnabled == true) }
        push()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) = push()
        }

        context.registerReceiver(receiver, IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED))
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }

    DisposableEffect(Unit) {
        val cm = context.getSystemService<ConnectivityManager>()
        if (cm == null) {
            onDispose { }
        } else {
            val callback = object : ConnectivityManager.NetworkCallback() {
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                        status = status.copy(wifiLevel = wifiLevelFromDbm(caps.signalStrength))
                    }
                }
                override fun onLost(network: Network) {
                    status = status.copy(wifiLevel = null)
                }
            }
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .build()
            runCatching { cm.registerNetworkCallback(request, callback) }
            onDispose { runCatching { cm.unregisterNetworkCallback(callback) } }
        }
    }

    DisposableEffect(Unit) {
        val tm = context.getSystemService<TelephonyManager>()
        val hasModem = tm != null && tm.phoneType != TelephonyManager.PHONE_TYPE_NONE

        if (tm == null || !hasModem || tm.simState != TelephonyManager.SIM_STATE_READY) {
            status = status.copy(cellularLevel = null)
            onDispose { }
        } else {
            status = status.copy(cellularLevel = 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val callback = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength) {
                        status = status.copy(cellularLevel = signalStrength.level.coerceIn(0, SIGNAL_MAX_LEVEL))
                    }
                }
                runCatching { tm.registerTelephonyCallback(context.mainExecutor, callback) }
                onDispose { runCatching { tm.unregisterTelephonyCallback(callback) } }
            } else {
                @Suppress("DEPRECATION")
                val listener = object : PhoneStateListener() {
                    @Deprecated("Deprecated in Java")
                    override fun onSignalStrengthsChanged(signalStrength: SignalStrength?) {
                        val lvl = signalStrength?.level ?: 0
                        status = status.copy(cellularLevel = lvl.coerceIn(0, SIGNAL_MAX_LEVEL))
                    }
                }
                @Suppress("DEPRECATION")
                runCatching { tm.listen(listener, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) }
                @Suppress("DEPRECATION")
                onDispose { runCatching { tm.listen(listener, PhoneStateListener.LISTEN_NONE) } }
            }
        }
    }

    DisposableEffect(Unit) {
        val im = context.getSystemService<InputManager>()
        fun push() { status = status.copy(controllerConnected = anyControllerConnected()) }
        push()
        val listener = object : InputManager.InputDeviceListener {
            override fun onInputDeviceAdded(deviceId: Int) = push()
            override fun onInputDeviceRemoved(deviceId: Int) = push()
            override fun onInputDeviceChanged(deviceId: Int) = push()
        }
        runCatching { im?.registerInputDeviceListener(listener, null) }
        onDispose { runCatching { im?.unregisterInputDeviceListener(listener) } }
    }

    return status
}

fun wifiLevelFromDbm(dbm: Int): Int = when {
    dbm == NetworkCapabilities.SIGNAL_STRENGTH_UNSPECIFIED -> SIGNAL_MAX_LEVEL
    dbm >= -55 -> 4
    dbm >= -66 -> 3
    dbm >= -77 -> 2
    dbm >= -88 -> 1
    else       -> 0
}

private fun anyControllerConnected(): Boolean =
    runCatching {
        InputDevice.getDeviceIds().any { id ->
            val device = InputDevice.getDevice(id) ?: return@any false
            if (device.isVirtual) return@any false
            val sources = device.sources
            (sources and InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD ||
                (sources and InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
        }
    }.getOrElse { Timber.w(it, "Controller scan failed"); false }
