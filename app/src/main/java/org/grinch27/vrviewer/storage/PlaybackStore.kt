// Open decisions: folder libraries and a Room migration belong to a later, bounded library feature.
// Risks/verification: revoked URI grants, malformed preferences and process death; never resolve URI to a raw path.
package org.grinch27.vrviewer.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import org.grinch27.vrviewer.core.DisplayMode
import org.grinch27.vrviewer.core.PlaybackProfile
import org.grinch27.vrviewer.core.Projection
import org.grinch27.vrviewer.core.StereoLayout
import org.json.JSONArray
import org.json.JSONObject

data class MovieEntry(
    val uri: String,
    val name: String,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val profile: PlaybackProfile = PlaybackProfile(),
    val updatedAt: Long = 0,
)

/** Small, bounded local history. No video copy, thumbnail extraction, network or cloud backup. */
class PlaybackStore(context: Context) {
    private val preferences = context.getSharedPreferences("local_playback_v1", Context.MODE_PRIVATE)

    @Synchronized fun recent(): List<MovieEntry> = runCatching {
        val array = JSONArray(preferences.getString("entries", "[]"))
        (0 until minOf(array.length(), 50)).mapNotNull { index ->
            runCatching { decode(array.getJSONObject(index)) }.getOrNull()
        }.filter { Uri.parse(it.uri).scheme == "content" }.distinctBy { it.uri }
    }.getOrDefault(emptyList())

    @Synchronized fun save(entry: MovieEntry) {
        if (Uri.parse(entry.uri).scheme != "content") return
        val entries = (listOf(entry.copy(updatedAt = System.currentTimeMillis())) + recent().filter { it.uri != entry.uri }).take(50)
        val array = JSONArray()
        entries.forEach { array.put(encode(it)) }
        preferences.edit().putString("entries", array.toString()).apply()
    }

    /** Invoke on Dispatchers.IO: document providers may perform disk or binder work. */
    fun resolve(context: Context, uri: Uri): MovieEntry {
        require(uri.scheme == "content") { "Only content URIs are accepted" }
        val previous = recent().firstOrNull { it.uri == uri.toString() }
        val name = runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()?.take(256)?.takeIf { it.isNotBlank() } ?: previous?.name ?: "本地视频"
        return previous?.copy(name = name) ?: MovieEntry(uri.toString(), name)
    }

    fun retainReadGrant(context: Context, uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    private fun encode(entry: MovieEntry) = JSONObject().apply {
        put("uri", entry.uri)
        put("name", entry.name.take(256))
        put("position", entry.positionMs.coerceAtLeast(0))
        put("duration", entry.durationMs.coerceAtLeast(0))
        put("updated", entry.updatedAt)
        put("projection", entry.profile.projection.name)
        put("stereo", entry.profile.stereo.name)
        put("display", entry.profile.display.name)
        put("swap", entry.profile.swapEyes)
        put("half", entry.profile.halfPacked)
        put("fov", entry.profile.normalized().fieldOfView.toDouble())
        put("head", entry.profile.headTracking)
    }

    private fun decode(json: JSONObject): MovieEntry = MovieEntry(
        uri = json.getString("uri"),
        name = json.optString("name", "本地视频").take(256),
        positionMs = json.optLong("position").coerceAtLeast(0),
        durationMs = json.optLong("duration").coerceAtLeast(0),
        updatedAt = json.optLong("updated"),
        profile = PlaybackProfile(
            projection = runCatching { Projection.valueOf(json.optString("projection")) }.getOrDefault(Projection.FLAT),
            stereo = runCatching { StereoLayout.valueOf(json.optString("stereo")) }.getOrDefault(StereoLayout.MONO),
            display = runCatching { DisplayMode.valueOf(json.optString("display")) }.getOrDefault(DisplayMode.SINGLE),
            swapEyes = json.optBoolean("swap"),
            halfPacked = json.optBoolean("half"),
            fieldOfView = json.optDouble("fov", 80.0).toFloat(),
            headTracking = json.optBoolean("head"),
        ).normalized(),
    )
}
