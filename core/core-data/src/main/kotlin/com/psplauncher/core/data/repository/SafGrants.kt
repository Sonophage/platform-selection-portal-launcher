package com.psplauncher.core.data.repository

import android.content.ContentResolver

enum class FolderLinkStatus {
    LINKED,

    ACCESS_LOST,
}

object SafGrants {
    fun persistedReadUris(contentResolver: ContentResolver): Set<String> =
        contentResolver.persistedUriPermissions
            .filter { it.isReadPermission }
            .map { it.uri.toString() }
            .toSet()

    fun linkStatus(treeUri: String, persistedReadUris: Set<String>): FolderLinkStatus =
        if (treeUri in persistedReadUris) FolderLinkStatus.LINKED else FolderLinkStatus.ACCESS_LOST
}
