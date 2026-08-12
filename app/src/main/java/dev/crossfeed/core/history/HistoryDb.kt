package dev.crossfeed.core.history

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class Play(
    val id: Long,
    val title: String,
    val artist: String?,
    val album: String?,
    val durationMs: Long,
    val listenedMs: Long,
    val source: String,
    val startedAt: Long,
    val genre: String?,
    val artwork: String?,
)

data class Tally(val label: String, val plays: Int, val listenedMs: Long, val artwork: String? = null)

data class Summary(
    val totalPlays: Int,
    val listenedMs: Long,
    val distinctTracks: Int,
    val distinctArtists: Int,
)

class HistoryDb private constructor(context: Context) :
    SQLiteOpenHelper(context.applicationContext, NAME, null, VERSION) {

    private var pendingMerge = false

    fun runPendingMaintenance() {
        if (!pendingMerge) return
        pendingMerge = false
        runCatching { mergeDuplicates(RESUME_WINDOW_MS) }
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE plays (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                artist TEXT,
                album TEXT,
                duration_ms INTEGER NOT NULL DEFAULT 0,
                listened_ms INTEGER NOT NULL DEFAULT 0,
                source TEXT NOT NULL,
                started_at INTEGER NOT NULL,
                genre TEXT,
                artwork TEXT,
                post_id TEXT,
                hidden INTEGER NOT NULL DEFAULT 0,
                last_at INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_plays_started ON plays(started_at DESC)")
        db.execSQL("CREATE INDEX idx_plays_track ON plays(title, artist)")
        createAggregates(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) createAggregates(db)
        if (oldVersion < 5) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS lyrics (key TEXT NOT NULL PRIMARY KEY, synced TEXT, plain TEXT, " +
                    "instrumental INTEGER NOT NULL DEFAULT 0, missing INTEGER NOT NULL DEFAULT 0, " +
                    "fetched_at INTEGER NOT NULL)",
            )
            db.execSQL("ALTER TABLE plays ADD COLUMN post_id TEXT")
            db.execSQL("ALTER TABLE plays ADD COLUMN hidden INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 8) pendingMerge = true
        if (oldVersion < 7) {
            db.execSQL("ALTER TABLE plays ADD COLUMN last_at INTEGER NOT NULL DEFAULT 0")
            db.execSQL("UPDATE plays SET last_at = started_at + listened_ms WHERE last_at = 0")
        }
        if (oldVersion < 6) {
            db.execSQL(
            "CREATE TABLE IF NOT EXISTS resurface (key TEXT NOT NULL PRIMARY KEY, next_at INTEGER NOT NULL, " +
                    "interval_days INTEGER NOT NULL DEFAULT 30, dismissed INTEGER NOT NULL DEFAULT 0)",
            )
            db.execSQL(
            "CREATE TABLE IF NOT EXISTS credits (key TEXT NOT NULL PRIMARY KEY, missing INTEGER NOT NULL DEFAULT 0, " +
                    "fetched_at INTEGER NOT NULL)",
            )
            db.execSQL(
            "CREATE TABLE IF NOT EXISTS credit_people (key TEXT NOT NULL, person TEXT NOT NULL, " +
                    "role TEXT NOT NULL, PRIMARY KEY(key, person, role))",
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_credit_person ON credit_people(person)")
        }
    }

    private fun createAggregates(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS agg (kind TEXT NOT NULL, label TEXT NOT NULL, day TEXT NOT NULL, " +
                "plays INTEGER NOT NULL DEFAULT 0, listened_ms INTEGER NOT NULL DEFAULT 0, " +
                "PRIMARY KEY(kind,label,day))",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS finishes (source TEXT NOT NULL, day TEXT NOT NULL, " +
                "finished_sum REAL NOT NULL DEFAULT 0, tracks INTEGER NOT NULL DEFAULT 0, " +
                "skips INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(source,day))",
        )
        db.execSQL("CREATE TABLE IF NOT EXISTS track_genre (title TEXT NOT NULL PRIMARY KEY, genre TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS meta (key TEXT NOT NULL PRIMARY KEY, value INTEGER NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS remote_art (key TEXT NOT NULL PRIMARY KEY, url TEXT NOT NULL)")
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS lyrics (key TEXT NOT NULL PRIMARY KEY, synced TEXT, plain TEXT, " +
                "instrumental INTEGER NOT NULL DEFAULT 0, missing INTEGER NOT NULL DEFAULT 0, " +
                "fetched_at INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS resurface (key TEXT NOT NULL PRIMARY KEY, next_at INTEGER NOT NULL, " +
                "interval_days INTEGER NOT NULL DEFAULT 30, dismissed INTEGER NOT NULL DEFAULT 0)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS credits (key TEXT NOT NULL PRIMARY KEY, missing INTEGER NOT NULL DEFAULT 0, " +
                "fetched_at INTEGER NOT NULL)",
        )
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS credit_people (key TEXT NOT NULL, person TEXT NOT NULL, " +
                "role TEXT NOT NULL, PRIMARY KEY(key, person, role))",
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_credit_person ON credit_people(person)")
    }

    data class CachedLyrics(
        val synced: String?,
        val plain: String?,
        val instrumental: Boolean,
        val missing: Boolean,
        val fetchedAt: Long,
    )

    fun lyrics(key: String): CachedLyrics? {
        readableDatabase.rawQuery(
            "SELECT synced, plain, instrumental, missing, fetched_at FROM lyrics WHERE key = ?",
            arrayOf(key),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return CachedLyrics(
                synced = cursor.getString(0),
                plain = cursor.getString(1),
                instrumental = cursor.getInt(2) == 1,
                missing = cursor.getInt(3) == 1,
                fetchedAt = cursor.getLong(4),
            )
        }
    }

    fun putLyrics(key: String, synced: String?, plain: String?, instrumental: Boolean, missing: Boolean) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO lyrics (key, synced, plain, instrumental, missing, fetched_at) " +
                "VALUES (?,?,?,?,?,?)",
            arrayOf(key, synced, plain, if (instrumental) 1 else 0, if (missing) 1 else 0, System.currentTimeMillis()),
        )
    }

    fun linkPost(rowId: Long, postId: String) {
        writableDatabase.execSQL("UPDATE plays SET post_id = ? WHERE id = ?", arrayOf(postId, rowId))
    }

    fun postIdOf(rowId: Long): String? {
        readableDatabase.rawQuery("SELECT post_id FROM plays WHERE id = ?", arrayOf(rowId.toString())).use { c ->
            return if (c.moveToFirst()) c.getString(0) else null
        }
    }

    fun knownTracks(limit: Int): List<dev.crossfeed.core.curate.Pick> {
        val out = mutableListOf<dev.crossfeed.core.curate.Pick>()
        readableDatabase.rawQuery(
            "SELECT title, IFNULL(artist,''), album, MAX(duration_ms), artwork, genre, COUNT(*) " +
                "FROM plays WHERE duration_ms > 30000 GROUP BY title, artist " +
                "ORDER BY COUNT(*) DESC, MAX(started_at) DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    dev.crossfeed.core.curate.Pick(
                        title = cursor.getString(0),
                        artist = cursor.getString(1),
                        album = cursor.getString(2),
                        durationMs = cursor.getLong(3),
                        artwork = cursor.getString(4),
                        genre = cursor.getString(5),
                        known = true,
                    ),
                )
            }
        }
        return out
    }

    fun knownGenres(limit: Int = 14): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT genre, COUNT(*) FROM plays WHERE genre IS NOT NULL AND genre != '' " +
                "GROUP BY genre ORDER BY COUNT(*) DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.getString(0))
        }
        return out
    }

    data class Forgotten(
        val title: String,
        val artist: String?,
        val artwork: String?,
        val plays: Int,
        val lastAt: Long,
    ) {
        val key: String get() = "$title|${artist.orEmpty()}".lowercase()
    }

    fun forgotten(minPlays: Int = 3, quietDays: Int = 60, limit: Int = 12): List<Forgotten> {
        val now = System.currentTimeMillis()
        val cutoff = now - java.util.concurrent.TimeUnit.DAYS.toMillis(quietDays.toLong())
        val out = mutableListOf<Forgotten>()
        readableDatabase.rawQuery(
            "SELECT p.title, IFNULL(p.artist,''), MAX(p.artwork), COUNT(*) c, MAX(p.started_at) last " +
                "FROM plays p LEFT JOIN resurface r ON r.key = LOWER(p.title || '|' || IFNULL(p.artist,'')) " +
                "WHERE IFNULL(r.dismissed,0) = 0 AND IFNULL(r.next_at,0) <= ? " +
                "GROUP BY p.title, p.artist HAVING c >= ? AND last < ? " +
                "ORDER BY c DESC, last ASC LIMIT ?",
            arrayOf(now.toString(), minPlays.toString(), cutoff.toString(), limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Forgotten(
                        title = cursor.getString(0),
                        artist = cursor.getString(1).takeIf { it.isNotBlank() },
                        artwork = cursor.getString(2),
                        plays = cursor.getInt(3),
                        lastAt = cursor.getLong(4),
                    ),
                )
            }
        }
        return out
    }

    fun resurfaceAgain(key: String) {
        val current = readableDatabase.rawQuery(
            "SELECT interval_days FROM resurface WHERE key = ?",
            arrayOf(key),
        ).use { if (it.moveToFirst()) it.getInt(0) else 30 }
        val next = (current * 2).coerceAtMost(720)
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO resurface (key, next_at, interval_days, dismissed) VALUES (?,?,?,0)",
            arrayOf(
                key,
                System.currentTimeMillis() + java.util.concurrent.TimeUnit.DAYS.toMillis(next.toLong()),
                next,
            ),
        )
    }

    fun resurfaceNever(key: String) {
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO resurface (key, next_at, interval_days, dismissed) VALUES (?,?,?,1)",
            arrayOf(key, Long.MAX_VALUE, 720),
        )
    }

    data class Credit(val person: String, val role: String)

    fun credits(key: String): List<Credit>? {
        val known = readableDatabase.rawQuery(
            "SELECT missing FROM credits WHERE key = ?",
            arrayOf(key),
        ).use { if (it.moveToFirst()) it.getInt(0) else -1 }
        if (known < 0) return null
        if (known == 1) return emptyList()
        val out = mutableListOf<Credit>()
        readableDatabase.rawQuery(
            "SELECT person, role FROM credit_people WHERE key = ? ORDER BY role, person",
            arrayOf(key),
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(Credit(cursor.getString(0), cursor.getString(1)))
        }
        return out
    }

    fun putCredits(key: String, people: List<Credit>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT OR REPLACE INTO credits (key, missing, fetched_at) VALUES (?,?,?)",
                arrayOf(key, if (people.isEmpty()) 1 else 0, System.currentTimeMillis()),
            )
            db.execSQL("DELETE FROM credit_people WHERE key = ?", arrayOf(key))
            for (credit in people) {
                db.execSQL(
                    "INSERT OR REPLACE INTO credit_people (key, person, role) VALUES (?,?,?)",
                    arrayOf(key, credit.person, credit.role),
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun appearances(person: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT key) FROM credit_people WHERE person = ?",
            arrayOf(person),
        ).use { return if (it.moveToFirst()) it.getInt(0) else 0 }
    }

    fun mergeDuplicates(windowMs: Long): Int {
        data class Row(val id: Long, val key: String, val startedAt: Long, val lastAt: Long, val listened: Long)

        val rows = mutableListOf<Row>()
        readableDatabase.rawQuery(
            "SELECT id, LOWER(TRIM(title)) || '|' || LOWER(TRIM(IFNULL(artist,''))) || '|' || source, " +
                "started_at, MAX(last_at, started_at), listened_ms FROM plays ORDER BY started_at ASC",
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                rows.add(
                    Row(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getLong(2),
                        cursor.getLong(3),
                        cursor.getLong(4),
                    ),
                )
            }
        }

        val keepers = mutableMapOf<String, Row>()
        val folded = mutableListOf<Pair<Long, Long>>()
        val gone = mutableListOf<Long>()
        for (row in rows) {
            val held = keepers[row.key]
            if (held != null && row.startedAt - held.lastAt <= windowMs) {
                val total = held.listened + row.listened
                keepers[row.key] = held.copy(lastAt = maxOf(held.lastAt, row.lastAt), listened = total)
                folded.add(held.id to total)
                gone.add(row.id)
            } else {
                keepers[row.key] = row
            }
        }
        if (gone.isEmpty()) return 0

        val db = writableDatabase
        db.beginTransaction()
        try {
            for ((id, total) in folded) {
                db.execSQL("UPDATE plays SET listened_ms = ? WHERE id = ?", arrayOf(total, id))
            }
            for (id in gone.chunked(200)) {
                db.execSQL("DELETE FROM plays WHERE id IN (${id.joinToString(",")})")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return gone.size
    }

    fun remoteArt(key: String): String? {
        readableDatabase.rawQuery("SELECT url FROM remote_art WHERE key = ?", arrayOf(key)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    fun setRemoteArt(key: String, url: String) {
        writableDatabase.execSQL("INSERT OR REPLACE INTO remote_art(key,url) VALUES(?,?)", arrayOf(key, url))
    }

    fun record(
        title: String,
        artist: String?,
        album: String?,
        source: String,
        startedAt: Long,
        deltaMs: Long,
        newPlay: Boolean,
    ) {
        if (deltaMs <= 0 && !newPlay) return
        val day = Days.of(startedAt)
        val hour = Days.hourOf(startedAt).toString()
        val count = if (newPlay) 1 else 0
        val db = writableDatabase
        db.beginTransaction()
        runCatching {
            db.bump(Kind.DAY, day, day, count, deltaMs)
            db.bump(Kind.HOUR, hour, day, count, deltaMs)
            db.bump(Kind.SOURCE, source, day, count, deltaMs)
            db.bump(Kind.TITLE, title, day, count, deltaMs)
            artist?.let { db.bump(Kind.ARTIST, it, day, count, deltaMs) }
            album?.let { db.bump(Kind.ALBUM, it, day, count, deltaMs) }
            db.setTransactionSuccessful()
        }
        db.endTransaction()
    }

    fun recordFinish(
        title: String,
        source: String,
        startedAt: Long,
        listenedMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        val finished = (listenedMs.toDouble() / durationMs).coerceAtMost(1.0)
        val skipped = finished < 0.3
        val day = Days.of(startedAt)
        writableDatabase.bumpFinish(source, day, finished, skipped)
        if (skipped) writableDatabase.bump(Kind.SKIP, title, day, 1, 0)
    }

    fun raiseMeta(key: String, value: Long) {
        writableDatabase.execSQL("INSERT OR IGNORE INTO meta(key,value) VALUES(?,0)", arrayOf(key))
        writableDatabase.execSQL(
            "UPDATE meta SET value = ? WHERE key = ? AND value < ?",
            arrayOf(value, key, value),
        )
    }

    fun meta(key: String): Long {
        readableDatabase.rawQuery("SELECT value FROM meta WHERE key = ?", arrayOf(key)).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    fun topByPlays(kind: String, sinceDay: String, limit: Int = 1): List<Tally> {
        val out = mutableListOf<Tally>()
        readableDatabase.rawQuery(
            "SELECT label, SUM(plays), SUM(listened_ms) FROM agg WHERE kind=? AND day>=? " +
                "GROUP BY label ORDER BY SUM(plays) DESC LIMIT ?",
            arrayOf(kind, sinceDay, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Tally(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)))
            }
        }
        return out
    }

    fun listenedOf(id: Long): Long {
        readableDatabase.rawQuery("SELECT listened_ms FROM plays WHERE id = ?", arrayOf(id.toString()))
            .use { cursor -> return if (cursor.moveToFirst()) cursor.getLong(0) else 0L }
    }

    fun insert(play: Play): Long = writableDatabase.insert(
        "plays",
        null,
        ContentValues().apply {
            put("title", play.title)
            put("artist", play.artist)
            put("album", play.album)
            put("duration_ms", play.durationMs)
            put("listened_ms", play.listenedMs)
            put("source", play.source)
            put("started_at", play.startedAt)
            put("genre", play.genre)
            put("artwork", play.artwork)
            put("last_at", play.startedAt)
        },
    )

    fun findRecent(title: String, artist: String?, source: String, since: Long): Long? {
        readableDatabase.rawQuery(
            "SELECT id FROM plays WHERE LOWER(TRIM(title)) = ? AND source = ? " +
                "AND MAX(last_at, started_at) >= ? " +
                "AND (LOWER(TRIM(IFNULL(artist,''))) = ? OR LOWER(TRIM(IFNULL(artist,''))) = '' OR ? = '') " +
                "ORDER BY MAX(last_at, started_at) DESC LIMIT 1",
            arrayOf(
                title.trim().lowercase(),
                source,
                since.toString(),
                artist.orEmpty().trim().lowercase(),
                artist.orEmpty().trim().lowercase(),
            ),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    fun updateListened(id: Long, listenedMs: Long) {
        writableDatabase.execSQL(
            "UPDATE plays SET listened_ms = ?, last_at = ? WHERE id = ?",
            arrayOf(listenedMs, System.currentTimeMillis(), id),
        )
    }

    fun dropOldestWindow(days: Int): Int {
        val oldest = oldestAt() ?: return 0
        val cutoff = oldest + java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong())
        return writableDatabase.delete("plays", "started_at < ?", arrayOf(cutoff.toString()))
    }

    fun setGenre(title: String, artist: String?, genre: String) {
        writableDatabase.execSQL(
            "UPDATE plays SET genre = ? WHERE genre IS NULL AND title = ? AND IFNULL(artist,'') = ?",
            arrayOf(genre, title, artist.orEmpty()),
        )
        writableDatabase.execSQL(
            "INSERT OR REPLACE INTO track_genre(title,genre) VALUES(?,?)",
            arrayOf(title, genre),
        )
    }

    fun needsGenre(limit: Int = 12): List<Pair<String, String?>> {
        val out = mutableListOf<Pair<String, String?>>()
        readableDatabase.rawQuery(
            "SELECT p.title, p.artist FROM plays p LEFT JOIN track_genre g ON g.title = p.title " +
                "WHERE g.genre IS NULL GROUP BY p.title, p.artist " +
                "ORDER BY MAX(p.started_at) DESC LIMIT ?",
            arrayOf(limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.getString(0) to cursor.getString(1))
        }
        return out
    }

    fun feed(since: Long = 0, query: String = "", limit: Int = 200): List<Play> {
        val args = mutableListOf<String>(since.toString())
        var where = "hidden = 0 AND started_at >= ?"
        if (query.isNotBlank()) {
            where += " AND (title LIKE ? OR IFNULL(artist,'') LIKE ? OR IFNULL(album,'') LIKE ?)"
            repeat(3) { args.add("%${query.trim()}%") }
        }
        args.add(limit.toString())
        val out = mutableListOf<Play>()
        readableDatabase.rawQuery(
            "SELECT id,title,artist,album,duration_ms,listened_ms,source,started_at,genre,artwork " +
                "FROM plays WHERE $where ORDER BY started_at DESC LIMIT ?",
            args.toTypedArray(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(
                    Play(
                        id = cursor.getLong(0),
                        title = cursor.getString(1),
                        artist = cursor.getString(2),
                        album = cursor.getString(3),
                        durationMs = cursor.getLong(4),
                        listenedMs = cursor.getLong(5),
                        source = cursor.getString(6),
                        startedAt = cursor.getLong(7),
                        genre = cursor.getString(8),
                        artwork = cursor.getString(9),
                    ),
                )
            }
        }
        return out
    }

    fun summary(sinceDay: String): Summary {
        var plays = 0
        var listened = 0L
        readableDatabase.rawQuery(
            "SELECT IFNULL(SUM(plays),0), IFNULL(SUM(listened_ms),0) FROM agg WHERE kind=? AND day>=?",
            arrayOf(Kind.DAY, sinceDay),
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                plays = cursor.getInt(0)
                listened = cursor.getLong(1)
            }
        }
        return Summary(plays, listened, distinct(Kind.TITLE, sinceDay), distinct(Kind.ARTIST, sinceDay))
    }

    private fun distinct(kind: String, sinceDay: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(DISTINCT label) FROM agg WHERE kind=? AND day>=?",
            arrayOf(kind, sinceDay),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    fun top(kind: String, sinceDay: String, limit: Int = 5): List<Tally> {
        val out = mutableListOf<Tally>()
        readableDatabase.rawQuery(
            "SELECT label, SUM(plays), SUM(listened_ms) FROM agg WHERE kind=? AND day>=? " +
                "GROUP BY label ORDER BY SUM(listened_ms) DESC LIMIT ?",
            arrayOf(kind, sinceDay, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Tally(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)))
            }
        }
        return out
    }

    fun topGenres(sinceDay: String, limit: Int = 5): List<Tally> {
        val out = mutableListOf<Tally>()
        readableDatabase.rawQuery(
            "SELECT g.genre, SUM(a.plays), SUM(a.listened_ms) FROM agg a " +
                "JOIN track_genre g ON g.title = a.label " +
                "WHERE a.kind=? AND a.day>=? GROUP BY g.genre ORDER BY 3 DESC LIMIT ?",
            arrayOf(Kind.TITLE, sinceDay, limit.toString()),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                out.add(Tally(cursor.getString(0), cursor.getInt(1), cursor.getLong(2)))
            }
        }
        return out
    }

    fun hourHistogram(sinceDay: String): List<Pair<Int, Long>> {
        val out = mutableListOf<Pair<Int, Long>>()
        readableDatabase.rawQuery(
            "SELECT CAST(label AS INTEGER), SUM(listened_ms) FROM agg WHERE kind=? AND day>=? GROUP BY label",
            arrayOf(Kind.HOUR, sinceDay),
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.getInt(0) to cursor.getLong(1))
        }
        return out
    }

    fun activeDays(sinceDay: String = "0"): List<Pair<String, Long>> {
        val out = mutableListOf<Pair<String, Long>>()
        readableDatabase.rawQuery(
            "SELECT day, SUM(listened_ms) FROM agg WHERE kind=? AND day>=? GROUP BY day ORDER BY day DESC",
            arrayOf(Kind.DAY, sinceDay),
        ).use { cursor ->
            while (cursor.moveToNext()) out.add(cursor.getString(0) to cursor.getLong(1))
        }
        return out
    }

    fun completion(sinceDay: String, source: String? = null): Triple<Double, Int, Int> {
        val args = mutableListOf(sinceDay)
        var where = "day>=?"
        if (source != null) {
            where += " AND source=?"
            args.add(source)
        }
        readableDatabase.rawQuery(
            "SELECT IFNULL(SUM(finished_sum),0), IFNULL(SUM(tracks),0), IFNULL(SUM(skips),0) FROM finishes WHERE " + where,
            args.toTypedArray(),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return Triple(0.0, 0, 0)
            val sum = cursor.getDouble(0)
            val tracks = cursor.getInt(1)
            return Triple(if (tracks > 0) sum / tracks else 0.0, tracks, cursor.getInt(2))
        }
    }

    fun firstSeenCount(kind: String, sinceDay: String): Int {
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM (SELECT label, MIN(day) AS first_day FROM agg WHERE kind=? GROUP BY label) " +
                "WHERE first_day >= ?",
            arrayOf(kind, sinceDay),
        ).use { cursor -> return if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
    }

    fun obsession(sinceDay: String): Triple<String, String, Int>? {
        readableDatabase.rawQuery(
            "SELECT label, day, plays FROM agg WHERE kind=? AND day>=? ORDER BY plays DESC LIMIT 1",
            arrayOf(Kind.TITLE, sinceDay),
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return Triple(cursor.getString(0), cursor.getString(1), cursor.getInt(2))
        }
    }

    fun sourceList(sinceDay: String): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT DISTINCT source FROM finishes WHERE day>=?",
            arrayOf(sinceDay),
        ).use { cursor -> while (cursor.moveToNext()) out.add(cursor.getString(0)) }
        return out
    }

    fun needsGenreFromAgg(limit: Int = 12): List<String> {
        val out = mutableListOf<String>()
        readableDatabase.rawQuery(
            "SELECT a.label FROM agg a LEFT JOIN track_genre g ON g.title = a.label " +
                "WHERE a.kind=? AND g.genre IS NULL GROUP BY a.label ORDER BY SUM(a.listened_ms) DESC LIMIT ?",
            arrayOf(Kind.TITLE, limit.toString()),
        ).use { cursor -> while (cursor.moveToNext()) out.add(cursor.getString(0)) }
        return out
    }

    fun eraseFeedOlderThan(days: Int): Int {
        val cutoff = System.currentTimeMillis() - java.util.concurrent.TimeUnit.DAYS.toMillis(days.toLong())
        val values = android.content.ContentValues().apply { put("hidden", 1) }
        return writableDatabase.update("plays", values, "hidden = 0 AND started_at < ?", arrayOf(cutoff.toString()))
    }

    fun feedCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM plays WHERE hidden = 0", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun playCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM plays", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun hide(id: Long) {
        val values = android.content.ContentValues().apply { put("hidden", 1) }
        writableDatabase.update("plays", values, "id = ?", arrayOf(id.toString()))
    }

    fun hideTrack(title: String, artist: String?) {
        val values = android.content.ContentValues().apply { put("hidden", 1) }
        writableDatabase.update(
            "plays",
            values,
            "title = ? AND IFNULL(artist,'') = ?",
            arrayOf(title, artist.orEmpty()),
        )
    }

    fun clear() {
        writableDatabase.delete("plays", null, null)
    }

    fun sweepArt(context: Context) {
        val keep = mutableSetOf<String>()
        readableDatabase.rawQuery("SELECT artwork FROM plays WHERE artwork IS NOT NULL", null).use { cursor ->
            while (cursor.moveToNext()) keep.add(cursor.getString(0))
        }
        ArtStore.sweep(context, keep)
    }

    fun compact() {
        writableDatabase.execSQL("VACUUM")
    }

    fun sizeBytes(context: Context): Long {
        val base = context.getDatabasePath(NAME)
        val db = listOf(base, java.io.File(base.path + "-wal"), java.io.File(base.path + "-shm"))
            .filter { it.exists() }
            .sumOf { it.length() }
        return db + ArtStore.sizeBytes(context)
    }

    fun oldestAt(): Long? {
        readableDatabase.rawQuery("SELECT MIN(started_at) FROM plays", null).use { cursor ->
            if (!cursor.moveToFirst() || cursor.isNull(0)) return null
            return cursor.getLong(0)
        }
    }

    companion object {
        private const val NAME = "listening.db"
        private const val RESUME_WINDOW_MS = 60 * 60_000L
        private const val VERSION = 8

        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: HistoryDb(context).also { instance = it }
        }
    }
}
