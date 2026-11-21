package ir.hawraa.quran.data.download

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * A failure while fetching one file.
 *
 * [permanent] separates "this file will never work" (404, 410, 403 — the source changed or
 * blocked us) from "this file did not work *now*" (timeout, 5xx, no network). The download
 * queue uses it to decide whether to keep a row retryable, because retrying a 404 forever is
 * how a queue quietly never finishes.
 */
class FetchException(
    val status: Int,
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause) {
    val permanent: Boolean
        get() = status in 400..499 && status != 408 && status != 429
}

/** Fetches one URL to one file. An interface so the download queue can be tested without a network. */
interface FileFetcher {
    /**
     * Downloads [url] to [target], creating parent directories. Returns the final size in bytes.
     *
     * Must be **resumable**: if the call is interrupted, a later call with the same arguments
     * continues from what is already on disk rather than starting over. A whole-Quran download
     * is thousands of files, so a dropped connection must cost one file, not the batch.
     */
    suspend fun fetch(url: String, target: File): Long
}

/**
 * [FileFetcher] over `HttpURLConnection`.
 *
 * `HttpURLConnection` rather than OkHttp on purpose: it is in the platform, it supports the one
 * thing this feature needs (`Range`), and adding an HTTP client dependency for a single ranged
 * GET would be a dependency to keep updated for no behaviour.
 *
 * The resume protocol is deliberately conservative. Bytes land in `file.part`; a `Range`
 * request is issued only when that file already has bytes; and a `200` answer (the server
 * ignoring the range) truncates and restarts instead of appending, which is the difference
 * between a correct file and a corrupt one. The rename to the final name happens only after a
 * complete read, so a file that exists is a file that finished.
 */
class HttpFileFetcher : FileFetcher {

    override suspend fun fetch(url: String, target: File): Long = withContext(Dispatchers.IO) {
        val parent = target.parentFile
        if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
            throw IOException("cannot create ${parent.path}")
        }
        val part = File(target.path + PART_SUFFIX)
        val resumeFrom = if (part.exists()) part.length() else 0L

        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            instanceFollowRedirects = true
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            // Without this the server may gzip the body, and appending a gzip stream to a
            // partial file produces audio that is silently corrupt.
            setRequestProperty("Accept-Encoding", "identity")
            if (resumeFrom > 0) setRequestProperty("Range", "bytes=$resumeFrom-")
        }

        try {
            val status = connection.responseCode
            val append = when {
                status == HttpURLConnection.HTTP_PARTIAL -> true
                status == HttpURLConnection.HTTP_OK -> false
                else -> throw FetchException(status, "HTTP $status for $url")
            }

            // A 206 must begin where the range asked. Servers that answer 206 with the whole
            // body exist, and appending that to a partial file yields a file of exactly the
            // wrong length that plays as garbage. Trust the header over the status code
            // whenever the header is there — some servers (including a chunked static handler)
            // omit it, in which case the declared size check downstream is what catches it.
            if (append) {
                val start = connection.getHeaderField("Content-Range")
                    ?.let { RANGE_START.find(it)?.groupValues?.getOrNull(1)?.toLongOrNull() }
                if (start != null && start != resumeFrom) {
                    throw IOException("resumed at byte $start but $resumeFrom were requested ($url)")
                }
            }

            if (!append && resumeFrom > 0 && !part.delete()) {
                throw IOException("cannot reset ${part.path}")
            }

            val declared = connection.contentLengthLong.takeIf { it > 0 }
            val startAt = if (append) resumeFrom else 0L
            var written = 0L

            FileOutputStream(part, append).use { stream ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read <= 0) break
                        stream.write(buffer, 0, read)
                        written += read
                    }
                }
                stream.flush()
            }

            if (declared != null && written != declared) {
                throw IOException("short read: $written of $declared bytes from $url")
            }
            if (startAt + written == 0L) {
                throw IOException("empty response from $url")
            }

            if (target.exists() && !target.delete()) {
                throw IOException("cannot replace ${target.path}")
            }
            if (!part.renameTo(target)) {
                throw IOException("cannot move ${part.path} to ${target.path}")
            }
            target.length()
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PART_SUFFIX = ".part"
        const val CONNECT_TIMEOUT_MS = 20_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_BYTES = 64 * 1024
        const val USER_AGENT = "Hawraa/1.0 (Android)"

        /** `bytes 12000-29999/30000` → `12000`. */
        val RANGE_START = Regex("^bytes\\s+(\\d+)-")
    }
}
