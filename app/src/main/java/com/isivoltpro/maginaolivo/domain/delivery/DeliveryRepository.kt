package com.isivoltpro.maginaolivo.domain.delivery

import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.domain.harvest.HarvestContext
import java.util.UUID
import kotlinx.coroutines.flow.Flow

interface DeliveryRepository {
    fun observeAll(): Flow<List<Delivery>>

    fun observeForCampaign(campaignId: UUID): Flow<List<Delivery>>

    fun observe(id: UUID): Flow<Delivery?>

    /** Farms with a running Campaign and its Parcels: where a Delivery can be recorded. */
    fun observeContexts(): Flow<List<HarvestContext>>

    suspend fun create(draft: DeliveryDraft): AppResult<UUID>

    /** Only while the Campaign is running; a closed Campaign's deliveries are history. */
    suspend fun update(id: UUID, draft: DeliveryDraft): AppResult<Unit>

    suspend fun delete(id: UUID): AppResult<Unit>

    /**
     * Records or corrects the Delivery's yield analysis. It is a separate record: the
     * Delivery keeps its date, kilos, ticket and version. Allowed after the Campaign closes,
     * because laboratory results arrive late.
     */
    suspend fun recordYield(deliveryId: UUID, draft: YieldDraft): AppResult<UUID>

    suspend fun removeYield(deliveryId: UUID): AppResult<Unit>
}
