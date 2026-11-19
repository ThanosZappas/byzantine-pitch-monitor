package app.byzantine.pitchmonitor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Colours taken from Byzantine icons: gold leaf, cinnabar red and lamp-lit dark wood. */
object Palette {
    val Gold = Color(0xFFD9AE4E)
    val GoldDim = Color(0xFF8A6D2E)
    val Cinnabar = Color(0xFFC0463A)
    val Rose = Color(0xFFE07A6A)
    val Ink = Color(0xFF120E0B)
    val Wood = Color(0xFF1C1612)
    val WoodLight = Color(0xFF2A211A)
    val Parchment = Color(0xFFEDE3D1)
    val Muted = Color(0xFF9A8D7A)
    val GridLine = Color(0xFF5A4E40)

    val OnPitch = Color(0xFF7FD48C)
    val NearPitch = Color(0xFFE8B84B)
    val OffPitch = Color(0xFFE86A5A)
}

private val scheme = darkColorScheme(
    primary = Palette.Gold,
    onPrimary = Palette.Ink,
    primaryContainer = Color(0xFF4A3A1A),
    onPrimaryContainer = Palette.Parchment,
    secondary = Palette.Cinnabar,
    onSecondary = Palette.Parchment,
    secondaryContainer = Color(0xFF4A1E18),
    onSecondaryContainer = Palette.Parchment,
    background = Palette.Ink,
    onBackground = Palette.Parchment,
    surface = Palette.Wood,
    onSurface = Palette.Parchment,
    surfaceVariant = Palette.WoodLight,
    onSurfaceVariant = Palette.Muted,
    surfaceContainer = Palette.Wood,
    surfaceContainerHigh = Palette.WoodLight,
    surfaceContainerLow = Palette.Wood,
    outline = Palette.GridLine,
)

@Composable
fun ByzantineTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
