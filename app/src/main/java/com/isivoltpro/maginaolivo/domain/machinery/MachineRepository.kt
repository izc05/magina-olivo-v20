package com.isivoltpro.maginaolivo.domain.machinery

import com.isivoltpro.maginaolivo.core.common.AppResult
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface MachineRepository {
    /** Machines in use, by name. */
    fun observeActive(): Flow<List<Machine>>

    fun observeArchived(): Flow<List<Machine>>

    fun observe(id: UUID): Flow<Machine?>

    /** The Activities that used this machine, newest first. */
    fun observeUses(id: UUID): Flow<List<MachineUse>>

    suspend fun create(draft: MachineDraft): AppResult<UUID>

    suspend fun update(id: UUID, draft: MachineDraft): AppResult<Unit>

    /** Retires the machine: it leaves the pickers, and past Activities keep showing it. */
    suspend fun archive(id: UUID): AppResult<Unit>

    suspend fun restore(id: UUID): AppResult<Unit>
}
