package com.isivoltpro.maginaolivo.feature.catastro

import com.isivoltpro.maginaolivo.domain.feed.SpanishMunicipalityCatalog
import com.isivoltpro.maginaolivo.domain.registry.RegistryLocation

/** Offline Catastro municipality-code to INE-code concordance from the official
 * `OVCCallejeroCodigos.asmx/ConsultaMunicipioCodigos` service (province list, 2026-11-05).
 * A Catastro code is never interpreted as an INE code without this explicit mapping.
 */
object CadastralMunicipalityCatalog {
    private const val RESOURCE = "catastro/catastro_ine_municipios.tsv"
    private val ruralReference = Regex("^[0-9]{5}[A-Z][0-9]{8}$")
    private val ineByCadastralCode: Map<String, String> by lazy {
        val stream = CadastralMunicipalityCatalog::class.java.classLoader?.getResourceAsStream(RESOURCE)
            ?: error("Missing $RESOURCE")
        stream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size == 2 && parts.all { it.length == 5 && it.all(Char::isDigit) } &&
                    parts[0].take(2) == parts[1].take(2)) parts[0] to parts[1] else null
            }.toMap()
        }
    }

    fun resolve(reference: String?): RegistryLocation? {
        val normalized = reference?.trim()?.uppercase() ?: return null
        if (!ruralReference.matches(normalized)) return null
        val ineCode = ineByCadastralCode[normalized.take(5)] ?: return null
        val municipality = SpanishMunicipalityCatalog.findByCode(ineCode) ?: return null
        return RegistryLocation(municipality.displayName, municipality.provinceName)
    }
}
