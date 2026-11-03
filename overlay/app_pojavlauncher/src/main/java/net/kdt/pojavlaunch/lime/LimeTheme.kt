package net.kdt.pojavlaunch.lime

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Фирменная «лаймовая» тёмная палитра Material 3. */
private val LimeColors = darkColorScheme(
    primary = Color(0xFFA6E36E),
    onPrimary = Color(0xFF173800),
    primaryContainer = Color(0xFF265000),
    onPrimaryContainer = Color(0xFFC1FF8F),
    secondary = Color(0xFFBCCBAB),
    onSecondary = Color(0xFF273420),
    secondaryContainer = Color(0xFF3D4B35),
    onSecondaryContainer = Color(0xFFD8E7C6),
    tertiary = Color(0xFFA0CFD0),
    onTertiary = Color(0xFF003738),
    background = Color(0xFF11140E),
    onBackground = Color(0xFFE2E3D8),
    surface = Color(0xFF11140E),
    onSurface = Color(0xFFE2E3D8),
    surfaceVariant = Color(0xFF43483C),
    onSurfaceVariant = Color(0xFFC3C8B8),
    surfaceContainer = Color(0xFF1E2219),
    surfaceContainerHigh = Color(0xFF282C23),
    outline = Color(0xFF8D9283),
    error = Color(0xFFFFB4AB),
)

private val LimeShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
)

private val LimeTypography = Typography(
    headlineSmall = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
)

@Composable
fun LimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LimeColors,
        shapes = LimeShapes,
        typography = LimeTypography,
        content = content,
    )
}
