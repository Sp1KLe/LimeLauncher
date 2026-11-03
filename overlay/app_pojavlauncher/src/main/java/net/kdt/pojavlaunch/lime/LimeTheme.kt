package net.kdt.pojavlaunch.lime

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

/** Фирменная «лаймовая» тёмная палитра Material 3. */
private val LimeDark = darkColorScheme(
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

/** Светлая палитра в тех же лаймовых тонах. */
private val LimeLight = lightColorScheme(
    primary = Color(0xFF356B00),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFB7F67F),
    onPrimaryContainer = Color(0xFF0A2100),
    secondary = Color(0xFF56624B),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD9E7CB),
    onSecondaryContainer = Color(0xFF131F0B),
    tertiary = Color(0xFF386665),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF9FAEF),
    onBackground = Color(0xFF1A1C16),
    surface = Color(0xFFF9FAEF),
    onSurface = Color(0xFF1A1C16),
    surfaceVariant = Color(0xFFE0E4D6),
    onSurfaceVariant = Color(0xFF44483D),
    surfaceContainer = Color(0xFFEEEFE4),
    surfaceContainerHigh = Color(0xFFE8E9DE),
    outline = Color(0xFF74796C),
    error = Color(0xFFBA1A1A),
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

/**
 * Тема LimeLauncher. Светлая или тёмная выбирается по настройке темы
 * (по умолчанию — как в системе); это учитывает AppCompatDelegate.
 */
@Composable
fun LimeTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    SideEffect {
        // Значки в строке состояния должны быть читаемы на светлом и тёмном фоне.
        val activity = context as? Activity
        if (activity != null) {
            WindowCompat.getInsetsController(activity.window, activity.window.decorView)
                .isAppearanceLightStatusBars = !dark
        }
    }
    MaterialTheme(
        colorScheme = if (dark) LimeDark else LimeLight,
        shapes = LimeShapes,
        typography = LimeTypography,
        content = content,
    )
}
