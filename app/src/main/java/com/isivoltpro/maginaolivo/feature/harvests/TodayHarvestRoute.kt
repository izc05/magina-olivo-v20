package com.isivoltpro.maginaolivo.feature.harvests

import android.util.Log
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
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import java.time.ZoneId
import java.util.UUID
import kotlinx.coroutines.withTimeoutOrNull
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens

internal const val LOG_TAG = "MaginaOlivo"
private const val OPEN_DAY_TIMEOUT_MS = 10_000L
internal const val JORNADA_RESOURCE_LABOUR = "labour"

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
        // Device check (build 683): the shortcut sat on a spinner. Whatever holds the lookup up,
        // the wait is bounded: after it the screen says so, offers «Reintentar» and logs it.
        value = withTimeoutOrNull(OPEN_DAY_TIMEOUT_MS) {
            persistence.harvestRepository.openJornada(farmId, clock.today(ZoneId.systemDefault()))
        } ?: AppResult.Failure(AppError.Storage("open_jornada_timeout", null)).also {
            Log.w(LOG_TAG, "Jornal: opening today's day of farm $farmId took over ${OPEN_DAY_TIMEOUT_MS} ms")
        }
        (value as? AppResult.Failure)?.let { Log.w(LOG_TAG, "Jornal: today's day of farm $farmId not opened: ${it.error}") }
    }
    when (val result = day) {
        null -> Box(
            Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).testTag("harvest-today-loading"),
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
            // #365: reached from Cuaderno → Jornal, so the day opens on its Jornales.
            initialResource = JORNADA_RESOURCE_LABOUR,
        )
        is AppResult.Failure -> Box(
            Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).statusBarsPadding().padding(MoSpacing.screen)
                .testTag("harvest-today-error"),
        ) {
            MoErrorState(
                title = "No se pudo abrir el día de hoy",
                body = when ((result.error as? AppError.Validation)?.field) {
                    "parcels" -> "La campaña no tiene parcelas: añádelas a la campaña para anotar jornales."
                    else -> if ((result.error as? AppError.Storage)?.operation == "open_jornada_timeout") {
                        "Está tardando más de lo normal. Pulsa «Reintentar»."
                    } else {
                        harvestErrorMessage(result.error)
                    }
                },
                onRetry = { attempt++ },
            )
        }
    }
}
