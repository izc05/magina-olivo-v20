package com.isivoltpro.maginaolivo.domain.report

import com.isivoltpro.maginaolivo.domain.analytics.CurrencyTotal
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.analytics.OliveSeason
import com.isivoltpro.maginaolivo.domain.delivery.DeliverySummary
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.CostPerKg
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 25 — the report of one olive season across the holding, built from the very
 * [FarmOverview] the «Resumen de la explotación» already shows.
 *
 * It computes nothing of its own: the same weighed kilos, the same weighted yield, the same posted
 * recollection and general costs, and the same cost per kilo. A season with farms in several
 * currencies is not converted, and a figure that cannot be added is said to be unavailable
 * (Gate 25). General costs of the holding are kept apart from the recollection cost, exactly as on
 * screen (#359 follow-up).
 */
data class SeasonReport(
    val season: String,
    val generatedOn: LocalDate,
    val sections: List<ReportSection>,
    val warnings: List<String> = emptyList(),
) : Printable {
    override fun document() = ReportDocument(
        title = "Resumen de la temporada $season",
        subtitle = null,
        period = "Temporada $season (1 de septiembre a 31 de agosto)",
        generatedOn = generatedOn,
        sections = sections,
        warnings = warnings,
    )

    companion object {
        private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
        private val DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", SPANISH)

        fun of(overview: FarmOverview, today: LocalDate): SeasonReport = SeasonReport(
            season = overview.season,
            generatedOn = today,
            sections = buildList {
                add(production(overview))
                add(costs(overview))
                if (overview.farms.isNotEmpty()) add(farms(overview))
                add(period(overview))
            },
            warnings = buildList {
                if (overview.farms.isEmpty()) add("Ninguna finca tuvo campaña en esta temporada.")
                overview.yieldCoveragePercent?.let { coverage ->
                    if (overview.delivery.deliveryCount > 0 && coverage < 100) {
                        add("El rendimiento está medido sobre el $coverage % de los kilos pesados.")
                    }
                }
                when (overview.costComplete) {
                    false -> add("Hay campañas con costes sin confirmar: el coste por kilo está incompleto.")
                    null -> add("No se ha podido comprobar si faltan costes por confirmar.")
                    true -> Unit
                }
                if (overview.costs.size > 1 || overview.totalCosts.size > 1) {
                    add("Hay gastos en más de una moneda: no se convierte ninguna y no hay coste por kilo conjunto.")
                }
                overview.farmsWithoutCampaign.takeIf { it.isNotEmpty() }?.let {
                    add("Sin campaña en la temporada: ${it.joinToString(", ")}.")
                }
            },
        )

        private fun production(overview: FarmOverview) = ReportSection(
            title = "Producción de la temporada",
            lines = listOf(
                ReportLine("Kilos pesados", kilos(overview.delivery)),
                ReportLine("Pesadas", overview.delivery.deliveryCount.toString()),
                ReportLine("Rendimiento graso", yieldOf(overview.delivery)),
                ReportLine(
                    "Fincas con campaña",
                    "${overview.farms.size} de ${overview.farms.size + overview.farmsWithoutCampaign.size}",
                ),
            ),
            note = "Los kilos son los de las pesadas de las campañas de esta temporada; el rendimiento " +
                "es su media ponderada por kilos.",
        )

        private fun costs(overview: FarmOverview) = ReportSection(
            title = "Costes de la temporada",
            lines = listOf(
                ReportLine("Coste de recogida", amounts(overview.costs)),
                ReportLine("Coste de recogida por kilo", perKg(overview.costPerKgMilli, overview.costs, overview.costComplete)),
                ReportLine("Gastos generales (sin campaña)", amounts(overview.generalCosts)),
                ReportLine("Coste total", amounts(overview.totalCosts)),
                ReportLine("Coste total por kilo", perKg(overview.totalCostPerKgMilli, overview.totalCosts, overview.costComplete)),
            ),
            note = "Solo gastos publicados. Los gastos generales de la finca no entran en el coste de " +
                "recogida ni en su coste por kilo.",
        )

        private fun farms(overview: FarmOverview) = ReportSection(
            title = "Por finca",
            lines = overview.farms.map { farm ->
                ReportLine(
                    label = farm.farmName + if (farm.archived) " (archivada)" else "",
                    value = listOfNotNull(
                        kilos(farm.delivery),
                        overview.sharePercent(farm)?.let { "$it % del total" },
                        "rend. ${yieldOf(farm.delivery)}",
                        perKg(farm.costPerKgMilli, farm.costs, farm.costComplete).takeIf { it != DASH }
                            ?.let { "coste/kg $it" },
                    ).joinToString(" · "),
                )
            },
            note = "Una finca archivada participó en la temporada y se conserva como historia.",
        )

        private fun period(overview: FarmOverview): ReportSection {
            val range = OliveSeason.range(overview.season)
            return ReportSection(
                title = "Periodo",
                lines = listOf(
                    ReportLine("Desde", range.start.format(DAY)),
                    ReportLine("Hasta", range.endInclusive.format(DAY)),
                ),
            )
        }

        private const val DASH = "—"

        /** «—» when nothing was weighed: a season with no Pesada has no kilos, not zero kilos. */
        private fun kilos(delivery: DeliverySummary): String =
            if (delivery.deliveryCount == 0) DASH
            else delivery.deliveredGrams?.let(Weight::format) ?: "No disponible"

        private fun yieldOf(delivery: DeliverySummary): String =
            delivery.fatYield?.let { Percent.format(it.hundredths) } ?: DASH

        private fun amounts(costs: List<CurrencyTotal>): String =
            if (costs.isEmpty()) DASH
            else costs.joinToString(" · ") { total ->
                total.amountMinor?.let { Money.format(it, total.currency) }
                    ?: "Importe no disponible (${total.currency})"
            }

        /** #449: a figure built on unconfirmed costs says «(incompleto)» beside itself. */
        private fun perKg(milli: Long?, costs: List<CurrencyTotal>, complete: Boolean?): String =
            milli?.let { value ->
                costs.singleOrNull()?.let {
                    CostPerKg.format(value, it.currency) + if (complete == false) " (incompleto)" else ""
                }
            } ?: DASH
    }
}
