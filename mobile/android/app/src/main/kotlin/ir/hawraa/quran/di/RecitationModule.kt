package ir.hawraa.quran.di

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import ir.hawraa.quran.data.recitation.AndroidSpeechEngine
import ir.hawraa.quran.data.recitation.Media3RecitationEngine
import ir.hawraa.quran.data.recitation.RecitationEngine
import ir.hawraa.quran.data.recitation.SpeechEngine
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RecitationModule {

    @Binds
    @Singleton
    abstract fun bindRecitationEngine(engine: Media3RecitationEngine): RecitationEngine

    /**
     * TTS for a preset's translation and tafsir steps. Bound here rather than created inside
     * the player so a JVM test can substitute a fake and so `TextToSpeech` is initialised
     * once for the whole process.
     */
    @Binds
    @Singleton
    abstract fun bindSpeechEngine(engine: AndroidSpeechEngine): SpeechEngine

    companion object {

        /**
         * Disk cache for streamed ayahs (decision D12).
         *
         * 200 MB is roughly 60 pages of 48 kbps audio — enough that re-reading a page or
         * repeating an ayah never re-downloads it, small enough that it cannot crowd out
         * a phone's storage. `SimpleCache` may only be opened once per process for a given
         * directory, which is exactly why it is a singleton here and not created per
         * screen; Media3 will throw if that rule is broken.
         */
        @Provides
        @Singleton
        @UnstableApi
        fun provideRecitationCache(@ApplicationContext context: Context): SimpleCache =
            SimpleCache(
                File(context.cacheDir, CACHE_DIR),
                LeastRecentlyUsedCacheEvictor(CACHE_BYTES),
                StandaloneDatabaseProvider(context),
            )

        private const val CACHE_DIR = "recitation-audio"
        private const val CACHE_BYTES = 200L * 1024 * 1024
    }
}
