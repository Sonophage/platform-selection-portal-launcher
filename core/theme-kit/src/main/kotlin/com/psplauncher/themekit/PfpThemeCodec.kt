package com.psplauncher.themekit

import com.psplauncher.core.archive.BoundedZipReader
import com.psplauncher.core.archive.ZipLimitExceededException
import com.psplauncher.core.archive.ZipLimits
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.serialization.json.Json

object PfpThemeCodec {
    const val FILE_EXTENSION = "pfptheme"
    private const val ENTRY_MANIFEST = "manifest.json"
    private const val ENTRY_WALLPAPER = "wallpaper.png"
    private const val ENTRY_PREVIEW = "preview.png"
    private const val ICONS_PREFIX = "icons/"
    private const val SYSICONS_PREFIX = "sysicons/"
    private const val MOTION_PREFIX = "motion."

    val ICON_EXTENSIONS = setOf("png", "gif")
    val MOTION_EXTENSIONS = setOf("mp4", "webm", "gif")

    val BUNDLE_LIMITS = ZipLimits(
        maxEntries    = 256,
        maxEntryBytes = 64L * 1024 * 1024,
        maxTotalBytes = 256L * 1024 * 1024,
    )

    const val MAX_ICON_BYTES = 8 * 1024 * 1024

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    fun write(bundle: PfpThemeBundle, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_MANIFEST))
            zip.write(json.encodeToString(PfpThemeManifest.serializer(), bundle.manifest).toByteArray())
            zip.closeEntry()
            bundle.wallpaper?.let { zip.writeEntry(ENTRY_WALLPAPER, it) }
            bundle.preview?.let { zip.writeEntry(ENTRY_PREVIEW, it) }

            for ((key, image) in bundle.icons.toSortedMap()) {
                if (IconSlots.isValidKey(key) && image.extension.lowercase() in ICON_EXTENSIONS) {
                    zip.writeEntry("$ICONS_PREFIX$key.${image.extension.lowercase()}", image.bytes)
                }
            }
            for ((platformId, image) in bundle.sysicons.toSortedMap()) {
                if (CustomizableIcons.isValidKey("sysicon_$platformId") && image.extension.lowercase() in ICON_EXTENSIONS) {
                    zip.writeEntry("$SYSICONS_PREFIX$platformId.${image.extension.lowercase()}", image.bytes)
                }
            }

            bundle.motion?.let { motion ->
                val ext = motion.extension.lowercase()
                if (ext in MOTION_EXTENSIONS) {
                    zip.putNextEntry(ZipEntry("$MOTION_PREFIX$ext"))
                    motion.copyTo(zip)
                    zip.closeEntry()
                }
            }
        }
    }

    fun write(bundle: PfpThemeBundle): ByteArray =
        ByteArrayOutputStream().also { write(bundle, it) }.toByteArray()

    @JvmOverloads
    fun read(input: InputStream, reopen: ((String) -> ThemeMotion)? = null): PfpThemeBundle? {
        var manifest: PfpThemeManifest? = null
        var wallpaper: ByteArray? = null
        var preview: ByteArray? = null
        val icons = mutableMapOf<String, ThemeImage>()
        val sysicons = mutableMapOf<String, ThemeImage>()
        var motionExtension: String? = null

        try {
            BoundedZipReader.read(input, BUNDLE_LIMITS) { entry ->
                when {
                    entry.name == ENTRY_MANIFEST -> manifest = runCatching {
                        json.decodeFromString(
                            PfpThemeManifest.serializer(),
                            entry.readBytes().decodeToString(),
                        )
                    }.getOrNull()
                    entry.name == ENTRY_WALLPAPER -> wallpaper = entry.readBytes()
                    entry.name == ENTRY_PREVIEW -> preview = entry.readBytes()
                    entry.name.startsWith(ICONS_PREFIX) -> {
                        val name = entry.name.removePrefix(ICONS_PREFIX)
                        val key = name.substringBeforeLast('.')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in ICON_EXTENSIONS && IconSlots.isValidKey(key)) {
                            entry.readBytes()
                                .takeIf { it.size <= MAX_ICON_BYTES }
                                ?.let { icons[key] = ThemeImage(it, ext) }
                        }
                    }
                    entry.name.startsWith(SYSICONS_PREFIX) -> {
                        val name = entry.name.removePrefix(SYSICONS_PREFIX)
                        val platformId = name.substringBeforeLast('.')
                        val ext = name.substringAfterLast('.', "").lowercase()
                        if (ext in ICON_EXTENSIONS && CustomizableIcons.isValidKey("sysicon_$platformId")) {
                            entry.readBytes()
                                .takeIf { it.size <= MAX_ICON_BYTES }
                                ?.let { sysicons[platformId] = ThemeImage(it, ext) }
                        }
                    }
                    entry.name.startsWith(MOTION_PREFIX) -> {
                        val ext = entry.name.removePrefix(MOTION_PREFIX).lowercase()
                        if (ext in MOTION_EXTENSIONS) motionExtension = ext
                    }
                }
            }
        } catch (e: ZipLimitExceededException) {
            return null
        }

        val m = manifest ?: return null
        if (m.manifest != PfpThemeManifest.MANIFEST_TYPE) return null
        return PfpThemeBundle(
            manifest = m,
            wallpaper = wallpaper,
            preview = preview,
            icons = icons,
            sysicons = sysicons,
            motion = motionExtension?.let { ext -> reopen?.invoke(ext) },
        )
    }

    fun read(bytes: ByteArray): PfpThemeBundle? =
        read(ByteArrayInputStream(bytes)) { ext -> motionFrom({ ByteArrayInputStream(bytes) }, ext) }

    fun read(file: File): PfpThemeBundle? =
        file.inputStream().use { read(it) { ext -> motionFrom({ file.inputStream() }, ext) } }

    fun readManifest(file: File): PfpThemeManifest? {
        var manifest: PfpThemeManifest? = null
        try {
            file.inputStream().use { input ->
                BoundedZipReader.read(input, BUNDLE_LIMITS) { entry ->
                    if (entry.name == ENTRY_MANIFEST) {
                        manifest = runCatching {
                            json.decodeFromString(
                                PfpThemeManifest.serializer(),
                                entry.readBytes().decodeToString(),
                            )
                        }.getOrNull()
                        entry.stop()
                    }
                }
            }
        } catch (e: ZipLimitExceededException) {
            return null
        }
        return manifest?.takeIf { it.manifest == PfpThemeManifest.MANIFEST_TYPE }
    }

    private fun motionFrom(source: () -> InputStream, ext: String): ThemeMotion =
        ThemeMotion(ext) { out ->
            var written = 0L
            source().use { input ->
                BoundedZipReader.read(input, BUNDLE_LIMITS) { entry ->
                    if (entry.name == "$MOTION_PREFIX$ext") {
                        written = entry.copyTo(out)
                        entry.stop()
                    }
                }
            }
            written
        }

    private fun ZipOutputStream.writeEntry(name: String, data: ByteArray) {
        putNextEntry(ZipEntry(name))
        write(data)
        closeEntry()
    }
}
