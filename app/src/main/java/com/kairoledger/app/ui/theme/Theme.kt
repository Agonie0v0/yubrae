package com.kairoledger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kairoledger.app.data.prefs.ThemeMode

@Immutable
data class KairoSemanticColors(
    val expense: Color,
    val expenseContainer: Color,
    val income: Color,
    val incomeContainer: Color,
    val transfer: Color,
    val transferContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val isDark: Boolean
)

val LocalKairoSemanticColors = staticCompositionLocalOf {
    KairoSemanticColors(
        expense = ExpenseLight,
        expenseContainer = ExpenseContainerLight,
        income = IncomeLight,
        incomeContainer = IncomeContainerLight,
        transfer = TransferLight,
        transferContainer = TransferContainerLight,
        warning = WarningLight,
        warningContainer = WarningContainerLight,
        isDark = false
    )
}

private val LightColorScheme = lightColorScheme(
    primary = PrismPrimaryLight,
    onPrimary = PrismOnPrimaryLight,
    primaryContainer = PrismPrimaryContainerLight,
    onPrimaryContainer = PrismOnPrimaryContainerLight,
    secondary = PrismSecondaryLight,
    onSecondary = PrismOnSecondaryLight,
    secondaryContainer = PrismSecondaryContainerLight,
    onSecondaryContainer = PrismOnSecondaryContainerLight,
    tertiary = PrismTertiaryLight,
    onTertiary = PrismOnTertiaryLight,
    tertiaryContainer = PrismTertiaryContainerLight,
    onTertiaryContainer = PrismOnTertiaryContainerLight,
    background = PrismBackgroundLight,
    onBackground = PrismOnBackgroundLight,
    surface = PrismSurfaceLight,
    onSurface = PrismOnSurfaceLight,
    surfaceVariant = PrismSurfaceVariantLight,
    onSurfaceVariant = PrismOnSurfaceVariantLight,
    outline = PrismOutlineLight,
    outlineVariant = PrismOutlineVariantLight,
    surfaceContainerLowest = PrismSurfaceContainerLowestLight,
    surfaceContainerLow = PrismSurfaceContainerLowLight,
    surfaceContainer = PrismSurfaceContainerLight,
    surfaceContainerHigh = PrismSurfaceContainerHighLight,
    surfaceContainerHighest = PrismSurfaceContainerHighestLight,
    error = PrismErrorLight,
    onError = PrismOnErrorLight,
    errorContainer = PrismErrorContainerLight,
    onErrorContainer = PrismOnErrorContainerLight
)

private val DarkColorScheme = darkColorScheme(
    primary = PrismPrimaryDark,
    onPrimary = PrismOnPrimaryDark,
    primaryContainer = PrismPrimaryContainerDark,
    onPrimaryContainer = PrismOnPrimaryContainerDark,
    secondary = PrismSecondaryDark,
    onSecondary = PrismOnSecondaryDark,
    secondaryContainer = PrismSecondaryContainerDark,
    onSecondaryContainer = PrismOnSecondaryContainerDark,
    tertiary = PrismTertiaryDark,
    onTertiary = PrismOnTertiaryDark,
    tertiaryContainer = PrismTertiaryContainerDark,
    onTertiaryContainer = PrismOnTertiaryContainerDark,
    background = PrismBackgroundDark,
    onBackground = PrismOnBackgroundDark,
    surface = PrismSurfaceDark,
    onSurface = PrismOnSurfaceDark,
    surfaceVariant = PrismSurfaceVariantDark,
    onSurfaceVariant = PrismOnSurfaceVariantDark,
    outline = PrismOutlineDark,
    outlineVariant = PrismOutlineVariantDark,
    surfaceContainerLowest = PrismSurfaceContainerLowestDark,
    surfaceContainerLow = PrismSurfaceContainerLowDark,
    surfaceContainer = PrismSurfaceContainerDark,
    surfaceContainerHigh = PrismSurfaceContainerHighDark,
    surfaceContainerHighest = PrismSurfaceContainerHighestDark,
    error = PrismErrorDark,
    onError = PrismOnErrorDark,
    errorContainer = PrismErrorContainerDark,
    onErrorContainer = PrismOnErrorContainerDark
)

@Composable
fun KairoLedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val semanticColors = if (darkTheme) {
        KairoSemanticColors(
            expense = ExpenseDark,
            expenseContainer = ExpenseContainerDark,
            income = IncomeDark,
            incomeContainer = IncomeContainerDark,
            transfer = TransferDark,
            transferContainer = TransferContainerDark,
            warning = WarningDark,
            warningContainer = WarningContainerDark,
            isDark = true
        )
    } else {
        KairoSemanticColors(
            expense = ExpenseLight,
            expenseContainer = ExpenseContainerLight,
            income = IncomeLight,
            incomeContainer = IncomeContainerLight,
            transfer = TransferLight,
            transferContainer = TransferContainerLight,
            warning = WarningLight,
            warningContainer = WarningContainerLight,
            isDark = false
        )
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalKairoSemanticColors provides semanticColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = KairoTypography,
            content = content
        )
    }
}

object KairoTheme {
    val semantic: KairoSemanticColors
        @Composable
        get() = LocalKairoSemanticColors.current
}
