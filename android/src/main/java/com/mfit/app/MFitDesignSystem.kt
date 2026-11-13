package com.mfit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Fitness & Wellness reference palette by Abdur Rouf.
 * The palette is sampled from the confirmed Dribbble reference:
 * #E5E8E7, #0D1819, #324B4B, #A4BFB9, #536A6A,
 * #738B8A, #614361 and #342A46.
 */
internal object MFitPalette {
    val Ink = Color(0xFFE5E8E7)
    val InkSoft = Color(0xFFA4BFB9)
    val Lime = Color(0xFFA4BFB9)
    val LimeSoft = Color(0xFF324B4B)
    val Canvas = Color(0xFF0D1819)
    val Surface = Color(0xCC324B4B)
    val SurfaceAlt = Color(0xD9536A6A)
    val Muted = Color(0xFF738B8A)
    val Stroke = Color(0x66536A6A)
    val Positive = Color(0xFFA4BFB9)
    val Danger = Color(0xFFBB5632)
    val Movement = Color(0xFF614361)
    val Eating = Color(0xFF342A46)
    val Water = Color(0xFF738B8A)
}

/** Deep teal glass with the restrained, cool highlights of the source design. */
internal fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color(0xFF536A6A)
): Modifier = this
    .shadow(14.dp, shape, clip = false, ambientColor = Color(0x66000000), spotColor = Color(0x55000000))
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                Color(0xFF536A6A).copy(alpha = .48f),
                Color(0xFF324B4B).copy(alpha = .88f),
                Color(0xFF0D1819).copy(alpha = .96f)
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color(0xFFA4BFB9).copy(alpha = .38f), Color(0xFF738B8A).copy(alpha = .16f))
        ),
        shape
    )

@Composable
internal fun MFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = MFitPalette.Lime,
            onPrimary = MFitPalette.Canvas,
            secondary = Color(0xFF738B8A),
            onSecondary = MFitPalette.Canvas,
            background = MFitPalette.Canvas,
            onBackground = MFitPalette.Ink,
            surface = MFitPalette.Surface,
            onSurface = MFitPalette.Ink,
            surfaceVariant = MFitPalette.SurfaceAlt,
            onSurfaceVariant = MFitPalette.InkSoft,
            outline = Color(0xFF536A6A),
            error = MFitPalette.Danger
        ),
        content = content
    )
}
