package com.psplauncher.core.common.logging

object LogRedaction {
    private val SECRET_PARAMS = Regex(
        "\\b((?i:devpassword|sspassword|password|passwd|pwd|apikey|api_key|key|client_id|client_secret|" +
            "clientsecret|access_token|refresh_token|token|secret|sspass|auth)|ssid)=([^&\\s\"']+)"
    )

    private val SECRET_HEADERS = Regex(
        "(?i)\\b(authorization|client-id|x-api-key|api-key)\\s*[:=]\\s*([^\\s\"',;]+(\\s+[^\\s\"',;]+)?)"
    )

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")

    fun redact(message: String): String {
        var out = message
        if ('=' in out) out = SECRET_PARAMS.replace(out) { "${it.groupValues[1]}=REDACTED" }
        out = SECRET_HEADERS.replace(out) { "${it.groupValues[1]}: REDACTED" }
        if ('@' in out) out = EMAIL.replace(out, "REDACTED@EMAIL")
        return out
    }
}
