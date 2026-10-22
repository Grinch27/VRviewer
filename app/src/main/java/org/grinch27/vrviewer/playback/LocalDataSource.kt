// Open decisions: network, DRM and externally supplied subtitles are outside the local-video scope.
// Risk/verification: enforce locality at the actual I/O boundary, including any extractor subrequests.
package org.grinch27.vrviewer.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException

@UnstableApi
class LocalDataSource(private val context: Context) : DataSource {
    private val delegate = DefaultDataSource.Factory(context).createDataSource()
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val allowed = when (uri.scheme) {
            "content" -> true
            // Private cache files are reserved for generated instrumentation fixtures, not external intents.
            "file" -> runCatching {
                val path = File(requireNotNull(uri.path)).canonicalPath
                path.startsWith(context.cacheDir.canonicalPath + File.separator)
            }.getOrDefault(false)
            else -> false
        }
        if (!allowed) throw IOException("Only selected local documents can be played")
        return delegate.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = delegate.read(buffer, offset, length)
    override fun getUri(): Uri? = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun close() = delegate.close()
}
