package com.psplauncher.core.ui.media

import android.content.Context
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.R

fun UiMediaSlot.bundledDefaultRes(): Int? = when (this) {
    UiMediaSlot.SOUND_SCROLL,
    UiMediaSlot.SOUND_SELECT,
    UiMediaSlot.SOUND_SYSTEM_BROWSE -> R.raw.sfx_cursor
    UiMediaSlot.SOUND_BACK -> R.raw.sfx_back
    UiMediaSlot.SOUND_CONFIRM -> R.raw.sfx_confirm
    UiMediaSlot.SOUND_ERROR -> R.raw.sfx_error
    UiMediaSlot.SOUND_LAUNCH -> R.raw.sfx_launch
    UiMediaSlot.SOUND_NOTIFICATION -> R.raw.sfx_notification
    UiMediaSlot.BOOT_AUDIO -> R.raw.sfx_opening

    UiMediaSlot.GAMEBOOT_AUDIO -> R.raw.sfx_launch

    UiMediaSlot.LAUNCH_DISC_AUDIO,
    UiMediaSlot.BOOT_VIDEO,
    UiMediaSlot.GAMEBOOT_VIDEO,

    UiMediaSlot.MENU_MUSIC,
    -> null
}

fun UiMediaSlot.bundledDefaultUri(packageName: String): String? =
    bundledDefaultRes()?.let { rawResourceUri(packageName, it) }

private fun rawResourceUri(packageName: String, resId: Int): String =
    "android.resource://$packageName/$resId"

fun gameBootDefaultAudioUri(packageName: String): String =
    rawResourceUri(packageName, R.raw.sfx_launch)

fun resolveBootAudio(
    customVideoPath: String?,
    customAudioPath: String?,
    bundledDefaultUri: String?,
): String? = customAudioPath ?: if (customVideoPath == null) bundledDefaultUri else null

fun UiMediaSlot.bundledDefaultUri(context: Context): String? =
    bundledDefaultUri(context.packageName)

fun resolveGameBootAudio(
    customVideoPath: String?,
    customAudioPath: String?,
    defaultUri: String?,
): String? = if (customVideoPath != null) null else customAudioPath ?: defaultUri

fun gameBootDefaultAudioUri(context: Context): String = gameBootDefaultAudioUri(context.packageName)
