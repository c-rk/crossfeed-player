package dev.crossfeed.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.LruCache
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object Artwork {

    private val cache = object : LruCache<String, Bitmap>(12 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    suspend fun load(context: Context, item: LibraryItem): Bitmap? = withContext(Dispatchers.IO) {
        val key = item.local?.uri?.toString() ?: item.artwork ?: return@withContext null
        cache.get(key)?.let { return@withContext it }
        val bitmap = item.local?.let { fromMediaStore(context, it) } ?: item.artwork?.let { load(context, it) }
        bitmap?.also { cache.put(key, it) }
    }

    private fun fromMediaStore(context: Context, track: LocalTrack): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return runCatching {
            context.contentResolver.loadThumbnail(track.uri, Size(256, 256), null)
        }.getOrNull()
    }

    suspend fun loadUrl(context: Context, url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url) ?: load(context, url)?.also { cache.put(url, it) }
    }

    suspend fun loadRemote(url: String): Bitmap? = withContext(Dispatchers.IO) {
        if (!url.startsWith("https://")) return@withContext null
        cache.get(url) ?: fromNetwork(url)?.also { cache.put(url, it) }
    }

    private fun load(context: Context, url: String): Bitmap? =
        if (url.startsWith("content://") || url.startsWith("file://")) {
            thumbnail(context, url) ?: runCatching {
                context.contentResolver.openInputStream(android.net.Uri.parse(url))
                    .use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
        } else {
            fromNetwork(url)
        }

    private fun thumbnail(context: Context, url: String): Bitmap? {
        val uri = android.net.Uri.parse(url)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }
                .getOrNull()
                ?.let { return it }
        }
        return runCatching {
            val reader = android.media.MediaMetadataRetriever()
            reader.use {
                it.setDataSource(context, uri)
                it.embeddedPicture?.let { bytes -> BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            }
        }.getOrNull()
    }

    private fun fromNetwork(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 5000
            }
            if (conn.responseCode !in 200..299) return null
            if (conn.contentLength > MAX_ART_BYTES) return null
            conn.inputStream.use { stream ->
                val bytes = stream.readBytes(MAX_ART_BYTES) ?: return null
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }
        } catch (_: Exception) {
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun java.io.InputStream.readBytes(limit: Int): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        while (true) {
            val read = read(buffer)
            if (read < 0) break
            out.write(buffer, 0, read)
            if (out.size() > limit) return null
        }
        return out.toByteArray()
    }

    private const val MAX_ART_BYTES = 3 * 1024 * 1024
}
