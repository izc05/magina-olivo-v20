package com.isivoltpro.maginaolivo.feature.accountlink

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.ui.components.MoPrimaryButton
import com.isivoltpro.maginaolivo.ui.components.MoTertiaryButton
import com.isivoltpro.maginaolivo.ui.theme.*
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun FarmLinkPanel(repository: LocalFarmLinkRepository, userId: UUID) {
    val local by repository.state.collectAsStateWithLifecycle()
    var choices by remember(userId) { mutableStateOf<List<FarmChoice>?>(null) }
    var selected by remember(userId) { mutableStateOf(setOf<UUID>()) }
    var accepted by remember(userId) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope=rememberCoroutineScope()
    suspend fun reload() {
        try { choices=repository.choices();error=null }
        catch(e:CancellationException) { throw e }
        catch (_:Exception) { error="No se pudieron comprobar las fincas. Tus registros se conservan." }
    }
    LaunchedEffect(repository,userId) { reload() }
    Column(Modifier.fillMaxWidth().testTag("farm-link-panel"),verticalArrangement=Arrangement.spacedBy(MoSpacing.sm)) {
        HorizontalDivider()
        Text("Fincas y cuenta",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
        local.ledger?.let {
            Text("${it.farmIds.size} fincas preparadas en este móvil. Pendiente de confirmar en el servidor. No se han enviado datos.",Modifier.testTag("farm-link-pending"),color=MoSurfaceTokens.secondaryText)
        }
        Text("Elige las fincas que quieres asociar a esta cuenta. Se conservarán tus registros, fotos y documentos.",color=MoSurfaceTokens.secondaryText)
        val known=local.ledger?.farmIds.orEmpty()
        choices?.forEach { farm ->
            val prepared=farm.id in known
            Row(Modifier.fillMaxWidth().toggleable(value=prepared || farm.id in selected,enabled=!busy && !prepared,role=Role.Checkbox,
                onValueChange={ checked -> selected=if(checked)selected+farm.id else selected-farm.id;accepted=false })
                .padding(vertical=MoSpacing.sm).testTag("farm-link-choice-${farm.id}"),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=prepared || farm.id in selected,onCheckedChange=null,enabled=!busy && !prepared)
                Text(if(prepared)"${farm.name} · pendiente de confirmar" else farm.name,color=MoColors.current.bodyText)
            }
        }
        if(choices?.isEmpty()==true)Text("Todavía no hay fincas para seleccionar.",color=MoSurfaceTokens.secondaryText)
        if(selected.isNotEmpty()) {
            Text("Seleccionadas: ${choices.orEmpty().filter { it.id in selected }.joinToString { it.name }}",Modifier.testTag("farm-link-summary"),color=MoColors.current.bodyText)
            Text("Las demás fincas seguirán solo en este móvil. Para proteger los registros comunes, las fincas de este móvil solo se abrirán con esta cuenta.",color=MoSurfaceTokens.secondaryText)
            Row(Modifier.fillMaxWidth().toggleable(value=accepted,enabled=!busy,role=Role.Checkbox,onValueChange={accepted=it})
                .padding(vertical=MoSpacing.sm).testTag("farm-link-consent"),verticalAlignment=Alignment.CenterVertically) {
                Checkbox(checked=accepted,onCheckedChange=null,enabled=!busy)
                Text("Quiero asociar las fincas seleccionadas a esta cuenta.",color=MoColors.current.bodyText)
            }
            MoPrimaryButton(if(busy)"Guardando copia y consentimiento…" else "Preparar vinculación",{
                val snapshot=choices.orEmpty().filter { it.id in selected }
                busy=true;error=null
                scope.launch {
                    try { repository.consent(userId,snapshot,accepted);selected=emptySet();accepted=false;reload() }
                    catch(e:CancellationException) { throw e }
                    catch (_:Exception) { error="No se pudo preparar la vinculación. Revisa la cuenta y las fincas o libera espacio. Tus registros se conservan." }
                    finally { busy=false }
                }
            },Modifier.fillMaxWidth().testTag("farm-link-confirm"),enabled=accepted && !busy)
            MoTertiaryButton("Cancelar selección",{selected=emptySet();accepted=false;error=null},Modifier.testTag("farm-link-cancel"),enabled=!busy)
        }
        if(busy || (choices==null && error==null)) CircularProgressIndicator()
        error?.let { Text(it,Modifier.testTag("farm-link-error"),color=MoColors.current.errorText)
            MoTertiaryButton("Volver a comprobar",{scope.launch { reload() }},enabled=!busy) }
    }
}
