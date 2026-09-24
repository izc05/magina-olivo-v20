package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/**
 * UI polish v2 — colour hierarchy (owner request, device review):
 * - MoOlivePrimary: only the primary CTA and the selected navigation item;
 * - MoOliveMid / MoOliveTint: chips, positive states, selection, text actions;
 * - MoCream / MoWarmWhite: backgrounds and cards;
 * - MoTextSecondary (warm grey): secondary text; MoInk: body text;
 * - MoInfo (blue-grey): planning and neutral information;
 * - MoWarning (soft amber): notices.
 * Every text colour keeps ≥ 4.5:1 contrast on cream and warm white.
 */
val MoOlivePrimary = Color(0xFF3E5A32)
val MoOliveMid = Color(0xFF4F6B39)
val MoOliveTint = Color(0xFFE9EEE0)
val MoInk = Color(0xFF2A2823)
val MoOliveDark = Color(0xFF173122)
val MoCream = Color(0xFFF8F6EE)
val MoWarmWhite = Color(0xFFFCFBF7)
val MoSage = Color(0xFFA7B08F)
val MoEarth = Color(0xFFB88B6B)
val MoSoftGold = Color(0xFFD4B76A)
val MoSoftGoldText = Color(0xFF76601E)
val MoTextSecondary = Color(0xFF67625A)
val MoOutline = Color(0xFFE5E1D6)
val MoOutlineStrong = Color(0xFFD6D0C2)
val MoSurfaceSoft = Color(0xFFF4F1E8)
val MoMapBase = Color(0xFFD8D5BA)
val MoMapContour = Color(0xFF9A9275)
val MoArtworkMapBase = Color(0xFFD8D7C4)
val MoArtworkMapGround = Color(0xFFC8C2A1)
val MoSuccess = Color(0xFF4E7A45)
val MoSuccessText = Color(0xFF466F3E)
val MoInfo = Color(0xFF5E7D8C)
val MoInfoText = Color(0xFF4C6876)
val MoWarning = Color(0xFFC49842)
val MoWarningText = Color(0xFF805B18)
val MoError = Color(0xFFB5534F)
val MoErrorText = Color(0xFFA34844)

// Icon families (CR-004 addendum): the icon line takes the text-strength shade of a palette
// token and sits on a light tint of the same token, so each kind of thing is recognised by
// colour as well as by shape. All line colours keep ≥ 4.5:1 on their tint.
val MoInfoTint = Color(0xFFE6EDF0)
val MoSoftGoldTint = Color(0xFFF6EFD9)
val MoEarthText = Color(0xFF7D5438)
val MoEarthTint = Color(0xFFF4EAE1)
val MoWarningTint = Color(0xFFF7EDD6)

val MoLightColorScheme = lightColorScheme(
    // Material's own "primary" drives text buttons, checkboxes and progress: those are
    // selection/secondary accents, so they take the mid olive. The dark olive CTA is
    // applied explicitly by MoPrimaryButton and the bottom bar.
    primary = MoOliveMid,
    onPrimary = MoWarmWhite,
    primaryContainer = MoOliveTint,
    onPrimaryContainer = MoOliveDark,
    secondary = MoSage,
    onSecondary = MoOliveDark,
    secondaryContainer = Color(0xFFEEF0E7),
    onSecondaryContainer = MoOliveDark,
    tertiary = MoEarth,
    onTertiary = MoWarmWhite,
    background = MoCream,
    onBackground = MoInk,
    surface = MoWarmWhite,
    onSurface = MoInk,
    surfaceVariant = MoSurfaceSoft,
    onSurfaceVariant = MoTextSecondary,
    outline = MoOutline,
    error = MoError,
    onError = MoWarmWhite,
)
