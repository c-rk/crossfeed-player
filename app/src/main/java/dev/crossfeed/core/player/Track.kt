package dev.crossfeed.core.player

data class Track(
    val id: String,
    val title: String,
    val artist: String?,
    val album: String? = null,
    val durationMs: Long = 0,
    val artwork: String? = null,
    val sourceId: String,
    val ref: String,
    val path: String? = null,
    val addedBy: String? = null,
)

data class Playable(
    val uri: String,
    val durationMs: Long = 0,
    val headers: Map<String, String> = emptyMap(),
    val expiresAt: Long = 0,
) {
    val stale: Boolean get() = expiresAt in 1..System.currentTimeMillis()
}
