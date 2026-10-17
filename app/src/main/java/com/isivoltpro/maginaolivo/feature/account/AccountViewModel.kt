package com.isivoltpro.maginaolivo.feature.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class AccountViewModel(val repository:AccountRepository):ViewModel() {
    val state=repository.state
    private fun run(action:suspend ()->Unit) { viewModelScope.launch(Dispatchers.IO) { action() } }
    fun resume(online:Boolean)=run { repository.resume(online) }
    fun login(email:String,password:String,online:Boolean)=run { repository.login(email,password,online) }
    fun register(email:String,password:String,online:Boolean)=run { repository.register(email,password,online) }
    fun recover(email:String,online:Boolean)=run { repository.requestRecovery(email,online) }
    fun updatePassword(password:String,online:Boolean)=run { repository.updatePassword(password,online) }
    // Navigation may be destroyed as soon as local identity is cleared; revocation must continue.
    fun logout(online:Boolean) { repository.requestLogout(online) }
    fun simulateLink(online:Boolean)=run { repository.simulateLink(online) }
}
