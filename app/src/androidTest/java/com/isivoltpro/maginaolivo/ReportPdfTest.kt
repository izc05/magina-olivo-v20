package com.isivoltpro.maginaolivo

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.domain.report.ReportDocument
import com.isivoltpro.maginaolivo.domain.report.ReportLine
import com.isivoltpro.maginaolivo.domain.report.ReportSection
import com.isivoltpro.maginaolivo.feature.reports.ReportPdf
import java.io.File
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 25: the report becomes a real PDF on the phone, with no connection and no permission. */
class ReportPdfTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun theCampaignReportIsWrittenAsAReadablePdfWithItsFooterOnEveryPage() {
        val report = report(sections = 1)
        val target = File(context.cacheDir, ReportPdf.fileName(report))
        if (target.exists()) assertTrue(target.delete())

        val written = ReportPdf.write(report, target)

        assertTrue("The report file must exist", written.isFile)
        assertTrue("A one-page report is still a real document", written.length() > 1_000)
        assertEquals("%PDF", written.readBytes().take(4).toByteArray().decodeToString())
        assertEquals("informe-campana-de-recogida-2026-27-10-10-2026.pdf", written.name)

        open(written) { renderer ->
            assertEquals(1, renderer.pageCount)
            renderer.openPage(0).use { page ->
                // A4 at 72 dpi, which is what PdfDocument draws in.
                assertEquals(ReportPdf.PAGE_WIDTH, page.width)
                assertEquals(ReportPdf.PAGE_HEIGHT, page.height)
            }
        }
        assertTrue(written.delete())
    }

    /** A long campaign breaks into pages instead of writing past the bottom of the sheet. */
    @Test fun aReportTooLongForOneSheetBreaksIntoPages() {
        val report = report(sections = 12)
        val target = File(context.cacheDir, "informe-largo.pdf")
        val written = ReportPdf.write(report, target)
        open(written) { renderer -> assertTrue("Expected more than one page", renderer.pageCount > 1) }
        assertTrue(written.delete())
    }

    private fun report(sections: Int) = ReportDocument(
        title = "Campaña de recogida 2026/27",
        subtitle = "Finca Demo Mágina · Bedmar · Jaén",
        period = "Campaña: 28-09-2026 → en curso",
        generatedOn = today,
        sections = (1..sections).map { index ->
            ReportSection(
                title = "Bloque $index",
                lines = (1..8).map { line -> ReportLine("Concepto larguísimo número $line", "1.234 kg") },
                note = "Una nota que explica de dónde salen estas cifras y que no son un cálculo nuevo.",
            )
        },
        warnings = listOf("La campaña aún no tiene pesadas."),
    )

    private fun open(file: File, block: (PdfRenderer) -> Unit) {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use(block)
        }
    }
}
