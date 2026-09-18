package com.isivoltpro.maginaolivo.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.isivoltpro.maginaolivo.FoundationScreen
import com.isivoltpro.maginaolivo.ui.reference.onboarding.OnboardingReferenceScreen
import com.isivoltpro.maginaolivo.ui.theme.MaginaOlivoTheme

@Composable
fun AppRoot(
    compositionRoot: AppCompositionRoot,
) {
    var showOnboarding by remember { mutableStateOf(true) }

    MaginaOlivoTheme {
        if (showOnboarding) {
            OnboardingReferenceScreen(
                onFinished = { showOnboarding = false },
            )
        } else {
            FoundationScreen(
                environment = compositionRoot.environment,
            )
        }
    }
}
