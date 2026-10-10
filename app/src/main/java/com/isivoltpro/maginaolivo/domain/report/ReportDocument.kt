package com.isivoltpro.maginaolivo.domain.report

import java.time.LocalDate

/** One figure of a report: what it is and what it says. */
data class ReportLine(val label: String, val value: String)

/** A block of a report; [note] is said under it, in the farmer's words. */
data class ReportSection(val title: String, val lines: List<ReportLine>, val note: String? = null)

/**
 * Phase 25 — what any report of this app is, as plain data with no Android in it: a heading, the
 * period it covers, what is incomplete about it, and its blocks.
 *
 * A report never computes a figure of its own. Each one is built from the same aggregates the
 * screens read, so what a document says cannot disagree with what the farmer saw (Gate 25), and
 * what is not known is said to be unknown instead of printed as zero.
 */
data class ReportDocument(
    val title: String,
    /** Who and where: "Finca Demo Mágina · Bedmar · Jaén". */
    val subtitle: String?,
    val period: String,
    val generatedOn: LocalDate,
    val sections: List<ReportSection>,
    val warnings: List<String> = emptyList(),
    /** Said at the foot of every page: what this document is, and what it is not. */
    val footer: String = FOOTER,
) {
    companion object {
        const val FOOTER =
            "Documento generado por Mágina Olivo desde los datos de este teléfono. " +
                "No es un certificado ni un documento oficial."
    }
}

/** Anything the app can print. */
interface Printable {
    fun document(): ReportDocument
}
