package dev.crossfeed.core.history

/**
 * The moods a check-in offers, and the colour each one paints its day in the weather.
 *
 * None of them is sage, because sage means other people everywhere in the app, and none is a
 * service's brand colour, because the accent is busy being that. They stay on the phone: nothing
 * here is ever posted.
 */
object Moods {

    data class Mood(val name: String, val argb: Long)

    val all = listOf(
        Mood("happy", 0xFFF6C453),
        Mood("calm", 0xFF6FA8DC),
        Mood("tender", 0xFFE39AC2),
        Mood("hyped", 0xFFFF8A4C),
        Mood("sad", 0xFF7C7FC4),
        Mood("tired", 0xFF9C9285),
    )

    val names: Set<String> = all.map { it.name }.toSet()

    fun of(name: String?): Mood? = all.firstOrNull { it.name == name }
}
