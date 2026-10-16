package com.khosravi.devin.present.uikit.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import com.khosravi.devin.present.R

/**
 * The Material colour scheme, read from `res/values/colors.xml` (light) or `res/values-night/colors.xml` (dark),
 * the same resources the XML `Theme.App` uses, so the window and Compose share one palette.
 */
@Composable
internal fun devinColorScheme(darkTheme: Boolean): ColorScheme {
    val primary = colorResource(R.color.primary)
    val onPrimary = colorResource(R.color.on_primary)
    val primaryContainer = colorResource(R.color.primary_container)
    val onPrimaryContainer = colorResource(R.color.on_primary_container)
    val secondary = colorResource(R.color.secondary)
    val onSecondary = colorResource(R.color.on_secondary)
    val secondaryContainer = colorResource(R.color.secondary_container)
    val onSecondaryContainer = colorResource(R.color.on_secondary_container)
    val tertiary = colorResource(R.color.tertiary)
    val onTertiary = colorResource(R.color.on_tertiary)
    val tertiaryContainer = colorResource(R.color.tertiary_container)
    val onTertiaryContainer = colorResource(R.color.on_tertiary_container)
    val background = colorResource(R.color.background)
    val onBackground = colorResource(R.color.on_background)
    val surface = colorResource(R.color.surface)
    val onSurface = colorResource(R.color.on_surface)
    val surfaceVariant = colorResource(R.color.surface_variant)
    val onSurfaceVariant = colorResource(R.color.on_surface_variant)
    val outline = colorResource(R.color.outline)
    val outlineVariant = colorResource(R.color.outline_variant)
    val error = colorResource(R.color.error)
    val onError = colorResource(R.color.on_error)
    val errorContainer = colorResource(R.color.error_container)
    val onErrorContainer = colorResource(R.color.on_error_container)
    return if (darkTheme) {
        darkColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline, outlineVariant = outlineVariant,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        )
    } else {
        lightColorScheme(
            primary = primary, onPrimary = onPrimary,
            primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
            secondary = secondary, onSecondary = onSecondary,
            secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary, onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer, onTertiaryContainer = onTertiaryContainer,
            background = background, onBackground = onBackground,
            surface = surface, onSurface = onSurface,
            surfaceVariant = surfaceVariant, onSurfaceVariant = onSurfaceVariant,
            outline = outline, outlineVariant = outlineVariant,
            error = error, onError = onError,
            errorContainer = errorContainer, onErrorContainer = onErrorContainer,
        )
    }
}

/** Colours of one log level: the round icon (and its background) and the level badge. */
@Immutable
data class LogLevelColors(
    val icon: Color,
    val iconBackground: Color,
    val badgeBackground: Color,
    val badgeText: Color,
)

/** App-specific colours that have no Material role. Read through [MaterialTheme.devinColors]. */
@Immutable
data class DevinColors(
    val logError: LogLevelColors,
    val logWarning: LogLevelColors,
    val logInfo: LogLevelColors,
    val logDebug: LogLevelColors,
    val chipSelected: Color,
    val chipUnselected: Color,
    val chipTextSelected: Color,
    val chipTextUnselected: Color,
    val okHttpChipSelected: Color,
    val okHttpChipUnselected: Color,
    val okHttpChipTextSelected: Color,
    val okHttpChipTextUnselected: Color,
    val sessionStartBackground: Color,
    val sessionStartText: Color,
    val sessionStartSecondaryText: Color,
    val sessionStartDot: Color,
    val sessionStartPill: Color,
    val sessionStartPillText: Color,
    val jsonKey: Color,
    val jsonValue: Color,
    val jsonNumberAndNull: Color,
    val jsonElements: Color,
    val jsonBoolean: Color,
    val statusError: Color,
    val statusSuccess: Color,
    val searchMatchBackground: Color,
    val searchMatchText: Color,
    val searchCurrentMatchBackground: Color,
    val searchCurrentMatchText: Color,
    val searchCounterBackground: Color,
    val searchCounterText: Color,
    val updateAvailableText: Color,
)

/** [DevinColors] from the colour resources; `values-night` gives the dark variant. */
@Composable
internal fun devinColors(): DevinColors = DevinColors(
    logError = LogLevelColors(
        icon = colorResource(R.color.log_error_icon),
        iconBackground = colorResource(R.color.log_error_icon_bg),
        badgeBackground = colorResource(R.color.log_error_container),
        badgeText = colorResource(R.color.log_error_text),
    ),
    logWarning = LogLevelColors(
        icon = colorResource(R.color.log_warning_icon),
        iconBackground = colorResource(R.color.log_warning_icon_bg),
        badgeBackground = colorResource(R.color.log_warning_container),
        badgeText = colorResource(R.color.log_warning_text),
    ),
    logInfo = LogLevelColors(
        icon = colorResource(R.color.log_info_icon),
        iconBackground = colorResource(R.color.log_info_icon_bg),
        badgeBackground = colorResource(R.color.log_info_container),
        badgeText = colorResource(R.color.log_info_text),
    ),
    logDebug = LogLevelColors(
        icon = colorResource(R.color.log_debug_icon),
        iconBackground = colorResource(R.color.log_debug_icon_bg),
        badgeBackground = colorResource(R.color.log_debug_container),
        badgeText = colorResource(R.color.log_debug_text),
    ),
    chipSelected = colorResource(R.color.chip_selected),
    chipUnselected = colorResource(R.color.chip_unselected),
    chipTextSelected = colorResource(R.color.chip_text_selected),
    chipTextUnselected = colorResource(R.color.chip_text_unselected),
    okHttpChipSelected = colorResource(R.color.tag_okhttp_container_selected),
    okHttpChipUnselected = colorResource(R.color.tag_okhttp_container_unselected),
    okHttpChipTextSelected = colorResource(R.color.tag_okhttp_text_selected),
    okHttpChipTextUnselected = colorResource(R.color.tag_okhttp_text_unselected),
    sessionStartBackground = colorResource(R.color.session_start_background),
    sessionStartText = colorResource(R.color.session_start_text),
    sessionStartSecondaryText = colorResource(R.color.session_start_secondary_text),
    sessionStartDot = colorResource(R.color.session_start_dot),
    sessionStartPill = colorResource(R.color.session_start_pill),
    sessionStartPillText = colorResource(R.color.session_start_pill_text),
    jsonKey = colorResource(R.color.colorHttpJsonKey),
    jsonValue = colorResource(R.color.colorHttpJsonValue),
    jsonNumberAndNull = colorResource(R.color.colorHttpJsonDigit_null_value),
    jsonElements = colorResource(R.color.colorHttpJsonElements),
    jsonBoolean = colorResource(R.color.colorHttpJsonBoolean),
    statusError = colorResource(R.color.status_error),
    statusSuccess = colorResource(R.color.status_success),
    searchMatchBackground = colorResource(R.color.search_match_background),
    searchMatchText = colorResource(R.color.search_match_text),
    searchCurrentMatchBackground = colorResource(R.color.search_current_match_background),
    searchCurrentMatchText = colorResource(R.color.search_current_match_text),
    searchCounterBackground = colorResource(R.color.search_counter_background),
    searchCounterText = colorResource(R.color.search_counter_text),
    updateAvailableText = colorResource(R.color.update_available_text),
)

val LocalDevinColors = staticCompositionLocalOf<DevinColors> {
    error("DevinColors not provided: wrap the content in DevinTheme")
}

val MaterialTheme.devinColors: DevinColors
    @Composable
    @ReadOnlyComposable
    get() = LocalDevinColors.current
