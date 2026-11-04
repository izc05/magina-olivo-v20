package com.isivoltpro.maginaolivo.app

import android.content.Context
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.DeliveryPayloadRoute
import com.isivoltpro.maginaolivo.data.repository.LegacyDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.sync.ManualSync

/** Outside the dev flavor there is no agricultural synchronization: v2 Pesadas, no owner check, no manual sync. */
@Suppress("UNUSED_PARAMETER")
object SyncWiring {
    fun ownerVerifier(remote: WorkspaceAuthorizationRemote?, issuer: String): WorkspaceOwnerVerifier? = null

    fun deliveryRoute(database: MaginaOlivoDatabase): DeliveryPayloadRoute = LegacyDeliveryRoute

    fun manualSync(context: Context, database: MaginaOlivoDatabase, account: AccountRepository, issuer: String,
        farmLinks: LocalFarmLinkRepository, workspaces: LocalWorkspaceRepository, remote: WorkspaceAuthorizationRemote?,
        ids: IdGenerator): ManualSync? = null
}
