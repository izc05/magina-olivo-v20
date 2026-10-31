package com.isivoltpro.maginaolivo.feature.farms

import com.isivoltpro.maginaolivo.ui.theme.MoColors

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwner
import com.isivoltpro.maginaolivo.domain.attachment.AttachmentOwnerType
import com.isivoltpro.maginaolivo.feature.activities.FarmActivitiesRoute
import com.isivoltpro.maginaolivo.feature.attachments.AttachmentsRoute
import com.isivoltpro.maginaolivo.feature.campaigns.FarmCampaignsRoute
import com.isivoltpro.maginaolivo.feature.parcels.FarmParcelsRoute
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.util.UUID
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

import androidx.compose.ui.Alignment
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.produceState
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.ui.components.MoErrorState

private sealed interface FarmRouteState {
    data object Loading : FarmRouteState
    data class Success(val farm: Farm) : FarmRouteState
    data object NotFound : FarmRouteState
}

/**
 * One part of a Farm on its own screen (design v3): the same sections that used to be stacked
 * on the Farm detail, now reached from the Farm hub so no screen needs a long scroll.
 */
@Composable
fun FarmSectionRoute(
    farmId: UUID,
    section: FarmSection,
    persistence: LocalPersistence,
    onParcelSelected: (UUID) -> Unit,
    onCampaignSelected: (UUID) -> Unit,
    onActivitySelected: (UUID) -> Unit,
    onImportFromCatastro: (() -> Unit)? = null,
    onMap: (() -> Unit)? = null,
) {
    val farmState by produceState<FarmRouteState>(initialValue = FarmRouteState.Loading, key1 = farmId) {
        persistence.farmRepository.observeById(farmId).collect { farm ->
            value = if (farm != null) FarmRouteState.Success(farm) else FarmRouteState.NotFound
        }
    }

    when (val current = farmState) {
        FarmRouteState.Loading -> {
            Scaffold(
                modifier = Modifier.fillMaxSize().testTag("farm-section-root"),
                containerColor = MoSurfaceTokens.appBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = MoSpacing.screen),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator(Modifier.testTag("farm-section-loading"))
                }
            }
        }
        FarmRouteState.NotFound -> {
            Scaffold(
                modifier = Modifier.fillMaxSize().testTag("farm-section-root"),
                containerColor = MoSurfaceTokens.appBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).padding(horizontal = MoSpacing.screen),
                    verticalArrangement = Arrangement.Center,
                ) {
                    MoErrorState(
                        title = "Finca no disponible",
                        body = "No encontramos esta finca o no tienes permiso para acceder a ella.",
                        modifier = Modifier.testTag("farm-section-unauthorized"),
                    )
                }
            }
        }
        is FarmRouteState.Success -> {
            val farm = current.farm
            Scaffold(
                modifier = Modifier.fillMaxSize().testTag("farm-section-root"),
                containerColor = MoSurfaceTokens.appBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
            ) { padding ->
                Column(
                    Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                        .padding(horizontal = MoSpacing.screen),
                    verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
                ) {
                    Spacer(Modifier.height(MoSpacing.sm))
                    Text(
                        section.title,
                        style = MaterialTheme.typography.headlineLarge,
                        color = MoColors.current.primaryText,
                        modifier = Modifier.semantics { heading() },
                    )
                    Text(farm.name, style = MaterialTheme.typography.bodyLarge, color = MoSurfaceTokens.secondaryText)
                    when (section) {
                        FarmSection.PARCELS -> FarmParcelsRoute(
                            farmId = farmId,
                            persistence = persistence,
                            onParcelSelected = onParcelSelected,
                            onImportFromCatastro = onImportFromCatastro,
                            onMap = onMap,
                        )
                        FarmSection.CAMPAIGNS -> FarmCampaignsRoute(farmId, persistence, onCampaignSelected)
                        // CR-011 §3: never shown here; the hub entry opens the one Cuaderno on this Farm.
                        FarmSection.NOTEBOOK -> Unit
                        FarmSection.ACTIVITIES -> FarmActivitiesRoute(
                            farmId = farmId,
                            persistence = persistence,
                            onActivitySelected = onActivitySelected,
                        )
                        FarmSection.DOCUMENTS -> AttachmentsRoute(
                            owner = AttachmentOwner(AttachmentOwnerType.FARM, farmId),
                            persistence = persistence,
                            title = "Documentos de la finca",
                        )
                    }
                    Spacer(Modifier.height(MoSpacing.lg))
                }
            }
        }
    }
}
