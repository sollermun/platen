package com.sparklaw.platen

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy          = Color(0xFF0D3B5F)
private val NavyDark      = Color(0xFF082940)
private val NavyContainer = Color(0xFF1C5C8F)
private val NavyLight     = Color(0xFF6DB8E8)
private val NavyPale      = Color(0xFFCDE4F5)

private val SecondaryBlue          = Color(0xFF2E6DA4)
private val SecondaryContainer     = Color(0xFFD0E8F8)
private val TertiarySlate          = Color(0xFF4A6070)
private val TertiaryContainer      = Color(0xFFCFDEE8)

private val SurfaceLight   = Color(0xFFF5F8FC)
private val BackgroundLight= Color(0xFFF0F4F8)
private val OnSurfaceLight = Color(0xFF0E1822)
private val OutlineLight   = Color(0xFF8AAABF)

private val SurfaceDark    = Color(0xFF0F1E2B)
private val BackgroundDark = Color(0xFF0A1520)
private val OnSurfaceDark  = Color(0xFFDCEAF5)
private val OutlineDark    = Color(0xFF3D6480)

private val LightColorScheme = lightColorScheme(
    primary                = Navy,
    onPrimary              = Color.White,
    primaryContainer       = NavyContainer,
    onPrimaryContainer     = NavyPale,
    secondary              = SecondaryBlue,
    onSecondary            = Color.White,
    secondaryContainer     = SecondaryContainer,
    onSecondaryContainer   = NavyDark,
    tertiary               = TertiarySlate,
    onTertiary             = Color.White,
    tertiaryContainer      = TertiaryContainer,
    onTertiaryContainer    = Color(0xFF1A2D38),
    background             = Color(0xFFFAFBFC),
    onBackground           = OnSurfaceLight,
    surface                = Color(0xFFFAFBFC),
    onSurface              = OnSurfaceLight,
    surfaceVariant         = Color(0xFFDCE8F0),
    onSurfaceVariant       = Color(0xFF2A4054),
    outline                = OutlineLight,
    error                  = Color(0xFFBA1A1A),
    onError                = Color.White,
    errorContainer         = Color(0xFFFFDAD6),
    onErrorContainer       = Color(0xFF410002),
)

private val DarkColorScheme = darkColorScheme(
    primary                = NavyLight,
    onPrimary              = Color.White,
    primaryContainer       = NavyDark,
    onPrimaryContainer     = NavyPale,
    secondary              = Color(0xFF90C4E8),
    onSecondary            = Color(0xFF0D2F48),
    secondaryContainer     = Color(0xFF1A4D70),
    onSecondaryContainer   = Color(0xFFCDE4F5),
    tertiary               = Color(0xFF9BB8C8),
    onTertiary             = Color(0xFF1A2D38),
    tertiaryContainer      = Color(0xFF2E4858),
    onTertiaryContainer    = Color(0xFFCFDEE8),
    background             = BackgroundDark,
    onBackground           = OnSurfaceDark,
    surface                = SurfaceDark,
    onSurface              = OnSurfaceDark,
    surfaceVariant         = Color(0xFF1A2E3D),
    onSurfaceVariant       = Color(0xFF90B0C8),
    outline                = Color(0xFF6898B8),
    error                  = Color(0xFFFFB4AB),
    onError                = Color(0xFF690005),
    errorContainer         = Color(0xFF93000A),
    onErrorContainer       = Color(0xFFFFDAD6),
)

@Composable
fun PlatenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
