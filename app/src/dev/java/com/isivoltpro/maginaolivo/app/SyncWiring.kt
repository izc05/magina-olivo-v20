package com.isivoltpro.maginaolivo.app

import android.content.Context
import com.isivoltpro.maginaolivo.BuildConfig
import com.isivoltpro.maginaolivo.core.id.IdGenerator
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.DeliveryPayloadRoute
import com.isivoltpro.maginaolivo.data.repository.HistoricalDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LegacyDeliveryRoute
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.ServerWorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.WorkspaceAuthorizationRemote
import com.isivoltpro.maginaolivo.data.repository.WorkspaceOwnerVerifier
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.ManualSyncCoordinator
import com.isivoltpro.maginaolivo.feature.account.AccountRepository
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import com.isivoltpro.maginaolivo.feature.sync.ManualSync
import java.io.File

/**
 * Manual sync wiring, dev flavor only. The single place that builds the HTTPS RPC transport, the #808 v3 Pesada route
 * and the server owner check for the app. Staging and production have their own [SyncWiring] that builds none of them.
 * Nothing here schedules anything: the returned [ManualSync] runs only when the user presses «Sincronizar ahora».
 */
object SyncWiring {
    /** The fresh server check that this session owns the workspace (#808 PREPARE). Null without Auth configuration. */
    fun ownerVerifier(remote: WorkspaceAuthorizationRemote?, issuer: String): WorkspaceOwnerVerifier? =
        remote?.takeIf { issuer.isNotBlank() }?.let { ServerWorkspaceOwnerVerifier(it, issuer) }

    /**
     * New Pesadas use v3 (Jornada + Pesada, receivable on another device) when the bound account owns the workspace
     * locally; the server re-verifies ownership at PREPARE. Without Auth configuration, v2 as before.
     */
    fun deliveryRoute(database: MaginaOlivoDatabase): DeliveryPayloadRoute =
        if (BuildConfig.AUTH_PROJECT_URL.isBlank()) LegacyDeliveryRoute
        else HistoricalDeliveryRoute { workspaceId, accountId -> database.workspaceDao().findById(workspaceId)?.ownerUserId?.toString() == accountId }

    fun manualSync(context: Context, database: MaginaOlivoDatabase, account: AccountRepository, issuer: String,
        farmLinks: LocalFarmLinkRepository, workspaces: LocalWorkspaceRepository, remote: WorkspaceAuthorizationRemote?,
        ids: IdGenerator): ManualSync? {
        val configuration = AuthConfiguration.forEnvironment(BuildConfig.ENVIRONMENT, BuildConfig.AUTH_PROJECT_URL,
            BuildConfig.AUTH_PUBLIC_KEY, context.packageName) ?: return null
        val verifier = ownerVerifier(remote, issuer) ?: return null
        return ManualSyncCoordinator(database, account, issuer, farmLinks, workspaces, verifier, ids,
            transport = { scope -> HttpsDeliveryRpcTransport(configuration, scope = scope) },
            leaseDirectory = File(context.noBackupFilesDir, "manual-sync-lease"))
    }
}
