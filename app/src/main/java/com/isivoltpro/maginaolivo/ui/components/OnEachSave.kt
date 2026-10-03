package com.isivoltpro.maginaolivo.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * #380 — runs [onSaved] each time [saveCount] rises, so an editor closes after every save.
 * Keying on the success message missed a second save with the same message (the null in
 * between can fall inside one frame). A lower count is a new ViewModel after process death,
 * not a save: it only rebases, so a restored editor and its draft stay open.
 */
@Composable
fun OnEachSave(saveCount: Int, onSaved: () -> Unit) {
    var seen by rememberSaveable { mutableIntStateOf(saveCount) }
    val latest by rememberUpdatedState(onSaved)
    LaunchedEffect(saveCount) {
        if (saveCount < seen) seen = saveCount
        if (saveCount > seen) {
            seen = saveCount
            latest()
        }
    }
}
