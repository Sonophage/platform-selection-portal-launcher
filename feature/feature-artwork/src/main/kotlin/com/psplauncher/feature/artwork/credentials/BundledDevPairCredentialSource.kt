package com.psplauncher.feature.artwork.credentials

import com.psplauncher.feature.artwork.BuildConfig
import com.psplauncher.feature.artwork.MetadataApiKeyProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

object DevPairDecoder {
    fun decode(share: ByteArray?, mask: ByteArray?): String? {
        if (share == null || mask == null) return null
        if (share.isEmpty() || mask.isEmpty() || share.size != mask.size) return null
        val plain = ByteArray(share.size) { i -> (share[i].toInt() xor mask[i].toInt()).toByte() }
        val text = String(plain, Charsets.UTF_8)
        return text.trim().takeIf { it.isNotEmpty() }
    }
}

@Singleton
class BundledDevPairCredentialSource @Inject constructor(
    private val keys: MetadataApiKeyProvider,
) : MetadataCredentialSource {
    private fun bundledPair(): Pair<String, String>? {
        val devId = DevPairDecoder.decode(BuildConfig.SS_DEV_ID_SHARE, BuildConfig.SS_DEV_ID_MASK)
        val devPassword = DevPairDecoder.decode(BuildConfig.SS_DEV_PASSWORD_SHARE, BuildConfig.SS_DEV_PASSWORD_MASK)
        return if (devId != null && devPassword != null) devId to devPassword else null
    }

    override val screenScraper: Flow<ScreenScraperCredentials?> =
        keys.ssUsernameFlow.map { userId ->

            val (devId, devPassword) = bundledPair() ?: return@map null
            ScreenScraperCredentials.of(
                devId        = devId,
                devPassword  = devPassword,
                userId       = userId,
                userPassword = keys.getSsPassword(),
            )
        }

    override suspend fun screenScraperNow(): ScreenScraperCredentials? {
        val (devId, devPassword) = bundledPair() ?: return null
        return ScreenScraperCredentials.of(
            devId        = devId,
            devPassword  = devPassword,
            userId       = keys.getSsUsername(),
            userPassword = keys.getSsPassword(),
        )
    }
}
