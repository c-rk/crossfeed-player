/*
 * Crossfeed — routing model.
 *
 * Routing is a RANKED LIST, not a single choice. routes[0] is the primary: it
 * handles links on its own when it is the only route, it is what the app is
 * coloured by, and it is what the copy elsewhere refers to as "your pick".
 * With several routes, a link resolution shows a chooser sheet in rank order.
 *
 * Invariant: the list is never empty and never contains duplicates.
 */

package org.tentkotta.crossfeed.routing

import androidx.compose.ui.graphics.Color

enum class Service(
    /** Full name, used in the dial hub, chips, and body copy. */
    val label: String,
    /** Short name for tight spaces. */
    val short: String,
    val accent: Color,
    /** Package name to route intents at — verify these against the device. */
    val packageName: String?,
    /** Host this service's links use, for the link-handling screen. */
    val linkHost: String?
) {
    AppleMusic("apple music", "apple", Color(0xFFFF375F), "com.apple.android.music", "music.apple.com"),
    Spotify("spotify", "spotify", Color(0xFF1ED760), "com.spotify.music", "open.spotify.com"),
    YouTubeMusic("yt music", "yt", Color(0xFFFF5A3C), "com.google.android.apps.youtube.music", "music.youtube.com"),
    Deezer("deezer", "deezer", Color(0xFFA45CFF), "deezer.android.app", "deezer.page.link"),
    Tidal("tidal", "tidal", Color(0xFF3AD0FF), "com.aspiro.tidal", "tidal.com"),
    OnDevice("on device", "device", Color(0xFFF6A06B), null, null);

    companion object {
        /**
         * Dial order, clockwise from 12 o'clock. This is fixed — node positions
         * must not reshuffle when the selection changes, or the dial stops being
         * a place you can learn.
         */
        val dialOrder: List<Service> = listOf(
            AppleMusic, Spotify, YouTubeMusic, Deezer, Tidal, OnDevice
        )
    }
}

@JvmInline
value class Routes private constructor(val ordered: List<Service>) {

    val primary: Service get() = ordered.first()
    val isSingle: Boolean get() = ordered.size == 1
    val accent: Color get() = primary.accent

    /** "the only route" / "+ 2 more, in order" — the dial hub's second line. */
    val summary: String
        get() = when (val extra = ordered.size - 1) {
            0 -> "the only route"
            1 -> "+ 1 more, in order"
            else -> "+ $extra more, in order"
        }

    fun rankOf(service: Service): Int? =
        ordered.indexOf(service).takeIf { it >= 0 }

    /** Dial tap: add to the end, or remove — but never drop below one. */
    fun toggled(service: Service): Routes = when {
        service !in ordered -> Routes(ordered + service)
        ordered.size == 1 -> this
        else -> Routes(ordered - service)
    }

    /** Rank-chip tap: promote to primary. Retints the app. */
    fun promoted(service: Service): Routes =
        if (service !in ordered) this
        else Routes(listOf(service) + ordered.filterNot { it == service })

    /** Header-chip tap: make this the primary AND keep the rest as fallbacks. */
    fun jumpedTo(service: Service): Routes =
        Routes(listOf(service) + ordered.filterNot { it == service })

    fun serialize(): String = ordered.joinToString(",") { it.name }

    companion object {
        val Default = Routes(listOf(Service.AppleMusic))

        fun of(vararg services: Service): Routes =
            services.distinct().takeIf { it.isNotEmpty() }?.let(::Routes) ?: Default

        fun parse(stored: String?): Routes {
            val parsed = stored?.split(',')
                ?.mapNotNull { name -> Service.entries.firstOrNull { it.name == name } }
                ?.distinct()
                .orEmpty()
            return if (parsed.isEmpty()) Default else Routes(parsed)
        }
    }
}

/**
 * How a resolved link should be opened.
 *  - [OpenDirectly]  one route, or "open straight away" is on and there is one exact match
 *  - [Chooser]       several routes: show the sheet, ordered by rank
 */
sealed interface LinkAction {
    data class OpenDirectly(val service: Service) : LinkAction
    data class Chooser(val options: List<Service>) : LinkAction
}

fun Routes.actionFor(
    availableForTrack: Set<Service>,
    openStraightAway: Boolean,
    preferOwnFiles: Boolean
): LinkAction {
    if (preferOwnFiles && Service.OnDevice in availableForTrack) {
        return LinkAction.OpenDirectly(Service.OnDevice)
    }
    val candidates = ordered.filter { it in availableForTrack }
        .ifEmpty { listOf(primary) }

    return if (candidates.size == 1 || (openStraightAway && candidates.isNotEmpty())) {
        LinkAction.OpenDirectly(candidates.first())
    } else {
        LinkAction.Chooser(candidates)
    }
}
