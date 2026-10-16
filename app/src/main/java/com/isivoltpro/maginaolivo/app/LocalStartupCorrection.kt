package com.isivoltpro.maginaolivo.app

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** New Activities wait for completion, not merely for another Activity to start the correction. */
internal class LocalStartupCorrection {
    private val mutex=Mutex()
    private var completed=false
    suspend fun ensure(action:suspend ()->Boolean):Boolean=mutex.withLock {
        if(completed) true else action().also { completed=it }
    }
}
