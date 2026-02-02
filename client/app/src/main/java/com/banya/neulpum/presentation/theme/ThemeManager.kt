package com.banya.neulpum.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ThemeManager {
    private var _currentTheme by mutableStateOf("auto")
    private var _currentFontSize by mutableStateOf("medium")
    private var _animationsEnabled by mutableStateOf(true)
    
    val currentTheme: String get() = _currentTheme
    val currentFontSize: String get() = _currentFontSize
    val animationsEnabled: Boolean get() = _animationsEnabled
    
    fun setTheme(theme: String) {
        _currentTheme = theme
    }
    
    fun setFontSize(size: String) {
        _currentFontSize = size
    }
    
    fun setAnimationsEnabled(enabled: Boolean) {
        _animationsEnabled = enabled
    }
    
    fun shouldUseDarkTheme(): Boolean {
        return when (_currentTheme) {
            "dark" -> true
            "light" -> false
            "auto" -> false // 시스템 테마 대신 기본값 사용
            else -> false
        }
    }
    
    fun getFontSize(): Float {
        return when (_currentFontSize) {
            "small" -> 12f
            "medium" -> 14f
            "large" -> 16f
            else -> 14f
        }
    }
    
    fun getLargeFontSize(): Float {
        return when (_currentFontSize) {
            "small" -> 16f
            "medium" -> 18f
            "large" -> 20f
            else -> 18f
        }
    }
    
    fun getTitleFontSize(): Float {
        return when (_currentFontSize) {
            "small" -> 18f
            "medium" -> 20f
            "large" -> 22f
            else -> 20f
        }
    }
    
    fun getHeaderFontSize(): Float {
        return when (_currentFontSize) {
            "small" -> 24f
            "medium" -> 26f
            "large" -> 28f
            else -> 26f
        }
    }
}

// 커스텀 색상 스키마 - 옅고 밝은 파랑 계열
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF64B5F6),  // 밝은 파랑
    secondary = Color(0xFF90CAF9),  // 옅은 파랑
    tertiary = Color(0xFFB3E5FC),  // 매우 옅은 파랑
    background = Color(0xFF1E1E1E),
    surface = Color(0xFF2D2D2D),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF64B5F6),  // 밝은 파랑 (Light Blue 300)
    secondary = Color(0xFF90CAF9),  // 옅은 파랑 (Light Blue 200)
    tertiary = Color(0xFFB3E5FC),  // 매우 옅은 파랑 (Light Blue 100)
    background = Color(0xFFE1F5FE),  // 매우 옅은 파랑 배경 (Light Blue 50)
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color(0xFF01579B),  // 진한 파랑 텍스트
    onTertiary = Color(0xFF01579B),  // 진한 파랑 텍스트
    onBackground = Color(0xFF01579B),  // 진한 파랑 텍스트
    onSurface = Color(0xFF01579B)  // 진한 파랑 텍스트
)

@Composable
fun Theme(
    content: @Composable () -> Unit
) {
    val colorScheme = if (ThemeManager.shouldUseDarkTheme()) {
        DarkColorScheme
    } else {
        LightColorScheme
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = PwoodaTypography,
        content = content
    )
}

val PwoodaTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ThemeManager.getFontSize().sp,
        lineHeight = (ThemeManager.getFontSize() + 6).sp,
        letterSpacing = 0.5.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = ThemeManager.getFontSize().sp,
        lineHeight = (ThemeManager.getFontSize() + 4).sp,
        letterSpacing = 0.25.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = ThemeManager.getTitleFontSize().sp,
        lineHeight = (ThemeManager.getTitleFontSize() + 8).sp,
        letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ThemeManager.getLargeFontSize().sp,
        lineHeight = (ThemeManager.getLargeFontSize() + 6).sp,
        letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ThemeManager.getFontSize().sp,
        lineHeight = (ThemeManager.getFontSize() + 4).sp,
        letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = ThemeManager.getFontSize().sp,
        lineHeight = (ThemeManager.getFontSize() + 4).sp,
        letterSpacing = 0.1.sp
    )
)
