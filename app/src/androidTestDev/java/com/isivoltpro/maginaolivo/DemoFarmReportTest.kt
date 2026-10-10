package com.isivoltpro.maginaolivo

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.app.AppCompositionRoot
import com.isivoltpro.maginaolivo.app.DemoFarmSeeder
import com.isivoltpro.maginaolivo.app.DevTools
import com.isivoltpro.maginaolivo.app.LocalPersistence
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.data.local.model.isRunning
import com.isivoltpro.maginaolivo.domain.analytics.FarmOverview
import com.isivoltpro.maginaolivo.domain.delivery.Percent
import com.isivoltpro.maginaolivo.domain.expense.CostPerKg
import com.isivoltpro.maginaolivo.domain.expense.Money
import com.isivoltpro.maginaolivo.domain.farm.Farm
import com.isivoltpro.maginaolivo.domain.harvest.Weight
import com.isivoltpro.maginaolivo.domain.report.CampaignReport
import com.isivoltpro.maginaolivo.domain.report.SeasonReport
import com.isivoltpro.maginaolivo.feature.reports.ReportPdf
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gate 25 end to end: the reports are built from the **real agricultural records** of the DEV demo
 * farm — through the same repositories the app reads — and say exactly the figures documented in
 * `docs/DEMO-FARM-SCENARIO.md`. If a Pesada, a jornal or a cost ever stopped reaching a report,
 * this fails.
 */
class DemoFarmReportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val persistence: LocalPersistence = AppCompositionRoot.createAndroid(context, "DEV").localPersistence!!
    private val tools = DevTools.demoFarm(persistence)
    private val today = LocalDate.of(2026, 10, 10)

    @Test fun theCampaignAndSeasonReportsSayTheDemoFarmsRealFigures() = runBlocking {
        val loaded = tools.load()
        assertTrue("load: $loaded", loaded is AppResult.Success)
        val workspace = (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        val demo = demoFarms(workspace).single()
        val campaigns = persistence.campaignRepository.observeForFarm(demo.id).first()
        val running = campaigns.single { it.status.isRunning }
        val deliveries = persistence.deliveryRepository.observeAll().first()
        val harvests = persistence.harvestRepository.observeAll().first()
        val expenses = persistence.expenseRepository.observeAll().first()

        // ---- Campaign report: the active 2026/27 campaign of the demo farm.
        val report = CampaignReport.of(running, demo, deliveries, harvests, expenses, today)
        val production = report.sections.single { it.title == "Producción" }
        assertEquals(Weight.format(6_680_000), production.lines.single { it.label == "Kilos pesados" }.value)
        assertEquals("4", production.lines.single { it.label == "Pesadas" }.value)
        assertEquals("3", production.lines.single { it.label == "Días de recogida" }.value)
        assertEquals(Percent.format(2_057), production.lines.single { it.label == "Rendimiento graso" }.value)

        val costs = report.sections.single { it.title == "Costes de recogida" }
        assertEquals(Money.format(73_250, "EUR"), costs.lines.single { it.label == "Jornales" }.value)
        assertEquals(Money.format(54_000, "EUR"), costs.lines.single { it.label == "Maquinaria" }.value)
        assertEquals(Money.format(25_000, "EUR"), costs.lines.single { it.label == "Otros gastos" }.value)
        assertEquals(Money.format(152_250, "EUR"), costs.lines.single { it.label == "Total de recogida" }.value)
        assertEquals(CostPerKg.format(228, "EUR"), costs.lines.single { it.label == "Coste por kilo" }.value)

        // The four Pesadas are listed with their own vale, and the two of 01-10 are both there.
        val pesadas = report.sections.single { it.title == "Pesadas" }
        assertEquals(4, pesadas.lines.size)
        listOf("DEMO-001", "DEMO-002", "DEMO-004", "DEMO-003").forEach { vale ->
            assertTrue("$vale must be in the report", pesadas.lines.any { it.label.contains(vale) })
        }
        assertEquals(2, pesadas.lines.count { it.label.startsWith("01-10-2026") })
        assertEquals(3, report.sections.single { it.title == "Días de recogida" }.lines.size)
        assertTrue("A complete campaign carries no warning: ${report.warnings}", report.warnings.isEmpty())

        // ---- Season report: recollection apart from the general costs of the holding.
        val season = SeasonReport.of(
            FarmOverview.of("2026/27", listOf(demo), campaigns, deliveries, expenses),
            today,
        )
        val seasonCosts = season.sections.single { it.title == "Costes de la temporada" }
        assertEquals(Money.format(152_250, "EUR"), seasonCosts.lines.single { it.label == "Coste de recogida" }.value)
        assertEquals(
            Money.format(118_500, "EUR"),
            seasonCosts.lines.single { it.label == "Gastos generales (sin campaña)" }.value,
        )
        assertEquals(Money.format(270_750, "EUR"), seasonCosts.lines.single { it.label == "Coste total" }.value)
        assertEquals(
            CostPerKg.format(405, "EUR"),
            seasonCosts.lines.single { it.label == "Coste total por kilo" }.value,
        )

        // ---- Both become real PDFs on this device.
        listOf(report.document(), season.document()).forEach { document ->
            val file = ReportPdf.write(document, File(context.cacheDir, ReportPdf.fileName(document)))
            assertEquals("%PDF", file.readBytes().take(4).toByteArray().decodeToString())
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(descriptor).use { assertTrue(it.pageCount >= 1) }
            }
            assertTrue(file.delete())
        }
    }

    @After fun retireDemo() = runBlocking {
        val workspace = (persistence.workspaceRepository.ensureLocalWorkspace() as AppResult.Success).value
        demoFarms(workspace).forEach { farm ->
            persistence.activityRepository.observeForFarm(farm.id).first()
                .filter { it.status == com.isivoltpro.maginaolivo.data.local.model.ActivityStatus.PLANNED }
                .forEach { persistence.activityRepository.cancel(it.id) }
            persistence.campaignRepository.observeForFarm(farm.id).first().filter { it.status.isRunning }.forEach {
                persistence.campaignRepository.close(it.id, maxOf(it.startDate, DemoFarmSeeder.LAST_DAY))
            }
            persistence.farmRepository.archive(farm.id)
        }
    }

    private suspend fun demoFarms(workspace: UUID): List<Farm> =
        persistence.farmRepository.observeActive(workspace).first().filter { DemoFarmSeeder.isDemo(it) }
}
