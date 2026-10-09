package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
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
// CR-011 §19: cards are near-white over the cream background so content separates clearly.
val MoWarmWhite = Color(0xFFFFFEFA)
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
val MoSuccessTint = Color(0xFFE8EFE3)
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

// CR-011 §20: semantic accents for the Cuaderno actions that shared a family before —
// Tratamiento (technical green), Jornal (terracotta) and Gasto (gold-brown). Same agro family,
// nothing loud; each line colour keeps ≥ 4.5:1 on its tint and on the card white.
val MoTreatmentText = Color(0xFF2E6A5C)
val MoTreatmentTint = Color(0xFFE1EEE9)
val MoLabourText = Color(0xFF9A4524)
val MoLabourTint = Color(0xFFF8E5DA)
val MoMoneyText = Color(0xFF6A5326)
val MoMoneyTint = Color(0xFFEEE6D2)

// #345: rain/radar accent — a clearer water blue than the slate «Tiempo»/Riego info family, so
// the rain radar reads as its own block. Line ≥ 4.5:1 on its tint and on the card white.
val MoRainText = Color(0xFF1D5E8C)
val MoRainTint = Color(0xFFE2EFF8)

// #362: weekly forecast families. Soft, low-saturation tints; each day also says its state in
// words and with its icon, so colour is never the only signal.
val MoSkyTint = Color(0xFFEDF3F6)
val MoCloudTint = Color(0xFFE9ECEF)
val MoCloudText = Color(0xFF4F5B66)

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
    // #706: warm paper behind near-white cards; significant outlines remain >= 3:1.
    background = Color(0xFFF1ECDF),
    onBackground = MoInk,
    surface = MoWarmWhite,
    onSurface = MoInk,
    surfaceVariant = MoSurfaceSoft,
    onSurfaceVariant = Color(0xFF575248),
    outline = Color(0xFF8B7E68),
    surfaceContainerLowest = MoWarmWhite,
    surfaceContainerLow = MoWarmWhite,
    surfaceContainer = MoSurfaceSoft,
    surfaceContainerHigh = Color(0xFFF8F3E8),
    surfaceContainerHighest = Color(0xFFF8F3E8),
    error = MoErrorText,
    onError = MoWarmWhite,
)

/** #707: warm olive night surfaces; photographs and external maps keep their source pixels. */
val MoDarkColorScheme = darkColorScheme(
    primary = Color(0xFFB6D39E),
    onPrimary = Color(0xFF192416),
    primaryContainer = Color(0xFF293A24),
    onPrimaryContainer = Color(0xFFDFECCD),
    secondary = Color(0xFFC5CBAE),
    onSecondary = Color(0xFF252B1E),
    secondaryContainer = Color(0xFF33392A),
    onSecondaryContainer = Color(0xFFE4EAD0),
    tertiary = Color(0xFFE6BD9E),
    onTertiary = Color(0xFF352417),
    background = Color(0xFF171914),
    onBackground = Color(0xFFF0EBDD),
    surface = Color(0xFF23261F),
    onSurface = Color(0xFFF0EBDD),
    surfaceVariant = Color(0xFF303329),
    onSurfaceVariant = Color(0xFFD0C9BB),
    outline = Color(0xFFA99F8C),
    surfaceContainerLowest = Color(0xFF12140F),
    surfaceContainerLow = Color(0xFF23261F),
    surfaceContainer = Color(0xFF292C24),
    surfaceContainerHigh = Color(0xFF303329),
    surfaceContainerHighest = Color(0xFF383B31),
    error = Color(0xFFFFB6AC),
    onError = Color(0xFF442926),
    errorContainer = Color(0xFF442926),
    onErrorContainer = Color(0xFFFFB6AC),
)
