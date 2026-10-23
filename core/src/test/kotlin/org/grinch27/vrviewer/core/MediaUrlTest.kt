// Verification: pure URL parsing, no DNS/network; signed query bytes must remain unchanged.
// Unknowns/research: HTML pages, cookies, expiring authorization and live seek windows need separate handling.
package org.grinch27.vrviewer.core

import org.junit.Assert.*
import org.junit.Test

class MediaUrlTest {
    @Test fun acceptsDirectMediaAndLanUrls() {
        assertEquals("https://example.com/video.mp4?token=a%2Bb&sig=1", MediaUrl.normalize(" https://example.com/video.mp4?token=a%2Bb&sig=1 "))
        assertEquals("http://192.168.1.2:8080/live.m3u8", MediaUrl.normalize("http://192.168.1.2:8080/live.m3u8"))
        assertEquals("https://[::1]:8443/stream.mpd", MediaUrl.normalize("HTTPS://[::1]:8443/stream.mpd"))
    }
    @Test fun rejectsUnsupportedOrMalformedUris() {
        for (input in listOf("", "example.com/video.mp4", "https:///video.mp4", "javascript:alert(1)", "file:///sdcard/video.mp4", "content://media/video/1", "ftp://example.com/a", "http://example.com:0/a", "http://example.com:65536/a", "https://user:password@example.com/a", "https://example.com/a b.mp4")) {
            assertNull(input, MediaUrl.normalize(input))
        }
    }
    @Test fun preservesEncodedPathsAndQueryOrder() {
        assertEquals("https://example.com/a%2Fb.mp4?x=1&x=2&token=a%2Fb", MediaUrl.normalize("https://example.com/a%2Fb.mp4?x=1&x=2&token=a%2Fb"))
    }
}
