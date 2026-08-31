package dev.crossfeed.core.history

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object ArtStore {

    private fun dir(context: Context) = File(context.filesDir, "art").apply { mkdirs() }

    fun save(context: Context, key: String, bitmap: Bitmap): String? = runCatching {
        val stem = "${key.hashCode().toUInt()}"
        // sleeves saved before the move to webp are still perfectly good, so they are kept rather
        // than re-encoded, and simply age out when the sweep next runs
        val existing = File(dir(context), "$stem.jpg")
        if (existing.exists()) return@runCatching "file://${existing.absolutePath}"
        val file = File(dir(context), "$stem.webp")
        if (!file.exists()) {
            val scale = 320f / maxOf(bitmap.width, bitmap.height).coerceAtLeast(1)
            val scaled = if (scale < 1f) {
                Bitmap.createScaledBitmap(
                    bitmap,
                    (bitmap.width * scale).toInt().coerceAtLeast(1),
                    (bitmap.height * scale).toInt().coerceAtLeast(1),
                    true,
                )
            } else {
                bitmap
            }
            val format = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            file.outputStream().use { scaled.compress(format, 80, it) }
        }
        "file://${file.absolutePath}"
    }.getOrNull()

    /** Where the sleeves live, for anything that needs to read them as files. */
    fun folder(context: Context): File = dir(context)

    fun files(context: Context): List<File> = dir(context).listFiles()?.toList().orEmpty()

    /** The name a stored sleeve goes by, or nothing if this artwork is not one of ours. */
    fun nameOf(artwork: String?): String? {
        val path = artwork?.removePrefix("file://") ?: return null
        if (!path.contains("/art/")) return null
        return path.substringAfterLast('/').takeIf { it.isNotBlank() }
    }

    /** Puts a sleeve back on disk under the name it had, and says where it now is. */
    fun accept(context: Context, name: String, bytes: ByteArray): String? = runCatching {
        val clean = name.substringAfterLast('/').substringAfterLast('\\')
        if (clean.isBlank()) return@runCatching null
        val file = File(dir(context), clean)
        if (!file.exists()) file.outputStream().use { it.write(bytes) }
        "file://" + file.absolutePath
    }.getOrNull()

    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun sweep(context: Context, keep: Set<String>) {
        dir(context).listFiles()?.forEach { file ->
            if (keep.none { it.endsWith(file.name) }) file.delete()
        }
    }
}
