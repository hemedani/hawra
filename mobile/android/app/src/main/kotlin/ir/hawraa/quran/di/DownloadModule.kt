package ir.hawraa.quran.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import ir.hawraa.quran.data.download.AutoCacheVerseProvisioner
import ir.hawraa.quran.data.download.DownloadManager
import ir.hawraa.quran.data.media.MediaGuyaAudioSource
import ir.hawraa.quran.domain.download.LocalAudioSource
import ir.hawraa.quran.domain.download.VerseAudioProvisioner
import ir.hawraa.quran.domain.media.GuyaAudioSource
import javax.inject.Singleton

/**
 * Binds the download layer's read side.
 *
 * Playback only ever needs "is this ayah on disk?", so it is given [LocalAudioSource] rather
 * than [DownloadManager]: the reader cannot accidentally enqueue or delete a download, and a
 * test can answer the question with a fixed index.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DownloadModule {

    @Binds
    @Singleton
    abstract fun bindLocalAudioSource(manager: DownloadManager): LocalAudioSource

    /**
     * The write-side capability a preset needs to download-then-play verse by verse. Bound to the
     * interface so the player depends on the policy, not on [ir.hawraa.quran.data.download.AudioAutoCache]
     * and [DownloadManager] directly.
     */
    @Binds
    @Singleton
    abstract fun bindVerseAudioProvisioner(
        provisioner: AutoCacheVerseProvisioner,
    ): VerseAudioProvisioner

    /**
     * The guya-audio seam a preset's translation/tafsir step uses to play real narrated audio
     * (falling back to TTS). Bound to the interface so the player depends on the policy, not on the
     * media catalogue directly.
     */
    @Binds
    @Singleton
    abstract fun bindGuyaAudioSource(
        source: MediaGuyaAudioSource,
    ): GuyaAudioSource
}
