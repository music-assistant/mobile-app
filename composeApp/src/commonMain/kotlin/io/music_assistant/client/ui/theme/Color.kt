package io.music_assistant.client.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ---- Light ----
val primaryLight = Color(0xFF2CB1F5)
val onPrimaryLight = Color(0xFF003547)
val primaryContainerLight = Color(0xFFCBE6FF)
val onPrimaryContainerLight = Color(0xFF001D31)
val secondaryLight = Color(0xFF4E616D)
val onSecondaryLight = Color(0xFFFFFFFF)
val secondaryContainerLight = Color(0xFFD2E5F4)
val onSecondaryContainerLight = Color(0xFF0A1E29)
val tertiaryLight = Color(0xFF984061)
val onTertiaryLight = Color(0xFFFFFFFF)
val tertiaryContainerLight = Color(0xFFFFD9E2)
val onTertiaryContainerLight = Color(0xFF3E001D)
val errorLight = Color(0xFFBA1A1A)
val onErrorLight = Color(0xFFFFFFFF)
val errorContainerLight = Color(0xFFFFDAD6)
val onErrorContainerLight = Color(0xFF410002)
val backgroundLight = Color(0xFFF7F9FF)
val onBackgroundLight = Color(0xFF181C20)
val surfaceLight = Color(0xFFF7F9FF)
val onSurfaceLight = Color(0xFF181C20)
val surfaceVariantLight = Color(0xFFDDE3EB)
val onSurfaceVariantLight = Color(0xFF41474E)
val outlineLight = Color(0xFF71787F)
val outlineVariantLight = Color(0xFFC1C7CF)
val scrimLight = Color(0xFF000000)
val inverseSurfaceLight = Color(0xFF2D3135)
val inverseOnSurfaceLight = Color(0xFFEEF0F6)
val inversePrimaryLight = Color(0xFF8DCDFF)
val surfaceDimLight = Color(0xFFD7DAE0)
val surfaceBrightLight = Color(0xFFF7F9FF)
val surfaceContainerLowestLight = Color(0xFFFFFFFF)
val surfaceContainerLowLight = Color(0xFFF1F3FA)
val surfaceContainerLight = Color(0xFFEBEEF4)
val surfaceContainerHighLight = Color(0xFFE5E8EE)
val surfaceContainerHighestLight = Color(0xFFE0E2E9)

// ---- Dark ----
val primaryDark = Color(0xFF8DCDFF)
val onPrimaryDark = Color(0xFF00344C)
val primaryContainerDark = Color(0xFF004C6B)
val onPrimaryContainerDark = Color(0xFFCBE6FF)
val secondaryDark = Color(0xFFB6C9D8)
val onSecondaryDark = Color(0xFF20333E)
val secondaryContainerDark = Color(0xFF374955)
val onSecondaryContainerDark = Color(0xFFD2E5F4)
val tertiaryDark = Color(0xFFFFB1C8)
val onTertiaryDark = Color(0xFF5E1133)
val tertiaryContainerDark = Color(0xFF7B2949)
val onTertiaryContainerDark = Color(0xFFFFD9E2)
val errorDark = Color(0xFFFFB4AB)
val onErrorDark = Color(0xFF690005)
val errorContainerDark = Color(0xFF93000A)
val onErrorContainerDark = Color(0xFFFFDAD6)
val backgroundDark = Color(0xFF101418)
val onBackgroundDark = Color(0xFFE0E2E9)
val surfaceDark = Color(0xFF101418)
val onSurfaceDark = Color(0xFFE0E2E9)
val surfaceVariantDark = Color(0xFF41474E)
val onSurfaceVariantDark = Color(0xFFC1C7CF)
val outlineDark = Color(0xFF8B9198)
val outlineVariantDark = Color(0xFF41474E)
val scrimDark = Color(0xFF000000)
val inverseSurfaceDark = Color(0xFFE0E2E9)
val inverseOnSurfaceDark = Color(0xFF2D3135)
val inversePrimaryDark = Color(0xFF00658E)
val surfaceDimDark = Color(0xFF101418)
val surfaceBrightDark = Color(0xFF363A3F)
val surfaceContainerLowestDark = Color(0xFF0B0E13)
val surfaceContainerLowDark = Color(0xFF181C20)
val surfaceContainerDark = Color(0xFF1C2024)
val surfaceContainerHighDark = Color(0xFF262B2F)
val surfaceContainerHighestDark = Color(0xFF31353A)

/**
 * Accent used for the "favorite" heart indicator across the app.
 * Bound to the [androidx.compose.material3.ColorScheme.tertiary] role so it harmonizes with
 * the scheme and tracks light/dark automatically.
 */
val favoriteTint: Color
    @Composable @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.tertiary
