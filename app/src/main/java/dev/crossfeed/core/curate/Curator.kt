package dev.crossfeed.core.curate

import android.content.Context
import dev.crossfeed.core.catalog.Catalog
import dev.crossfeed.core.history.HistoryDb
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs

data class Pick(
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val artwork: String?,
    val genre: String?,
    val known: Boolean,
)

data class Recipe(
    val minutes: Int = 45,
    val familiarity: Float = 0.5f,
    val genres: Set<String> = emptySet(),
    val languages: Set<String> = emptySet(),
    val preferAlbums: Boolean = false,
)

data class Result(
    val picks: List<Pick>,
    val totalMs: Long,
    val albums: Int,
    val targetMs: Long,
) {
    val short: Boolean get() = totalMs < targetMs - 120_000
}

object Curator {

    private const val SLACK_MS = 45_000L
    private const val CAP = 2

    suspend fun build(context: Context, recipe: Recipe): Result = withContext(Dispatchers.IO) {
        val target = recipe.minutes * 60_000L
        val db = HistoryDb.get(context)
        val familiarity = recipe.familiarity.coerceIn(0f, 1f)

        val known = if (familiarity > 0.02f) db.knownTracks(600).filter { fits(it, recipe) } else emptyList()
        val knownBudget = (target * familiarity).toLong()
        val freshBudget = target - knownBudget

        val fresh = if (freshBudget > 0) discover(context, recipe, known, freshBudget) else emptyList()
        val pool = known + fresh

        val picks = if (recipe.preferAlbums) {
            byAlbum(pool, target)
        } else {
            fill(known.shuffled(), knownBudget, CAP) + fill(fresh.shuffled(), freshBudget, CAP)
        }

        var chosen = topUp(picks, pool, target, CAP)
        if (!recipe.preferAlbums && chosen.sumOf { it.durationMs } < target - 120_000) {
            chosen = topUp(chosen, pool, target, Int.MAX_VALUE)
        }
        val ordered = spread(chosen)
        Result(
            picks = ordered,
            totalMs = ordered.sumOf { it.durationMs },
            albums = ordered.mapNotNull { it.album }.distinct().size,
            targetMs = target,
        )
    }

    private fun fits(pick: Pick, recipe: Recipe): Boolean {
        if (pick.durationMs <= 0) return false
        if (recipe.genres.isNotEmpty() && Genre.of(pick.genre) !in recipe.genres) return false
        if (recipe.languages.isNotEmpty() && Language.of(pick.title, pick.genre) !in recipe.languages) {
            return false
        }
        return true
    }

    private fun fill(pool: List<Pick>, budget: Long, perArtist: Int): List<Pick> {
        if (budget <= 0) return emptyList()
        val out = mutableListOf<Pick>()
        val counts = mutableMapOf<String, Int>()
        var used = 0L
        for (pick in pool) {
            if (used + pick.durationMs > budget + SLACK_MS) continue
            val artist = pick.artist.lowercase()
            if ((counts[artist] ?: 0) >= perArtist) continue
            out.add(pick)
            counts[artist] = (counts[artist] ?: 0) + 1
            used += pick.durationMs
            if (budget - used < 60_000) break
        }
        return out
    }

    private fun byAlbum(pool: List<Pick>, target: Long): List<Pick> {
        val albums = pool.filter { !it.album.isNullOrBlank() }
            .groupBy { it.album!! }
            .filter { it.value.size >= 3 }
            .toList()
            .sortedByDescending { it.second.size }

        val out = mutableListOf<Pick>()
        var used = 0L
        for ((_, tracks) in albums) {
            val sorted = tracks.distinctBy { it.title }
            val length = sorted.sumOf { it.durationMs }
            if (used + length <= target + SLACK_MS) {
                out.addAll(sorted)
                used += length
            }
            if (target - used < 4 * 60_000) break
        }
        return out
    }

    private fun topUp(picks: List<Pick>, pool: List<Pick>, target: Long, perArtist: Int): List<Pick> {
        val out = picks.toMutableList()
        var used = out.sumOf { it.durationMs }
        val chosen = out.map { "${it.title}|${it.artist}".lowercase() }.toMutableSet()
        val counts = out.groupingBy { it.artist.lowercase() }.eachCount().toMutableMap()
        while (used < target - 30_000) {
            val gap = target - used
            val next = pool
                .asSequence()
                .filter { "${it.title}|${it.artist}".lowercase() !in chosen }
                .filter { (counts[it.artist.lowercase()] ?: 0) < perArtist }
                .filter { used + it.durationMs <= target + SLACK_MS }
                .minByOrNull { abs(it.durationMs - gap) } ?: break
            out.add(next)
            chosen.add("${next.title}|${next.artist}".lowercase())
            counts[next.artist.lowercase()] = (counts[next.artist.lowercase()] ?: 0) + 1
            used += next.durationMs
        }
        return out
    }

    private fun spread(picks: List<Pick>): List<Pick> {
        if (picks.size < 3) return picks
        val out = mutableListOf<Pick>()
        val rest = picks.toMutableList()
        while (rest.isNotEmpty()) {
            val last = out.lastOrNull()?.artist
            val next = rest.firstOrNull { it.artist != last } ?: rest.first()
            out.add(next)
            rest.remove(next)
        }
        return out
    }

    private suspend fun discover(
        context: Context,
        recipe: Recipe,
        known: List<Pick>,
        budget: Long,
    ): List<Pick> {
        val seen = known.map { "${it.title}|${it.artist}".lowercase() }.toMutableSet()
        val out = mutableListOf<Pick>()

        val seeds = mutableListOf<String>()
        for (language in recipe.languages) seeds.addAll(Language.seeds(language))
        seeds.addAll(recipe.genres.map { Genre.seed(it) })
        if (recipe.languages.isEmpty()) {
            seeds.addAll(
                HistoryDb.get(context).knownTracks(120)
                    .map { it.artist }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .shuffled()
                    .take(5),
            )
        }
        if (seeds.isEmpty()) seeds.add("top songs")

        for (seed in seeds.distinct().take(10)) {
            if (out.sumOf { it.durationMs } > budget * 3) break
            for (hit in Catalog.search(context, seed, limit = 50)) {
                // a record nobody is credited on cannot be spread across a running order
                val artist = hit.artist?.takeIf { it.isNotBlank() } ?: continue
                val key = "${hit.title}|$artist".lowercase()
                if (!seen.add(key)) continue
                val pick = Pick(
                    title = hit.title,
                    artist = artist,
                    album = hit.album,
                    durationMs = (hit.durationMs ?: 0).toLong(),
                    artwork = hit.artwork,
                    genre = hit.genre,
                    known = false,
                )
                if (fits(pick, recipe)) out.add(pick)
            }
        }
        return out.shuffled()
    }
}

object Genre {

    private val folds = listOf(
        listOf("hip hop", "hip-hop", "rap", "trap", "drill") to "hip-hop",
        listOf("r&b", "rnb", "soul", "funk", "motown") to "r&b",
        listOf("house", "techno", "electronic", "edm", "dubstep", "trance", "garage") to "electronic",
        listOf("dance", "disco") to "dance",
        listOf("metal", "hardcore") to "metal",
        listOf("punk") to "punk",
        listOf("indie") to "indie",
        listOf("alternative") to "alternative",
        listOf("rock") to "rock",
        listOf("jazz", "bebop", "swing") to "jazz",
        listOf("blues") to "blues",
        listOf("classical crossover", "classical", "opera", "orchestral", "baroque", "carnatic", "hindustani")
            to "classical",
        listOf("singer/songwriter", "singer-songwriter", "folk", "americana", "acoustic") to "folk",
        listOf("country", "bluegrass") to "country",
        listOf("reggae", "dancehall", "ska") to "reggae",
        listOf("ambient", "new age", "chill", "lo-fi", "lofi") to "ambient",
        listOf("devotional", "spiritual", "gospel", "bhajan", "christian") to "devotional",
        listOf("soundtrack", "bollywood", "filmi", "film", "score", "musicals", "anime") to "film",
        listOf("tamil", "telugu", "malayalam", "kannada", "punjabi", "bengali", "marathi", "hindi") to "film",
        listOf("k-pop", "j-pop", "c-pop", "mandopop", "indian pop", "pop") to "pop",
        listOf("latin", "reggaeton", "salsa", "bossa") to "latin",
        listOf("world", "afrobeat", "afro") to "world",
    )

    val canonical = listOf(
        "pop", "rock", "hip-hop", "r&b", "electronic", "dance", "indie", "alternative",
        "metal", "punk", "jazz", "blues", "classical", "folk", "country", "reggae",
        "ambient", "devotional", "film", "latin", "world",
    )

    fun of(raw: String?): String? {
        val lower = raw?.lowercase()?.trim().orEmpty()
        if (lower.isBlank()) return null
        for ((needles, name) in folds) {
            if (needles.any { lower.contains(it) }) return name
        }
        return null
    }

    fun seed(name: String): String = when (name) {
        "film" -> "film soundtrack"
        "classical" -> "classical music"
        else -> "$name hits"
    }
}

object Language {

    private val scripts = listOf(
        "tamil" to 0x0B80..0x0BFF,
        "hindi" to 0x0900..0x097F,
        "telugu" to 0x0C00..0x0C7F,
        "malayalam" to 0x0D00..0x0D7F,
        "kannada" to 0x0C80..0x0CFF,
        "bengali" to 0x0980..0x09FF,
        "punjabi" to 0x0A00..0x0A7F,
        "korean" to 0xAC00..0xD7AF,
        "japanese" to 0x3040..0x30FF,
        "arabic" to 0x0600..0x06FF,
    )

    private val byGenre = listOf(
        "tamil" to "tamil",
        "telugu" to "telugu",
        "malayalam" to "malayalam",
        "kannada" to "kannada",
        "punjabi" to "punjabi",
        "bengali" to "bengali",
        "marathi" to "marathi",
        "bollywood" to "hindi",
        "filmi" to "hindi",
        "hindi" to "hindi",
        "k-pop" to "korean",
        "korean" to "korean",
        "j-pop" to "japanese",
        "japanese" to "japanese",
        "mandopop" to "chinese",
        "c-pop" to "chinese",
        "latin" to "spanish",
        "reggaeton" to "spanish",
        "spanish" to "spanish",
        "french" to "french",
        "german" to "german",
        "arabic" to "arabic",
        "turkish" to "turkish",
    )

    private val notEnglish = listOf("indian", "desi", "afro", "world", "k-", "j-", "anime")

    private val englishish = listOf(
        "pop", "rock", "hip-hop", "rap", "r&b", "soul", "country", "metal", "punk",
        "electronic", "dance", "house", "techno", "jazz", "blues", "folk", "indie",
        "alternative", "singer/songwriter",
    )

    val options = listOf(
        "english", "hindi", "tamil", "telugu", "malayalam", "kannada",
        "punjabi", "korean", "japanese", "spanish", "other",
    )

    private val seedTerms = mapOf(
        "english" to listOf("pop hits", "indie rock", "r&b hits", "alternative"),
        "hindi" to listOf("bollywood hits", "hindi songs"),
        "tamil" to listOf("tamil hits", "tamil songs"),
        "telugu" to listOf("telugu hits"),
        "malayalam" to listOf("malayalam hits"),
        "kannada" to listOf("kannada hits"),
        "punjabi" to listOf("punjabi hits"),
        "korean" to listOf("k-pop hits"),
        "japanese" to listOf("j-pop hits"),
        "spanish" to listOf("latin hits", "reggaeton"),
        "other" to listOf("world music"),
    )

    fun seeds(language: String): List<String> = seedTerms[language].orEmpty()

    fun of(title: String, genre: String?): String? {
        for ((name, range) in scripts) {
            if (title.any { it.code in range }) return name
        }
        val lower = genre?.lowercase().orEmpty()
        if (lower.isBlank()) return null
        for ((needle, name) in byGenre) {
            if (lower.contains(needle)) return name
        }
        if (notEnglish.any { lower.contains(it) }) return "other"
        if (englishish.any { lower.contains(it) }) return "english"
        return null
    }
}
