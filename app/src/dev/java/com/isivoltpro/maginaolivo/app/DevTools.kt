package com.isivoltpro.maginaolivo.app

/** #399: the dev flavor's QA tools; this source set never ships in staging or production. */
object DevTools {
    fun demoFarm(persistence: LocalPersistence): DemoFarmTools = DemoFarmSeeder(persistence)
}
