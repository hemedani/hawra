package ir.hawraa.quran.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import ir.hawraa.quran.BuildConfig
import ir.hawraa.quran.data.local.DownloadDao
import ir.hawraa.quran.data.local.HawraaDatabase
import ir.hawraa.quran.data.local.PresetDao
import ir.hawraa.quran.data.local.PresetProgressDao
import ir.hawraa.quran.data.local.PresetSessionDao
import ir.hawraa.quran.data.local.ReadingSessionDao
import ir.hawraa.quran.data.download.FileFetcher
import ir.hawraa.quran.data.download.HttpFileFetcher
import ir.hawraa.quran.data.remote.AuthApi
import ir.hawraa.quran.data.remote.LesanAuthApi
import ir.hawraa.quran.data.remote.LesanClient
import ir.hawraa.quran.domain.Clock
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock { System.currentTimeMillis() }

    /**
     * Application-lifetime scope for work that must survive a ViewModel going away —
     * notably finalizing a reading session when the reader screen is popped.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * The main thread, for the one piece of work that must be there: driving the Media3 player.
     * The preset run loop lives on [ApplicationScope] (IO) but hops here for every engine call —
     * see [Main].
     */
    @Provides
    @Main
    fun provideMainDispatcher(): CoroutineDispatcher = Dispatchers.Main

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("hawraa_settings")
        }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                explicitNulls = false
            })
        }
    }

    @Provides
    @Singleton
    @ServerUrl
    fun provideServerUrl(): String = BuildConfig.SERVER_URL

    /**
     * Lesan only answers `POST /lesan`; a request to the root comes back
     * `{"message":"Invalid request: Expected POST to /lesan, got POST to /"}` with status 501.
     * The endpoint is therefore built here, once, from an origin that carries no path — so the
     * one place that knows the wire path is the one place that creates the client.
     */
    @Provides
    @Singleton
    fun provideLesanClient(http: HttpClient): LesanClient =
        LesanClient(http, "${BuildConfig.SERVER_URL}/lesan")

    /**
     * The one implementation of [AuthApi]. Explicit rather than `@Binds` because this module is an
     * `object`; the indirection is what lets the auth repository's rules be tested against a fake.
     */
    @Provides
    @Singleton
    fun provideAuthApi(client: LesanClient): AuthApi = LesanAuthApi(client)

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HawraaDatabase =
        Room.databaseBuilder(context, HawraaDatabase::class.java, HawraaDatabase.NAME)
            .addMigrations(HawraaDatabase.MIGRATION_1_2, HawraaDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun provideReadingSessionDao(db: HawraaDatabase): ReadingSessionDao =
        db.readingSessionDao()

    @Provides
    fun providePresetDao(db: HawraaDatabase): PresetDao = db.presetDao()

    @Provides
    fun providePresetSessionDao(db: HawraaDatabase): PresetSessionDao = db.presetSessionDao()

    @Provides
    fun providePresetProgressDao(db: HawraaDatabase): PresetProgressDao = db.presetProgressDao()

    @Provides
    fun provideDownloadDao(db: HawraaDatabase): DownloadDao = db.downloadDao()

    /**
     * Where downloaded recitation audio lives.
     *
     * `filesDir`, not `cacheDir`: these files are what the user asked for, and the system is
     * free to delete a cache whenever it needs space. Losing a downloaded juz to a cache
     * eviction would look like a bug to the person who waited for it.
     */
    @Provides
    @Singleton
    @AudioRoot
    fun provideAudioRoot(@ApplicationContext context: Context): File =
        File(context.filesDir, AUDIO_DIR).apply { if (!exists()) mkdirs() }

    @Provides
    @Singleton
    fun provideFileFetcher(): FileFetcher = HttpFileFetcher()

    private const val AUDIO_DIR = "recitation"
}