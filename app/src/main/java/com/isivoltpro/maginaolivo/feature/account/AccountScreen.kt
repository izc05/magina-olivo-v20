package com.isivoltpro.maginaolivo.feature.account

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.isivoltpro.maginaolivo.ui.brand.MaginaOlivoWordmark
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.BuildConfig
import com.isivoltpro.maginaolivo.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.launch
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceChoice
import com.isivoltpro.maginaolivo.domain.workspace.WorkspaceRepository
import java.util.UUID

private enum class AccountForm { LOGIN, REGISTER, RECOVER }

@Composable
fun AccountRoute(repository:AccountRepository,onBack:()->Unit,simulationAllowed:Boolean=false,connectivity:Flow<Boolean>? = null,
    farmLinks:com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository?=null,showBack:Boolean=true,
    workspaces:WorkspaceRepository?=null,manualSync:com.isivoltpro.maginaolivo.feature.sync.ManualSync?=null) {
    val context=LocalContext.current
    var simulation by rememberSaveable { mutableStateOf(false) }
    val simulated=remember { AccountRepository(SimulatedAccountAuthApi()) }
    val selected=if(simulation)simulated else repository
    val model:AccountViewModel=viewModel(key=if(simulation)"account-simulation" else "account-live",factory=viewModelFactory { initializer { AccountViewModel(selected) } })
    val state by model.state.collectAsStateWithLifecycle()
    val network=remember(context,connectivity) { connectivity ?: accountConnectivity(context) }
    val online by network.collectAsStateWithLifecycle(initialValue=false)
    LaunchedEffect(online,model) { model.resume(online) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { model.resume(online) }
    var form by rememberSaveable { mutableStateOf(AccountForm.LOGIN) }
    var email by rememberSaveable { mutableStateOf("") }
    // Passwords are intentionally not rememberSaveable/SavedStateHandle/Bundle data.
    var password by remember { mutableStateOf("") }
    LaunchedEffect(state.status) { password="" }
    val enabled=state.status!=AccountStatus.UNCONFIGURED && online && !state.busy
    val scope=rememberCoroutineScope()
    var workspaceChoices by remember { mutableStateOf<List<WorkspaceChoice>>(emptyList()) }
    var selectedWorkspace by remember { mutableStateOf<UUID?>(null) }
    var workspaceNotice by remember { mutableStateOf<String?>(null) }
    var confirmDeletionRequest by rememberSaveable { mutableStateOf(false) }
    if (confirmDeletionRequest) AlertDialog(
        onDismissRequest = { confirmDeletionRequest = false },
        title = { Text("Solicitar eliminación de cuenta") },
        text = { Text("Abrirás una página externa para solicitar la eliminación de la cuenta y los datos asociados. Cerrar sesión no elimina tus datos locales.") },
        confirmButton = { MoPrimaryButton("Abrir solicitud", {
            confirmDeletionRequest = false
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.ACCOUNT_DELETION_URL)))
        }) },
        dismissButton = { MoTertiaryButton("Cancelar", { confirmDeletionRequest = false }) },
    )
    LaunchedEffect(workspaces,state.userId,state.status,state.online,simulation) {
        if(workspaces==null || simulation || state.status==AccountStatus.EXPIRED) {
            workspaceChoices=emptyList();selectedWorkspace=null
        } else {
            workspaceChoices=(workspaces.availableWorkspaces() as? AppResult.Success)?.value ?: emptyList()
            selectedWorkspace=(workspaces.activeWorkspace() as? AppResult.Success)?.value
        }
    }
    fun choose(next:AccountForm) { form=next;password="";model.repository.clearNotice() }
    Column(Modifier.fillMaxSize().background(MoCream).statusBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(MoSpacing.screen).testTag("account-root"),verticalArrangement=Arrangement.spacedBy(MoSpacing.sm)) {
        if(showBack) MoBackButton("Volver a Perfil",onBack,Modifier.testTag("account-back"))
        if(state.email==null || state.status==AccountStatus.EXPIRED) {
            MaginaOlivoWordmark(Modifier.align(Alignment.CenterHorizontally).testTag("account-logo"))
        }
        Text("Cuenta",style=MaterialTheme.typography.headlineLarge,color=MoInk)
        Text("Mi cuenta",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
        if(state.simulated) Text("Simulación de Auth: no crea cuentas ni envía correos. Usa datos ficticios.",Modifier.testTag("account-simulation"),color=MoColors.current.warningText)
        val status=when(state.status) {
            AccountStatus.UNCONFIGURED -> "Configuración de pruebas pendiente"
            AccountStatus.SIGNED_OUT -> "Sin sesión"
            AccountStatus.CHECKING -> "Comprobando sesión"
            AccountStatus.SIGNED_IN -> if(state.simulated)"Sesión simulada" else "Sesión iniciada"
            AccountStatus.OFFLINE -> "Sesión guardada · sin conexión"
            AccountStatus.EXPIRED -> "Sesión caducada · vuelve a iniciar sesión cuando tengas conexión"
            AccountStatus.RECOVERY -> "Enlace verificado · nueva contraseña"
        }
        Surface(shape=MoShape.card,color=MoWarmWhite,border=androidx.compose.foundation.BorderStroke(1.dp,MoSurfaceTokens.cardStroke)) {
            Column(Modifier.fillMaxWidth().padding(MoSpacing.sm)) {
                Text(status,Modifier.testTag("account-status"),style=MaterialTheme.typography.titleMedium,color=MoColors.current.bodyText)
                Text(state.email ?: "Inicia sesión para vincular tus fincas",color=MoSurfaceTokens.secondaryText)
            }
        }
        if(state.status==AccountStatus.RECOVERY) {
            PasswordInput(password,{password=it},"Nueva contraseña (8–128 caracteres)","account-new-password",enabled)
            MoPrimaryButton("Guardar nueva contraseña",{ val value=password;password="";model.updatePassword(value,online) },Modifier.fillMaxWidth().testTag("account-save-password"),enabled)
        } else if(state.email==null || state.status==AccountStatus.EXPIRED) {
            MoTextField(email,{email=it},"Correo electrónico",Modifier.fillMaxWidth().testTag("account-email"),enabled=!state.busy,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email),containerColor=MoWarmWhite)
            if(form!=AccountForm.RECOVER) PasswordInput(password,{password=it},if(form==AccountForm.REGISTER)"Contraseña (8–128 caracteres)" else "Contraseña","account-password",!state.busy)
            when(form) {
                AccountForm.LOGIN -> MoPrimaryButton("Entrar",{val value=password;password="";model.login(email,value,online)},Modifier.fillMaxWidth().testTag("account-login"),enabled)
                AccountForm.REGISTER -> MoPrimaryButton("Registrarme",{val value=password;password="";model.register(email,value,online)},Modifier.fillMaxWidth().testTag("account-register"),enabled)
                AccountForm.RECOVER -> MoPrimaryButton("Enviar enlace de recuperación",{model.recover(email,online)},Modifier.fillMaxWidth().testTag("account-recover"),enabled)
            }
            if(form!=AccountForm.LOGIN) MoSecondaryButton("Volver a entrar",{choose(AccountForm.LOGIN)},Modifier.fillMaxWidth().testTag("account-mode-login"),!state.busy)
            if(form!=AccountForm.REGISTER) MoSecondaryButton("Crear cuenta",{choose(AccountForm.REGISTER)},Modifier.fillMaxWidth().testTag("account-mode-register"),!state.busy)
            if(form!=AccountForm.RECOVER) MoTertiaryButton("Recuperar contraseña",{choose(AccountForm.RECOVER)},Modifier.testTag("account-mode-recover"),!state.busy)
        }
        if(state.email!=null) {
            Text("Acceso y seguridad",style=MaterialTheme.typography.titleMedium,color=MoColors.current.primaryText)
            Text("Para cambiar la contraseña, cierra sesión y usa «Recuperar contraseña».",color=MoSurfaceTokens.secondaryText)
            MoDestructiveButton("Cerrar sesión",{model.logout(online)},Modifier.fillMaxWidth().testTag("account-logout"),!state.busy)
        }
        if(!simulation && state.userId != null && BuildConfig.ACCOUNT_DELETION_URL.isNotBlank()) {
            MoTertiaryButton("Solicitar eliminación de cuenta y datos",{
                confirmDeletionRequest = true
            },Modifier.fillMaxWidth().testTag("account-delete-request"))
        }
        Text("Mis datos",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
        MoCompactListItem("Guardados en este teléfono",subtitle="Tus fincas y registros locales se conservan al cerrar sesión.",modifier=Modifier.testTag("account-local-data"))
        val canViewLinks=state.userId!=null && farmLinks?.accessAllowed(state.userId)==true
        val observedLinkState=if(!simulation) farmLinks?.state?.collectAsStateWithLifecycle()?.value else null
        val linkState=observedLinkState.takeIf { canViewLinks }
        val linkStatus=when {
            state.userId==null -> "Inicia sesión para comprobar tus fincas"
            farmLinks==null -> "Vinculación no disponible en esta pantalla"
            observedLinkState?.unreadable == true -> "No se puede comprobar la vinculación local"
            !canViewLinks -> "Estas fincas están protegidas con otra cuenta"
            linkState?.ledger!=null -> "Consentimiento local registrado · confirmación remota no comprobada"
            else -> "Vinculación de fincas pendiente"
        }
        MoCompactListItem("Vinculación con la cuenta",subtitle=linkStatus,modifier=Modifier.testTag("account-link-status"),subtitleMaxLines=3)
        if(workspaces!=null && !simulation) {
            Text("Espacio de trabajo",style=MaterialTheme.typography.titleMedium,color=MoColors.current.primaryText)
            if(workspaceChoices.isEmpty()) Text("No hay espacios disponibles para esta sesión. Tus datos locales se conservan.",color=MoSurfaceTokens.secondaryText)
            workspaceChoices.forEach { choice ->
                MoSecondaryButton(
                    if(choice.id==selectedWorkspace) "${choice.name} · ${if(choice.remote) "acceso guardado" else "solo local"} · seleccionado"
                    else "Usar ${choice.name} · ${if(choice.remote) "acceso guardado" else "solo local"}",
                    { scope.launch {
                        when(workspaces.selectWorkspace(choice.id)) {
                            is AppResult.Success -> { selectedWorkspace=choice.id;workspaceNotice="Espacio seleccionado" }
                            is AppResult.Failure -> workspaceNotice="No se pudo seleccionar este espacio"
                        }
                    } },
                    Modifier.fillMaxWidth().testTag("workspace-select-${choice.id}"),
                    !state.busy,
                )
            }
            workspaceNotice?.let { Text(it,color=MoSurfaceTokens.secondaryText) }
        }
        if(!online) Text("Sin conexión. Puedes seguir trabajando con tus datos locales; el acceso y la recuperación necesitan conexión.",color=MoSurfaceTokens.secondaryText)
        if(state.busy) { CircularProgressIndicator(Modifier.testTag("account-loading"));Text("Procesando…",color=MoSurfaceTokens.secondaryText) }
        state.error?.let { Text(it,Modifier.testTag("account-error").semantics { liveRegion=LiveRegionMode.Polite },color=MoColors.current.errorText) }
        state.message?.let { Text(it,Modifier.testTag("account-message").semantics { liveRegion=LiveRegionMode.Polite },color=MoSurfaceTokens.secondaryText) }
        if(!simulation && !state.simulated && state.userId!=null && state.status in setOf(AccountStatus.SIGNED_IN,AccountStatus.OFFLINE) && farmLinks!=null) {
            // A finished manual sync may bring farms (from another device) that now need explicit consent here.
            val syncRun=manualSync?.state?.collectAsStateWithLifecycle()?.value
            key(syncRun?.takeIf { it !is com.isivoltpro.maginaolivo.feature.sync.ManualSyncState.Running }) {
                com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkPanel(farmLinks,state.userId!!)
            }
            com.isivoltpro.maginaolivo.feature.sync.ManualSyncPanel(manualSync,online)
            if(manualSync!=null) LaunchedEffect(syncRun) {
                // After a run, the workspace list may include the workspace just received from another device.
                if(syncRun !is com.isivoltpro.maginaolivo.feature.sync.ManualSyncState.Running) workspaces?.let { repo ->
                    (repo.availableWorkspaces() as? AppResult.Success)?.let { workspaceChoices=it.value }
                    (repo.activeWorkspace() as? AppResult.Success)?.let { selectedWorkspace=it.value }
                }
            }
        } else if (!simulation && !state.simulated && farmLinks != null) {
            Text("Mis fincas",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
            Text("Inicia sesión para ver y preparar la vinculación de tus fincas. Los datos locales siguen protegidos.",color=MoSurfaceTokens.secondaryText)
            Text("Sincronización",style=MaterialTheme.typography.titleLarge,color=MoColors.current.primaryText)
            MoPrimaryButton("Sincronizar ahora",{},Modifier.fillMaxWidth().testTag("manual-sync-now"),enabled=false)
            Text("Inicia sesión para sincronizar.",color=MoSurfaceTokens.secondaryText)
        }
        if(state.simulated && state.pendingPurpose!=null) MoSecondaryButton("Abrir enlace simulado",{model.simulateLink(online)},Modifier.fillMaxWidth().testTag("account-simulate-link"),!state.busy)
        if(simulationAllowed && !state.simulated) MoTertiaryButton("Probar Cuenta (simulación)",{simulation=true;choose(AccountForm.LOGIN)},Modifier.testTag("account-start-simulation"),!state.busy)
        if(simulation) MoTertiaryButton("Salir de simulación",{model.logout(false);simulation=false;choose(AccountForm.LOGIN)},Modifier.testTag("account-end-simulation"),!state.busy)
    }
}

@Composable private fun PasswordInput(value:String,onChange:(String)->Unit,label:String,tag:String,enabled:Boolean) {
    var visible by remember { mutableStateOf(false) }
    MoTextField(value,onChange,label,Modifier.fillMaxWidth().testTag(tag),enabled=enabled,
        visualTransformation=if(visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions=KeyboardOptions(keyboardType=if(visible) KeyboardType.Text else KeyboardType.Password),
        trailingIcon={ TextButton(onClick={visible=!visible},modifier=Modifier.testTag("account-password-visibility")) {
            Text(if(visible) "Ocultar" else "Mostrar")
        } },containerColor=MoWarmWhite)
}
private fun accountConnectivity(context:Context):Flow<Boolean> = callbackFlow {
    val manager=context.getSystemService(ConnectivityManager::class.java)
    fun connected():Boolean=manager?.getNetworkCapabilities(manager.activeNetwork)?.let {
        it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    } ?: false
    trySend(connected())
    val callback=object:ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network:Network) { trySend(connected()) }
        override fun onLost(network:Network) { trySend(connected()) }
        override fun onCapabilitiesChanged(network:Network,capabilities:NetworkCapabilities) { trySend(connected()) }
    }
    manager?.registerDefaultNetworkCallback(callback)
    awaitClose { manager?.unregisterNetworkCallback(callback) }
}
