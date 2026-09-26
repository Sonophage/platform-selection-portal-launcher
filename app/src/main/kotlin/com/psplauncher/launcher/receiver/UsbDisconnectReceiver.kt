package com.psplauncher.launcher.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.psplauncher.feature.library.scanner.LibraryRescanCoordinator
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import timber.log.Timber

class UsbDisconnectReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun libraryRescanCoordinator(): LibraryRescanCoordinator
    }

    private var lastConnected: Boolean? = null

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_USB_STATE) return

        val connected = intent.getBooleanExtra(EXTRA_CONNECTED, false)
        val was = lastConnected
        lastConnected = connected

        if (was != true || connected) return

        Timber.i("USB disconnected — requesting library rescan")
        EntryPointAccessors
            .fromApplication(context.applicationContext, Deps::class.java)
            .libraryRescanCoordinator()
            .onMediaMounted()
    }

    companion object {
        const val ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE"
        private const val EXTRA_CONNECTED = "connected"
    }
}
