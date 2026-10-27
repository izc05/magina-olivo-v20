package com.isivoltpro.maginaolivo

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.regional.RegionalContext
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstDeliveryRepository
import com.isivoltpro.maginaolivo.data.repository.deliveryPayloadHash
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryDraft
import com.isivoltpro.maginaolivo.domain.delivery.DeliveryShareInput
import com.isivoltpro.maginaolivo.domain.delivery.PesadaOrigin
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEV-only. Stages ONE synthetic manual Pesada of 1540 kg in installation B, offline and without
 * a photo, through the real repository and the real consent scope, and reports the facts the
 * drill depends on. It sends nothing and does not touch the worker, the lease or any transport.
 * Mode `stage` creates; mode `report` only reads.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDrillStageTest {
    private val args = InstrumentationRegistry.getArguments()
    private val mode = args.getString("g24DrillMode")
    private fun say(text: String) { Log.i("G24DRILL", text); println("G24DRILL $text") }

    private val user = UUID.fromString("008ea900-5f3c-407f-8917-ad72bc9d955b")
    private val farm = UUID.fromString("72dc9b9e-8814-4d8c-b95d-00b986a3e66e")
    private val parcel = UUID.fromString("6243d355-6ce6-4189-a37e-8527fb233abe")
    private val campaign = UUID.fromString("1c053c81-294c-405b-8426-04ca8a18ecf9")

    private fun rows(db: MaginaOlivoDatabase, sql: String): List<List<String?>> =
        db.openHelper.readableDatabase.query(sql).use { c ->
            buildList { while (c.moveToNext()) add((0 until c.columnCount).map { c.getString(it) }) }
        }

    @Test
    fun stage() = runBlocking {
        assumeTrue(mode == "stage" || mode == "report" || mode == "harvest")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MaginaOlivoDatabase.getInstance(context)
        val account = AccountRuntime.get(context)
        account.resume(true)
        assertEquals(user, account.state.value.userId)
        val links = LocalFarmLinkRepository(context, db, account, BuildConfig.AUTH_PROJECT_URL)
        assertTrue("Farm must carry active consent", links.isFarmConsented(farm, user))
        if (mode == "harvest") {
            val before = rows(db, "SELECT status, version FROM campaigns WHERE id='$campaign'")
            say("campaign before=$before")
            say("campaign_parcels before=" + rows(db, "SELECT id, parcel_id, farm_id_at_start FROM campaign_parcels WHERE campaign_id='$campaign'"))
            assertEquals(listOf(listOf<String?>("PREPARATION", "1")), before)
            val clock0 = SystemAppClock(); val ids0 = UuidGenerator(); val disp0 = DefaultAppDispatchers()
            val ws0 = LocalWorkspaceRepository(db, clock0, ids0, disp0, RegionalContext.spainDefault())
            val camps = com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository(db, clock0, ids0, disp0, ws0)
            say("activate=${camps.activate(campaign)}")
            say("markHarvest=${camps.markHarvest(campaign)}")
            say("campaign after=" + rows(db, "SELECT status, version FROM campaigns WHERE id='$campaign'"))
            say("campaign_parcels after=" + rows(db, "SELECT id, parcel_id, farm_id_at_start FROM campaign_parcels WHERE campaign_id='$campaign'"))
        }
        if (mode == "stage") {
            assertEquals("No Pesada yet", 0, rows(db, "SELECT COUNT(*) FROM deliveries")[0][0]!!.toInt())
            val clock = SystemAppClock()
            val ids = UuidGenerator()
            val dispatchers = DefaultAppDispatchers()
            val workspaces = LocalWorkspaceRepository(db, clock, ids, dispatchers, RegionalContext.spainDefault())
            val repo = OfflineFirstDeliveryRepository(db, clock, ids, dispatchers, workspaces, links)
            val created = repo.create(
                DeliveryDraft(
                    farmId = farm,
                    deliveryDate = LocalDate.now(),
                    destinationOrganizationId = null,
                    destinationName = "Destino sintetico QA G2.4",
                    netGrams = 1_540_000L,
                    shares = listOf(DeliveryShareInput(parcel, null)),
                    origin = PesadaOrigin.TREE,
                ),
            )
            say("create=${(created as? AppResult.Success)?.value ?: created}")
        }
        say("deliveries=" + rows(db, "SELECT id, farm_id, net_grams FROM deliveries"))
        rows(db, "SELECT id, entity_type, entity_id, operation, status, payload_version, base_remote_version, account_id, farm_id, last_error_code FROM sync_outbox ORDER BY created_at")
            .forEach { say("outbox=$it") }
        val d = rows(db, "SELECT id, payload_json, payload_version FROM sync_outbox WHERE entity_type='DELIVERY'")
        say("deliveryOutboxRows=${d.size}")
        d.forEach { say("payload=${it[1]}"); say("hash=${deliveryPayloadHash(it[1]!!, it[2]!!.toInt())}") }
        say("prepareRows=" + rows(db, "SELECT COUNT(*) FROM delivery_dispatch_prepare")[0][0])
        say("consentedFarm=${links.isFarmConsented(farm, user)} campaign=$campaign parcel=$parcel")
    }
}
