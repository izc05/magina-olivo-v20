package com.isivoltpro.maginaolivo.data.repository

import java.util.UUID

/** Serializes local intent attribution with account changes and farm consent/revocation.
 * Attribution is not authorization to send. Unauthenticated/unconsented writes stay local.
 */
interface DeliveryWriteScope {
    suspend fun <T> withAccount(farmId: UUID, action: suspend (String?) -> T): T
}

object LocalOnlyDeliveryWriteScope : DeliveryWriteScope {
    override suspend fun <T> withAccount(farmId: UUID, action: suspend (String?) -> T): T = action(null)
}
