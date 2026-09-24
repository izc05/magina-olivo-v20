package com.isivoltpro.maginaolivo.domain.harvest

import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import java.time.LocalTime
import java.util.UUID

/**
 * Phase 19B (CR-005 §7): a Jornada de recolección is a Harvest with the Pesadas linked to it.
 * Each Pesada keeps its own cooperative, ticket, hour and yield; the Jornada only reads them.
 * With Pesadas, the Jornada's kilos are their sum (the repository keeps them equal).
 */
data class Jornada(val harvest: Harvest, val pesadas: List<Delivery>) {
    val kilosFromPesadas: Boolean get() = pesadas.isNotEmpty()
    val pesadaGrams: Long get() = pesadas.sumOf { it.netGrams }

    /** Every cooperative or mill that received a Pesada of this day, in weighing order. */
    val destinations: List<String> get() = pesadas.map { it.destinationName }.distinct()

    companion object {
        fun of(harvest: Harvest, deliveries: List<Delivery>): Jornada =
            Jornada(harvest, linkedTo(harvest.id, deliveries))

        /** The live Pesadas of one Jornada, oldest first; ones without an hour go last in their day. */
        fun linkedTo(harvestId: UUID, deliveries: List<Delivery>): List<Delivery> =
            deliveries.filter { it.harvestId == harvestId }
                .sortedWith(compareBy<Delivery>({ it.deliveryDate }, { it.deliveryTime ?: LocalTime.MAX }))
    }
}
