package com.isivoltpro.maginaolivo.feature.campaignhub

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.core.common.AppError
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.cycle.*
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.MoShape
import com.isivoltpro.maginaolivo.ui.theme.MoSpacing
import com.isivoltpro.maginaolivo.ui.theme.MoSurfaceTokens
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.launch

/** Viewing selection stays here; new-record forms independently select the Farm's OPEN cycle. */
@Composable
fun AgriculturalCyclePanel(repository: AgriculturalCycleRepository, farmId: UUID, today: LocalDate,
    onSelection: (CycleSnapshot?, UUID?) -> Unit = { _, _ -> }, onRecord: (CycleRecord) -> Unit = {}) {
    val result by remember(repository,farmId) { repository.observe(farmId) }
        .collectAsStateWithLifecycle(initialValue=null)
    val snapshot=(result as? AppResult.Success)?.value
    LaunchedEffect(result) { if(snapshot==null) onSelection(null,null) }
    when {
        result==null -> CircularProgressIndicator()
        snapshot==null -> Text(cycleMessage((result as AppResult.Failure).error),color=MaterialTheme.colorScheme.error)
        else -> key(snapshot.scope.workspaceId,snapshot.scope.ownerId,farmId) {
            CyclePanelContent(repository,snapshot,snapshot.today ?: today,onSelection,onRecord)
        }
    }
}

@Composable
private fun CyclePanelContent(repository: AgriculturalCycleRepository, snapshot: CycleSnapshot, today: LocalDate,
    onSelection: (CycleSnapshot?,UUID?) -> Unit, onRecord: (CycleRecord) -> Unit) {
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val selected=snapshot.cycles.firstOrNull {it.id.toString()==picked}
        ?: snapshot.cycles.firstOrNull {it.effectiveEndDate==null} ?: snapshot.cycles.firstOrNull()
    var view by rememberSaveable { mutableStateOf("") }
    var label by rememberSaveable { mutableStateOf("") }
    var date by rememberSaveable { mutableStateOf("") }
    var noRecollection by rememberSaveable { mutableStateOf(false) }
    var operation by rememberSaveable { mutableStateOf(UUID.randomUUID().toString()) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var preview by remember { mutableStateOf<CycleClosePreview?>(null) }
    var suggestion by remember { mutableStateOf<LocalDate?>(null) }
    var record by remember { mutableStateOf<CycleRecord?>(null) }
    val coroutine=rememberCoroutineScope()
    val reset: () -> Unit = { view="";preview=null;record=null;error=null }
    BackHandler(view.isNotEmpty()) { if(!busy) reset() }
    LaunchedEffect(snapshot,selected?.id) { onSelection(snapshot,selected?.id) }

    Surface(shape=MoShape.card,color=MoSurfaceTokens.cardSurface,border=BorderStroke(1.dp,MoSurfaceTokens.cardStroke),
        modifier=Modifier.fillMaxWidth().testTag("agricultural-cycle-panel")) {
        Column(Modifier.fillMaxWidth().imePadding().padding(MoSpacing.md),verticalArrangement=Arrangement.spacedBy(MoSpacing.sm)) {
            Text("Campaña agrícola",style=MaterialTheme.typography.titleLarge)
            message?.let { Text(it,modifier=Modifier.testTag("cycle-saved")) }
            error?.let { Text(it,color=MaterialTheme.colorScheme.error,modifier=Modifier.testTag("cycle-error")) }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            when(view) {
                "create" -> {
                    Text("Nueva campaña para esta finca",style=MaterialTheme.typography.titleMedium)
                    MoTextField(label,{label=it},"Nombre de la campaña",modifier=Modifier.testTag("cycle-label"))
                    MoDateInputField(date,{date=it},"Fecha de inicio",modifier=Modifier.testTag("cycle-start-date"))
                    Text("Tus registros anteriores se conservan sin cambios. Podrás vincularlos uno a uno.",style=MaterialTheme.typography.bodySmall)
                    MoPrimaryButton("Crear campaña agrícola",{
                        val start=runCatching {LocalDate.parse(date)}.getOrNull()
                        if(start==null) error="Elige una fecha de inicio válida."
                        else {busy=true;error=null;coroutine.launch {
                            when(val saved=repository.create(snapshot.scope,label,start,UUID.fromString(operation))) {
                                is AppResult.Failure -> error=cycleMessage(saved.error)
                                is AppResult.Success -> {picked=saved.value.toString();reset();message="Campaña guardada en este teléfono."}
                            };busy=false
                        }}
                    },Modifier.fillMaxWidth().testTag("cycle-create-confirm"),enabled=!busy)
                    MoTertiaryButton("Cancelar",reset,Modifier.testTag("cycle-form-cancel"),enabled=!busy)
                }
                "close" -> if(selected!=null) {
                    Text("Cerrar ${selected.label}",style=MaterialTheme.typography.titleMedium)
                    Text("Elige el día real en que terminó esta campaña, aunque lo confirmes hoy.")
                    suggestion?.let { Text("Última pesada: ${it.cycleDate()} · fecha sugerida",style=MaterialTheme.typography.bodySmall) }
                    MoDateInputField(date,{date=it;preview=null},"Fecha efectiva de cierre",modifier=Modifier.testTag("cycle-effective-end"))
                    if(snapshot.records.none {it.cycleId==selected.id && it.kind=="RECOLLECTION"}) {
                        Row(Modifier.fillMaxWidth().testTag("cycle-no-recollection").clickable(enabled=!busy) {
                            noRecollection=!noRecollection;preview=null
                        }) { Checkbox(noRecollection,null);Text("Esta finca no ha tenido recolección",Modifier.weight(1f)) }
                    }
                    MoSecondaryButton("Revisar cierre",{
                        val end=runCatching {LocalDate.parse(date)}.getOrNull()
                        if(end==null) error="Elige una fecha de cierre válida."
                        else {busy=true;error=null;coroutine.launch {
                            when(val checked=repository.previewClose(selected.id,end,noRecollection)) {
                                is AppResult.Failure -> error=cycleMessage(checked.error)
                                is AppResult.Success -> preview=checked.value
                            };busy=false
                        }}
                    },Modifier.fillMaxWidth().testTag("cycle-review-close"),enabled=!busy)
                    preview?.let { checked ->
                        Text("Cierre efectivo: ${checked.effectiveEndDate.cycleDate()}",style=MaterialTheme.typography.titleMedium)
                        Text("${checked.records.size} registros vinculados. No cambiaremos sus fechas ni importes.")
                        if(checked.unassignedCount>0) Text("Quedan ${checked.unassignedCount} registros sin asignar en esta finca. No se vincularán automáticamente.")
                        checked.blockers.forEach { Text(cycleCodeMessage(it),color=MaterialTheme.colorScheme.error) }
                        if(checked.blockers.isEmpty()) MoPrimaryButton("Confirmar cierre",{
                            busy=true;error=null;coroutine.launch {
                                when(val saved=repository.close(checked,UUID.fromString(operation))) {
                                    is AppResult.Failure -> {error=cycleMessage(saved.error);preview=null}
                                    is AppResult.Success -> {reset();message="Campaña cerrada. Tu histórico se conserva."}
                                };busy=false
                            }
                        },Modifier.fillMaxWidth().testTag("cycle-close-confirm"),enabled=!busy)
                    }
                    Text("El cierre agrícola es independiente de las liquidaciones y cobros posteriores.",style=MaterialTheme.typography.bodySmall)
                    MoTertiaryButton("Cancelar",reset,Modifier.testTag("cycle-form-cancel"),enabled=!busy)
                }
                "records","assign" -> if(selected!=null) {
                    Text(if(view=="assign") "Vincular a ${selected.label}" else "Registros de ${selected.label}",style=MaterialTheme.typography.titleMedium)
                    if(view=="assign") Text("Elige un registro y confirma su destino. No cambiamos fechas ni cantidades.")
                    // A closed cycle never receives money by assignment; do not offer an expense it would reject.
                    val rows=snapshot.records.filter {if(view=="assign") it.cycleId==null &&
                        (selected.effectiveEndDate==null || it.kind!=CycleRecordKind.EXPENSE.name) else it.cycleId==selected.id}
                        .sortedBy {when(it.kind) {"RECOLLECTION"->0;"ACTIVITY"->1;else->2}}
                    if(rows.isEmpty()) Text(if(view=="assign") "No quedan registros sin asignar." else "Todavía no hay registros vinculados.")
                    rows.forEach { row -> MoCompactListItem(title=row.title,subtitle="${row.date.cycleDate()} · ${row.kind.cycleKind()}",
                        icon=MoIcons.Activity,onClick={if(view=="assign"){record=row;operation=UUID.randomUUID().toString()} else onRecord(row)}) }
                    record?.let { chosen ->
                        Text("¿Vincular «${chosen.title}» a «${selected.label}»?",style=MaterialTheme.typography.titleMedium)
                        MoPrimaryButton("Confirmar vinculación",{
                            busy=true;error=null;coroutine.launch {
                                when(val saved=repository.assign(snapshot.scope,selected.id,CycleRecordKind.valueOf(chosen.kind),
                                    chosen.id,"Vinculación confirmada por el agricultor",UUID.fromString(operation))) {
                                    is AppResult.Failure -> error=cycleMessage(saved.error)
                                    is AppResult.Success -> {record=null;message="Registro vinculado. Los datos originales se conservan."}
                                };busy=false
                            }
                        },Modifier.fillMaxWidth().testTag("cycle-assign-confirm"),enabled=!busy)
                    }
                    MoTertiaryButton("Volver",reset,enabled=!busy)
                }
                else -> {
                    if(selected==null) {
                        Text("Aún no hay una campaña agrícola.")
                        Text("Los registros anteriores se conservan sin asignar.",style=MaterialTheme.typography.bodySmall)
                    } else {
                        var menu by remember {mutableStateOf(false)}
                        Box {
                            MoSecondaryButton(selected.label,{menu=true},Modifier.fillMaxWidth().testTag("cycle-selector"))
                            DropdownMenu(menu,{menu=false}) {snapshot.cycles.forEach {cycle->DropdownMenuItem(
                                text={Text("${cycle.label} · ${if(cycle.effectiveEndDate==null) "Abierta" else "Cerrada"}")},
                                onClick={picked=cycle.id.toString();menu=false;message=null;error=null})}}
                        }
                        Text(if(selected.effectiveEndDate==null) "Abierta" else "Cerrada agrícolamente",style=MaterialTheme.typography.titleMedium)
                        Text("Desde ${selected.startDate.cycleDate()}${selected.effectiveEndDate?.let {" hasta ${it.cycleDate()}"}.orEmpty()}")
                        if(selected.effectiveEndDate!=null) Text("Estás consultando el histórico. Los registros nuevos usarán la campaña abierta de esta finca.",
                            style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("cycle-history-does-not-write"))
                        MoSecondaryButton("Ver registros",{view="records"},Modifier.fillMaxWidth().testTag("cycle-records"))
                        MoTertiaryButton("Vincular registros anteriores",{view="assign";message=null},Modifier.testTag("cycle-assign-open"))
                        if(selected.effectiveEndDate==null) MoSecondaryButton("Cerrar campaña agrícola",{
                            busy=true;error=null;message=null;operation=UUID.randomUUID().toString();noRecollection=false
                            coroutine.launch {
                                when(val initial=repository.previewClose(selected.id,today,false)) {
                                    is AppResult.Failure -> error=cycleMessage(initial.error)
                                    is AppResult.Success -> {suggestion=initial.value.lastDeliveryDate;date=(suggestion ?: today).toString();preview=null;view="close"}
                                };busy=false
                            }
                        },Modifier.fillMaxWidth().testTag("cycle-close-open"),enabled=!busy)
                    }
                    if(snapshot.cycles.none {it.effectiveEndDate==null}) MoPrimaryButton("Crear campaña agrícola",{
                        val start=snapshot.cycles.mapNotNull {it.effectiveEndDate}.maxOrNull()?.plusDays(1) ?: today
                        date=start.toString();label="${start.year}/${(start.year+1).toString().takeLast(2)}"
                        operation=UUID.randomUUID().toString();view="create";message=null;error=null
                    },Modifier.fillMaxWidth().testTag("cycle-new"),enabled=!busy)
                    val sync=cycleSyncText(snapshot.sync)
                    Text(sync,style=MaterialTheme.typography.bodySmall,modifier=Modifier.testTag("cycle-sync-state"),
                        color=if(snapshot.sync?.status in setOf("CONFLICT","NEEDS_REVIEW","NOT_AUTHORIZED")) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

internal fun cycleSyncText(sync: com.isivoltpro.maginaolivo.domain.cycle.CycleSyncView?): String = when(sync?.status) {
    null -> "Guardada en este teléfono."
    "SYNCED" -> "Guardada en este teléfono y sincronizada con tu cuenta."
    "WAITING_PARENTS" -> "Guardada en este teléfono. Se enviará cuando la finca y sus recolecciones estén sincronizadas."
    "CONFLICT" -> "Otro dispositivo ha cambiado esta campaña agrícola. No se ha sobrescrito nada: queda pendiente de revisión."
    "NOT_AUTHORIZED" -> "Guardada en este teléfono. Solo el propietario del espacio puede sincronizarla."
    "NEEDS_REVIEW" -> "Guardada en este teléfono. El servidor no la aceptó tal cual: queda pendiente de revisión."
    else -> "Guardada en este teléfono. Pendiente de enviar en la próxima sincronización."
}

internal fun LocalDate.cycleDate(): String = format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
private fun String.cycleKind()=when(this) {"RECOLLECTION"->"Recolección";"EXPENSE"->"Gasto";else->"Trabajo"}
internal fun cycleMessage(error: AppError): String = when(error) {
    is AppError.Validation -> cycleCodeMessage(error.code)
    is AppError.Conflict -> "Los datos han cambiado. Revísalos de nuevo antes de confirmar."
    is AppError.Permission -> "No podemos confirmar tu acceso a esta finca. Revisa tu cuenta y espacio de trabajo."
    else -> "No se pudo guardar. Tus datos anteriores se conservan; puedes volver a intentarlo."
}
internal fun cycleCodeMessage(code: String): String = when(code) {
    "invalid_label"->"Escribe un nombre de hasta 80 caracteres."
    "cycle_overlap"->"Estas fechas se solapan con otra campaña. Revisa el inicio y el cierre anterior."
    "before_start","outside_cycle"->"La fecha queda fuera de esta campaña. Revisa las fechas y el ciclo elegido."
    "future_end"->"El cierre no puede ser posterior a hoy."
    "records_after_end"->"Hay trabajos o recolecciones posteriores al cierre elegido. Revísalos antes de confirmar."
    "recollections_not_closed"->"Finaliza primero las recolecciones vinculadas a esta campaña."
    "confirm_no_recollection"->"Confirma si esta finca no ha tenido recolección."
    "historical_cycle_review_required"->"Este cambio alteraría la campaña agrícola del registro histórico. Revisa primero su vinculación en Campaña."
    "has_no_recollection"->"Este cierre se confirmó sin recolección. No se puede añadir una recolección después."
    "has_recollection"->"Esta campaña ya tiene una recolección vinculada."
    "assign_parent_first"->"Vincula primero su recolección o trabajo a una campaña agrícola."
    "cycle_closed","already_closed"->"Esta campaña está cerrada. Los trabajos nuevos necesitan una campaña abierta."
    "source_cycle_closed"->"Este registro pertenece a una campaña cerrada y conserva su historial. No se puede mover a otra campaña."
    "cycle_selection_required"->"Selecciona una campaña abierta o créala en Campaña antes de guardar."
    "idempotency_conflict"->"Esta confirmación ya corresponde a otros datos. Revisa la campaña guardada."
    else->"No se puede confirmar con estos datos. Revisa la finca, las fechas y los registros elegidos."
}
