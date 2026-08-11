package dev.crossfeed.core

enum class EntityKind { TRACK, ALBUM, ARTIST, PLAYLIST, UNKNOWN }

data class TrackMeta(
    val title: String,
    val artist: String?,
    val album: String? = null,
    val durationMs: Int? = null,
    val artwork: String? = null,
    val kind: EntityKind = EntityKind.TRACK,
) {
    val query: String get() = listOfNotNull(artist, title).joinToString(" ")

    val display: String get() = if (artist.isNullOrBlank()) title else "$title by $artist"
}
