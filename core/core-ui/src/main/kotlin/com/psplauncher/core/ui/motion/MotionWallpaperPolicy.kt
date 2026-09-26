package com.psplauncher.core.ui.motion

import com.psplauncher.core.ui.wave.WaveStyle

object MotionWallpaperPolicy {
    enum class Decision {
        POSTER,

        PLAY,

        PLAY_REDUCED,
    }

    data class Inputs(

        val hasMotion: Boolean,

        val hasPoster: Boolean,

        val style: WaveStyle,

        val covered: Boolean,

        val throttled: Boolean,

        val appVisible: Boolean,
    )

    fun decide(inputs: Inputs): Decision {
        if (!inputs.hasMotion || !inputs.hasPoster) return Decision.POSTER

        if (!inputs.style.animated) return Decision.POSTER

        if (inputs.covered || inputs.throttled || !inputs.appVisible) return Decision.POSTER
        return if (inputs.style.reduced) Decision.PLAY_REDUCED else Decision.PLAY
    }
}
