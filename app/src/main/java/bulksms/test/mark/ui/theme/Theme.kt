package bulksms.test.mark.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary = Mint,
    onPrimary = Surface,
    primaryContainer = MintLight,
    onPrimaryContainer = MintDark,
    secondary = Navy,
    onSecondary = Surface,
    secondaryContainer = NavyLight,
    onSecondaryContainer = Surface,
    tertiary = Amber,
    onTertiary = Surface,
    tertiaryContainer = AmberLight,
    onTertiaryContainer = Amber,
    background = OffWhite,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = OffWhite,
    onSurfaceVariant = Slate,
    outline = Outline,
    outlineVariant = Outline,
    // No red — use amber for "error" too. Keeps the locked design language.
    error = Amber,
    onError = Surface,
    errorContainer = AmberLight,
    onErrorContainer = Amber
)

@Composable
fun BulkSmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = AppTypography,
        content = content
    )
}
