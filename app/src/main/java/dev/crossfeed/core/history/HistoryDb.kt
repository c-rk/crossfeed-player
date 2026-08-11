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
                artwork TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_plays_started ON plays(started_at DESC)")
        db.execSQL("CREATE INDEX idx_plays_track ON plays(title, artist)")
        createAggregates(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 4) createAggregates(db)
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
        },
    )

    fun findRecent(title: String, artist: String?, source: String, since: Long): Long? {
        readableDatabase.rawQuery(
            "SELECT id FROM plays WHERE title = ? AND IFNULL(artist,'') = ? AND source = ? " +
                "AND started_at >= ? ORDER BY started_at DESC LIMIT 1",
            arrayOf(title, artist.orEmpty(), source, since.toString()),
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getLong(0) else null
        }
    }

    fun updateListened(id: Long, listenedMs: Long) {
        writableDatabase.execSQL(
            "UPDATE plays SET listened_ms = ? WHERE id = ?",
            arrayOf(listenedMs, id),
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
        var where = "started_at >= ?"
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
        return writableDatabase.delete("plays", "started_at < ?", arrayOf(cutoff.toString()))
    }

    fun feedCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM plays", null).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    fun delete(id: Long) {
        writableDatabase.delete("plays", "id = ?", arrayOf(id.toString()))
    }

    fun deleteTrack(title: String, artist: String?) {
        writableDatabase.delete("plays", "title = ? AND IFNULL(artist,'') = ?", arrayOf(title, artist.orEmpty()))
    }

    fun deleteOlderThan(cutoff: Long): Int =
        writableDatabase.delete("plays", "started_at < ?", arrayOf(cutoff.toString()))

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
        private const val VERSION = 4

        @Volatile
        private var instance: HistoryDb? = null

        fun get(context: Context): HistoryDb = instance ?: synchronized(this) {
            instance ?: HistoryDb(context).also { instance = it }
        }
    }
}
