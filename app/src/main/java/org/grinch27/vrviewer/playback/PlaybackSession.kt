// Requirements/ambiguities: URL progress/settings survive rotation within this Activity session, not permanent history.
// Research/optimization: retain only one active source in a ViewModel; do not retain player, Surface or Activity.
// Risks/verification: discard checkpoints from a source already closed or replaced; process death starts URLs afresh.
package org.grinch27.vrviewer.playback

import androidx.lifecycle.ViewModel
import org.grinch27.vrviewer.storage.MovieEntry

class PlaybackSession : ViewModel() {
    var current: MovieEntry? = null
        private set
    private var activeUri: String? = null

    fun select(uri: String?) {
        if (uri != activeUri) current = null
        activeUri = uri
    }
    fun checkpoint(entry: MovieEntry) {
        if (entry.uri == activeUri) current = entry
    }
}
