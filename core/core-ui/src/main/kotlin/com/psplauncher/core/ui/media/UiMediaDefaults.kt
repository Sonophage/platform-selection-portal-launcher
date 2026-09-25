package com.psplauncher.core.ui.media

import android.content.Context
import com.psplauncher.core.domain.model.UiMediaSlot
import com.psplauncher.core.ui.R

/**
 * The bundled default sample for each customizable slot — the ONE mapping from slot to the
 * `res/raw` asset that plays when the user has not assigned anything.
 *
 * Both consumers hang off this single function so the mapping cannot drift between them:
 * [com.psplauncher.core.ui.sound.MenuSoundPlayer] loads one SoundPool sample per DISTINCT
 * slot (Navigation's three events share `sfx_cursor` by construction, not by three copies), and
 * [bundledDefaultUri] hands ExoPlayer a URI for the AUDIO_TRACK slots that have a default.
 *
 * [UiMediaSlot.BOOT_AUDIO] is the only AUDIO_TRACK slot with a default — that is what makes Boot
 * Sound audible out of the box (before this, `bootAudioPath == null` meant silence). GameBoot has
 * no audio SLOT at all any more; its default sound is [gameBootDefaultAudioUri], which is not
 * user-assignable and so does not belong in this table. Video slots deliberately return null
 * forever: there is no bundled boot or GameBoot video and none should be added.
 */
fun UiMediaSlot.bundledDefaultRes(): Int? = when (this) {
    // The same bundled cue for all three movement events. The slots are separate so they CAN
    // differ; out of the box they do not, because there is one cursor sample in res/raw and
    // inventing two more is not something code can do.
    UiMediaSlot.SOUND_SCROLL,
    UiMediaSlot.SOUND_SELECT,
    UiMediaSlot.SOUND_SYSTEM_BROWSE -> R.raw.sfx_cursor
    UiMediaSlot.SOUND_BACK -> R.raw.sfx_back
    UiMediaSlot.SOUND_CONFIRM -> R.raw.sfx_confirm
    UiMediaSlot.SOUND_ERROR -> R.raw.sfx_error
    UiMediaSlot.SOUND_LAUNCH -> R.raw.sfx_launch
    UiMediaSlot.SOUND_NOTIFICATION -> R.raw.sfx_notification
    UiMediaSlot.BOOT_AUDIO -> R.raw.sfx_opening
    // The sound the built-in GameBoot sequence is drawn against, now reachable as a default for
    // the slot that can replace it rather than only through gameBootDefaultAudioUri().
    UiMediaSlot.GAMEBOOT_AUDIO -> R.raw.sfx_launch
    // The opener has no bundled cue: out of the box the ceremony opens in silence, as it always
    // has, and a sound here is something the user brings.
    UiMediaSlot.LAUNCH_DISC_AUDIO,
    UiMediaSlot.BOOT_VIDEO,
    UiMediaSlot.GAMEBOOT_VIDEO,
    // Menu music has no bundled track on purpose: the toggle is inert until the user assigns
    // one, which is the difference between an option and a launcher that starts singing.
    UiMediaSlot.MENU_MUSIC,
    -> null
}

/**
 * [UiMediaSlot.bundledDefaultRes] as a URI string ExoPlayer can open (the numeric resource-id
 * form its RawResourceDataSource resolves). Takes the package name rather than a [Context] so
 * the resolution stays testable in a plain JVM unit test; callers already hold a context.
 */
fun UiMediaSlot.bundledDefaultUri(packageName: String): String? =
    bundledDefaultRes()?.let { rawResourceUri(packageName, it) }

/** The `res/raw` URI form ExoPlayer's RawResourceDataSource resolves. */
private fun rawResourceUri(packageName: String, resId: Int): String =
    "android.resource://$packageName/$resId"

/**
 * The sound the built-in GameBoot sequence is drawn against — the bundled `sfx_launch` sample,
 * exactly 5.000 s, which is what feature-xmb's `GameBootSequence` timeline is beat-matched to
 * (core-ui cannot see that module, hence the prose reference).
 *
 * Deliberately NOT a [UiMediaSlot]: GameBoot is ONE thing the user replaces wholesale with their
 * own clip (which brings its own audio), so there is nothing here to assign separately. That is
 * why this is a plain function rather than another row in [bundledDefaultRes].
 */
fun gameBootDefaultAudioUri(packageName: String): String =
    rawResourceUri(packageName, R.raw.sfx_launch)

/**
 * What the Boot Sequence should actually play for audio, given the user's custom boot video,
 * their custom boot sound, and the slot's bundled default:
 *
 *  • a custom boot sound always wins, over both the clip's own track and the bundled chime;
 *  • a custom boot video with NO custom sound keeps its own audio track (null) — falling back to
 *    the bundled chime here would silently mute every custom boot video and play the opening
 *    under it, which is never what the user meant;
 *  • no custom media at all → the bundled opening chime, so Boot Sound ships audible.
 */
fun resolveBootAudio(
    customVideoPath: String?,
    customAudioPath: String?,
    bundledDefaultUri: String?,
): String? = customAudioPath ?: if (customVideoPath == null) bundledDefaultUri else null

/** Convenience overload for callers that already hold a [Context]. */
fun UiMediaSlot.bundledDefaultUri(context: Context): String? =
    bundledDefaultUri(context.packageName)

/**
 * What the GameBoot transition should actually play for audio:
 *
 *  • a custom GameBoot video keeps its own audio track (null) — playing anything under someone's
 *    clip would score their video with a sound they never asked for;
 *  • otherwise the user's own GameBoot sound if they have assigned one;
 *  • otherwise the built-in sequence's bundled cue.
 *
 * Same shape as [resolveBootAudio] now that GameBoot has a sound slot again. The video branch is
 * what keeps the old rule intact: replacing the whole presentation still replaces the whole
 * presentation.
 */
fun resolveGameBootAudio(
    customVideoPath: String?,
    customAudioPath: String?,
    defaultUri: String?,
): String? = if (customVideoPath != null) null else customAudioPath ?: defaultUri

/** Convenience overload for callers that already hold a [Context]. */
fun gameBootDefaultAudioUri(context: Context): String = gameBootDefaultAudioUri(context.packageName)
