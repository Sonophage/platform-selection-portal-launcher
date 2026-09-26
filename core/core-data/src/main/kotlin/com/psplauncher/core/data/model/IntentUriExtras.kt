package com.psplauncher.core.data.model

import java.io.ByteArrayOutputStream

object IntentUriExtras {
    fun parse(intentUri: String): Map<String, String> {
        val body = intentUri.substringAfter("#Intent;", missingDelimiterValue = intentUri)
        return body.split(';')
            .mapNotNull { segment ->
                if (!segment.contains('=')) return@mapNotNull null
                val key = segment.substringBefore('=')

                if (key.length < 3 || key[1] != '.') return@mapNotNull null
                key to decode(segment.substringAfter('='))
            }
            .toMap()
    }

    fun stringExtra(intentUri: String?, name: String): String? =
        intentUri?.takeIf { it.isNotBlank() }?.let { parse(it)["S.$name"] }

    private fun decode(raw: String): String {
        if ('%' !in raw) return raw
        val bytes = ByteArrayOutputStream(raw.length)
        var i = 0
        while (i < raw.length) {
            if (raw[i] == '%' && i + 2 < raw.length) {
                val high = Character.digit(raw[i + 1], 16)
                val low = Character.digit(raw[i + 2], 16)
                if (high >= 0 && low >= 0) {
                    bytes.write(high * 16 + low)
                    i += 3
                    continue
                }
            }

            val next = raw.indexOf('%', i + 1).let { if (it < 0) raw.length else it }
            bytes.write(raw.substring(i, next).toByteArray(Charsets.UTF_8))
            i = next
        }
        return String(bytes.toByteArray(), Charsets.UTF_8)
    }
}
