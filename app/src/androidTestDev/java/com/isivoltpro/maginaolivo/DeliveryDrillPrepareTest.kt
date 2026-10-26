package com.isivoltpro.maginaolivo

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.core.common.AppResult
import com.isivoltpro.maginaolivo.core.dispatchers.DefaultAppDispatchers
import com.isivoltpro.maginaolivo.core.id.UuidGenerator
import com.isivoltpro.maginaolivo.core.time.SystemAppClock
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.LocalFarmLinkRepository
import com.isivoltpro.maginaolivo.data.repository.LocalWorkspaceRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstCampaignRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstFarmRepository
import com.isivoltpro.maginaolivo.data.repository.OfflineFirstParcelRepository
import com.isivoltpro.maginaolivo.domain.campaign.NewCampaign
import com.isivoltpro.maginaolivo.domain.farm.NewFarm
import com.isivoltpro.maginaolivo.domain.parcel.NewParcel
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * DEV-only, one-shot preparation of the MINIMAL synthetic LOCAL hierarchy in installation B for
 * the single-Pesada drill. It runs inside the real app process against the real Room database,
 * the real Auth session store and the real consent ledger, and it sends NOTHING: no agricultural
 * RPC, no worker, no transport. Auth is contacted only by `resume` to restore the session.
 *
 * Mode `-e g24DrillMode inspect` is read-only. Mode `hierarchy` creates farm, parcel and campaign
 * and records the G1b consent, but ONLY when every expectation below holds, so it cannot touch
 * the wrong installation or a database that already carries agricultural data.
 */
@RunWith(AndroidJUnit4::class)
class DeliveryDrillPrepareTest {

    private val args = InstrumentationRegistry.getArguments()
    private val mode = args.getString("g24DrillMode")
    private val expectedWorkspace = args.getString("g24Workspace")
    private val expectedUser = args.getString("g24User")

    private fun say(text: String) { Log.i("G24DRILL", text); println("G24DRILL $text") }

    private fun count(db: MaginaOlivoDatabase, table: String): Long =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM $table").use { it.moveToFirst(); it.getLong(0) }

    @Test
    fun prepare() = runBlocking {
        assumeTrue("g24DrillMode is inspect|hierarchy", mode == "inspect" || mode == "hierarchy")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = MaginaOlivoDatabase.getInstance(context)
        val account = AccountRuntime.get(context)
        val before = account.state.value
        say("pre-resume status=${before.status} error=${before.error}")
        account.resume(true)
        val after = account.state.value
        say("post-resume status=${after.status} error=${after.error} online=${after.online}")
        val userId = account.state.value.userId
        val workspace = db.workspaceDao().findFirstActive()
        say("mode=$mode user=$userId workspace=${workspace?.id}")
        listOf("workspaces", "farms", "parcels", "campaigns", "deliveries", "sync_outbox").forEach {
            say("count $it=${count(db, it)}")
        }
        if (mode == "inspect") return@runBlocking

        // Guards: wrong device, wrong account, or any agricultural row present => stop untouched.
        assertEquals("Account must be the QA account", expectedUser, userId?.toString())
        assertEquals("Workspace must be B's", expectedWorkspace, workspace?.id?.toString())
        assertEquals("No farms yet", 0L, count(db, "farms"))
        assertEquals("No parcels yet", 0L, count(db, "parcels"))
        assertEquals("No campaigns yet", 0L, count(db, "campaigns"))
        assertEquals("No deliveries yet", 0L, count(db, "deliveries"))

        val clock = SystemAppClock()
        val ids = UuidGenerator()
        val dispatchers = DefaultAppDispatchers()
        val farms = OfflineFirstFarmRepository(db, clock, ids, dispatchers,
            LocalWorkspaceRepository(db, clock, ids, dispatchers, com.isivoltpro.maginaolivo.core.regional.RegionalContext.spainDefault()))
        val parcels = OfflineFirstParcelRepository(db, clock, ids, dispatchers)
        val workspaces = LocalWorkspaceRepository(db, clock, ids, dispatchers,
            com.isivoltpro.maginaolivo.core.regional.RegionalContext.spainDefault())
        val campaigns = OfflineFirstCampaignRepository(db, clock, ids, dispatchers, workspaces)

        val farmId = (farms.create(NewFarm(workspace!!.id, "Finca QA G2.4")) as AppResult.Success).value
        val parcelId = (parcels.create(NewParcel(farmId, "Parcela QA G2.4")) as AppResult.Success).value
        val campaignId = (campaigns.create(
            NewCampaign(farmId, "Campana QA G2.4", LocalDate.now(), setOf(parcelId)),
        ) as AppResult.Success).value

        val links = LocalFarmLinkRepository(context, db, account, com.isivoltpro.maginaolivo.BuildConfig.AUTH_PROJECT_URL)
        val ledger = links.consent(userId!!, links.choices().filter { it.id == farmId }, true)
        val consent = ledger.consents.last()
        say("RESULT workspace=${workspace.id} farm=$farmId parcel=$parcelId campaign=$campaignId")
        say("RESULT consentOperation=${consent.operationId} subject=${consent.subject} farms=${consent.farms.map { it.id }}")
        listOf("workspaces", "farms", "parcels", "campaigns", "deliveries", "sync_outbox").forEach {
            say("after $it=${count(db, it)}")
        }
    }
}
