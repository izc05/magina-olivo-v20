package com.isivoltpro.maginaolivo.feature.sync

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * «Sincronizar ahora». The only trigger of [ManualSync]: no automatic or periodic synchronization exists.
 * [sync] is null in builds without synchronization (staging, production), and the panel says so.
 */
@Composable
fun ManualSyncPanel(sync: ManualSync?, online: Boolean) {
    Column(Modifier.fillMaxWidth().testTag("manual-sync-panel"), verticalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
        HorizontalDivider()
        Text("Sincronización", style = MaterialTheme.typography.titleLarge, color = MoColors.current.primaryText)
        if (sync == null) {
            Text("La sincronización no está disponible en esta versión. Tus datos siguen guardados en este teléfono.",
                Modifier.testTag("manual-sync-unavailable"), color = MoSurfaceTokens.secondaryText)
            return@Column
        }
        val state by sync.state.collectAsStateWithLifecycle()
        val running = state is ManualSyncState.Running
        Text("Envía lo que has registrado en este teléfono y recibe lo de tus otros dispositivos. Solo se sincroniza cuando pulsas el botón.",
            color = MoSurfaceTokens.secondaryText)
        MoPrimaryButton(if (running) "Sincronizando…" else "Sincronizar ahora", { sync.requestSync() },
            Modifier.fillMaxWidth().testTag("manual-sync-now"), enabled = online && !running)
        if (!online && !running) Text("Sin conexión. Conéctate para sincronizar.", color = MoSurfaceTokens.secondaryText)
        val live = Modifier.testTag("manual-sync-status").semantics { liveRegion = LiveRegionMode.Polite }
        when (val s = state) {
            ManualSyncState.Idle -> Unit
            is ManualSyncState.Running -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm)) {
                CircularProgressIndicator(Modifier.size(20.dp).testTag("manual-sync-progress"))
                Text("Sincronizando · ${s.step}", live, color = MoColors.current.bodyText)
            }
            is ManualSyncState.Completed -> {
                Text("Completado · ${time(s.at)} · ${s.sent} enviados, ${s.received} recibidos", live, color = MoColors.current.bodyText)
                s.notes.forEach { Text(it, color = MoSurfaceTokens.secondaryText) }
            }
            is ManualSyncState.Pending -> {
                Text("Pendiente · ${time(s.at)} · ${s.sent} enviados, ${s.received} recibidos", live, color = MoColors.current.bodyText)
                s.reasons.forEach { Text("• $it", Modifier.testTag("manual-sync-reason"), color = MoSurfaceTokens.secondaryText) }
            }
            is ManualSyncState.Failed -> {
                Text("Error · ${time(s.at)}", live, color = MoColors.current.errorText)
                Text(s.message, Modifier.testTag("manual-sync-error"), color = MoColors.current.errorText)
                Text("Tus datos siguen guardados en este teléfono. Puedes volver a intentarlo.", color = MoSurfaceTokens.secondaryText)
            }
        }
    }
}

private val clock = DateTimeFormatter.ofPattern("HH:mm")
private fun time(at: java.time.Instant) = clock.format(at.atZone(ZoneId.systemDefault()))
