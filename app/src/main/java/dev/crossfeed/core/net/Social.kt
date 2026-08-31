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

/**
 * A song handed to one person.
 *
 * Not a post with a smaller audience: a post is something you played and a dm is something you
 * chose for somebody. It carries who sent it, what they sent, and optionally the post it was an
 * answer to, so a reply can point back at what it is replying to.
 */
data class Dm(
    val id: String,
    val handle: String,
    val title: String,
    val artist: String?,
    val album: String?,
    val art: String?,
    val link: String?,
    val note: String?,
    val postId: String?,
    val at: Long,
    val fresh: Boolean,
)

data class Person(val id: String, val handle: String, val display: String)

data class Circle(val accepted: List<Person>, val incoming: List<Person>, val outgoing: List<Person>)


data class SavedTrack(val title: String, val artist: String?, val art: String?)

data class Live(
    val handle: String,
    val title: String,
    val artist: String?,
    val art: String?,
    val source: String?,
    val at: Long,
    val self: Boolean,
    val listenedMs: Long = 0,
    val durationMs: Long = 0,
) {
    /** How far through the track they were when they last said. */
    val through: Float
        get() = if (durationMs > 0) (listenedMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /**
     * The same thing, carried forward to now.
     *
     * Someone's phone only speaks every half minute, so a ring drawn from the last word alone
     * sits still and then jumps. Between words it is assumed they kept playing, which is true
     * almost always and cheap to be wrong about. Almost: it will not run further than a little
     * past the next expected word, so a paused track drifts a few seconds and then waits rather
     * than sweeping confidently to the end of a song nobody is hearing.
     */
    fun throughAt(nowMs: Long): Float {
        if (durationMs <= 0) return 0f
        val since = (nowMs - at).coerceIn(0L, CREEP_MS)
        return ((listenedMs + since).toFloat() / durationMs).coerceIn(0f, 1f)
    }

    private companion object {
        const val CREEP_MS = 35_000L
    }
}

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
                        listenedMs = it.optLong("listenedMs"),
                        durationMs = it.optLong("durationMs"),
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
                listenedMs = it.optLong("listenedMs"),
                durationMs = it.optLong("durationMs"),
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

    /** Songs people have sent you, newest first. */
    suspend fun dms(context: Context): List<Dm> = withContext(Dispatchers.IO) {
        Api.get(context, "/v1/dms").optJSONArray("items").map {
            Dm(
                id = it.optString("id"),
                handle = it.optString("handle"),
                title = it.optString("title"),
                artist = it.stringOrNull("artist"),
                album = it.stringOrNull("album"),
                art = it.stringOrNull("art"),
                link = it.stringOrNull("link"),
                note = it.stringOrNull("note"),
                postId = it.stringOrNull("postId"),
                at = it.optLong("at"),
                fresh = it.optBoolean("fresh"),
            )
        }
    }

    /**
     * Hands a song to somebody. Only to people you have both agreed to, which the server checks
     * rather than trusting, because this is the one thing in crossfeed addressed to a person.
     */
    suspend fun send(
        context: Context,
        to: String,
        title: String,
        artist: String?,
        album: String? = null,
        art: String? = null,
        link: String? = null,
        postId: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("to", to.removePrefix("@").trim())
            .put("title", title)
            .put("artist", artist)
            .put("album", album)
            .put("art", art)
            .put("link", link)
            .put("postId", postId)
        Api.post(context, "/v1/dms", body).optString("id")
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
