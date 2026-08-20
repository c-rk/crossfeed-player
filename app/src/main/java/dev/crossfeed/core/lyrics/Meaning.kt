package dev.crossfeed.core.lyrics

import android.util.Log
import com.google.android.gms.tasks.Task
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
    suspend fun forLines(songKey: String, lines: List<String>, target: String): List<String?>? {
        val to = TranslateLanguage.fromLanguageTag(target) ?: TranslateLanguage.ENGLISH
        val cacheKey = "$songKey|$to"
        done[cacheKey]?.let { return it }

        val sample = lines.filter { it.isNotBlank() }.take(24).joinToString("\n")
        if (sample.isBlank()) return null

        val detected = runCatching { identify(sample) }.getOrNull() ?: return null
        val from = TranslateLanguage.fromLanguageTag(detected) ?: return null
        if (from == to) return null

        val translator = Translation.getClient(
            TranslatorOptions.Builder().setSourceLanguage(from).setTargetLanguage(to).build(),
        )
        return try {
            translator.downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
            val out = lines.map { line ->
                if (line.isBlank()) null else runCatching { translator.translate(line).await() }.getOrNull()
            }
            done[cacheKey] = out
            out
        } catch (error: Exception) {
            Log.w("Meaning", "could not translate", error)
            null
        } finally {
            translator.close()
        }
    }

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
}
