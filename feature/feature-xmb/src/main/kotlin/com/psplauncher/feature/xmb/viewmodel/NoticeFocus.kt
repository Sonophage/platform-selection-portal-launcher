package com.psplauncher.feature.xmb.viewmodel

sealed interface NoticeFocus {
    data object Media : NoticeFocus

    data class Notice(val key: String) : NoticeFocus
}

const val NOTICE_ROWS = 5
