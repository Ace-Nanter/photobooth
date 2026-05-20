package ovh.pandore.photobooth.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkColorScheme = darkColorScheme(
    primary           = SlateBlue80,
    onPrimary         = Color(0xFF002F6E),
    secondary         = SlateGrey80,
    onSecondary       = Color(0xFF1F3142),
    tertiary          = SlateAccent80,
    onTertiary        = Color(0xFF3B1E6A),
    background        = SurfaceDark,
    onBackground      = Color(0xFFE2E2E9),
    surface           = SurfaceDark,
    onSurface         = Color(0xFFE2E2E9),
    surfaceVariant    = Color(0xFF2E3136),
    onSurfaceVariant  = Color(0xFFBEC7D5),
)

private val LightColorScheme = lightColorScheme(
    primary           = SlateBlue40,
    onPrimary         = Color.White,
    secondary         = SlateGrey40,
    onSecondary       = Color.White,
    tertiary          = SlateAccent40,
    onTertiary        = Color.White,
    background        = SurfaceLight,
    onBackground      = Color(0xFF191C20),
    surface           = SurfaceLight,
    onSurface         = Color(0xFF191C20),
    surfaceVariant    = Color(0xFFDDE3F0),
    onSurfaceVariant  = Color(0xFF424A5A),
)

@Composable
fun PhotoboothTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Material You dynamique disponible sur Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else      -> LightColorScheme
    }

    // Synchronise l'apparence des icônes de la barre de statut (clair/sombre)
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view)
                .isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography   = Typography,
        content      = content
    )
}
