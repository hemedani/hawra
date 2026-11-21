package ir.hawraa.quran.data.quran

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.hawraa.quran.domain.quran.QuranContent
import ir.hawraa.quran.domain.quran.QuranPage
import ir.hawraa.quran.domain.quran.QuranVerse
import ir.hawraa.quran.domain.quran.SurahInfo
import ir.hawraa.quran.domain.quran.TranslationKeys
import ir.hawraa.quran.domain.quran.VerseTranslation
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Reads the bundled, versioned Quran content. Offline by construction: the asset
 * ships inside the APK, nothing is fetched at runtime (product invariant: sacred
 * text is never downloaded or mutated).
 *
 * Single owner of the asset. The parsed content is immutable and cached for the
 * process lifetime (~26 KB of JSON, 5 pages).
 */
@Singleton
class QuranContentRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val json = Json { ignoreUnknownKeys = true }

    private val mutex = Mutex()

    @Volatile
    private var cached: QuranContent? = null

    suspend fun content(): QuranContent =
        cached ?: mutex.withLock { cached ?: load().also { cached = it } }

    private suspend fun load(): QuranContent = withContext(Dispatchers.IO) {
        val raw = context.assets.open(ASSET_PATH).bufferedReader().use { it.readText() }
        val dto = json.decodeFromString<QuranContentDto>(raw)
        QuranContent(
            schemaVersion = dto.schemaVersion,
            mushaf = dto.mushaf,
            basmalaUthmani = dto.basmalaUthmani,
            surahs = dto.surahs.associate { s ->
                s.number to SurahInfo(
                    number = s.number,
                    nameArabic = s.nameArabic,
                    nameFa = s.nameFa,
                    nameEn = s.nameEn,
                    ayahCount = s.ayahCount,
                    revelationPlace = s.revelationPlace,
                    bismillahPre = s.bismillahPre,
                )
            },
            pages = dto.pages.map { p ->
                QuranPage(
                    page = p.page,
                    juz = p.juz,
                    verses = p.verses.map { v ->
                        QuranVerse(
                            key = v.key,
                            surah = v.surah,
                            ayah = v.ayah,
                            textUthmani = v.textUthmani,
                            translations = translationsOf(v),
                            juz = v.juz,
                            hizb = v.hizb,
                            rubElHizb = v.rubElHizb,
                            sajdah = v.sajdah,
                        )
                    },
                )
            },
        )
    }

    /**
     * Translations for one verse.
     *
     * The asset currently carries a single `translationFa`; a future build emits a
     * `translations` array instead. Both shapes map to the same keyed list, so the reader
     * never needs to know which one it is looking at.
     */
    private fun translationsOf(verse: VerseDto): List<VerseTranslation> {
        if (verse.translations.isNotEmpty()) {
            return verse.translations.map { VerseTranslation(key = it.key, text = it.text) }
        }
        if (verse.translationFa.isBlank()) return emptyList()
        return listOf(
            VerseTranslation(key = TranslationKeys.ANSARIAN, text = verse.translationFa),
        )
    }

    private companion object {
        const val ASSET_PATH = "quran/pages-v1.json"
    }
}
