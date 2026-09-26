package com.psplauncher.core.data.model

object StorefrontIdentity {
    val KNOWN_STORES: Set<String> = setOf("STEAM", "EPIC", "GOG", "AMAZON", "CUSTOM_GAME")

    fun normalizeStore(raw: String?): String? =
        raw?.trim()?.uppercase()?.takeIf { it in KNOWN_STORES }

    fun isPlausibleAppId(id: String?): Boolean {
        val trimmed = id?.trim() ?: return false
        return trimmed.isNotEmpty() && trimmed.length <= 12 && trimmed.all(Char::isDigit)
    }

    fun fromLaunchIntentUri(intentUri: String?): Pair<String, String>? {
        if (intentUri.isNullOrBlank()) return null
        val extras = IntentUriExtras.parse(intentUri)

        val gameNativeId = extras["i.app_id"] ?: extras["l.app_id"]
        if (isPlausibleAppId(gameNativeId)) {
            val store = normalizeStore(extras["S.game_source"]) ?: "STEAM"
            return store to gameNativeId!!.trim()
        }

        val steamAppId = extras["S.steamAppId"]
        if (isPlausibleAppId(steamAppId)) return "STEAM" to steamAppId!!.trim()

        return null
    }
}
