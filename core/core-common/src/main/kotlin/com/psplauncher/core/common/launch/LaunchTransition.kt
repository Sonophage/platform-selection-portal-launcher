package com.psplauncher.core.common.launch

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle

object LaunchTransition {
    fun options(context: Context): Bundle? =
        runCatching { ActivityOptions.makeCustomAnimation(context, 0, 0).toBundle() }.getOrNull()

    fun Intent.withoutTransition(): Intent = addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
}
