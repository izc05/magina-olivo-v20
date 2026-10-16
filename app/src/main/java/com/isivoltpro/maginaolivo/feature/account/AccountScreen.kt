package com.isivoltpro.maginaolivo.feature.account

import android.content.Context
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.isivoltpro.maginaolivo.ui.components.*
import com.isivoltpro.maginaolivo.ui.theme.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.channels.awaitClose

private enum class AccountForm { LOGIN, REGISTER, RECOVER }

@Composable
fun AccountRoute(repository:AccountRepository,onBack:()->Unit,simulationAllowed:Boolean=false,connectivity:Flow<Boolean>? = null,
    farmLinks:com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository?=null,showBack:Boolean=true) {
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
    fun choose(next:AccountForm) { form=next;password="";model.repository.clearNotice() }
    Column(Modifier.fillMaxSize().background(MoSurfaceTokens.appBackground).statusBarsPadding().imePadding()
        .verticalScroll(rememberScrollState()).padding(MoSpacing.screen).testTag("account-root"),verticalArrangement=Arrangement.spacedBy(MoSpacing.sm)) {
        if(showBack) MoTertiaryButton("Volver a Perfil",onBack,Modifier.testTag("account-back"))
        Text("Cuenta",style=MaterialTheme.typography.headlineLarge,color=MoColors.current.primaryText)
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
        Text(status,Modifier.testTag("account-status"),style=MaterialTheme.typography.titleMedium,color=MoColors.current.bodyText)
        state.email?.let { Text(it,color=MoSurfaceTokens.secondaryText) }
        Text(if(farmLinks!=null && !simulation) "Tus registros, fincas, fotos y documentos siguen guardados en este teléfono. Puedes preparar su vinculación a la cuenta; todavía no se sincronizan."
            else "Tus registros, fincas, fotos y documentos siguen guardados en este teléfono. Esta pantalla no los vincula ni los sincroniza.",color=MoSurfaceTokens.secondaryText)
        if(!online) Text("Sin conexión. Puedes seguir trabajando con tus datos locales; el acceso y la recuperación necesitan conexión.",color=MoSurfaceTokens.secondaryText)
        if(state.busy) { CircularProgressIndicator(Modifier.testTag("account-loading"));Text("Procesando…",color=MoSurfaceTokens.secondaryText) }
        state.error?.let { Text(it,Modifier.testTag("account-error").semantics { liveRegion=LiveRegionMode.Polite },color=MoColors.current.errorText) }
        state.message?.let { Text(it,Modifier.testTag("account-message").semantics { liveRegion=LiveRegionMode.Polite },color=MoSurfaceTokens.secondaryText) }
        if(!simulation && !state.simulated && state.userId!=null && state.status in setOf(AccountStatus.SIGNED_IN,AccountStatus.OFFLINE) && farmLinks!=null) {
            com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkPanel(farmLinks,state.userId!!)
        }
        if(state.status==AccountStatus.RECOVERY) {
            PasswordInput(password,{password=it},"Nueva contraseña (8–128 caracteres)","account-new-password",enabled)
            MoPrimaryButton("Guardar nueva contraseña",{ val value=password;password="";model.updatePassword(value,online) },Modifier.fillMaxWidth().testTag("account-save-password"),enabled)
        } else if(state.email==null || state.status==AccountStatus.EXPIRED) {
            MoTextField(email,{email=it},"Correo electrónico",Modifier.fillMaxWidth().testTag("account-email"),enabled=!state.busy,
                keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Email))
            if(form!=AccountForm.RECOVER) PasswordInput(password,{password=it},if(form==AccountForm.REGISTER)"Contraseña (8–128 caracteres)" else "Contraseña","account-password",!state.busy)
            when(form) {
                AccountForm.LOGIN -> MoPrimaryButton("Iniciar sesión",{val value=password;password="";model.login(email,value,online)},Modifier.fillMaxWidth().testTag("account-login"),enabled)
                AccountForm.REGISTER -> MoPrimaryButton("Registrarme",{val value=password;password="";model.register(email,value,online)},Modifier.fillMaxWidth().testTag("account-register"),enabled)
                AccountForm.RECOVER -> MoPrimaryButton("Enviar enlace de recuperación",{model.recover(email,online)},Modifier.fillMaxWidth().testTag("account-recover"),enabled)
            }
            MoSecondaryButton("Tengo cuenta",{choose(AccountForm.LOGIN)},Modifier.fillMaxWidth().testTag("account-mode-login"),!state.busy)
            MoSecondaryButton("Crear cuenta",{choose(AccountForm.REGISTER)},Modifier.fillMaxWidth().testTag("account-mode-register"),!state.busy)
            MoTertiaryButton("He olvidado la contraseña",{choose(AccountForm.RECOVER)},Modifier.testTag("account-mode-recover"),!state.busy)
        }
        if(state.email!=null) MoDestructiveButton("Cerrar sesión",{model.logout(online)},Modifier.fillMaxWidth().testTag("account-logout"),!state.busy)
        if(state.simulated && state.pendingPurpose!=null) MoSecondaryButton("Abrir enlace simulado",{model.simulateLink(online)},Modifier.fillMaxWidth().testTag("account-simulate-link"),!state.busy)
        if(simulationAllowed && !state.simulated) MoTertiaryButton("Probar Cuenta (simulación)",{simulation=true;choose(AccountForm.LOGIN)},Modifier.testTag("account-start-simulation"),!state.busy)
        if(simulation) MoTertiaryButton("Salir de simulación",{model.logout(false);simulation=false;choose(AccountForm.LOGIN)},Modifier.testTag("account-end-simulation"),!state.busy)
    }
}

@Composable private fun PasswordInput(value:String,onChange:(String)->Unit,label:String,tag:String,enabled:Boolean) {
    MoTextField(value,onChange,label,Modifier.fillMaxWidth().testTag(tag),enabled=enabled,
        visualTransformation=PasswordVisualTransformation(),keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Password))
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
