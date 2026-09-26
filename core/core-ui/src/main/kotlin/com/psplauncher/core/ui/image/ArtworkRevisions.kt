package com.psplauncher.core.ui.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

object ArtworkRevisions {
    private val revisions = mutableStateMapOf<String, Int>()

    fun bump(uri: String) {
        revisions[uri] = (revisions[uri] ?: 0) + 1
    }

    fun of(uri: String): Int = revisions[uri] ?: 0

    fun cacheKey(uri: String): String? = of(uri).takeIf { it > 0 }?.let { "$uri#r$it" }
}

@Composable
fun rememberArtworkModel(uri: String?): Any? {
    if (uri == null) return null
    val key = ArtworkRevisions.cacheKey(uri) ?: return uri
    val context = LocalPlatformContext.current
    return remember(key, context) {
        ImageRequest.Builder(context)
            .data(uri)
            .memoryCacheKey(key)
            .build()
    }
}
