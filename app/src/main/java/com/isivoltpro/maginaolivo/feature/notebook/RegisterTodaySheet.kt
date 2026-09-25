package com.isivoltpro.maginaolivo.feature.notebook

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoInk
import com.isivoltpro.maginaolivo.ui.theme.MoOliveDark
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoTextSecondary

/**
 * UX-D (Issue #246 §3) — "Registrar hoy": ¿Qué has hecho hoy? One choice, then only the form
 * of that thing, already in the Farm shown here. The same nine choices as the quick actions,
 * routed to the same existing forms: there is no second register system.
 */
@Composable
fun RegisterTodaySheet(
    /** "Finca · Campaña", shown before anything is saved; null when no Farm is chosen yet. */
    context: String?,
    onChoose: (NotebookQuickAction) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = MoSpacing.screen)
            .padding(bottom = MoSpacing.sm)
            .testTag("register-action-sheet"),
        verticalArrangement = Arrangement.spacedBy(MoSpacing.xs),
    ) {
        Text("¿Qué has hecho hoy?", style = MaterialTheme.typography.headlineMedium, color = MoInk)
        Text(
            context ?: "Elige qué anotar. Se guarda primero en este teléfono.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (context != null) MoOliveDark else MoTextSecondary,
            modifier = Modifier.testTag("register-today-context"),
        )
        NotebookQuickAction.entries.forEach { action ->
            MoCompactListItem(
                title = action.label,
                subtitle = action.description,
                icon = action.icon(),
                onClick = { onChoose(action) },
                modifier = Modifier.testTag("register-today-${action.name.lowercase()}"),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            MoTertiaryButton("Cancelar", onCancel, modifier = Modifier.testTag("quick-add-cancel"))
        }
    }
}
