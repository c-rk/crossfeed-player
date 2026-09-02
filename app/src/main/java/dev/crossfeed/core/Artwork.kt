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
        cache.get(url)?.let { return@withContext it }
        // three places to look, cheapest first: memory, then this phone's disk, then the network
        if (url.startsWith("https://")) {
            kept(context, url)?.let { held ->
                cache.put(url, held)
                return@withContext held
            }
        }
        val loaded = load(context, url) ?: return@withContext null
        cache.put(url, loaded)
        if (url.startsWith("https://")) keep(context, url, loaded)
        loaded
    }

    /**
     * Sleeves fetched from the net, kept on the phone.
     *
     * A feed of forty records is forty fetches every time the app is opened, for pictures that
     * never change. They are small once they are webp, they belong to rows that will be scrolled
     * past again tomorrow, and the alternative is a screen of letters while the network catches
     * up. The folder is swept when it gets big rather than never.
     */
    private fun shelf(context: Context) = java.io.File(context.cacheDir, "sleeves").apply { mkdirs() }

    private fun nameOf(url: String) = url.hashCode().toUInt().toString() + ".webp"

    private fun kept(context: Context, url: String): Bitmap? = runCatching {
        val file = java.io.File(shelf(context), nameOf(url))
        if (!file.exists()) return null
        file.setLastModified(System.currentTimeMillis())
        BitmapFactory.decodeFile(file.absolutePath)
    }.getOrNull()

    private fun keep(context: Context, url: String, bitmap: Bitmap) {
        runCatching {
            val folder = shelf(context)
            val file = java.io.File(folder, nameOf(url))
            if (file.exists()) return
            val format = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Bitmap.CompressFormat.WEBP_LOSSY
            } else {
                @Suppress("DEPRECATION")
                Bitmap.CompressFormat.WEBP
            }
            file.outputStream().use { bitmap.compress(format, 80, it) }
            sweep(folder)
        }
    }

    /** Keeps the shelf under a sensible size, oldest touched first out. */
    private fun sweep(folder: java.io.File) {
        val files = folder.listFiles() ?: return
        var total = files.sumOf { it.length() }
        if (total <= SHELF_BYTES) return
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= SHELF_BYTES) break
            total -= file.length()
            file.delete()
        }
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

    /**
     * Fetches a sleeve, and tries again before giving up.
     *
     * A feed asks for a dozen of these at once over whatever signal is going, and a single timeout
     * used to mean a letter where a record should be, for as long as that row stayed on screen.
     * The timeouts are longer than they were and one stumble is forgiven, because the cost of
     * asking twice is a second and the cost of not asking is a blank square.
     */
    private fun fromNetwork(url: String, tries: Int = 2): Bitmap? {
        for (attempt in 1..tries) {
            fetch(url)?.let { return it }
            if (attempt < tries) Thread.sleep(400)
        }
        return null
    }

    private fun fetch(url: String): Bitmap? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 8000
                readTimeout = 12000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "crossfeed")
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
    private const val SHELF_BYTES = 24L * 1024 * 1024
}
