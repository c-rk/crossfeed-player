package dev.crossfeed.core.lyrics

import android.util.Log
import com.google.android.gms.tasks.Task
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import kotlin.coroutines.resume

/**
 * What a line actually says, in your own language.
 *
 * Everything runs on the phone: a language pack is fetched once and then translation works with
 * no network at all. Lyrics are never sent anywhere — the only request ever made is for the model
 * itself, and it carries nothing about the song.
 */
object Meaning {

    private val done = HashMap<String, List<String?>>()

    /** Null when there is nothing worth showing: same language, unsupported, or no model. */
    /**
     * Why there is nothing to show, when there is nothing to show. Every failure here used to be a
     * silent null, so tapping the switch simply did nothing and there was no way to tell whether
     * the language was unsupported, the pack had not arrived, or the song was already in your own
     * language.
     */
    var problem by mutableStateOf<String?>(null)
        private set

    suspend fun forLines(songKey: String, lines: List<String>, target: String): List<String?>? {
        problem = null
        val to = TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.ENGLISH
        val cacheKey = "$songKey|$to"
        done[cacheKey]?.let { return it }

        val sample = lines.filter { it.isNotBlank() }.take(24).joinToString("\n")
        if (sample.isBlank()) return null

        val detected = runCatching { identify(sample) }.getOrNull()
        if (detected == null) {
            problem = "could not tell what language this is"
            return null
        }
        val from = TranslateLanguage.fromLanguageTag(detected)
        if (from == null) {
            problem = "there is no translation for " + nameOf(detected)
            return null
        }
        if (from == to) {
            problem = "already in your language"
            return null
        }

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
        )
        return try {
            // a failed download used to look exactly like a successful one, so every line came back
            // empty and that emptiness was then remembered for good
            if (!translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).ok()) {
                problem = "could not fetch the " + nameOf(detected) + " pack"
                return null
            }
            val out = lines.map { line ->
                if (line.isBlank()) null else runCatching { translator.translate(line).await() }.getOrNull()
            }
            if (out.none { !it.isNullOrBlank() }) {
                problem = "could not translate this one"
                return null
            }
            done[cacheKey] = out
            out
        } catch (error: Exception) {
            Log.w("Meaning", "could not translate", error)
            problem = "could not translate this one"
            null
        } finally {
            translator.close()
        }
    }

    /**
     * Whether a song can be translated at all, known before anyone asks. Telling the language
     * apart runs on the phone and takes a moment, so the switch can say "no translation for
     * malayalam" up front instead of being tapped to find out.
     */
    sealed interface Verdict {
        data object Possible : Verdict
        data class Unsupported(val language: String) : Verdict
        data object Same : Verdict
    }

    private val verdicts = HashMap<String, Verdict>()

    suspend fun verdict(songKey: String, lines: List<String>, target: String): Verdict {
        verdicts[songKey]?.let { return it }
        val sample = lines.filter { it.isNotBlank() }.take(24).joinToString("\n")
        // when the language cannot be told, the switch stays offered: the attempt can still say why
        val detected = runCatching { identify(sample) }.getOrNull() ?: return Verdict.Possible
        val from = TranslateLanguage.fromLanguageTag(detected)
        val to = TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.ENGLISH
        val answer = when {
            from == null -> Verdict.Unsupported(nameOf(detected))
            from == to -> Verdict.Same
            else -> Verdict.Possible
        }
        verdicts[songKey] = answer
        return answer
    }

    private fun nameOf(tag: String): String =
        Locale(tag).displayLanguage.lowercase().ifBlank { tag }

    /** The whole song decides the language, because a single line is too little to go on. */
    private suspend fun identify(sample: String): String? {
        val client = LanguageIdentification.getClient()
        return try {
            client.identifyLanguage(sample).await()?.takeIf { it != "und" }
        } finally {
            client.close()
        }
    }


    /**
     * Languages worth keeping on the phone before they are needed. Each pack is around thirty
     * megabytes and every pair pivots through english, so english is always part of the cost.
     */
    val commonLanguages: List<Pair<String, String>> = listOf(
        TranslateLanguage.ENGLISH to "english",
        TranslateLanguage.TAMIL to "tamil",
        TranslateLanguage.HINDI to "hindi",
        TranslateLanguage.KANNADA to "kannada",
        TranslateLanguage.TELUGU to "telugu",
        TranslateLanguage.BENGALI to "bengali",
        TranslateLanguage.MARATHI to "marathi",
        TranslateLanguage.URDU to "urdu",
        TranslateLanguage.SPANISH to "spanish",
        TranslateLanguage.FRENCH to "french",
        TranslateLanguage.GERMAN to "german",
        TranslateLanguage.PORTUGUESE to "portuguese",
        TranslateLanguage.KOREAN to "korean",
        TranslateLanguage.JAPANESE to "japanese",
        TranslateLanguage.CHINESE to "chinese",
        TranslateLanguage.ARABIC to "arabic",
        TranslateLanguage.RUSSIAN to "russian",
    )

    private val models = RemoteModelManager.getInstance()

    private fun modelOf(tag: String) = TranslateRemoteModel.Builder(tag).build()

    /** Which packs are already on the phone, so the list can say rather than guess. */
    suspend fun kept(): Set<String> =
        models.getDownloadedModels(TranslateRemoteModel::class.java).await()
            ?.map { it.language }?.toSet().orEmpty()

    /** Fetched over wi-fi only: thirty megabytes is not something to spend someone's data on. */
    suspend fun fetch(tag: String): Boolean {
        val conditions = DownloadConditions.Builder().requireWifi().build()
        return models.download(modelOf(tag), conditions).await() != null ||
            kept().contains(tag)
    }

    suspend fun drop(tag: String): Boolean = models.deleteDownloadedModel(modelOf(tag)).await() != null

    fun deviceLanguage(): String = Locale.getDefault().language.ifBlank { "en" }

    private suspend fun <T> Task<T>.await(): T? = suspendCancellableCoroutine { slot ->
        addOnSuccessListener { slot.resume(it) }
        addOnFailureListener { slot.resume(null) }
        addOnCanceledListener { slot.resume(null) }
    }

    /** For tasks that carry no value: null means nothing, so completion is asked about instead. */
    private suspend fun Task<Void>.ok(): Boolean = suspendCancellableCoroutine { slot ->
        addOnSuccessListener { slot.resume(true) }
        addOnFailureListener { slot.resume(false) }
        addOnCanceledListener { slot.resume(false) }
    }
}
