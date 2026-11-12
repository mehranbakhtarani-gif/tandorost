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

internal object MFitPalette {
    val Ink = Color(0xFF151B1A)
    val InkSoft = Color(0xFF26302D)
    val Lime = Color(0xFFD5F45B)
    val LimeSoft = Color(0xFFEAF3C8)
    val Canvas = Color(0xFFF3F5F0)
    val Surface = Color(0xFFFFFFFF)
    val SurfaceAlt = Color(0xFFE9EDE6)
    val Muted = Color(0xFF7B8580)
    val Stroke = Color(0xFFE4E9E1)
    val Positive = Color(0xFF3D8064)
    val Danger = Color(0xFFB94A43)
}

/**
 * A translucent, layered glass-like surface. Android Compose does not expose
 * a general backdrop blur for arbitrary siblings, so this uses translucency,
 * a soft gradient, a fine highlight stroke and a lifted edge.
 */
internal fun Modifier.glassSurface(
    shape: Shape = RoundedCornerShape(22.dp),
    tint: Color = Color.White
): Modifier = this
    .shadow(14.dp, shape, clip = false)
    .clip(shape)
    .background(
        Brush.linearGradient(
            listOf(
                tint.copy(alpha = .52f),
                Color.White.copy(alpha = .34f),
                tint.copy(alpha = .22f)
            )
        )
    )
    .border(
        1.dp,
        Brush.linearGradient(
            listOf(Color.White.copy(alpha = .86f), Color.White.copy(alpha = .18f))
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
            onSecondary = MFitPalette.Ink,
            background = MFitPalette.Canvas,
            onBackground = MFitPalette.Ink,
            surface = MFitPalette.Surface,
            onSurface = MFitPalette.Ink,
            surfaceVariant = MFitPalette.SurfaceAlt,
            onSurfaceVariant = MFitPalette.Muted,
            outline = MFitPalette.Stroke
        ),
        content = content
    )
}
