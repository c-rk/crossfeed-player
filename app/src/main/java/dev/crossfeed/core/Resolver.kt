package dev.crossfeed.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

data class Route(
    val platform: Platform,
    val url: String,
    val exact: Boolean,
)

data class Resolved(
    val sourceUrl: String,
    val source: Platform?,
    val meta: TrackMeta?,
    val routes: List<Route>,
    val local: LocalTrack? = null,
    val error: String? = null,
) {
    val primary: Route? get() = routes.firstOrNull()

    val decided: Boolean get() = error == null && (local != null || (routes.size == 1 && routes[0].exact))
}

object Resolver {

    suspend fun resolve(context: Context, rawUrl: String): Resolved = withContext(Dispatchers.IO) {
        val prefs = Prefs(context)
        val cache = Cache(context)
        val targets = prefs.targets
        val url = LinkParser.normalize(rawUrl)
        val source = LinkParser.platformOf(url)

        cache.get(url, targets)?.let { hit ->
            val local = hit.meta?.takeIf { prefs.preferLocal }?.let { LocalLibrary.find(context, it) }
            return@withContext hit.copy(local = local)
        }

        val meta = SourceMeta.read(url, source)
            ?: return@withContext Resolved(
                sourceUrl = url,
                source = source,
                meta = null,
                routes = emptyList(),
                error = "could not read that link",
            )

        val resolved = coroutineScope {
            val localJob = async {
                if (prefs.preferLocal) LocalLibrary.find(context, meta) else null
            }
            val routeJobs = targets.map { target ->
                async { locate(meta, url, source, target, prefs.country) }
            }
            Resolved(
                sourceUrl = url,
                source = source,
                meta = meta,
                routes = routeJobs.awaitAll(),
                local = localJob.await(),
            )
        }

        cache.put(resolved, targets)
        resolved
    }

    private fun locate(
        meta: TrackMeta,
        url: String,
        source: Platform?,
        target: Platform,
        country: String,
    ): Route = when {
        source == target -> Route(target, url, true)
        target == Platform.APPLE_MUSIC -> AppleCatalog.find(meta, country)
            ?.let { Route(target, it.url, true) }
            ?: Route(target, target.searchUrl(meta.query, country), false)

        else -> Route(target, target.searchUrl(meta.query, country), false)
    }
}
