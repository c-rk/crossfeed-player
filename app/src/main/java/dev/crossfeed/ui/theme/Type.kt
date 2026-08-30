package dev.crossfeed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.crossfeed.R

/**
 * Four faces, each with one job.
 *
 * The headings keep crossfeed's own hand, which is the app's face and not something a redesign
 * gets to take away, and the figures keep the hand the app has always counted in. Figtree is
 * everything a finger touches or an eye skims, which is where the new design earns its keep. Lora
 * appears in one place in the whole app, the lyrics, and its being a serif is the point: a song
 * read as a poem rather than as an interface.
 */

@OptIn(ExperimentalTextApi::class)
private fun figtree(weight: Int) = Font(
    resId = R.font.figtree_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun lora(weight: Int, italic: Boolean = false) = Font(
    resId = if (italic) R.font.lora_italic_variable else R.font.lora_variable,
    weight = FontWeight(weight),
    style = if (italic) FontStyle.Italic else FontStyle.Normal,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

@OptIn(ExperimentalTextApi::class)
private fun caveat(weight: Int) = Font(
    resId = R.font.caveat_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Crossfeed's own hand. The redesign moved the headings to a serif; they stay here instead. */
val Display = FontFamily(Font(R.font.permanent_marker, FontWeight.Normal))

/** And its own hand for figures, which is what the app has always counted in. */
val Figures = FontFamily(caveat(500), caveat(600), caveat(700))
val Grotesk = FontFamily(figtree(400), figtree(500), figtree(600), figtree(700), figtree(800))
val Reading = FontFamily(lora(400), lora(500), lora(600), lora(400, italic = true), lora(500, italic = true))

/** Figures line up under each other, so a column of numbers reads as a column. */
private const val TABULAR = "tnum"

object Type {

    // the redesign's own scale. sizes are the design's px, which are dp on a 360 wide screen

    /** A page's name. One per screen, top left. */
    val page = TextStyle(
        fontFamily = Display,
        fontSize = 25.sp,
        lineHeight = 30.sp,
        letterSpacing = 0.4.sp,
    )

    /** The one big number on the listening page. */
    val heroFigure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.Bold,
        fontSize = 58.sp,
        lineHeight = 54.sp,
        fontFeatureSettings = TABULAR,
    )

    /** The three smaller numbers under it, and anything else counted. */
    val statFigure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 28.sp,
        fontFeatureSettings = TABULAR,
    )

    val bigFigure = TextStyle(
        fontFamily = Figures,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 30.sp,
        fontFeatureSettings = TABULAR,
    )

    /** A heading inside a page. */
    val section = TextStyle(
        fontFamily = Display,
        fontSize = 16.sp,
        lineHeight = 19.sp,
        letterSpacing = 0.3.sp,
    )

    val sectionSmall = TextStyle(
        fontFamily = Display,
        fontSize = 14.sp,
        lineHeight = 17.sp,
        letterSpacing = 0.25.sp,
    )

    val sectionLarge = TextStyle(
        fontFamily = Display,
        fontSize = 18.sp,
        lineHeight = 21.sp,
        letterSpacing = 0.3.sp,
    )

    /** A row's name. */
    val rowTitle = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 13.sp,
        lineHeight = 16.sp,
    )

    val rowTitleLarge = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 13.5.sp,
        lineHeight = 17.sp,
    )

    val cardTitle = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    )

    /**
     * The smallest tier anything is allowed to be. The design was revised once for legibility and
     * this is where it landed; nothing goes under it and nothing goes fainter than t3.
     */
    val meta = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )

    val metaStrong = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 9.5.sp,
        lineHeight = 13.sp,
    )

    val stamp = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        fontFeatureSettings = TABULAR,
    )

    val chip = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp,
        lineHeight = 15.sp,
    )

    val label = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
    )

    val note = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )

    val description = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 11.5.sp,
        lineHeight = 16.sp,
    )

    /** All caps, tracked wide, tiny. Used for the labels that name a figure. */
    val tag = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 10.5.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.5.sp,
    )

    val tagSmall = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 9.5.sp,
        lineHeight = 12.sp,
        letterSpacing = 1.2.sp,
    )

    val tagWide = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 1.6.sp,
    )

    // lyrics, the only serif in the app

    val lyricActive = TextStyle(
        fontFamily = Reading,
        fontWeight = FontWeight.Medium,
        fontSize = 25.sp,
        lineHeight = 32.sp,
    )

    val lyricNear = TextStyle(
        fontFamily = Reading,
        fontWeight = FontWeight.Normal,
        fontSize = 17.sp,
        lineHeight = 23.sp,
    )

    val lyricMeaning = TextStyle(
        fontFamily = Reading,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    val lyricMeaningNear = TextStyle(
        fontFamily = Reading,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    val quiet = TextStyle(
        fontFamily = Reading,
        fontWeight = FontWeight.Normal,
        fontStyle = FontStyle.Italic,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    // the names the rest of the app already calls things, pointed at the new faces so every
    // screen that has not been rebuilt yet still reads as one app

    val wordmark = page.copy(fontSize = 26.sp, lineHeight = 32.sp)
    val blockTitle = section
    val figure = statFigure.copy(fontSize = 34.sp, lineHeight = 36.sp)
    val hero = sectionLarge.copy(fontSize = 28.sp, lineHeight = 34.sp)
    val title = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 17.sp,
        lineHeight = 22.sp,
    )
    val headline = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    )
    val body = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
    val callout = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 12.5.sp,
        lineHeight = 17.sp,
    )
    val footnote = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 15.sp,
    )
    val caps = tag
}

val CrossfeedTypography = Typography(
    displayLarge = Type.hero,
    headlineMedium = Type.title,
    titleMedium = Type.headline,
    bodyMedium = Type.body,
    labelSmall = Type.footnote,
)
