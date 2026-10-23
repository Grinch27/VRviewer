// Requirements/ambiguities: URL means an HTTP(S) media direct link; web-page extraction is not requested.
// Research/optimization: parse without DNS or preflight requests; retain signed query parameters verbatim.
// Risks/verification: reject other schemes, missing hosts, credentials and invalid ports before playback.
package org.grinch27.vrviewer.core

import java.net.URI
import java.util.Locale

object MediaUrl {
    fun normalize(input: String): String? = runCatching {
        val value = URI(input.trim())
        val scheme = value.scheme?.lowercase(Locale.ROOT)
        require(scheme == "http" || scheme == "https")
        require(!value.host.isNullOrBlank())
        require(value.rawUserInfo == null)
        require(value.port == -1 || value.port in 1..65535)
        val ascii = value.toASCIIString()
        scheme + ascii.substring(value.scheme.length)
    }.getOrNull()
}
