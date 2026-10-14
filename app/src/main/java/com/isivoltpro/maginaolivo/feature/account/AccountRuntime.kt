package com.isivoltpro.maginaolivo.feature.account

import android.content.Context
import com.isivoltpro.maginaolivo.BuildConfig
import java.io.File

/** One serialized Auth owner per application process; never auto-load or contact Auth at startup. */
object AccountRuntime {
    private var repository:AccountRepository?=null
    @Synchronized fun get(context:Context):AccountRepository {
        repository?.let { return it }
        val config=AuthConfiguration.forEnvironment(BuildConfig.ENVIRONMENT,BuildConfig.AUTH_PROJECT_URL,BuildConfig.AUTH_PUBLIC_KEY,context.packageName)
        val created=if(config==null) AccountRepository(null) else {
            val ref=java.net.URI(config.projectUrl).host.substringBefore('.')
            val file=File(context.applicationContext.noBackupFilesDir,"account-auth-$ref.enc")
            val store=AndroidAccountSessionStore(file,"magina-account-$ref-v1","${context.packageName}|${config.projectUrl}")
            AccountRepository(SupabaseAuthApi(config,HttpsAuthTransport(config)),store)
        }
        repository=created;return created
    }
}
