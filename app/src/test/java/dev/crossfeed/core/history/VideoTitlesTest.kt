package dev.crossfeed.core.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VideoTitlesTest {

    @Test
    fun `splits the artist off the front`() {
        assertEquals("Creep" to "Radiohead", VideoTitles.guess("Radiohead - Creep", "RadioheadVEVO"))
    }

    @Test
    fun `drops the noise uploaders add`() {
        assertEquals(
            "Creep" to "Radiohead",
            VideoTitles.guess("Radiohead - Creep (Official Music Video) [4K]", "Radiohead"),
        )
    }

    @Test
    fun `a remaster is a pressing, not a different song`() {
        assertEquals(
            "Marquee Moon" to "Television",
            VideoTitles.guess("Television - Marquee Moon (Remastered)", "Television"),
        )
    }

    @Test
    fun `keeps brackets that belong to the song`() {
        assertEquals(
            "Wolves (feat. Johan Hasselblom)" to "Lights & Motion",
            VideoTitles.guess("Lights & Motion - Wolves (feat. Johan Hasselblom)", "Lights & Motion"),
        )
    }

    @Test
    fun `falls back to the channel when there is no dash`() {
        assertEquals("Weightless" to "Marconi Union", VideoTitles.guess("Weightless", "Marconi Union - Topic"))
    }

    @Test
    fun `cuts everything after a pipe`() {
        assertEquals(
            "Tum Hi Ho" to "Arijit Singh",
            VideoTitles.guess("Arijit Singh - Tum Hi Ho | Aashiqui 2 | Full Video Song", "T-Series"),
        )
    }

    @Test
    fun `a title that is only noise is not a song`() {
        assertNull(VideoTitles.guess("(Official Video)", null))
    }
}
