// Requirements/ambiguities: selected local documents plus explicit HTTP(S) media links; no HTML extraction or DRM.
// Research/optimization: one Media3 path handles progressive, HLS and DASH; do not prefetch on the home screen.
// Risks/verification: whitelist schemes at I/O, bound network timeouts, never log URLs or signed query strings.
package org.grinch27.vrviewer.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.File
import java.io.IOException
import org.grinch27.vrviewer.core.MediaUrl

@UnstableApi
class PlaybackDataSource(private val context: Context) : DataSource {
    private val http = DefaultHttpDataSource.Factory()
        .setUserAgent("VRviewer/0.1")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(20_000)
    private val delegate = DefaultDataSource.Factory(context, http).createDataSource()
    override fun open(dataSpec: DataSpec): Long {
        val uri = dataSpec.uri
        val allowed = when (uri.scheme) {
            "content" -> true
            "http", "https" -> MediaUrl.normalize(uri.toString()) != null
            // Private cache files are reserved for generated instrumentation fixtures, not external intents.
            "file" -> runCatching {
                val path = File(requireNotNull(uri.path)).canonicalPath
                path.startsWith(context.cacheDir.canonicalPath + File.separator)
            }.getOrDefault(false)
            else -> false
        }
        if (!allowed) throw IOException("Unsupported media source")
        return delegate.open(dataSpec)
    }
    override fun read(buffer: ByteArray, offset: Int, length: Int) = delegate.read(buffer, offset, length)
    override fun getUri(): Uri? = delegate.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate.responseHeaders
    override fun addTransferListener(transferListener: TransferListener) = delegate.addTransferListener(transferListener)
    override fun close() = delegate.close()
}
