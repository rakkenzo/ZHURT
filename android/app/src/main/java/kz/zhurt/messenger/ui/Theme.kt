package kz.zhurt.messenger.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Gold = Color(0xFFE8C468)
private val Ink = Color(0xFF142016)
private val Cream = Color(0xFFF6F1E4)
private val Bg = Color(0xFF0D1712)

@Composable
fun ZhurtTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Gold,
            onPrimary = Ink,
            background = Bg,
            surface = Color(0xFF13261C),
            onBackground = Cream,
            onSurface = Cream
        ),
        content = content
    )
}
