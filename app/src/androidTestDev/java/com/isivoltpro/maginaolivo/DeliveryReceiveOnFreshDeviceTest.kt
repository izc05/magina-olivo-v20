package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryReceiveOutcome
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.data.repository.sync.receiveDeliveryBundle
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import java.util.UUID
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in G2.5 drill for the one audited DEV Pesada on the clean receiver D. */
@RunWith(AndroidJUnit4::class)
class DeliveryReceiveOnFreshDeviceTest {
    @Test fun receivesAuditedDeliveryOnceWithNoOutbox() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("g25ReceiveProject") == PROJECT)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
        assertEquals("https://$PROJECT.supabase.co", BuildConfig.AUTH_PROJECT_URL)
        val config = checkNotNull(AuthConfiguration.forEnvironment(
            "DEV", BuildConfig.AUTH_PROJECT_URL, BuildConfig.AUTH_PUBLIC_KEY, context.packageName,
        ))
        val db = MaginaOlivoDatabase.getInstance(context)
        fun count(table: String) = db.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        val graphTables = listOf(
            "workspaces", "farms", "parcels", "farm_parcel_memberships", "campaigns",
            "campaign_parcels", "harvests", "harvest_parcels", "deliveries",
            "delivery_parcels",
        )
        graphTables.forEach { assertEquals("D must start empty: $it", 0, count(it)) }
        assertEquals(0, count("sync_outbox"))
        assertEquals(0, count("delivery_import_receipts"))

        val account = AccountRuntime.get(context)
        account.resume(true)
        assertEquals(ACTOR, account.state.value.userId)
        val transport = DeliveryRpcAdapter(HttpsDeliveryRpcTransport(config))
        val bundle = account.withServerVerifiedIdentity(
            remote = { session -> transport.readOwnDeliveryReceiveBundle(session, DELIVERY, ISSUER) },
            commit = { actor, value ->
                assertEquals(ACTOR, actor)
                assertEquals(ACTOR, value.accountId)
                assertEquals(DELIVERY, value.delivery.id)
                assertEquals(1_540_000L, value.delivery.netGrams)
                assertEquals(HASH, value.headPayloadHash)
                assertEquals(DeliveryReceiveOutcome.IMPORTED, db.receiveDeliveryBundle(value))
                value
            },
        )
        graphTables.forEach { assertEquals("Exactly one imported row: $it", 1, count(it)) }
        assertEquals(1, count("delivery_import_receipts"))
        assertEquals(0, count("sync_outbox"))
        assertEquals(DeliveryReceiveOutcome.ALREADY_IMPORTED, db.receiveDeliveryBundle(bundle))
        assertEquals(1, count("delivery_import_receipts"))
        assertEquals(0, count("sync_outbox"))
    }

    private companion object {
        const val PROJECT = "zubpicdibqbmewxeurhm"
        const val ISSUER = "https://$PROJECT.supabase.co/auth/v1"
        const val HASH = "e5c3825c96a37a42186b800b7bebc59167fe68d6c738672e20921d2dd5380902"
        val ACTOR = UUID.fromString("008ea900-5f3c-407f-8917-ad72bc9d955b")
        val DELIVERY = UUID.fromString("07db9e31-e48e-4041-8c82-bb875af0c5e4")
    }
}
