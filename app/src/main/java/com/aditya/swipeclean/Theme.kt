package com.aditya.swipeclean

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

val PaletteNames = listOf("Dynamic", "Forest", "Ocean", "Sunset", "Berry", "Teal")
val PaletteSeeds: List<Color?> = listOf(
    null, Color(0xFF2E7D32), Color(0xFF0B6BCB), Color(0xFFEF6C00), Color(0xFFAD1457), Color(0xFF00897B)
)

private fun hueOf(c: Color): Float { val hsv = FloatArray(3); android.graphics.Color.colorToHSV(c.toArgb(), hsv); return hsv[0] }
private fun hsl(h: Float, s: Float, l: Float) = Color.hsl(((h % 360f) + 360f) % 360f, s, l)

fun schemeFromSeed(seed: Color, dark: Boolean): ColorScheme {
    val h = hueOf(seed); val t = h + 60f
    return if (!dark) lightColorScheme(
        primary = hsl(h, .6f, .36f), onPrimary = Color.White,
        primaryContainer = hsl(h, .7f, .88f), onPrimaryContainer = hsl(h, .8f, .10f),
        secondary = hsl(h, .25f, .40f), onSecondary = Color.White,
        secondaryContainer = hsl(h, .30f, .88f), onSecondaryContainer = hsl(h, .4f, .12f),
        tertiary = hsl(t, .45f, .40f), onTertiary = Color.White,
        tertiaryContainer = hsl(t, .55f, .88f), onTertiaryContainer = hsl(t, .6f, .12f),
        background = hsl(h, .25f, .98f), onBackground = hsl(h, .2f, .10f),
        surface = hsl(h, .25f, .98f), onSurface = hsl(h, .2f, .10f),
        surfaceVariant = hsl(h, .2f, .90f), onSurfaceVariant = hsl(h, .1f, .30f),
        outline = hsl(h, .1f, .50f),
        surfaceContainerLowest = Color.White, surfaceContainerLow = hsl(h, .25f, .96f),
        surfaceContainer = hsl(h, .22f, .94f), surfaceContainerHigh = hsl(h, .2f, .92f),
        surfaceContainerHighest = hsl(h, .2f, .90f)
    ) else darkColorScheme(
        primary = hsl(h, .7f, .75f), onPrimary = hsl(h, .8f, .15f),
        primaryContainer = hsl(h, .55f, .25f), onPrimaryContainer = hsl(h, .7f, .90f),
        secondary = hsl(h, .35f, .75f), onSecondary = hsl(h, .4f, .15f),
        secondaryContainer = hsl(h, .25f, .28f), onSecondaryContainer = hsl(h, .4f, .90f),
        tertiary = hsl(t, .55f, .75f), onTertiary = hsl(t, .6f, .15f),
        tertiaryContainer = hsl(t, .40f, .28f), onTertiaryContainer = hsl(t, .6f, .90f),
        background = hsl(h, .15f, .07f), onBackground = hsl(h, .1f, .90f),
        surface = hsl(h, .15f, .07f), onSurface = hsl(h, .1f, .90f),
        surfaceVariant = hsl(h, .10f, .22f), onSurfaceVariant = hsl(h, .1f, .78f),
        outline = hsl(h, .08f, .55f),
        surfaceContainerLowest = hsl(h, .15f, .05f), surfaceContainerLow = hsl(h, .15f, .10f),
        surfaceContainer = hsl(h, .15f, .12f), surfaceContainerHigh = hsl(h, .14f, .16f),
        surfaceContainerHighest = hsl(h, .13f, .20f)
    )
}

@Composable
fun SwipeCleanTheme(mode: Int, palette: Int, content: @Composable () -> Unit) {
    val dark = when (mode) { 1 -> false; 2 -> true; else -> isSystemInDarkTheme() }
    val ctx = LocalContext.current
    val seed = PaletteSeeds.getOrNull(palette)
    val scheme = if (seed == null && Build.VERSION.SDK_INT >= 31) {
        if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
    } else schemeFromSeed(seed ?: PaletteSeeds[1]!!, dark)
    val view = LocalView.current
    SideEffect {
        val w = (view.context as Activity).window
        val c = WindowCompat.getInsetsController(w, view)
        c.isAppearanceLightStatusBars = !dark
        c.isAppearanceLightNavigationBars = !dark
    }
    MaterialTheme(
        colorScheme = scheme,
        shapes = Shapes(
            small = RoundedCornerShape(12.dp), medium = RoundedCornerShape(20.dp),
            large = RoundedCornerShape(28.dp), extraLarge = RoundedCornerShape(36.dp)
        ),
        content = content
    )
}
