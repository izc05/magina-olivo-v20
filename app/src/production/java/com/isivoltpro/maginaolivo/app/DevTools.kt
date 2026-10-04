package com.isivoltpro.maginaolivo.app

/** #399: outside the dev flavor there is no demo data at all. */
object DevTools {
    @Suppress("UNUSED_PARAMETER")
    fun demoFarm(persistence: LocalPersistence): DemoFarmTools? = null
}
