package com.psplauncher.core.data.database.di

import android.content.Context
import androidx.room.Room
import com.psplauncher.core.data.database.PFPDatabase
import com.psplauncher.core.data.database.dao.AppOverrideDao
import com.psplauncher.core.data.database.dao.ArtworkImportReportDao
import com.psplauncher.core.data.database.dao.ArtworkRecordDao
import com.psplauncher.core.data.database.dao.BackupDao
import com.psplauncher.core.data.database.dao.CategoryDao
import com.psplauncher.core.data.database.dao.CollectionDao
import com.psplauncher.core.data.database.dao.GameDao
import com.psplauncher.core.data.database.dao.LaunchOutcomeDao
import com.psplauncher.core.data.database.dao.LibrarySourceDao
import com.psplauncher.core.data.database.dao.MemoryCardDao
import com.psplauncher.core.data.database.dao.MusicFolderDao
import com.psplauncher.core.data.database.dao.MusicTrackDao
import com.psplauncher.core.data.database.dao.PlaylistDao
import com.psplauncher.core.data.database.dao.PlaySessionDao
import com.psplauncher.core.data.database.dao.PlatformDao
import com.psplauncher.core.data.database.dao.ThemeDao
import com.psplauncher.core.data.database.dao.HiddenPlacementDao
import com.psplauncher.core.data.database.dao.BookDao
import com.psplauncher.core.data.database.dao.BookLibraryDao
import com.psplauncher.core.data.database.dao.PhotoDao
import com.psplauncher.core.data.database.dao.PhotoLibraryDao
import com.psplauncher.core.data.database.dao.ScanTombstoneDao
import com.psplauncher.core.data.database.dao.VideoDao
import com.psplauncher.core.data.database.dao.VideoLibraryDao
import com.psplauncher.core.data.database.dao.VideoPlaylistDao
import com.psplauncher.core.data.repository.GameRepositoryImpl
import com.psplauncher.core.data.repository.MusicRepositoryImpl
import com.psplauncher.core.data.repository.BookRepositoryImpl
import com.psplauncher.core.data.repository.PhotoRepositoryImpl
import com.psplauncher.core.data.repository.VideoRepositoryImpl
import com.psplauncher.core.domain.repository.GameRepository
import com.psplauncher.core.domain.repository.MusicRepository
import com.psplauncher.core.domain.repository.BookRepository
import com.psplauncher.core.domain.repository.PhotoRepository
import com.psplauncher.core.domain.repository.VideoRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun providePFPDatabase(@ApplicationContext context: Context): PFPDatabase =
        Room.databaseBuilder(
            context,
            PFPDatabase::class.java,
            PFPDatabase.DATABASE_NAME,
        )
        // Never use fallbackToDestructiveMigration — users would lose their entire library
        // (No partial unique index on games: Room cannot express it in the schema export, so any
        // database carrying it — from a migration or this callback — fails Room's post-migration
        // validation. The one-primary-per-disc-set invariant is enforced by DiscSetBuilder /
        // DiscSetReconciler at scan time instead.)
        // One list, declared beside the migrations themselves. See PFPDatabase.ALL_MIGRATIONS.
        .addMigrations(*PFPDatabase.ALL_MIGRATIONS)
        .build()

    @Provides fun provideGameDao(db: PFPDatabase): GameDao = db.gameDao()
    @Provides fun provideLaunchOutcomeDao(db: PFPDatabase): LaunchOutcomeDao = db.launchOutcomeDao()
    @Provides fun provideSsMediaCacheDao(db: PFPDatabase): com.psplauncher.core.data.database.dao.SsMediaCacheDao = db.ssMediaCacheDao()
    @Provides fun providePlatformDao(db: PFPDatabase): PlatformDao = db.platformDao()
    @Provides fun provideCategoryDao(db: PFPDatabase): CategoryDao = db.categoryDao()
    @Provides fun providePlaySessionDao(db: PFPDatabase): PlaySessionDao = db.playSessionDao()
    @Provides fun provideLibrarySourceDao(db: PFPDatabase): LibrarySourceDao = db.librarySourceDao()
    @Provides fun provideThemeDao(db: PFPDatabase): ThemeDao = db.themeDao()
    @Provides fun provideMemoryCardDao(db: PFPDatabase): MemoryCardDao = db.memoryCardDao()
    @Provides fun provideAppOverrideDao(db: PFPDatabase): AppOverrideDao = db.appOverrideDao()
    @Provides fun provideCollectionDao(db: PFPDatabase): CollectionDao = db.collectionDao()
    @Provides fun provideMusicFolderDao(db: PFPDatabase): MusicFolderDao = db.musicFolderDao()
    @Provides fun provideMusicTrackDao(db: PFPDatabase): MusicTrackDao = db.musicTrackDao()
    @Provides fun providePlaylistDao(db: PFPDatabase): PlaylistDao = db.playlistDao()
    @Provides fun provideVideoLibraryDao(db: PFPDatabase): VideoLibraryDao = db.videoLibraryDao()
    @Provides fun provideVideoDao(db: PFPDatabase): VideoDao = db.videoDao()
    @Provides fun provideVideoPlaylistDao(db: PFPDatabase): VideoPlaylistDao = db.videoPlaylistDao()
    @Provides fun provideHiddenPlacementDao(db: PFPDatabase): HiddenPlacementDao = db.hiddenPlacementDao()
    @Provides fun providePhotoLibraryDao(db: PFPDatabase): PhotoLibraryDao = db.photoLibraryDao()
    @Provides fun providePhotoDao(db: PFPDatabase): PhotoDao = db.photoDao()
    @Provides fun provideBookLibraryDao(db: PFPDatabase): BookLibraryDao = db.bookLibraryDao()
    @Provides fun provideBookDao(db: PFPDatabase): BookDao = db.bookDao()
    @Provides fun provideScanTombstoneDao(db: PFPDatabase): ScanTombstoneDao = db.scanTombstoneDao()
    @Provides fun provideBackupDao(db: PFPDatabase): BackupDao = db.backupDao()
    @Provides fun provideArtworkRecordDao(db: PFPDatabase): ArtworkRecordDao = db.artworkRecordDao()
    @Provides fun provideArtworkImportReportDao(db: PFPDatabase): ArtworkImportReportDao = db.artworkImportReportDao()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindGameRepository(impl: GameRepositoryImpl): GameRepository

    @Binds
    @Singleton
    abstract fun bindMusicRepository(impl: MusicRepositoryImpl): MusicRepository

    @Binds
    @Singleton
    abstract fun bindVideoRepository(impl: VideoRepositoryImpl): VideoRepository

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository
}
