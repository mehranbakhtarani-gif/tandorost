package com.mfit.app

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Mint-glass visual language translated from the latest Tandorost HTML reference.
 * Keeps the native Compose components and their existing behavior intact.
 */
internal object MFitPalette {
    val Ink = Color(0xFF10201A)
    val InkSoft = Color(0xFF31483E)
    val Lime = Color(0xFF22A879)
    val LimeSoft = Color(0xFFD7F2E5)
    val Canvas = Color(0xFFE6F1ED)
    val Surface = Color(0xCCFFFFFF)
    val SurfaceAlt = Color(0xD9F4F8F5)
    val Muted = Color(0xFF52665E)
    val Stroke = Color(0xB3FFFFFF)
    val Positive = Color(0xFF22B07D)
    val Danger = Color(0xFFFF5D4A)
    val Movement = Color(0xFFFF5D4A)
    val Eating = Color(0xFFF4B032)
    val Water = Color(0xFF25B0EC)
}

/** Soft translucent surface with a bright glass edge and a restrained lift. */
internal fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color.White
): Modifier = this
    .shadow(12.dp, shape, clip = false)
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                tint.copy(alpha = .72f),
                Color.White.copy(alpha = .48f),
                tint.copy(alpha = .38f)
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = .96f), Color.White.copy(alpha = .38f))
        ),
        shape
    )

@Composable
internal fun MFitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = MFitPalette.Ink,
            onPrimary = Color.White,
            secondary = MFitPalette.Lime,
            onSecondary = Color.White,
            background = MFitPalette.Canvas,
            onBackground = MFitPalette.Ink,
            surface = MFitPalette.Surface,
            onSurface = MFitPalette.Ink,
            surfaceVariant = MFitPalette.SurfaceAlt,
            onSurfaceVariant = MFitPalette.Muted,
            outline = Color(0xFFB8CEC3)
        ),
        content = content
    )
}
