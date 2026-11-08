package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag

/** Whether the form inside a [MoFormSheet] holds something the farmer would lose by closing it. */
@Stable
class MoFormGuard {
    internal var compared by mutableStateOf<Boolean?>(null)
    private var edited by mutableStateOf(false)

    /** A form comparing all its values ([moFormChanges]) is exact; any other counts typed text. */
    val dirty: Boolean get() = compared ?: edited

    fun noteEdit() { edited = true }
}

val LocalMoFormGuard = compositionLocalOf<MoFormGuard?> { null }

/** True while [current] differs from what the form opened with; a value typed and undone is no change. */
@Composable
fun moFormChanges(opening: Any?, current: List<Any?>): Boolean {
    val baseline = remember(opening) { current }
    val changed = current != baseline
    val guard = LocalMoFormGuard.current
    SideEffect { guard?.compared = changed }
    return changed
}

@Composable
fun MoDiscardChangesDialog(onKeepEditing: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        modifier = Modifier.testTag("form-discard-dialog"),
        title = { Text("¿Descartar los cambios?") },
        text = { Text("Lo que has escrito sin guardar se perderá.") },
        confirmButton = { TextButton(onKeepEditing, Modifier.testTag("form-keep-editing")) { Text("Seguir editando") } },
        dismissButton = { TextButton(onDiscard, Modifier.testTag("form-discard")) { Text("Descartar cambios") } },
    )
}

/** A bottom sheet holding a form: touching outside, swiping down or Atrás ask before losing changes. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoFormSheet(
    onDismissRequest: () -> Unit,
    containerColor: Color = BottomSheetDefaults.ContainerColor,
    skipPartiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val guard = remember { MoFormGuard() }
    var asking by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = skipPartiallyExpanded,
        confirmValueChange = { target ->
            val blocked = target == SheetValue.Hidden && guard.dirty
            if (blocked) asking = true
            !blocked
        },
    )
    ModalBottomSheet(
        onDismissRequest = { if (guard.dirty) asking = true else onDismissRequest() },
        sheetState = sheetState,
        containerColor = containerColor,
    ) {
        CompositionLocalProvider(LocalMoFormGuard provides guard) { content() }
        if (asking) MoDiscardChangesDialog({ asking = false }, { asking = false; onDismissRequest() })
    }
}
