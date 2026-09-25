package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityUiState
import com.isivoltpro.maginaolivo.feature.activities.RegisterActivityViewModel
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoEmptyState
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoSectionHeader
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

@Composable
fun NotebookRootRoute(
    persistence: LocalPersistence,
    onRegisterToday: () -> Unit,
    onOpenFarmNotebook: (UUID) -> Unit,
) {
    // The same farm list the register flow already uses; no second source.
    val viewModel: RegisterActivityViewModel = viewModel(
        key = "notebook-root",
        factory = viewModelFactory {
            initializer { RegisterActivityViewModel(persistence.farmRepository, persistence.workspaceRepository) }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    NotebookRootScreen(state, onRegisterToday, onOpenFarmNotebook, viewModel::retry)
}

/**
 * UX-B (Issue #246) — Mi Cuaderno, the centre of the bottom bar: what was done today.
 * This first slice only gives it its place: "Registrar hoy" opens the existing register
 * choices, and each Farm's notebook is one tap away. The full shell comes in UX-C.
 */
@Composable
fun NotebookRootScreen(
    state: RegisterActivityUiState,
    onRegisterToday: () -> Unit,
    onOpenFarmNotebook: (UUID) -> Unit,
    onRetry: () -> Unit = {},
) {
    Scaffold(
        Modifier.fillMaxSize().testTag("notebook-root"),
        containerColor = MoCream,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).statusBarsPadding().verticalScroll(rememberScrollState())
                .padding(horizontal = MoSpacing.screen),
            verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
        ) {
            Spacer(Modifier.height(MoSpacing.sm))
            Text("Mi Cuaderno", style = MaterialTheme.typography.headlineLarge, color = MoOliveDark)
            Text("¿Qué has hecho hoy?", style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary)
            MoPrimaryButton("Registrar hoy", onRegisterToday, Modifier.fillMaxWidth().testTag("notebook-register-today"))
            MoSectionHeader("Cuaderno de cada finca")
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.testTag("notebook-root-loading"))
                state.error != null -> MoErrorState("No pudimos abrir tus fincas", state.error, onRetry = onRetry)
                state.farms.isEmpty() -> MoEmptyState(
                    "Aún no tienes fincas",
                    "Crea una finca en Mi Campo y aquí tendrás su cuaderno.",
                    icon = MoIcons.Tree,
                    modifier = Modifier.testTag("notebook-root-no-farms"),
                )
                else -> state.farms.forEach { farm ->
                    MoCompactListItem(
                        title = farm.name,
                        subtitle = farm.activeCampaignName?.let { "Campaña $it" } ?: "Sin campaña en marcha",
                        icon = MoIcons.Notebook,
                        onClick = { onOpenFarmNotebook(farm.id) },
                        modifier = Modifier.testTag("notebook-root-farm"),
                    )
                }
            }
            Spacer(Modifier.height(MoSpacing.lg))
        }
    }
}
