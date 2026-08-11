package dev.crossfeed.core.history

import android.content.Context
import android.graphics.Bitmap
import java.io.File

object ArtStore {

    private fun dir(context: Context) = File(context.filesDir, "art").apply { mkdirs() }

    fun save(context: Context, key: String, bitmap: Bitmap): String? = runCatching {
        val file = File(dir(context), "${key.hashCode().toUInt()}.jpg")
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
            file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 82, it) }
        }
        "file://${file.absolutePath}"
    }.getOrNull()

    fun sizeBytes(context: Context): Long =
        dir(context).listFiles()?.sumOf { it.length() } ?: 0L

    fun sweep(context: Context, keep: Set<String>) {
        dir(context).listFiles()?.forEach { file ->
            if (keep.none { it.endsWith(file.name) }) file.delete()
        }
    }
}
