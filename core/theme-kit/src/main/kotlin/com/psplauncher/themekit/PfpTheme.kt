package com.psplauncher.themekit

import java.io.File
import java.io.OutputStream
import kotlinx.serialization.Serializable

@Serializable
data class PfpThemeManifest(
    val manifest: String = MANIFEST_TYPE,
    val schemaVersion: Int = SCHEMA_VERSION,
    val name: String,

    val accentColor: String,

    val iconColor: String = ICON_COLOR_AUTO,

    val textColor: String = ICON_COLOR_AUTO,
    val waveStyle: String = WAVE_ANIMATED,

    val layout: XmbLayoutSpec? = null,
    val source: PfpThemeSource? = null,

    val created: String? = null,
) {
    companion object {
        const val MANIFEST_TYPE = "pfptheme"

        const val SCHEMA_VERSION = 3
        const val ICON_COLOR_AUTO = "auto"
        const val WAVE_ANIMATED = "animated"
        const val WAVE_STATIC = "static"
        const val WAVE_REDUCED = "reduced"
    }
}

@Serializable
data class PfpThemeSource(
    val type: String,
    val file: String? = null,
    val firmware: String? = null,
) {
    companion object {
        const val TYPE_PTF_IMPORT = "ptf-import"
        const val TYPE_USER_CREATED = "user-created"
    }
}

class ThemeMotion internal constructor(

    val extension: String,
    private val copy: (OutputStream) -> Long,
) {
    fun copyTo(out: OutputStream): Long = copy(out)

    override fun equals(other: Any?): Boolean = other is ThemeMotion && extension == other.extension

    override fun hashCode(): Int = extension.hashCode()

    override fun toString(): String = "ThemeMotion($extension)"

    companion object {
        fun ofFile(file: File, extension: String = file.extension.lowercase()): ThemeMotion =
            ThemeMotion(extension) { out -> file.inputStream().use { it.copyTo(out) } }

        fun ofBytes(bytes: ByteArray, extension: String): ThemeMotion =
            ThemeMotion(extension) { out -> out.write(bytes); bytes.size.toLong() }
    }
}

data class ThemeImage(
    val bytes: ByteArray,

    val extension: String,
) {
    override fun equals(other: Any?): Boolean =
        other is ThemeImage && extension == other.extension && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = 31 * bytes.contentHashCode() + extension.hashCode()
}

data class PfpThemeBundle(
    val manifest: PfpThemeManifest,

    val wallpaper: ByteArray?,

    val preview: ByteArray?,

    val icons: Map<String, ThemeImage> = emptyMap(),

    val sysicons: Map<String, ThemeImage> = emptyMap(),

    val motion: ThemeMotion? = null,
) {
    override fun equals(other: Any?): Boolean =
        other is PfpThemeBundle &&
            manifest == other.manifest &&
            wallpaper.contentEquals(other.wallpaper) &&
            preview.contentEquals(other.preview) &&
            icons.keys == other.icons.keys &&
            icons.all { (key, image) -> image == other.icons[key] } &&
            sysicons.keys == other.sysicons.keys &&
            sysicons.all { (key, image) -> image == other.sysicons[key] } &&
            motion == other.motion

    override fun hashCode(): Int {
        var h = 31 * (31 * manifest.hashCode() + wallpaper.contentHashCode()) + preview.contentHashCode()
        for ((key, image) in icons) h = 31 * h + (key.hashCode() xor image.hashCode())
        for ((key, image) in sysicons) h = 31 * h + (key.hashCode() xor image.hashCode())
        motion?.let { h = 31 * h + it.hashCode() }
        return h
    }
}
