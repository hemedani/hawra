package ir.hawraa.quran

import ir.hawraa.quran.data.download.DownloadCodec
import ir.hawraa.quran.data.download.ManifestFileDto
import ir.hawraa.quran.data.download.sizeMismatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Where a file comes from, and whether it arrived whole.
 *
 * Two questions that look like plumbing and are not:
 *
 *  - **Which URL?** A manifest can name an upstream CDN or the server's own mirror. If the
 *    mirror is preferred but resolved wrongly, the device downloads a 404 body and stores it as
 *    audio; if it is ignored, the feature silently does nothing.
 *  - **Is it complete?** A chunked response declares no length, so "the stream ended" is the
 *    only signal — and a dropped connection produces the same signal. The declared size is the
 *    only thing that can tell them apart.
 */
class ManifestSourceTest {

    private val origin = "http://192.168.1.111:1407"

    private fun dto(
        mirrorPath: String? = null,
        bytes: Long? = null,
        url: String = "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/1.mp3",
    ) = ManifestFileDto(
        key = "ayah:1:1",
        kind = "ayah",
        surah = 1,
        ayah = 1,
        globalAyah = 1,
        url = url,
        relativePath = "parhizgar-murattal-ayah-48/ayah/001/001.mp3",
        mirrorPath = mirrorPath,
        bytes = bytes,
    )

    @Test
    fun a_mirrored_file_is_fetched_from_the_server() {
        val file = DownloadCodec.fileOf(
            dto(mirrorPath = "/uploads/recitation/ed/ayah/001/001.mp3", bytes = 67_712),
            origin,
        )
        assertEquals(
            "http://192.168.1.111:1407/uploads/recitation/ed/ayah/001/001.mp3",
            file.url,
        )
        assertEquals(67_712L, file.bytes)
    }

    @Test
    fun an_unmirrored_file_keeps_its_upstream_url() {
        val file = DownloadCodec.fileOf(dto(), origin)
        assertEquals(
            "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/1.mp3",
            file.url,
        )
        assertNull(file.bytes)
    }

    @Test
    fun a_mirror_path_without_a_leading_slash_still_resolves() {
        val file = DownloadCodec.fileOf(dto(mirrorPath = "uploads/recitation/x.mp3"), origin)
        assertEquals("http://192.168.1.111:1407/uploads/recitation/x.mp3", file.url)
    }

    @Test
    fun a_trailing_slash_on_the_origin_does_not_double() {
        val file = DownloadCodec.fileOf(
            dto(mirrorPath = "/uploads/recitation/x.mp3"),
            "http://192.168.1.111:1407/",
        )
        assertEquals("http://192.168.1.111:1407/uploads/recitation/x.mp3", file.url)
    }

    @Test
    fun a_blank_mirror_path_is_treated_as_absent() {
        // An empty string is what a JSON field with no value often looks like after decoding;
        // treating it as a path would produce `http://host` as the URL of an audio file.
        val file = DownloadCodec.fileOf(dto(mirrorPath = ""), origin)
        assertEquals(
            "https://cdn.islamic.network/quran/audio/48/ar.parhizgar/1.mp3",
            file.url,
        )
    }

    // ---- completeness -------------------------------------------------------------

    @Test
    fun a_file_of_the_declared_size_is_accepted() {
        assertNull(sizeMismatch(67_712L, 67_712L))
    }

    @Test
    fun a_short_file_is_rejected_with_both_numbers() {
        assertEquals("expected 67712 bytes, got 4096", sizeMismatch(67_712L, 4_096L))
    }

    @Test
    fun an_over_long_file_is_rejected_too() {
        // Not hypothetical: appending a whole body onto a partial one produces exactly this.
        assertEquals("expected 67712 bytes, got 135424", sizeMismatch(67_712L, 135_424L))
    }

    @Test
    fun an_unknown_declared_size_is_not_a_failure() {
        assertNull(sizeMismatch(null, 4_096L))
    }
}
