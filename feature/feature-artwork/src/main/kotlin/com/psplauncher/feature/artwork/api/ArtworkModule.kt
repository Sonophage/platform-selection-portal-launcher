package com.psplauncher.feature.artwork.api

import android.content.Context
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.request.crossfade
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import com.psplauncher.feature.artwork.BuildConfig
import com.psplauncher.feature.artwork.match.FileTitleSearchStore
import com.psplauncher.feature.artwork.match.TitleSearchStore
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import okio.Path.Companion.toOkioPath
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

private val SECRET_QUERY_PARAMS = Regex("(sspassword|devpassword|apikey)=[^&\\s]*", RegexOption.IGNORE_CASE)

internal fun redactSecretQueryParams(message: String): String =
    SECRET_QUERY_PARAMS.replace(message) { "${it.groupValues[1]}=REDACTED" }

@Module
@InstallIn(SingletonComponent::class)
object ArtworkModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false

        install(HttpTimeout)
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(Logging) {
            level = if (BuildConfig.DEBUG) LogLevel.HEADERS else LogLevel.NONE
            logger = object : Logger {
                override fun log(message: String) = Timber.tag("Ktor").d(redactSecretQueryParams(message))
            }
            sanitizeHeader { header -> header.equals(HttpHeaders.Authorization, ignoreCase = true) }
        }
        engine {
            config {
                connectTimeout(15, TimeUnit.SECONDS)

                readTimeout(15, TimeUnit.SECONDS)
            }
        }
    }

    @Provides
    @Singleton
    fun provideTitleSearchStore(@ApplicationContext context: Context): TitleSearchStore =
        FileTitleSearchStore(context.cacheDir.resolve("match-searches"))

    @Provides
    @Singleton
    fun provideCoilImageLoader(@ApplicationContext context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve("artwork_cache").toOkioPath())
                    .maxSizeBytes(512L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)

            .components {
                add(coil3.gif.AnimatedImageDecoder.Factory())
            }
            .build()
}
