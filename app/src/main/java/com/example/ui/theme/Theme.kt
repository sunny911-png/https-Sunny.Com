package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val StadiumDarkColorScheme = darkColorScheme(
    primary = PitchEmerald,
    onPrimary = StadiumSlate,
    primaryContainer = PitchDeepEmerald,
    onPrimaryContainer = TextPrimary,
    secondary = ChampionshipGold,
    onSecondary = StadiumSlate,
    secondaryContainer = ElevatedSlate,
    onSecondaryContainer = ChampionshipGold,
    tertiary = FloodlightLime,
    onTertiary = StadiumSlate,
    background = StadiumSlate,
    onBackground = TextPrimary,
    surface = BroadcastCharcoal,
    onSurface = TextPrimary,
    surfaceVariant = ElevatedSlate,
    onSurfaceVariant = TextSecondary,
    outline = SlateBorder,
    error = BroadcastCrimson,
    onError = TextPrimary
)

val StadiumShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

@Composable
fun WorldSoccerTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = StadiumDarkColorScheme,
        typography = WorldSoccerTypography,
        shapes = StadiumShapes,
        content = content
    )
}
