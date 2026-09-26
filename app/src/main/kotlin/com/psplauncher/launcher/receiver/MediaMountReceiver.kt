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

class MediaMountReceiver : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun libraryRescanCoordinator(): LibraryRescanCoordinator
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MEDIA_MOUNTED) return

        Timber.i("Media mounted (${intent.data}) — requesting library rescan")
        EntryPointAccessors
            .fromApplication(context.applicationContext, Deps::class.java)
            .libraryRescanCoordinator()
            .onMediaMounted()
    }
}
