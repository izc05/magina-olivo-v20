package com.isivoltpro.maginaolivo

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.isivoltpro.maginaolivo.data.local.MaginaOlivoDatabase
import com.isivoltpro.maginaolivo.data.repository.sync.DeliveryRpcAdapter
import com.isivoltpro.maginaolivo.data.repository.sync.HttpsDeliveryRpcTransport
import com.isivoltpro.maginaolivo.feature.account.AccountRuntime
import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import java.util.Base64
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in DEV probe: Auth /user and one known-UUID RPC read. No Room or remote writes. */
@RunWith(AndroidJUnit4::class)
class DeliveryReadOnFreshDeviceTest {
    @Test fun readsExistingDeliveryWithoutImportingIt() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("g24ReadOnlyProject") == PROJECT)
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("com.isivoltpro.maginaolivo.dev", context.packageName)
        assertEquals("https://$PROJECT.supabase.co", BuildConfig.AUTH_PROJECT_URL)
        val config = checkNotNull(AuthConfiguration.forEnvironment(
            "DEV", BuildConfig.AUTH_PROJECT_URL, BuildConfig.AUTH_PUBLIC_KEY, context.packageName,
        ))
        val database = MaginaOlivoDatabase.getInstance(context)
        fun count(table: String) = database.openHelper.readableDatabase
            .query("SELECT COUNT(*) FROM $table").use { cursor ->
                check(cursor.moveToFirst())
                cursor.getInt(0)
            }
        for (table in TABLES) assertEquals("D must start without $table", 0, count(table))

        val account = AccountRuntime.get(context)
        account.resume(true) // /auth/v1/user validates the stored D session.
        assertEquals(ACTOR, account.state.value.userId)
        val aggregate = account.withServerVerifiedIdentity(
            remote = { session ->
                val claims = Json.parseToJsonElement(String(
                    Base64.getUrlDecoder().decode(session.accessToken.split('.')[1]), Charsets.UTF_8,
                )).jsonObject
                assertEquals("https://$PROJECT.supabase.co/auth/v1", claims.getValue("iss").jsonPrimitive.content)
                assertEquals(ACTOR.toString(), claims.getValue("sub").jsonPrimitive.content)
                DeliveryRpcAdapter(HttpsDeliveryRpcTransport(config))
                    .readOwnDeliveryAggregate(session, DELIVERY)
            },
            commit = { actor, value ->
                assertEquals(ACTOR, actor)
                value
            },
        )
        assertNotNull(aggregate)
        val delivery = requireNotNull(aggregate)
        assertEquals(DELIVERY, delivery.deliveryId)
        assertEquals(1L, delivery.version)
        assertEquals(HASH, delivery.headPayloadHash)
        assertEquals(FARM, delivery.farmId)
        assertEquals(CAMPAIGN, delivery.campaignId)
        assertEquals(1_540_000L, delivery.netGrams)
        assertEquals(1, delivery.parcels.size)
        assertEquals(PARCEL, delivery.parcels.single().parcelId)
        assertNull(delivery.parcels.single().weightGrams)
        for (table in TABLES) assertEquals("Read must not import $table", 0, count(table))
    }

    private companion object {
        const val PROJECT = "zubpicdibqbmewxeurhm"
        const val HASH = "e5c3825c96a37a42186b800b7bebc59167fe68d6c738672e20921d2dd5380902"
        val ACTOR = UUID.fromString("008ea900-5f3c-407f-8917-ad72bc9d955b")
        val DELIVERY = UUID.fromString("07db9e31-e48e-4041-8c82-bb875af0c5e4")
        val FARM = UUID.fromString("72dc9b9e-8814-4d8c-b95d-00b986a3e66e")
        val CAMPAIGN = UUID.fromString("1c053c81-294c-405b-8426-04ca8a18ecf9")
        val PARCEL = UUID.fromString("6243d355-6ce6-4189-a37e-8527fb233abe")
        val TABLES = listOf("farms", "parcels", "campaigns", "deliveries")
    }
}
