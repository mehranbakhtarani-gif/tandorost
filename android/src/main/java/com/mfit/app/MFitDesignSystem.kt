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

internal object MFitPalette {
    val Ink = Color(0xFFF2F5F1)
    val InkSoft = Color(0xFFCAD3CC)
    val Lime = Color(0xFFD5F45B)
    val LimeSoft = Color(0xFF354326)
    val Canvas = Color(0xFF090D0B)
    val Surface = Color(0xFF141B17)
    val SurfaceAlt = Color(0xFF202923)
    val Muted = Color(0xFF9AA69E)
    val Stroke = Color(0xFF344139)
    val Positive = Color(0xFF8FC9A7)
    val Danger = Color(0xFFFF8278)
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
        colorScheme = darkColorScheme(
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
