package com.isivoltpro.maginaolivo.data.repository

import android.content.Context
import com.isivoltpro.maginaolivo.BuildConfig
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import com.isivoltpro.maginaolivo.feature.account.AuthFailure

/** Background alarms use the same cached identity policy, without contacting Auth. */
object FarmLinkDeviceAccess {
    suspend fun <T> runIfAllowed(context: Context, denied: T, block: suspend () -> T): T {
        val account=AccountRuntime.get(context)
        return runIfAllowed(AndroidFarmLinkLedger.forContext(context),account,BuildConfig.AUTH_PROJECT_URL,denied,block)
    }
    internal suspend fun <T> runIfAllowed(store:com.isivoltpro.maginaolivo.feature.accountlink.FarmLinkLedgerStore,
        account:com.isivoltpro.maginaolivo.feature.account.AccountRepository,issuer:String,denied:T,block:suspend ()->T):T =
        try { account.withCachedLocalAccess { id ->
            val ledger=try { store.read() } catch (_:java.io.IOException) { return@withCachedLocalAccess denied }
            if(ledger==null || (id!=null && ledger.subject=="$issuer|$id")) block() else denied
        } } catch (_:AuthFailure) { denied }
}
