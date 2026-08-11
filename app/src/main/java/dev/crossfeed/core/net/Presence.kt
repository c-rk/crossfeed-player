package dev.crossfeed.core.net

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.crossfeed.core.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

object Presence {

    const val PERMISSION = Manifest.permission.ACCESS_COARSE_LOCATION

    fun allowed(context: Context) =
        ContextCompat.checkSelfPermission(context, PERMISSION) == PackageManager.PERMISSION_GRANTED

    suspend fun push(context: Context): Boolean {
        if (!Account(context).exists) return false
        val broadcast = Prefs(context).broadcast
        val location = if (allowed(context)) locate(context) else null
        val body = JSONObject().put("broadcast", broadcast)
        location?.let { body.put("lat", it.latitude).put("lon", it.longitude) }
        return withContext(Dispatchers.IO) {
            runCatching { Api.post(context, "/v1/me/presence", body) }.isSuccess
        }
    }

    suspend fun locate(context: Context): Location? {
        if (!allowed(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        cached(manager)?.let { if (System.currentTimeMillis() - it.time < FRESH_MS) return it }
        return withTimeoutOrNull(TIMEOUT_MS) { fix(manager) } ?: cached(manager)
    }

    private fun cached(manager: LocationManager): Location? = PROVIDERS
        .mapNotNull { provider -> runCatching { manager.getLastKnownLocation(provider) }.getOrNull() }
        .maxByOrNull { it.time }

    private suspend fun fix(manager: LocationManager): Location? = suspendCancellableCoroutine { cont ->
        val provider = PROVIDERS.firstOrNull { runCatching { manager.isProviderEnabled(it) }.getOrDefault(false) }
        if (provider == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                cont.invokeOnCancellation { signal.cancel() }
                manager.getCurrentLocation(
                    provider,
                    signal,
                    { runnable -> runnable.run() },
                ) { location -> if (cont.isActive) cont.resume(location) }
            } else {
                @Suppress("DEPRECATION")
                manager.requestSingleUpdate(
                    provider,
                    { location -> if (cont.isActive) cont.resume(location) },
                    null,
                )
            }
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private val PROVIDERS = listOf(
        LocationManager.NETWORK_PROVIDER,
        LocationManager.GPS_PROVIDER,
        LocationManager.PASSIVE_PROVIDER,
    )

    private const val FRESH_MS = 5 * 60_000L
    private const val TIMEOUT_MS = 8_000L
}
