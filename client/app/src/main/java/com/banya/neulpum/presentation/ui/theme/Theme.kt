package com.banya.neulpum.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),  // Green80
    secondary = Color(0xFF81C784),  // GreenGrey80
    tertiary = Color(0xFFA5D6A7),  // LightGreen80
    background = Color(0xFF1B5E20),  // 진한 녹색 배경
    surface = Color(0xFF2E7D32),  // 녹색 서피스
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF2E7D32),  // NHGreen
    secondary = Color(0xFF388E3C),  // GreenGrey40
    tertiary = Color(0xFF1B5E20),  // DarkGreen40
    background = Color(0xFFF1F8E9),  // 연한 녹색 배경
    surface = Color(0xFFFFFFFF),  // 흰색 서피스
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1B5E20),
    onSurface = Color(0xFF1B5E20),
)

@Composable
fun Theme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}