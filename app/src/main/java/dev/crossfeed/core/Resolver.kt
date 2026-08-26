package dev.crossfeed.core

import android.content.Context
import dev.crossfeed.core.catalog.Catalog
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
                async { locate(context, meta, url, source, target) }
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

    /**
     * A link already pointing at the service it was asked about needs no lookup, and no lookup
     * could improve on it. Everything else is asked of the catalogues.
     */
    private fun locate(
        context: Context,
        meta: TrackMeta,
        url: String,
        source: Platform?,
        target: Platform,
    ): Route {
        if (source == target) return Route(target, url, true)
        val address = Catalog.address(context, target, meta)
        return Route(target, address.url, address.exact)
    }
}
