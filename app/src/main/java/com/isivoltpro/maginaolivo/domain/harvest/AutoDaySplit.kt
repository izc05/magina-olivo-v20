package com.isivoltpro.maginaolivo.domain.harvest

import java.util.UUID

/**
 * CR-010 amendment J (#808, owner decision J-D1): the Parcel split of an automatic day, the SAME rule on Android and
 * on the server (`derive_auto_day`), so a second device verifies identical kilos and split.
 *
 * - Total kilos: the exact sum of the live Pesadas' nets (computed by the caller).
 * - Per Parcel: EXACT with the sum of the exact kilos only when every live share of that Parcel is EXACT with kilos;
 *   otherwise UNALLOCATED with no kilos. No proportional, equal or presumed split is ever invented.
 * - A Pesada without Parcels contributes no Parcel (its kilos stay in the total, «sin atribuir»), never "the whole Farm".
 * - Ordered by Parcel id text, as the server orders `harvest_parcels`.
 */
object AutoDaySplit {
    data class Share(val parcelId: UUID, val allocation: HarvestAllocation, val weightGrams: Long?)
    data class Parcel(val parcelId: UUID, val allocation: HarvestAllocation, val weightGrams: Long?)

    fun derive(shares: List<Share>): List<Parcel> =
        shares.groupBy { it.parcelId }.map { (parcelId, group) ->
            if (group.all { it.allocation == HarvestAllocation.EXACT && it.weightGrams != null }) {
                Parcel(parcelId, HarvestAllocation.EXACT, group.fold(0L) { total, share -> Math.addExact(total, share.weightGrams!!) })
            } else {
                Parcel(parcelId, HarvestAllocation.UNALLOCATED, null)
            }
        }.sortedBy { it.parcelId.toString() }
}
