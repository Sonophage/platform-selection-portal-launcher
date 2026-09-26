package com.psplauncher.core.data.repository

fun interface CustomIconCacheEvictor {
    fun evict(path: String)
}
