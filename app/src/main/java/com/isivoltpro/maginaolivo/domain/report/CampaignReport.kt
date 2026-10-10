package com.isivoltpro.maginaolivo.domain.report

import com.isivoltpro.maginaolivo.data.local.model.CampaignStatus
import com.isivoltpro.maginaolivo.domain.campaign.Campaign
import com.isivoltpro.maginaolivo.domain.delivery.Delivery
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.CostPerKg
import com.isivoltpro.maginaolivo.domain.expense.Expense
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.expense.RecollectionBucket
import com.isivoltpro.maginaolivo.domain.expense.RecollectionCurrency
import com.isivoltpro.maginaolivo.domain.expense.RecollectionLedger
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.Harvest
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 25 — a campaign's report as plain data, with no Android in it.
 *
 * Every figure comes from the same aggregates the screens read: the Pesadas' own kilos and
 * weighted yield, and the **posted** expense ledger split into its recollection buckets. Nothing
 * here computes money of its own, so what the report says cannot disagree with the campaign card
 * or the historical overview (Gate 25). A figure that is not known is said to be unknown; it is
 * never shown as zero, and an incomplete campaign carries its warning into the document.
 */
data class CampaignReport(
    val title: String,
    val farmName: String,
    val place: String?,
    val period: String,
    val generatedOn: LocalDate,
    val sections: List<ReportSection>,
    val warnings: List<String> = emptyList(),
) : Printable {
    override fun document() = ReportDocument(
        title = title,
        subtitle = listOfNotNull(farmName, place).joinToString(" · "),
        period = "Campaña: $period",
        generatedOn = generatedOn,
        sections = sections,
        warnings = warnings,
    )

    companion object {
        private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", SPANISH)

        /**
         * [deliveries], [harvests] and [expenses] may hold the whole olivar: only this campaign's
         * own records are read, so the caller never has to pre-filter them.
         */
        fun of(
            campaign: Campaign,
            farm: Farm?,
            deliveries: List<Delivery>,
            harvests: List<Harvest>,
            expenses: List<Expense>,
            today: LocalDate,
        ): CampaignReport {
            val own = deliveries.filter { it.campaignId == campaign.id }.sortedBy { it.deliveryDate }
            val days = harvests.filter { it.campaignId == campaign.id }.sortedBy { it.harvestDate }
            val summary = DeliverySummary.of(own)
            val ledgers = RecollectionLedger.of(campaign.id, expenses, deliveries)
            val warnings = buildList {
                if (own.isEmpty()) add("La campaña aún no tiene pesadas.")
                summary.coveragePercent(summary.fatYield)?.let { coverage ->
                    if (own.isNotEmpty() && coverage < 100) {
                        add("El rendimiento está medido sobre el $coverage % de los kilos pesados.")
                    }
                }
                if (ledgers.size > 1) add("Hay gastos en más de una moneda: no se convierte ninguna.")
                if (ledgers.any { it.amount() == null }) add("Algún total de gastos no se ha podido sumar.")
            }
            return CampaignReport(
                title = campaign.name,
                farmName = farm?.name ?: campaign.snapshots.firstNotNullOfOrNull { it.farmName } ?: "Finca",
                place = farm?.let { listOfNotNull(it.municipality, it.province).filter(String::isNotBlank) }
                    ?.takeIf { it.isNotEmpty() }?.joinToString(" · "),
                period = period(campaign),
                generatedOn = today,
                sections = buildList {
                    add(production(summary, own.size, days.size))
                    ledgers.forEach { add(costs(it)) }
                    if (own.isNotEmpty()) add(pesadas(own))
                    if (days.isNotEmpty()) add(jornadas(days))
                },
                warnings = warnings,
            )
        }

        private fun period(campaign: Campaign): String {
            val start = campaign.startDate.format(DAY)
            val end = campaign.endDate?.format(DAY)
            return when {
                end != null -> "$start → $end"
                campaign.status == CampaignStatus.CLOSED -> "$start → cerrada"
                else -> "$start → en curso"
            }
        }

        private fun production(summary: DeliverySummary, pesadas: Int, days: Int) = ReportSection(
            title = "Producción",
            lines = buildList {
                // With no Pesada there are no kilos to report: «0 kg» would read as a weighed zero.
                add(
                    ReportLine(
                        "Kilos pesados",
                        when {
                            pesadas == 0 -> "Sin pesadas registradas"
                            else -> summary.deliveredGrams?.let(Weight::format) ?: "No disponible"
                        },
                    ),
                )
                add(ReportLine("Pesadas", pesadas.toString()))
                add(ReportLine("Días de recogida", days.toString()))
                val fat = summary.fatYield
                add(
                    ReportLine(
                        "Rendimiento graso",
                        fat?.let { Percent.format(it.hundredths) } ?: "Sin análisis",
                    ),
                )
                summary.unallocatedGrams?.takeIf { it > 0 }?.let {
                    add(ReportLine("Kilos sin repartir por parcela", Weight.format(it)))
                }
            },
            note = "Los kilos son los de las pesadas; el rendimiento es su media ponderada por kilos.",
        )

        private fun costs(ledger: RecollectionCurrency): ReportSection {
            val currency = ledger.currency
            fun amount(bucket: RecollectionBucket?) = ledger.amount(bucket)?.let { Money.format(it, currency) }
            return ReportSection(
                title = if (currency == "EUR") "Costes de recogida" else "Costes de recogida ($currency)",
                lines = buildList {
                    add(ReportLine("Jornales", amount(RecollectionBucket.LABOUR) ?: "—"))
                    add(ReportLine("Maquinaria", amount(RecollectionBucket.EQUIPMENT) ?: "—"))
                    add(ReportLine("Otros gastos", amount(RecollectionBucket.OTHER) ?: "—"))
                    add(ReportLine("Total de recogida", amount(null) ?: "No disponible"))
                    add(
                        ReportLine(
                            "Coste por kilo",
                            ledger.costPerKgMilli?.let { CostPerKg.format(it, currency) } ?: "No disponible",
                        ),
                    )
                },
                note = "Solo gastos publicados en el libro de gastos. Los gastos generales de la finca " +
                    "no entran aquí ni en el coste por kilo.",
            )
        }

        private fun pesadas(own: List<Delivery>) = ReportSection(
            title = "Pesadas",
            lines = own.map { delivery ->
                ReportLine(
                    label = delivery.deliveryDate.format(DAY) +
                        (delivery.ticketNumber?.takeIf(String::isNotBlank)?.let { " · vale $it" } ?: ""),
                    value = listOfNotNull(
                        Weight.format(delivery.netGrams),
                        delivery.analysis?.fatYieldHundredths?.let(Percent::format),
                        delivery.origin?.label,
                        delivery.destinationName.takeIf(String::isNotBlank),
                    ).joinToString(" · "),
                )
            },
        )

        private fun jornadas(days: List<Harvest>) = ReportSection(
            title = "Días de recogida",
            lines = days.map { day ->
                ReportLine(
                    label = day.harvestDate.format(DAY),
                    value = if (day.totalGrams > 0) Weight.format(day.totalGrams) else "Kg pendientes de pesada",
                )
            },
        )
    }
}
