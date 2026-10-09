package com.isivoltpro.maginaolivo.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** #707 DARK-1: reactive semantic palette. Legacy constants remain artwork/light references. */
@Immutable
data class MoPalette(
    val primaryText: Color,
    val bodyText: Color,
    val secondaryText: Color,
    val actionText: Color,
    val actionTint: Color,
    val primaryButton: Color,
    val onPrimaryButton: Color,
    val infoText: Color,
    val infoTint: Color,
    val warningText: Color,
    val warningTint: Color,
    val errorText: Color,
    val errorTint: Color,
    val earthText: Color,
    val earthTint: Color,
    val valueText: Color,
    val valueTint: Color,
    val treatmentText: Color,
    val treatmentTint: Color,
    val labourText: Color,
    val labourTint: Color,
    val moneyText: Color,
    val moneyTint: Color,
    val rainText: Color,
    val rainTint: Color,
    val skyTint: Color,
    val cloudText: Color,
    val cloudTint: Color,
)

val MoLightPalette = MoPalette(
    MoOliveDark, MoInk, MoLightColorScheme.onSurfaceVariant, MoOliveMid, MoOliveTint,
    MoOlivePrimary, MoWarmWhite, MoInfoText, MoInfoTint, MoWarningText, MoWarningTint,
    MoErrorText, Color(0xFFF7E4E1), MoEarthText, MoEarthTint, MoSoftGoldText, MoSoftGoldTint,
    MoTreatmentText, MoTreatmentTint, MoLabourText, MoLabourTint, MoMoneyText, MoMoneyTint,
    MoRainText, MoRainTint, MoSkyTint, MoCloudText, MoCloudTint,
)

val MoDarkPalette = MoPalette(
    Color(0xFFF0EBDD), Color(0xFFF0EBDD), Color(0xFFD0C9BB), Color(0xFFB6D39E), Color(0xFF293A24),
    Color(0xFFB6D39E), Color(0xFF192416), Color(0xFFB0D1E0), Color(0xFF23353E),
    Color(0xFFECCB86), Color(0xFF3C311E), Color(0xFFFFB6AC), Color(0xFF442926),
    Color(0xFFE6BD9E), Color(0xFF392D24), Color(0xFFE2CC90), Color(0xFF38321F),
    Color(0xFFA5D6C8), Color(0xFF213930), Color(0xFFF2B69C), Color(0xFF402B22),
    Color(0xFFE2CC90), Color(0xFF38321F), Color(0xFFA8D4F5), Color(0xFF203544),
    Color(0xFF26343B), Color(0xFFC4CFD9), Color(0xFF2D3338),
)

internal val LocalMoPalette = staticCompositionLocalOf { MoLightPalette }

/** Read within composition so components respond immediately when appearance changes. */
object MoColors {
    val current: MoPalette
        @Composable get() = LocalMoPalette.current
}
