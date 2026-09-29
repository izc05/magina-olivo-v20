package com.isivoltpro.maginaolivo.feature.harvests

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.core.time.AppClock
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.ui.components.MoErrorState
import com.isivoltpro.maginaolivo.ui.theme.MoCream
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.ZoneId
import java.util.UUID

/**
 * CR-011 §8/§14 — Cuaderno → Jornal: finds or creates today's día de recolección of [farmId]
 * and shows it. Nobody opens a «jornada» by hand.
 *
 * The screen resolves the day itself instead of navigating once a background lookup ends: the
 * tap navigates at once, so no navigation ever runs from a coroutine, a rotation simply finds
 * the same day again (find-or-create is idempotent), and a failure is explained here instead
 * of landing silently somewhere else.
 */
@Composable
fun TodayHarvestRoute(
    farmId: UUID,
    persistence: LocalPersistence,
    clock: AppClock,
    onDeleted: () -> Unit,
    onAddPesada: (UUID) -> Unit = {},
    onPesadaSelected: (UUID) -> Unit = {},
    onExpenseSelected: (UUID) -> Unit = {},
) {
    var attempt by rememberSaveable { mutableIntStateOf(0) }
    val day by produceState<AppResult<UUID>?>(initialValue = null, farmId, attempt) {
        value = persistence.harvestRepository.openJornada(farmId, clock.today(ZoneId.systemDefault()))
    }
    when (val result = day) {
        null -> Box(
            Modifier.fillMaxSize().background(MoCream).testTag("harvest-today-loading"),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
        is AppResult.Success -> HarvestDetailRoute(
            harvestId = result.value,
            persistence = persistence,
            clock = clock,
            onDeleted = onDeleted,
            onAddPesada = onAddPesada,
            onPesadaSelected = onPesadaSelected,
            onExpenseSelected = onExpenseSelected,
        )
        is AppResult.Failure -> Box(
            Modifier.fillMaxSize().background(MoCream).statusBarsPadding().padding(MoSpacing.screen)
                .testTag("harvest-today-error"),
        ) {
            MoErrorState(
                title = "No se pudo abrir el día de hoy",
                body = when ((result.error as? AppError.Validation)?.field) {
                    "parcels" -> "La campaña no tiene parcelas: añádelas a la campaña para anotar jornales."
                    else -> harvestErrorMessage(result.error)
                },
                onRetry = { attempt++ },
            )
        }
    }
}
