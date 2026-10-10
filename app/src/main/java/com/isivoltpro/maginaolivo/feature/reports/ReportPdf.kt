package com.isivoltpro.maginaolivo.feature.reports

import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.isivoltpro.maginaolivo.domain.report.CampaignReport
import com.isivoltpro.maginaolivo.domain.report.ReportDocument
import com.isivoltpro.maginaolivo.domain.report.ReportSection
import java.io.File
import java.io.FileOutputStream
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Phase 25 — writes a [ReportDocument] as a PDF with the platform's own writer, on the phone and
 * with no connection. It only draws what the report already says: it never recomputes a figure,
 * so the document and the screen cannot disagree.
 *
 * The layout is deliberately plain — A4 at 72 dpi, one column, a page break when the page is
 * full — because the farmer sends this to a cooperative or an accountant, who needs it legible,
 * not decorated.
 */
object ReportPdf {
    /** A4 in PostScript points, the unit [PdfDocument] draws in. */
    const val PAGE_WIDTH = 595
    const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val BOTTOM = 72f

    private val SPANISH: Locale = Locale.forLanguageTag("es-ES")
    private val GENERATED: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", SPANISH)

    /** Writes the document to [target] and returns it. The file is replaced if it existed. */
    fun write(report: ReportDocument, target: File): File {
        val document = PdfDocument()
        try {
            val renderer = Renderer(document, report)
            renderer.header()
            report.warnings.forEach(renderer::warning)
            report.sections.forEach(renderer::section)
            renderer.finish()
            target.parentFile?.mkdirs()
            FileOutputStream(target).use(document::writeTo)
        } finally {
            document.close()
        }
        return target
    }

    /** "informe-campana-de-recogida-2026-27-10-10-2026.pdf" — a name the farmer finds again. */
    fun fileName(report: ReportDocument): String {
        val slug = (report.title + "-" + report.generatedOn.format(GENERATED))
            .lowercase(SPANISH)
            .replace(ACCENTS) { match -> ACCENT_MAP[match.value] ?: match.value }
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return "informe-$slug.pdf"
    }

    private val ACCENTS = Regex("[áéíóúüñç]")
    private val ACCENT_MAP = mapOf(
        "á" to "a", "é" to "e", "í" to "i", "ó" to "o", "ú" to "u", "ü" to "u", "ñ" to "n", "ç" to "c",
    )

    private class Renderer(private val document: PdfDocument, private val report: ReportDocument) {
        private val title = paint(17f, bold = true)
        private val heading = paint(12.5f, bold = true)
        private val body = paint(10.5f)
        private val quiet = paint(9f, grey = true)
        private var page: PdfDocument.Page = newPage()
        private var y = MARGIN
        private var pages = 1

        fun header() {
            draw(report.title, title, 22f)
            draw(report.subtitle.orEmpty(), body, 15f)
            draw(report.period, body, 15f)
            draw("Generado el ${report.generatedOn.format(GENERATED)}", quiet, 20f)
        }

        fun warning(text: String) = draw("• $text", quiet, 14f)

        fun section(section: ReportSection) {
            space(10f)
            draw(section.title, heading, 17f)
            section.lines.forEach { line ->
                val label = line.label
                val value = line.value
                val room = PAGE_WIDTH - 2 * MARGIN - body.measureText(value) - 8f
                drawPair(ellipsize(label, room), value)
            }
            section.note?.let { note ->
                space(2f)
                wrap(note, quiet, PAGE_WIDTH - 2 * MARGIN).forEach { draw(it, quiet, 12f) }
            }
        }

        fun finish() {
            footer()
            document.finishPage(page)
        }

        private fun drawPair(label: String, value: String) {
            ensure(14f)
            page.canvas.drawText(label, MARGIN, y, body)
            page.canvas.drawText(value, PAGE_WIDTH - MARGIN - body.measureText(value), y, body)
            y += 14f
        }

        private fun draw(text: String, paint: Paint, advance: Float) {
            if (text.isEmpty()) return
            ensure(advance)
            page.canvas.drawText(text, MARGIN, y, paint)
            y += advance
        }

        private fun space(amount: Float) {
            ensure(amount)
            y += amount
        }

        /** A page break keeps the footer on every page, so a loose sheet still says what it is. */
        private fun ensure(advance: Float) {
            if (y + advance <= PAGE_HEIGHT - BOTTOM) return
            footer()
            document.finishPage(page)
            pages += 1
            page = newPage()
            y = MARGIN
        }

        private fun footer() {
            val line = "${report.footer}  ·  página $pages"
            page.canvas.drawText(line, MARGIN, PAGE_HEIGHT - 40f, quiet)
        }

        private fun newPage(): PdfDocument.Page =
            document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pages).create())

        private fun ellipsize(text: String, room: Float): String {
            if (room <= 0f || body.measureText(text) <= room) return text
            var cut = text.length
            while (cut > 1 && body.measureText(text.take(cut) + "…") > room) cut -= 1
            return text.take(cut) + "…"
        }

        private fun wrap(text: String, paint: Paint, width: Float): List<String> {
            val lines = mutableListOf<String>()
            var current = StringBuilder()
            text.split(' ').forEach { word ->
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (paint.measureText(candidate) <= width) {
                    current = StringBuilder(candidate)
                } else {
                    if (current.isNotEmpty()) lines += current.toString()
                    current = StringBuilder(word)
                }
            }
            if (current.isNotEmpty()) lines += current.toString()
            return lines
        }

        private fun paint(size: Float, bold: Boolean = false, grey: Boolean = false) = Paint().apply {
            isAntiAlias = true
            textSize = size
            color = if (grey) 0xFF5F6368.toInt() else 0xFF1B1B1B.toInt()
            typeface = Typeface.create(Typeface.SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }
    }
}

/**
 * Phase 25 — where the campaign's report is written on this phone and how it leaves it.
 *
 * The file lives in the app's own `reports/` folder, which the existing FileProvider shares
 * read-only on request: no storage permission is asked for and nothing is written where another
 * app could change it. Sharing opens the system chooser, so the farmer decides where it goes.
 */
internal fun writeCampaignReport(
    context: android.content.Context,
    campaign: com.isivoltpro.maginaolivo.domain.campaign.Campaign,
    farm: com.isivoltpro.maginaolivo.domain.farm.Farm?,
    deliveries: List<com.isivoltpro.maginaolivo.domain.delivery.Delivery>,
    harvests: List<com.isivoltpro.maginaolivo.domain.harvest.Harvest>,
    expenses: List<com.isivoltpro.maginaolivo.domain.expense.Expense>,
    today: java.time.LocalDate = java.time.LocalDate.now(),
): File {
    val report = CampaignReport.of(campaign, farm, deliveries, harvests, expenses, today).document()
    return writeReport(context, report)
}

/** Offers the written report to whatever the phone can send or open a PDF with. */
internal fun shareReport(context: android.content.Context, file: File): Result<Unit> = runCatching {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${context.packageName}.$PROVIDER_SUFFIX",
        file,
    )
    val send = android.content.Intent(android.content.Intent.ACTION_SEND)
        .setType("application/pdf")
        .putExtra(android.content.Intent.EXTRA_STREAM, uri)
        .putExtra(android.content.Intent.EXTRA_SUBJECT, file.name)
        .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(
        android.content.Intent.createChooser(send, "Compartir informe")
            .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION),
    )
}

/** Writes any report into the app's own `reports/` folder and returns the file. */
internal fun writeReport(
    context: android.content.Context,
    report: com.isivoltpro.maginaolivo.domain.report.ReportDocument,
): File {
    val folder = File(context.filesDir, REPORTS_DIRECTORY)
    return ReportPdf.write(report, File(folder, ReportPdf.fileName(report)))
}

internal const val REPORTS_DIRECTORY = "reports"
private const val PROVIDER_SUFFIX = "attachments"
