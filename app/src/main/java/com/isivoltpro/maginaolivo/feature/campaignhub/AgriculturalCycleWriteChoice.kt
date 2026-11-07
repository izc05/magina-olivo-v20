package com.isivoltpro.maginaolivo.feature.campaignhub

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.cycle.AgriculturalCycleRepository
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.LocalDate
import java.util.UUID

/**
 * Compact and read-only, for writers that inherit their cycle from a parent record (campaign jornal).
 * Shows a single line only when saving would be rejected: the parent is not linked, or its cycle is closed.
 */
@Composable
fun AgriculturalCycleParentNotice(repository: AgriculturalCycleRepository?, farmId: UUID?, parentRecordId: UUID,
    modifier: Modifier=Modifier) {
    if(repository==null || farmId==null) return
    val result by remember(repository,farmId) {repository.observe(farmId)}.collectAsStateWithLifecycle(initialValue=null)
    val message=when(val current=result) {
        null -> null
        is AppResult.Failure -> cycleMessage(current.error)
        is AppResult.Success -> parentNoticeMessage(current.value,parentRecordId)
    } ?: return
    Text(message,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.error,
        modifier=modifier.testTag("cycle-parent-notice"))
}

internal fun parentNoticeMessage(snapshot: com.isivoltpro.maginaolivo.domain.cycle.CycleSnapshot, parentRecordId: UUID): String? {
    if(snapshot.cycles.isEmpty()) return null
    val parent=snapshot.records.firstOrNull {it.id==parentRecordId}?.cycleId
    val cycle=snapshot.cycles.firstOrNull {it.id==parent}
    return when {
        cycle==null -> "Vincula primero la recolección a una campaña agrícola para guardar jornales."
        cycle.effectiveEndDate!=null -> "La campaña agrícola «${cycle.label}» está cerrada y no admite jornales nuevos."
        else -> null
    }
}

/** Only new drafts. Never receives the cycle selected while browsing the historical dashboard. */
@Composable
fun AgriculturalCycleWriteChoice(repository: AgriculturalCycleRepository?, farmId: UUID?, value: UUID?,
    onValueChange: (UUID?) -> Unit, parentRecordId: UUID?=null, enabled: Boolean=true) {
    if(repository==null || farmId==null) return
    val result by remember(repository,farmId) {repository.observe(farmId)}.collectAsStateWithLifecycle(initialValue=null)
    val snapshot=(result as? AppResult.Success)?.value
    if(snapshot==null) {
        if(result is AppResult.Failure) {
            LaunchedEffect(result) {onValueChange(null)}
            Text(cycleMessage((result as AppResult.Failure).error),color=MaterialTheme.colorScheme.error)
        } else Text("Comprobando la campaña agrícola…",style=MaterialTheme.typography.bodySmall)
        return
    }
    key(snapshot.scope.workspaceId,snapshot.scope.ownerId,farmId) {
        val open=snapshot.cycles.filter {it.effectiveEndDate==null}
        val parent=parentRecordId?.let {id->snapshot.records.firstOrNull {it.id==id}?.cycleId}
        var initialized by rememberSaveable {mutableStateOf(false)}
        var invalidated by rememberSaveable {mutableStateOf(false)}
        var menu by remember {mutableStateOf(false)}
        var manage by rememberSaveable {mutableStateOf(false)}
        LaunchedEffect(snapshot.scope,open.map {it.id},value,parentRecordId,parent) {
            if(parentRecordId!=null) {
                initialized=true
                if(value!=parent) onValueChange(parent)
            } else if(!initialized) {
                initialized=true
                if(value==null && open.size==1) onValueChange(open.single().id)
                else if(value!=null && open.none {it.id==value}) {invalidated=true;onValueChange(null)}
            } else if(value!=null && open.none {it.id==value}) {
                invalidated=true;onValueChange(null)
            }
        }
        Column(Modifier.fillMaxWidth().testTag("cycle-write-choice"),verticalArrangement=Arrangement.spacedBy(MoSpacing.xxs)) {
            Text("Campaña agrícola del registro",style=MaterialTheme.typography.labelMedium)
            val inherited=snapshot.cycles.firstOrNull {it.id==parent}
            when {
                parentRecordId!=null && inherited!=null -> {
                    Text("${inherited.label} · ${if(inherited.effectiveEndDate==null) "Abierta" else "Cerrada"}")
                    if(inherited.effectiveEndDate!=null) Text("Esta campaña está cerrada. Los trabajos nuevos necesitan una campaña abierta.",style=MaterialTheme.typography.bodySmall)
                }
                snapshot.cycles.isEmpty() -> Text("Sin campaña agrícola. El registro se conservará sin asignar.",style=MaterialTheme.typography.bodySmall)
                parentRecordId!=null -> Text("Vincula primero la recolección o el trabajo a una campaña agrícola.",style=MaterialTheme.typography.bodySmall)
                open.isEmpty() -> Text("No hay una campaña abierta. Puedes crearla sin perder este formulario.",style=MaterialTheme.typography.bodySmall)
                else -> Box {
                    MoSecondaryButton(open.firstOrNull {it.id==value}?.label ?: "Seleccionar campaña abierta",{menu=true},
                        Modifier.fillMaxWidth().testTag("cycle-write-selector"),enabled=enabled)
                    DropdownMenu(menu,{menu=false}) {open.forEach {cycle->DropdownMenuItem(text={Text(cycle.label)},
                        onClick={onValueChange(cycle.id);invalidated=false;menu=false})}}
                }
            }
            if(invalidated) Text("La campaña elegida ya no está abierta. Revisa y selecciona el destino antes de guardar.",
                style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("cycle-write-review-required"))
            if(snapshot.cycles.isEmpty() || open.isEmpty() && parentRecordId==null || parentRecordId!=null && parent==null) {
                MoTertiaryButton("Crear o vincular campaña",{manage=true},Modifier.testTag("cycle-write-manage"),enabled=enabled)
            }
        }
        if(manage) Dialog(onDismissRequest={manage=false},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            Surface(color=MoSurfaceTokens.appBackground,modifier=Modifier.fillMaxWidth().fillMaxHeight(.95f).padding(8.dp)) {
                Column(Modifier.safeDrawingPadding().imePadding().verticalScroll(rememberScrollState()).padding(MoSpacing.sm)) {
                    MoTertiaryButton("← Volver al formulario",{manage=false})
                    Text("El borrador del registro se conserva.",style=MaterialTheme.typography.bodySmall)
                    AgriculturalCyclePanel(repository,farmId,snapshot.today ?: LocalDate.now(),onSelection={state,_->
                        val target=if(parentRecordId==null) state?.cycles?.singleOrNull {it.effectiveEndDate==null}?.id
                            else state?.records?.firstOrNull {it.id==parentRecordId}?.cycleId?.takeIf {id->state.cycles.any {it.id==id && it.effectiveEndDate==null}}
                        if(target!=null) {onValueChange(target);invalidated=false;manage=false}
                    })
                }
            }
        }
    }
}
