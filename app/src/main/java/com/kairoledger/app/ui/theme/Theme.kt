package com.kairoledger.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
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
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.kairoledger.app.data.prefs.ThemeMode
import com.kairoledger.app.data.prefs.ThemeStyle

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
    val isDark: Boolean,
    val heroGradient: List<Color>,
    val heroOnSurface: Color,
    val heroPillBg: Color,
    val heroBorder: Color
)

@Immutable
data class KairoThemePreset(
    val id: String,
    val nameZh: String,
    val taglineZh: String,
    val previewPrimary: Color,
    val previewContainer: Color,
    val previewSecondary: Color,
    val previewGradient: List<Color> = listOf(previewContainer, previewContainer)
)

object KairoThemePresets {
    val all: List<KairoThemePreset> = AllThemePaletteSpecs.map { spec ->
        KairoThemePreset(
            id = spec.id,
            nameZh = spec.nameZh,
            taglineZh = spec.taglineZh,
            previewPrimary = spec.light.primary,
            previewContainer = spec.light.primaryContainer,
            previewSecondary = spec.light.tertiary,
            previewGradient = spec.light.heroGradient
        )
    }

    fun find(themeStyle: String): KairoThemePreset {
        val normalized = themeStyle.trim()
        return all.firstOrNull { it.id.equals(normalized, ignoreCase = true) } ?: all.first()
    }
}

data class OverlayThemeColors(
    val isDark: Boolean,
    val cardBg: Int,
    val cardStroke: Int,
    val textPrimary: Int,
    val textSecondary: Int,
    val chipBg: Int,
    val accent: Int,
    val accentContainer: Int,
    val onAccentContainer: Int,
    val expenseColor: Int,
    val incomeColor: Int,
    val transferColor: Int,
    val inputBg: Int,
    val warningBg: Int,
    val warningText: Int
)

val LocalKairoSemanticColors = staticCompositionLocalOf {
    KairoSemanticColors(
        expense = YimuExpenseLight,
        expenseContainer = ExpenseContainerLight,
        income = YimuIncomeLight,
        incomeContainer = IncomeContainerLight,
        transfer = YimuTransferLight,
        transferContainer = TransferContainerLight,
        warning = YimuWarningLight,
        warningContainer = WarningContainerLight,
        isDark = false,
        heroGradient = YimuPaletteSpec.light.heroGradient,
        heroOnSurface = YimuPaletteSpec.light.heroOnSurface,
        heroPillBg = YimuPaletteSpec.light.heroPillBg,
        heroBorder = YimuPaletteSpec.light.heroBorder
    )
}

private fun resolveThemeSpec(themeStyle: String): ThemePaletteSpec {
    val normalized = themeStyle.trim()
    return AllThemePaletteSpecs.firstOrNull { it.id.equals(normalized, ignoreCase = true) }
        ?: YimuPaletteSpec
}

private fun ThemeColorSet.toLightColorScheme(): ColorScheme = lightColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    error = PrismErrorLight,
    onError = PrismOnErrorLight,
    errorContainer = PrismErrorContainerLight,
    onErrorContainer = PrismOnErrorContainerLight
)

private fun ThemeColorSet.toDarkColorScheme(): ColorScheme = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    primaryContainer = primaryContainer,
    onPrimaryContainer = onPrimaryContainer,
    secondary = secondary,
    onSecondary = onSecondary,
    secondaryContainer = secondaryContainer,
    onSecondaryContainer = onSecondaryContainer,
    tertiary = tertiary,
    onTertiary = onTertiary,
    tertiaryContainer = tertiaryContainer,
    onTertiaryContainer = onTertiaryContainer,
    background = background,
    onBackground = onBackground,
    surface = surface,
    onSurface = onSurface,
    surfaceVariant = surfaceVariant,
    onSurfaceVariant = onSurfaceVariant,
    outline = outline,
    outlineVariant = outlineVariant,
    surfaceContainerLowest = surfaceContainerLowest,
    surfaceContainerLow = surfaceContainerLow,
    surfaceContainer = surfaceContainer,
    surfaceContainerHigh = surfaceContainerHigh,
    surfaceContainerHighest = surfaceContainerHighest,
    error = PrismErrorDark,
    onError = PrismOnErrorDark,
    errorContainer = PrismErrorContainerDark,
    onErrorContainer = PrismOnErrorContainerDark
)

private fun ColorScheme.withOledPureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF08080A),
    surfaceContainer = Color(0xFF101013),
    surfaceContainerHigh = Color(0xFF16161A),
    surfaceContainerHighest = Color(0xFF1E1E24),
    surfaceVariant = Color(0xFF18181D),
    outlineVariant = Color(0xFF282830)
)

private fun resolveSemanticColors(
    spec: ThemePaletteSpec,
    darkTheme: Boolean,
    oledPureBlack: Boolean,
    greenExpenseMode: Boolean,
    dynamicBaseScheme: ColorScheme?
): KairoSemanticColors {
    val isYimu = spec.id == ThemeStyle.YIMU_FRESH
    val baseExpense = when {
        darkTheme -> ExpenseDark
        isYimu -> YimuExpenseLight
        else -> ExpenseLight
    }
    val baseExpenseContainer = if (darkTheme) ExpenseContainerDark else ExpenseContainerLight
    val baseIncome = when {
        darkTheme -> IncomeDark
        isYimu -> YimuIncomeLight
        else -> IncomeLight
    }
    val baseIncomeContainer = if (darkTheme) IncomeContainerDark else IncomeContainerLight

    val expense = if (greenExpenseMode) baseIncome else baseExpense
    val expenseContainer = if (greenExpenseMode) baseIncomeContainer else baseExpenseContainer
    val income = if (greenExpenseMode) baseExpense else baseIncome
    val incomeContainer = if (greenExpenseMode) baseExpenseContainer else baseIncomeContainer

    val transfer = when {
        darkTheme -> TransferDark
        isYimu -> YimuTransferLight
        else -> TransferLight
    }
    val transferContainer = if (darkTheme) TransferContainerDark else TransferContainerLight
    val warning = when {
        darkTheme -> WarningDark
        isYimu -> YimuWarningLight
        else -> WarningLight
    }
    val warningContainer = if (darkTheme) WarningContainerDark else WarningContainerLight

    val heroGradient: List<Color>
    val heroOnSurface: Color
    val heroPillBg: Color
    val heroBorder: Color

    if (dynamicBaseScheme != null) {
        val rawStops = listOf(
            dynamicBaseScheme.primaryContainer,
            dynamicBaseScheme.secondaryContainer,
            dynamicBaseScheme.tertiaryContainer
        )
        heroGradient = if (darkTheme && oledPureBlack) {
            rawStops.map { lerp(it, Color.Black, 0.58f) }
        } else {
            rawStops
        }
        heroOnSurface = dynamicBaseScheme.onPrimaryContainer
        heroPillBg = when {
            darkTheme && oledPureBlack -> Color(0xFF0A0A0E).copy(alpha = 0.88f)
            darkTheme -> dynamicBaseScheme.surfaceContainerLowest.copy(alpha = 0.78f)
            else -> Color.White.copy(alpha = 0.86f)
        }
        heroBorder = dynamicBaseScheme.primary.copy(alpha = if (darkTheme) 0.25f else 0.22f)
    } else {
        val colorSet = if (darkTheme) spec.dark else spec.light
        heroGradient = if (darkTheme && oledPureBlack) spec.oledHeroGradient else colorSet.heroGradient
        heroOnSurface = colorSet.heroOnSurface
        heroPillBg = if (darkTheme && oledPureBlack) spec.oledHeroPillBg else colorSet.heroPillBg
        heroBorder = colorSet.heroBorder
    }

    return KairoSemanticColors(
        expense = expense,
        expenseContainer = expenseContainer,
        income = income,
        incomeContainer = incomeContainer,
        transfer = transfer,
        transferContainer = transferContainer,
        warning = warning,
        warningContainer = warningContainer,
        isDark = darkTheme,
        heroGradient = heroGradient,
        heroOnSurface = heroOnSurface,
        heroPillBg = heroPillBg,
        heroBorder = heroBorder
    )
}

fun resolveOverlayThemeColors(
    themeStyle: String,
    isDark: Boolean,
    oledPureBlack: Boolean = false,
    greenExpenseMode: Boolean = false
): OverlayThemeColors {
    val spec = resolveThemeSpec(themeStyle)
    val colorSet = if (isDark) spec.dark else spec.light
    val baseScheme = if (isDark) colorSet.toDarkColorScheme() else colorSet.toLightColorScheme()
    val scheme = if (isDark && oledPureBlack) baseScheme.withOledPureBlack() else baseScheme
    val semantic = resolveSemanticColors(
        spec = spec,
        darkTheme = isDark,
        oledPureBlack = oledPureBlack,
        greenExpenseMode = greenExpenseMode,
        dynamicBaseScheme = null
    )

    val cardBg = when {
        isDark && oledPureBlack -> scheme.surfaceContainerLow
        isDark -> scheme.surfaceContainer
        else -> scheme.surfaceContainerLowest
    }
    val cardStroke = scheme.outlineVariant
    val chipBg = if (isDark) scheme.surfaceContainerHigh else scheme.surfaceContainer
    val inputBg = if (isDark) {
        if (oledPureBlack) scheme.surfaceContainer else scheme.surfaceContainerHigh
    } else {
        scheme.surfaceContainerLow
    }
    val warningText = if (isDark) semantic.warning else Color(0xFFB45309)

    return OverlayThemeColors(
        isDark = isDark,
        cardBg = cardBg.toArgb(),
        cardStroke = cardStroke.toArgb(),
        textPrimary = scheme.onSurface.toArgb(),
        textSecondary = scheme.onSurfaceVariant.toArgb(),
        chipBg = chipBg.toArgb(),
        accent = colorSet.overlayAccent.toArgb(),
        accentContainer = scheme.primaryContainer.toArgb(),
        onAccentContainer = scheme.onPrimaryContainer.toArgb(),
        expenseColor = semantic.expense.toArgb(),
        incomeColor = semantic.income.toArgb(),
        transferColor = semantic.transfer.toArgb(),
        inputBg = inputBg.toArgb(),
        warningBg = semantic.warningContainer.toArgb(),
        warningText = warningText.toArgb()
    )
}

@Composable
fun KairoLedgerTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    themeStyle: String = ThemeStyle.YIMU_FRESH,
    oledPureBlack: Boolean = false,
    greenExpenseMode: Boolean = false,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val spec = resolveThemeSpec(themeStyle)
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val dynamicBaseScheme = if (useDynamic) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    val baseScheme = dynamicBaseScheme
        ?: if (darkTheme) spec.dark.toDarkColorScheme() else spec.light.toLightColorScheme()
    val colorScheme = if (darkTheme && oledPureBlack) {
        baseScheme.withOledPureBlack()
    } else {
        baseScheme
    }

    val semanticColors = resolveSemanticColors(
        spec = spec,
        darkTheme = darkTheme,
        oledPureBlack = oledPureBlack,
        greenExpenseMode = greenExpenseMode,
        dynamicBaseScheme = dynamicBaseScheme
    )

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
