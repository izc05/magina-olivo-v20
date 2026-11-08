package com.isivoltpro.maginaolivo.domain.farm

import java.text.NumberFormat
import java.util.Locale

/**
 * Worked and cadastral surfaces added up separately. A parcel missing one figure is reported as
 * missing; it is never filled in with the other source.
 */
data class FarmAreaCoverage(
    val managedAreaM2: Double?,
    val managedParcels: Long,
    val cadastralAreaM2: Double?,
    val cadastralParcels: Long,
    val totalParcels: Long,
) {
    val managedLabel: String get() = managedAreaM2?.let(::hectares) ?: "Sin indicar"

    /** «Indicada en 2 de 3 parcelas · falta 1»; null when there are no parcels. */
    val managedCoverageText: String?
        get() {
            if (totalParcels == 0L) return null
            val parcels = if (totalParcels == 1L) "parcela" else "parcelas"
            val missing = totalParcels - managedParcels
            return "Superficie trabajada indicada en $managedParcels de $totalParcels $parcels" + when (missing) {
                0L -> ""
                1L -> " · falta 1"
                else -> " · faltan $missing"
            }
        }

    /** «Catastral: 179,1 ha · 1 de 3 parcelas · según Catastro»; null when no parcel has it. */
    val cadastralText: String?
        get() = cadastralAreaM2?.let {
            "Catastral: ${hectares(it)} · $cadastralParcels de $totalParcels ${if (totalParcels == 1L) "parcela" else "parcelas"} · según Catastro"
        }

    companion object {
        fun of(farms: List<Farm>): FarmAreaCoverage = FarmAreaCoverage(
            managedAreaM2 = farms.mapNotNull { it.managedAreaM2 }.takeIf { it.isNotEmpty() }?.sum(),
            managedParcels = farms.sumOf { it.managedParcelCount },
            cadastralAreaM2 = farms.mapNotNull { it.cadastralAreaM2 }.takeIf { it.isNotEmpty() }?.sum(),
            cadastralParcels = farms.sumOf { it.cadastralParcelCount },
            totalParcels = farms.sumOf { it.parcelCount },
        )

        private fun hectares(areaM2: Double): String =
            "${NumberFormat.getNumberInstance(Locale.forLanguageTag("es-ES")).apply { maximumFractionDigits = 2 }.format(areaM2 / 10_000.0)} ha"
    }
}
