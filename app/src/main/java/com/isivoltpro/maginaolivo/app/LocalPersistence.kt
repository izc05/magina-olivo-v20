package com.isivoltpro.maginaolivo.app

import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.domain.farm.FarmRepository

data class LocalPersistence(
    val database: MaginaOlivoDatabase,
    val farmRepository: FarmRepository,
)
