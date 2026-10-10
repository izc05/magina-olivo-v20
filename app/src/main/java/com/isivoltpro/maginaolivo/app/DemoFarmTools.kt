package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.core.common.AppResult

/**
 * #399 — the DEV/QA «Finca Demo». Only the dev flavor provides it ([DevTools.demoFarm]);
 * staging and production return null, so nothing is ever created on real data.
 */
interface DemoFarmTools {
    /** Creates the demo once; a second call changes nothing. The text is shown in Perfil. */
    suspend fun load(): AppResult<String>

    /** Retires the current demo (closed and archived) and creates a fresh one. */
    suspend fun reset(): AppResult<String>

    /**
     * #696 — «Eliminar datos de demostración»: retires the demo and creates nothing. Only the
     * demo's own Farm is touched; real Farms, Pesadas, costs and photos stay as they are.
     */
    suspend fun remove(): AppResult<String>
}
