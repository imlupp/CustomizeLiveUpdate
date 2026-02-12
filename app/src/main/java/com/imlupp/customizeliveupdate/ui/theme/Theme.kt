package com.imlupp.customizeliveupdate.ui.theme

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
import com.imlupp.customizeliveupdate.appPrimaryColor

// 自定义更清爽、专业的中性蓝灰色调（浅色模式）
private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0066CC),           // 主色：活力蓝
    primaryContainer = Color(0xFFE0F0FF),   // 浅蓝容器
    onPrimary = Color.White,
    secondary = Color(0xFF6B7280),          // 灰色辅助
    secondaryContainer = Color(0xFFE5E7EB),
    onSecondary = Color.White,
    tertiary = Color(0xFF10B981),           // 绿色点缀（成功/已取件用）
    background = Color(0xFFFAFAFA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF3F4F6),
    onSurface = Color(0xFF111827),
    outline = Color(0xFFD1D5DB)
)

// 深色模式（更暗、更护眼）
private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF60A5FA),            // 浅蓝（在深色下更醒目）
    primaryContainer = Color(0xFF1E40AF),
    onPrimary = Color.White,
    secondary = Color(0xFF9CA3AF),
    secondaryContainer = Color(0xFF374151),
    onSecondary = Color.White,
    tertiary = Color(0xFF34D399),
    background = Color(0xFF111827),
    surface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFF374151),
    onSurface = Color(0xFFE5E7EB),
    outline = Color(0xFF4B5563)
)

@Composable
fun CustomizeLiveUpdateTheme(
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
        darkTheme -> DarkColorScheme.copy(primary = appPrimaryColor)  // ← 这里用全局变量
        else -> LightColorScheme.copy(primary = appPrimaryColor)      // ← 这里用全局变量
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,  // 保持原样，或后面我们再优化字体
        content = content
    )
}