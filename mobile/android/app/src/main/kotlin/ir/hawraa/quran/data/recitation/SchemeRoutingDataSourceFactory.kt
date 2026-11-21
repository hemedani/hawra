package ir.hawraa.quran.data.recitation

import android.net.Uri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource

/**
 * Routes a media load to the right upstream by URI scheme.
 *
 * The reader plays three kinds of URLs: streamed `http(s)` (cache-then-network through the
 * [CacheDataSource]), and downloaded verses served as `file:` from internal storage. Media3's
 * [androidx.media3.exoplayer.source.DefaultMediaSourceFactory] takes exactly **one** data-source
 * factory, so without this router a `file:` URI would be opened by the HTTP loader and die with
 * `ERROR_CODE_IO_UNSPECIFIED` — every kept ayah unplayable, which is the opposite of keeping it.
 *
 * The delegate is chosen at `open` time, when the [DataSpec] with the URI first exists.
 */
class SchemeRoutingDataSourceFactory(
    private val cacheFactory: CacheDataSource.Factory,
) : DataSource.Factory {

    private val fileFactory = FileDataSource.Factory()

    override fun createDataSource(): DataSource = RoutingDataSource()

    inner class RoutingDataSource : DataSource {

        private val listeners = mutableListOf<TransferListener>()
        private var delegate: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            // The delegate does not exist yet; the listeners are forwarded when it is chosen.
            listeners.add(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val chosen = when (dataSpec.uri.scheme) {
                "file" -> fileFactory.createDataSource()
                else -> cacheFactory.createDataSource()
            }
            listeners.forEach(chosen::addTransferListener)
            delegate = chosen
            return chosen.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            requireDelegate().read(buffer, offset, length)

        override fun getUri(): Uri? = delegate?.uri

        override fun getResponseHeaders(): Map<String, List<String>> =
            delegate?.responseHeaders ?: emptyMap()

        override fun close() {
            delegate?.close()
        }

        private fun requireDelegate(): DataSource =
            delegate ?: throw IllegalStateException("RoutingDataSource opened before open()")
    }
}
