package com.isivoltpro.maginaolivo.feature.farms

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
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary
import java.util.UUID

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
) {
    val farmFlow = remember(farmId) { persistence.farmRepository.observeById(farmId) }
    val farm by farmFlow.collectAsStateWithLifecycle(initialValue = null)
    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("farm-section-root"),
        containerColor = MoCream,
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
                color = MoOliveDark,
                modifier = Modifier.semantics { heading() },
            )
            farm?.let { Text(it.name, style = MaterialTheme.typography.bodyLarge, color = MoTextSecondary) }
            when (section) {
                FarmSection.PARCELS -> FarmParcelsRoute(
                    farmId = farmId,
                    persistence = persistence,
                    onParcelSelected = onParcelSelected,
                    onImportFromCatastro = onImportFromCatastro,
                )
                FarmSection.CAMPAIGNS -> FarmCampaignsRoute(farmId, persistence, onCampaignSelected)
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
