package dev.focusharbor.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val HarborColors = lightColorScheme(
    primary = Color(0xFF38634B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE1EBDD),
    onPrimaryContainer = Color(0xFF1F382A),
    secondary = Color(0xFF58634F),
    background = Color(0xFFF5F6F0),
    onBackground = Color(0xFF222C25),
    surface = Color(0xFFFCFCF7),
    onSurface = Color(0xFF222C25),
    surfaceVariant = Color(0xFFE7EADF),
    onSurfaceVariant = Color(0xFF555F53),
    outline = Color(0xFF747E72),
    outlineVariant = Color(0xFFD4DACC),
    surfaceContainerLow = Color(0xFFF0F2E9),
)

private val HarborTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 44.sp,
        lineHeight = 49.sp,
        letterSpacing = (-1).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.Serif,
        fontWeight = FontWeight.Normal,
        fontSize = 25.sp,
        lineHeight = 31.sp,
    ),
)

@Composable
fun FocusHarborTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HarborColors,
        typography = HarborTypography,
        content = content,
    )
}
