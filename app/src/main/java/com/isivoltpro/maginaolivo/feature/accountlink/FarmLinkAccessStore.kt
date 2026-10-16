package com.isivoltpro.maginaolivo.feature.accountlink

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import java.util.UUID

/**
 * Retained by the Activity, so configuration changes keep Navigation's entry stores and
 * saveable-state identities. Only the current access boundary owns a child store: changing
 * account/access destroys the previous navigation and its ViewModels, without caching it.
 */
internal class FarmLinkAccessStore : ViewModel() {
    private data class Boundary(val userId: UUID?, val allowed: Boolean)
    private var boundary: Boundary? = null
    private var owner: ViewModelStoreOwner? = null

    /** Called on the UI thread before composing any content for the requested boundary. */
    fun ownerFor(userId: UUID?, allowed: Boolean): ViewModelStoreOwner {
        val requested = Boundary(userId, allowed)
        if (boundary != requested) {
            owner?.viewModelStore?.clear()
            owner = object : ViewModelStoreOwner {
                override val viewModelStore = ViewModelStore()
            }
            boundary = requested
        }
        return checkNotNull(owner)
    }

    override fun onCleared() {
        owner?.viewModelStore?.clear()
        owner = null
        boundary = null
    }
}
