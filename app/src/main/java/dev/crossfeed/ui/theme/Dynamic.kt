package dev.crossfeed.ui.theme

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class Daypart { DAWN, DAY, DUSK, NIGHT }

data class Mood(
    val hot: List<Color>,
    val cool: List<Color>,
    val wash: List<Color>,
) {
    val accent: Color get() = hot.first()
}

private val moods = mapOf(
    Daypart.DAWN to Mood(
        hot = listOf(Color(0xFFFF7A5C), Color(0xFFFFC24B)),
        cool = listOf(Color(0xFF5BE3C0), Color(0xFF3FA9FF)),
        wash = listOf(Color(0xFF17131A), Color(0xFF0E0D12), Color(0xFF060608)),
    ),
    Daypart.DAY to Mood(
        hot = listOf(Color(0xFF00C2FF), Color(0xFF00E5A8)),
        cool = listOf(Color(0xFF7CE04D), Color(0xFF00C2FF)),
        wash = listOf(Color(0xFF12171A), Color(0xFF0C1014), Color(0xFF050607)),
    ),
    Daypart.DUSK to Mood(
        hot = listOf(Color(0xFFFF4D6D), Color(0xFFFF9F1C)),
        cool = listOf(Color(0xFF00D0C0), Color(0xFFFF4D9E)),
        wash = listOf(Color(0xFF1A1316), Color(0xFF100D12), Color(0xFF060507)),
    ),
    Daypart.NIGHT to Mood(
        hot = listOf(Color(0xFF2ED9C3), Color(0xFF4D7CFF)),
        cool = listOf(Color(0xFF00E0FF), Color(0xFF3DE8A8)),
        wash = listOf(Color(0xFF101519), Color(0xFF0A0D12), Color(0xFF040406)),
    ),
)

fun daypartAt(hour: Int): Daypart = when (hour) {
    in 5..9 -> Daypart.DAWN
    in 10..16 -> Daypart.DAY
    in 17..20 -> Daypart.DUSK
    else -> Daypart.NIGHT
}

fun currentDaypart(): Daypart = daypartAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

fun moodFor(daypart: Daypart, seed: Color?): Mood {
    val base = moods.getValue(daypart)
    if (seed == null) return base
    return base.copy(
        hot = listOf(seed, blend(seed, base.hot[1], 0.62f)),
        cool = listOf(blend(base.cool[0], seed, 0.25f), base.cool[1]),
        wash = listOf(blend(base.wash[0], seed, 0.30f), blend(base.wash[1], seed, 0.10f), base.wash[2]),
    )
}

private fun blend(a: Color, b: Color, amount: Float) = Color(
    red = a.red + (b.red - a.red) * amount,
    green = a.green + (b.green - a.green) * amount,
    blue = a.blue + (b.blue - a.blue) * amount,
    alpha = 1f,
)

fun appAccent(context: Context, pkg: String): Color? {
    val bitmap = runCatching {
        context.packageManager.getApplicationIcon(pkg).toBitmap(48, 48)
    }.getOrNull() ?: return null
    return dominantColor(bitmap)
}

private fun dominantColor(bitmap: Bitmap): Color? {
    val buckets = HashMap<Int, FloatArray>()
    for (x in 0 until bitmap.width step 2) {
        for (y in 0 until bitmap.height step 2) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = (pixel ushr 24) and 0xFF
            if (alpha < 200) continue
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f
            val maxC = max(r, max(g, b))
            val minC = min(r, min(g, b))
            val saturation = if (maxC == 0f) 0f else (maxC - minC) / maxC
            if (saturation < 0.35f || maxC < 0.25f) continue
            val key = (((r * 7).toInt() shl 8) or ((g * 7).toInt() shl 4) or (b * 7).toInt())
            val slot = buckets.getOrPut(key) { FloatArray(4) }
            val weight = saturation * maxC
            slot[0] += r * weight
            slot[1] += g * weight
            slot[2] += b * weight
            slot[3] += weight
        }
    }
    val best = buckets.values.maxByOrNull { it[3] } ?: return null
    if (best[3] <= 0f) return null
    return lift(Color(best[0] / best[3], best[1] / best[3], best[2] / best[3]))
}

private fun lift(color: Color): Color {
    val maxC = max(color.red, max(color.green, color.blue))
    if (maxC >= 0.72f) return color
    val gain = 0.82f / maxC.coerceAtLeast(0.05f)
    return Color(
        red = (color.red * gain).coerceAtMost(1f),
        green = (color.green * gain).coerceAtMost(1f),
        blue = (color.blue * gain).coerceAtMost(1f),
    )
}

fun Color.distanceTo(other: Color): Float =
    abs(red - other.red) + abs(green - other.green) + abs(blue - other.blue)
