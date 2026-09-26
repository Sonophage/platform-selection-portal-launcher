package com.psplauncher.feature.settings.pc

import com.psplauncher.core.data.model.StorefrontIdentity
import com.psplauncher.feature.artwork.store.ArtworkKind
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull

@Serializable
data class PcGameExport(
    val format: String = PcGameExportCodec.FORMAT,
    val version: Int = PcGameExportCodec.VERSION,
    val title: String = "",
    val scrapedTitle: String? = null,
    val userTitleOverride: String? = null,
    val launcherPackage: String = "",
    val launchIntentUri: String? = null,
    val shortcutId: String? = null,
    val storefront: String? = null,
    val storefrontGameId: String? = null,
    val ssId: Long? = null,
    val igdbId: Long? = null,
    val steamGridDbId: Long? = null,
    val artwork: List<PcGameExportArtwork> = emptyList(),
) {
    val isPin: Boolean get() = shortcutId != null
}

@Serializable
data class PcGameExportArtwork(
    val kind: String = "",
    val sortOrder: Int = 0,
    val portableName: String = "",
)

sealed interface PcGameExportDecode {
    data class Valid(val export: PcGameExport) : PcGameExportDecode

    data class Rejected(val reason: String) : PcGameExportDecode
}

object PcGameExportCodec {
    const val FORMAT = "pfp-pc-game"
    const val VERSION = 1
    const val EXTENSION = "pfpgame"

    const val MAX_CHARS = 256 * 1024
    const val MAX_ARTWORK_ITEMS = 200

    const val MAX_TITLE_CHARS = 200

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true

        prettyPrint = true
    }

    fun encode(export: PcGameExport): String =
        json.encodeToString(PcGameExport.serializer(), export.copy(format = FORMAT, version = VERSION))

    fun decode(text: String): PcGameExportDecode {
        if (text.length > MAX_CHARS) return PcGameExportDecode.Rejected("is too large to be a game export")
        val root = try {
            Json.parseToJsonElement(text) as? JsonObject
        } catch (e: IllegalArgumentException) {
            null
        } ?: return PcGameExportDecode.Rejected("is not a PlayFieldPortal game export")
        if ((root["format"] as? JsonPrimitive)?.contentOrNull != FORMAT) {
            return PcGameExportDecode.Rejected("is not a PlayFieldPortal game export")
        }
        val version = (root["version"] as? JsonPrimitive)?.intOrNull ?: VERSION
        if (version > VERSION) {
            return PcGameExportDecode.Rejected(
                "was made by a newer version of PlayFieldPortal (export version $version); update to import it",
            )
        }
        if (version < 1) return PcGameExportDecode.Rejected("has an invalid version ($version)")

        val raw = try {
            json.decodeFromJsonElement(PcGameExport.serializer(), root)
        } catch (e: IllegalArgumentException) {
            return PcGameExportDecode.Rejected("could not be read: ${e.message}")
        }
        return validate(raw)
    }

    private fun validate(raw: PcGameExport): PcGameExportDecode {
        val title = raw.title.trim().take(MAX_TITLE_CHARS)
        if (title.isEmpty()) return PcGameExportDecode.Rejected("has no title")
        val launcherPackage = raw.launcherPackage.trim()
        if (launcherPackage.isEmpty()) return PcGameExportDecode.Rejected("names no launcher")
        if (raw.artwork.size > MAX_ARTWORK_ITEMS) return PcGameExportDecode.Rejected("lists too many artwork files")

        val shortcutId = raw.shortcutId.orNullIfBlank()
        val launchIntentUri = raw.launchIntentUri.orNullIfBlank()
        if (shortcutId == null && launchIntentUri == null) {
            return PcGameExportDecode.Rejected("has neither a launch intent nor a pinned shortcut")
        }

        val normalizedStore = StorefrontIdentity.normalizeStore(raw.storefront)
        val storefrontGameId = raw.storefrontGameId.orNullIfBlank()
        val hasValidStorefrontPair = normalizedStore != null && StorefrontIdentity.isPlausibleAppId(storefrontGameId)

        return PcGameExportDecode.Valid(
            raw.copy(
                title = title,
                scrapedTitle = raw.scrapedTitle.orNullIfBlank()?.take(MAX_TITLE_CHARS),
                userTitleOverride = raw.userTitleOverride.orNullIfBlank()?.take(MAX_TITLE_CHARS),
                launcherPackage = launcherPackage,
                shortcutId = shortcutId,

                launchIntentUri = if (shortcutId != null) null else launchIntentUri,
                storefront = if (hasValidStorefrontPair) normalizedStore else null,
                storefrontGameId = if (hasValidStorefrontPair) storefrontGameId else null,
                ssId = raw.ssId.orNullIfNotPositive(),
                igdbId = raw.igdbId.orNullIfNotPositive(),
                steamGridDbId = raw.steamGridDbId.orNullIfNotPositive(),
                artwork = raw.artwork.mapNotNull { item ->
                    val kind = item.kind.trim()
                    val name = item.portableName.trim()
                    val isKnownKind = runCatching { ArtworkKind.valueOf(kind) }.isSuccess
                    if (!isKnownKind || name.isEmpty() || item.sortOrder < 0 || !isSafePortableName(name)) null
                    else item.copy(kind = kind, portableName = name)
                },
            ),
        )
    }

    private fun isSafePortableName(name: String): Boolean =
        '/' !in name && '\\' !in name && ".." !in name && name.none { it.isISOControl() }

    private fun String?.orNullIfBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private fun Long?.orNullIfNotPositive(): Long? = this?.takeIf { it > 0 }
}
