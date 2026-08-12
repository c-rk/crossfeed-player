package dev.crossfeed.core.net

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class Post(
    val id: String,
    val handle: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val art: String?,
    val source: String?,
    val listenedMs: Long,
    val durationMs: Long,
    val updatedAt: Long,
    val reactions: Int,
    val mine: String?,
    val counts: Map<String, Int>,
    val self: Boolean,
) {
    fun withReaction(emoji: String?): Post {
        val next = counts.toMutableMap()
        mine?.let { old -> next[old] = ((next[old] ?: 1) - 1).coerceAtLeast(0) }
        emoji?.let { new -> next[new] = (next[new] ?: 0) + 1 }
        return copy(
            mine = emoji,
            counts = next.filterValues { it > 0 },
            reactions = next.values.sum(),
        )
    }
}

data class Alert(
    val postId: String,
    val emoji: String,
    val handle: String,
    val title: String,
    val artist: String?,
    val art: String?,
    val source: String?,
    val at: Long,
    val fresh: Boolean,
)

data class Alerts(val items: List<Alert>, val unread: Int, val requests: Int)

data class Together(val handle: String, val title: String, val artist: String?, val key: String)

data class Sync(
    val now: Long,
    val posts: List<Post>,
    val live: List<Live>?,
    val unread: Int,
    val requests: Int,
    val together: List<Together> = emptyList(),
)

data class Person(val id: String, val handle: String, val display: String)

data class Circle(val accepted: List<Person>, val incoming: List<Person>, val outgoing: List<Person>)

data class Neighbour(
    val id: String,
    val handle: String,
    val metres: Int,
    val link: String,
    val title: String?,
    val artist: String?,
)

data class SavedTrack(val title: String, val artist: String?, val art: String?)

data class Live(
    val handle: String,
    val title: String,
    val artist: String?,
    val art: String?,
    val source: String?,
    val at: Long,
    val self: Boolean,
)

object Social {

    val emojis = listOf("🔥", "❤️", "🎧", "😭", "👀")

    const val PAGE = 20

    suspend fun feed(context: Context, before: Long? = null): List<Post> = withContext(Dispatchers.IO) {
        val path = "/v1/feed?limit=$PAGE" + (before?.let { "&before=$it" } ?: "")
        Api.get(context, path).optJSONArray("items").map { it.toPost() }
    }

    suspend fun sync(context: Context, since: Long): Sync = withContext(Dispatchers.IO) {
        val response = Api.get(context, "/v1/sync?since=$since")
        Sync(
            now = response.optLong("now"),
            posts = response.optJSONArray("posts").map { it.toPost() },
            live = response.optJSONArray("live")?.let { array ->
                array.map {
                    Live(
                        handle = it.optString("handle"),
                        title = it.optString("title"),
                        artist = it.stringOrNull("artist"),
                        art = it.stringOrNull("art"),
                        source = it.stringOrNull("source"),
                        at = it.optLong("at"),
                        self = it.optBoolean("self"),
                    )
                }
            },
            unread = response.optInt("unread"),
            requests = response.optInt("requests"),
            together = response.optJSONArray("together").map {
                Together(
                    handle = it.optString("handle"),
                    title = it.optString("title"),
                    artist = it.stringOrNull("artist"),
                    key = it.optString("key"),
                )
            },
        )
    }

    suspend fun live(context: Context): List<Live> = withContext(Dispatchers.IO) {
        Api.get(context, "/v1/live").optJSONArray("items").map {
            Live(
                handle = it.optString("handle"),
                title = it.optString("title"),
                artist = it.stringOrNull("artist"),
                art = it.stringOrNull("art"),
                source = it.stringOrNull("source"),
                at = it.optLong("at"),
                self = it.optBoolean("self"),
            )
        }
    }

    suspend fun alerts(context: Context): Alerts = withContext(Dispatchers.IO) {
        val response = Api.get(context, "/v1/alerts")
        Alerts(
            items = response.optJSONArray("items").map {
                Alert(
                    postId = it.optString("postId"),
                    emoji = it.optString("emoji"),
                    handle = it.optString("handle"),
                    title = it.optString("title"),
                    artist = it.stringOrNull("artist"),
                    art = it.stringOrNull("art"),
                    source = it.stringOrNull("source"),
                    at = it.optLong("at"),
                    fresh = it.optBoolean("fresh"),
                )
            },
            unread = response.optInt("unread"),
            requests = response.optInt("requests"),
        )
    }

    suspend fun markAlertsSeen(context: Context) = withContext(Dispatchers.IO) {
        Api.post(context, "/v1/alerts/seen")
        Unit
    }

    suspend fun react(context: Context, postId: String, emoji: String) = withContext(Dispatchers.IO) {
        Api.post(context, "/v1/posts/$postId/react", JSONObject().put("emoji", emoji))
        Unit
    }

    suspend fun remove(context: Context, postId: String) = withContext(Dispatchers.IO) {
        Api.delete(context, "/v1/posts/$postId")
        Unit
    }

    suspend fun save(context: Context, post: Post) = withContext(Dispatchers.IO) {
        Api.post(
            context,
            "/v1/saves",
            JSONObject()
                .put("title", post.title)
                .put("artist", post.artist)
                .put("art", post.art),
        )
        Unit
    }

    suspend fun unsave(context: Context, track: SavedTrack) = withContext(Dispatchers.IO) {
        Api.post(
            context,
            "/v1/saves/remove",
            JSONObject().put("title", track.title).put("artist", track.artist),
        )
        Unit
    }

    suspend fun saved(context: Context): List<SavedTrack> = withContext(Dispatchers.IO) {
        Api.get(context, "/v1/saves").optJSONArray("items").map {
            SavedTrack(it.optString("title"), it.stringOrNull("artist"), it.stringOrNull("art"))
        }
    }

    suspend fun circle(context: Context): Circle = withContext(Dispatchers.IO) {
        val response = Api.get(context, "/v1/friends")
        Circle(
            accepted = response.optJSONArray("accepted").map { it.toPerson() },
            incoming = response.optJSONArray("incoming").map { it.toPerson() },
            outgoing = response.optJSONArray("outgoing").map { it.toPerson() },
        )
    }

    suspend fun request(context: Context, handle: String? = null, id: String? = null): String =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
            handle?.let { body.put("handle", it) }
            id?.let { body.put("id", it) }
            Api.post(context, "/v1/friends/request", body).optString("state")
        }

    suspend fun respond(context: Context, id: String, accept: Boolean) = withContext(Dispatchers.IO) {
        Api.post(context, "/v1/friends/respond", JSONObject().put("id", id).put("accept", accept))
        Unit
    }

    suspend fun unfriend(context: Context, id: String) = withContext(Dispatchers.IO) {
        Api.post(context, "/v1/friends/remove", JSONObject().put("id", id))
        Unit
    }

    suspend fun nearby(context: Context): List<Neighbour> = withContext(Dispatchers.IO) {
        Api.get(context, "/v1/nearby").optJSONArray("items").map {
            Neighbour(
                id = it.optString("id"),
                handle = it.optString("handle"),
                metres = it.optInt("metres"),
                link = it.optString("link"),
                title = it.stringOrNull("title"),
                artist = it.stringOrNull("artist"),
            )
        }
    }

    suspend fun forget(context: Context) = withContext(Dispatchers.IO) {
        Api.post(context, "/v1/me/forget")
        Unit
    }
}

internal fun JSONObject.toPost() = Post(
    id = optString("id"),
    handle = optString("handle"),
    title = optString("title"),
    artist = stringOrNull("artist"),
    album = stringOrNull("album"),
    art = stringOrNull("art"),
    source = stringOrNull("source"),
    listenedMs = optLong("listenedMs"),
    durationMs = optLong("durationMs"),
    updatedAt = optLong("updatedAt"),
    reactions = optInt("reactions"),
    mine = stringOrNull("mine"),
    counts = optJSONObject("counts").toCounts(),
    self = optBoolean("self"),
)

internal fun JSONObject?.toCounts(): Map<String, Int> {
    if (this == null) return emptyMap()
    val out = mutableMapOf<String, Int>()
    for (key in keys()) out[key] = optInt(key)
    return out
}

internal fun JSONObject.stringOrNull(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

internal fun JSONObject.toPerson() = Person(
    id = optString("id"),
    handle = optString("handle"),
    display = optString("display").ifBlank { optString("handle") },
)

internal fun <T> JSONArray?.map(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}
