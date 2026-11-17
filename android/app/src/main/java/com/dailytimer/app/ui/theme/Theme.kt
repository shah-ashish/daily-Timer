package com.dailytimer.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = CoralPrimary,
    secondary = IndigoAccent,
    background = DarkSystemBackground,
    surface = DarkCardSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = DarkTextPrimary,
    onSurface = DarkTextPrimary,
    outline = DarkBorder
)

private val LightColorScheme = lightColorScheme(
    primary = CoralPrimary,
    secondary = IndigoAccent,
    background = LightSystemBackground,
    surface = LightCardSurface,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    onBackground = LightTextPrimary,
    onSurface = LightTextPrimary,
    outline = LightBorder
)

@Composable
fun DailyTimerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
