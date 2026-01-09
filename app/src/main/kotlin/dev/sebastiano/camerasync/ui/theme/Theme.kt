package dev.sebastiano.camerasync.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val DarkColorScheme =
    darkColorScheme(
        primary = DarkSunnyYellow,
        onPrimary = Color.Black,
        primaryContainer = DarkSunnyYellowContainer,
        onPrimaryContainer = Color.Black,
        secondary = DarkElectricBlue,
        onSecondary = Color.White,
        tertiary = DarkPunchyPink,
        onTertiary = Color.White,
        background = Color(0xFF121212),
        onBackground = Color(0xFFE6E1E5),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE6E1E5),
        surfaceVariant = Color(0xFF49454F),
        onSurfaceVariant = Color(0xFFCAC4D0),
        errorContainer = Color(0xFF93000A),
        onErrorContainer = Color(0xFFFFDAD6),
    )

private val LightColorScheme =
    lightColorScheme(
        primary = Color(0xFF755B00), // Darker yellow/gold for text and key elements (contrast safe)
        onPrimary = Color.White,
        primaryContainer = SunnyYellow, // Vibrant yellow for large containers
        onPrimaryContainer = Color.Black,
        secondary = ElectricBlue,
        onSecondary = Color.White,
        tertiary = PunchyPink,
        onTertiary = Color.White,
        background = Color(0xFFFFFBFE),
        onBackground = Color(0xFF1C1B1F),
        surface = Color(0xFFFFFBFE),
        onSurface = Color(0xFF1C1B1F),
        surfaceVariant = Color(0xFFE7E0EB),
        onSurfaceVariant = Color(0xFF49454F),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
    )

// Chonky shapes for Expressive UI
val ChonkyShapes =
    Shapes(
        extraSmall = RoundedCornerShape(8.dp),
        small = RoundedCornerShape(12.dp),
        medium = RoundedCornerShape(20.dp),
        large = RoundedCornerShape(28.dp),
        extraLarge = RoundedCornerShape(36.dp),
    )

@Composable
fun CameraSyncTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Disabled by default for expressive branding
    content: @Composable () -> Unit,
) {
    val colorScheme =
        when {
            dynamicColor -> {
                val context = LocalContext.current
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = ChonkyShapes,
        content = content,
    )
}
