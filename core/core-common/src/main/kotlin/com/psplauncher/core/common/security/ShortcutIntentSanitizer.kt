package com.psplauncher.core.common.security

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

object ShortcutIntentSanitizer {
    private val GRANT_FLAGS =
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION or
        Intent.FLAG_GRANT_PREFIX_URI_PERMISSION

    @Suppress("QueryPermissionsNeeded")
    fun sanitize(raw: Intent, pm: PackageManager): Intent? {
        val safe = Intent(raw).apply {
            flags = flags and GRANT_FLAGS.inv()
            clipData = null
        }

        if (safe.component == null) {
            val resolved = safe.resolveActivity(pm) ?: return null
            safe.component = resolved
        } else if (!resolves(safe, pm)) {
            return null
        }
        return safe
    }

    private fun resolves(intent: Intent, pm: PackageManager): Boolean =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.resolveActivity(intent, PackageManager.ResolveInfoFlags.of(0)) != null
        } else {
            @Suppress("DEPRECATION")
            pm.resolveActivity(intent, 0) != null
        }
}
