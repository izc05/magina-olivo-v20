package com.isivoltpro.maginaolivo.feature.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.app.AppearanceMode
import com.isivoltpro.maginaolivo.app.AppearanceStore
import com.isivoltpro.maginaolivo.ui.components.MoCompactListItem
import com.isivoltpro.maginaolivo.ui.components.MoIcons
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.MoColors
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Device appearance only: selection reflects confirmed persistence, never a pending write. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppearanceSettings(store: AppearanceStore) {
    val mode by store.mode.collectAsStateWithLifecycle()
    var visible by rememberSaveable { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    MoCompactListItem(
        title = "Apariencia",
        subtitle = mode.label(),
        icon = MoIcons.Sun,
        onClick = { failed = false; visible = true },
        modifier = Modifier.testTag("profile-appearance"),
    )
    if (visible) {
        ModalBottomSheet(
            onDismissRequest = { if (!saving) visible = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MoSurfaceTokens.cardSurface,
            contentColor = MoColors.current.bodyText,
            modifier = Modifier.testTag("appearance-sheet"),
        ) {
            Column(
                Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = MoSpacing.screen),
                verticalArrangement = Arrangement.spacedBy(MoSpacing.sm),
            ) {
                Text("Apariencia", style = MaterialTheme.typography.headlineSmall)
                Text("Elige cómo ver la aplicación en este teléfono.", style = MaterialTheme.typography.bodyMedium)
                Column(Modifier.selectableGroup()) {
                    AppearanceMode.entries.forEach { option ->
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("appearance-option-${option.name}")
                                .selectable(
                                    selected = mode == option,
                                    enabled = !saving,
                                    role = Role.RadioButton,
                                    onClick = {
                                        if (!saving) {
                                            saving = true
                                            failed = false
                                            scope.launch {
                                                try {
                                                    val saved = try {
                                                        store.setMode(option)
                                                    } catch (cancelled: CancellationException) {
                                                        throw cancelled
                                                    } catch (_: Exception) {
                                                        false
                                                    }
                                                    if (saved) visible = false else failed = true
                                                } finally {
                                                    saving = false
                                                }
                                            }
                                        }
                                    },
                                )
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(MoSpacing.sm),
                        ) {
                            RadioButton(selected = mode == option, onClick = null, enabled = !saving)
                            Text(option.label(), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (saving) Text("Guardando…", style = MaterialTheme.typography.bodyMedium)
                if (failed) Text(
                    "No se pudo guardar la apariencia. Inténtalo de nuevo.",
                    color = MoColors.current.errorText,
                    style = MaterialTheme.typography.bodyMedium,
                )
                MoTertiaryButton(
                    text = "Cancelar",
                    onClick = { visible = false },
                    enabled = !saving,
                    modifier = Modifier.fillMaxWidth().testTag("appearance-cancel").padding(bottom = MoSpacing.md),
                )
            }
        }
    }
}

private fun AppearanceMode.label(): String = when (this) {
    AppearanceMode.SYSTEM -> "Seguir sistema"
    AppearanceMode.LIGHT -> "Claro"
    AppearanceMode.DARK -> "Oscuro"
}
