package dev.crossfeed.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.crossfeed.R

@OptIn(ExperimentalTextApi::class)
private fun geist(weight: Int) = Font(
    resId = R.font.geist_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Grotesk = FontFamily(geist(300), geist(400), geist(500), geist(600), geist(700))
val Handwritten = FontFamily(Font(R.font.permanent_marker, FontWeight.Normal))

@OptIn(ExperimentalTextApi::class)
private fun caveat(weight: Int) = Font(
    resId = R.font.caveat_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Script = FontFamily(caveat(500), caveat(600), caveat(700))

object Type {
    val wordmark = TextStyle(
        fontFamily = Handwritten,
        fontSize = 44.sp,
        lineHeight = 52.sp,
        letterSpacing = 0.5.sp,
    )
    val figure = TextStyle(
        fontFamily = Script,
        fontWeight = FontWeight.SemiBold,
        fontSize = 40.sp,
        lineHeight = 42.sp,
    )
    val hero = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 34.sp,
        lineHeight = 40.sp,
        letterSpacing = (-0.8).sp,
    )
    val title = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.4).sp,
    )
    val headline = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.2).sp,
    )
    val body = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.1).sp,
    )
    val callout = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 19.sp,
    )
    val footnote = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.Normal,
        fontSize = 12.5.sp,
        lineHeight = 16.sp,
    )
    val blockTitle = TextStyle(
        fontFamily = Handwritten,
        fontSize = 19.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.4.sp,
    )
    val caps = TextStyle(
        fontFamily = Grotesk,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.8.sp,
    )
}

val CrossfeedTypography = Typography(
    displayLarge = Type.hero,
    headlineMedium = Type.title,
    titleMedium = Type.headline,
    bodyMedium = Type.body,
    labelSmall = Type.footnote,
)
