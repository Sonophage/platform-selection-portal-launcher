package com.psplauncher.core.data.saf

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import timber.log.Timber
import java.io.File

data class SafChild(
    val documentId: String,
    val uri: Uri,
    val name: String,
    val mime: String?,
    val isDirectory: Boolean,
    val lastModified: Long?,
    val sizeBytes: Long?,
)

fun List<SafChild>.hasNoMediaMarker(): Boolean =
    any { !it.isDirectory && it.name.equals(".nomedia", ignoreCase = true) }

fun SafChild.isIgnoredDir(): Boolean = isDirectory && name.startsWith(".")

fun safScanStartDocId(context: android.content.Context, uri: Uri): String =
    if (DocumentsContract.isDocumentUri(context, uri)) DocumentsContract.getDocumentId(uri)
    else DocumentsContract.getTreeDocumentId(uri)

fun safSiblingDocumentId(documentId: String, siblingName: String): String? {
    val slash = documentId.lastIndexOf('/')
    if (slash >= 0) return documentId.substring(0, slash + 1) + siblingName
    val colon = documentId.lastIndexOf(':')
    if (colon >= 0) return documentId.substring(0, colon + 1) + siblingName
    return null
}

fun isSafeSiblingName(name: String): Boolean =
    name.isNotEmpty() && name != "." && name != ".." &&
        !name.contains('/') && !name.contains('\\') &&
        File(name).name == name

fun ContentResolver.querySafChildren(treeUri: Uri, parentDocumentId: String): List<SafChild> {
    val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
    val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
    )
    val out = mutableListOf<SafChild>()
    runCatching {
        query(childrenUri, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val docId = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val mime = c.getString(2)
                out.add(
                    SafChild(
                        documentId   = docId,
                        uri          = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId),
                        name         = name,
                        mime         = mime,
                        isDirectory  = mime == DocumentsContract.Document.MIME_TYPE_DIR,
                        lastModified = if (c.isNull(3)) null else c.getLong(3).takeIf { it > 0 },
                        sizeBytes    = if (c.isNull(4)) null else c.getLong(4).takeIf { it > 0 },
                    )
                )
            }
        }
    }.onFailure { Timber.w(it, "Could not list children of $parentDocumentId") }
    return out
}
