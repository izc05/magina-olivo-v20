package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.data.local.model.SyncEntityType
import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.io.IOException
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * G2.4 read/ACK adapter against the #773 matrix. These tests are the contract: they fail if the
 * adapter starts trusting the response for the account, recomputing hashes, exceeding the batch
 * limit, reserializing the payload, or treating a cut connection as a failed dispatch.
 */
class DeliveryRpcAdapterTest {

    private val operationId = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001")
    private val deliveryId = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000001")
    private val userId = UUID.fromString("cccccccc-0000-0000-0000-000000000001")
    private val session = AuthSession(userId, "b1@example.test", "access-token", "refresh-token",
        Instant.parse("2027-01-01T00:00:00Z"))

    private class Recorder(private val answer: (String, String) -> DeliveryRpcResponse) : DeliveryRpcTransport {
        val calls = mutableListOf<Pair<String, String>>()
        var tokens = mutableListOf<String>()
        override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
            calls += name to bodyJson
            tokens += accessToken
            return answer(name, bodyJson)
        }
    }

    private fun rpc(answer: (String, String) -> DeliveryRpcResponse) = Recorder(answer)

    // --------------------------------------------------------------------------------- write

    @Test
    fun theWriteEmbedsThePayloadBytesVerbatimAndCarriesTheVerifiedToken() = runBlocking {
        // Key order and spacing are NOT normalized: the server hashes p_delivery::text.
        val payload = """{"id":"$deliveryId","netGrams":2800000,"notes":"con  espacios","farmId":"$deliveryId"}"""
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"operation_id":"$operationId","entity_id":"$deliveryId",
                "remote_version":7,"payload_version":2}""")
        }
        val result = DeliveryRpcAdapter(transport).syncOwnDelivery(session, operationId, payload)

        assertEquals(DeliveryRpcAdapter.SYNC_OWN_DELIVERY_V2, transport.calls.single().first)
        assertTrue(
            "Los bytes del payload deben viajar tal cual: ${transport.calls.single().second}",
            transport.calls.single().second.contains(payload),
        )
        assertEquals(listOf("access-token"), transport.tokens)
        assertEquals(operationId, result.operationId)
        assertEquals(deliveryId, result.entityId)
        assertEquals(7L, result.remoteVersion)
        assertEquals(2, result.payloadVersion)
    }

    @Test
    fun aWriteThatAnswersForAnotherOperationIsAProtocolFailure() = runBlocking {
        val other = UUID.randomUUID()
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"operation_id":"$other","entity_id":"$deliveryId",
                "remote_version":1,"payload_version":2}""")
        }
        assertEquals(
            DeliveryRpcError.PROTOCOL,
            failureOf { DeliveryRpcAdapter(transport).syncOwnDelivery(session, operationId, "{}") }.error,
        )
    }

    // ---------------------------------------------------------------------------- receipts

    @Test
    fun theAccountOfAReceiptComesFromTheSessionAndNeverFromTheResponse() = runBlocking {
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"$operationId",
                "entity_id":"$deliveryId","entity_type":"DELIVERY","remote_version":7,
                "payload_version":2,"payload_hash":"${"a".repeat(64)}",
                "account_id":"99999999-9999-9999-9999-999999999999","actor":"intruso"}]}""")
        }
        val receipt = DeliveryRpcAdapter(transport).checkSyncOperations(session, listOf(operationId))[operationId]!!

        assertEquals("El actor sale de la sesion verificada", userId.toString(), receipt.accountId)
        assertEquals(SyncEntityType.DELIVERY, receipt.entityType)
        assertEquals(7L, receipt.remoteVersion)
        assertEquals("a".repeat(64), receipt.payloadHash)
        assertEquals(2, receipt.payloadVersion)
    }

    @Test
    fun aReceiptKeepsTheEntityTypeTheServerReportedEvenWhenItIsNotADelivery() = runBlocking {
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"$operationId",
                "entity_id":"$deliveryId","entity_type":"EXPENSE","remote_version":3,
                "payload_version":2,"payload_hash":"${"b".repeat(64)}"}]}""")
        }
        val receipt = DeliveryRpcAdapter(transport).checkSyncOperations(session, listOf(operationId))[operationId]!!
        // The adapter does not silently turn it into a Delivery: the guard downstream drops it.
        assertEquals(SyncEntityType.EXPENSE, receipt.entityType)
    }

    @Test
    fun anAbsentReceiptIsAbsenceAndNotAFailure() = runBlocking {
        val transport = rpc { _, _ -> DeliveryRpcResponse(200, """{"confirmed_receipts":[]}""") }
        assertTrue(DeliveryRpcAdapter(transport).checkSyncOperations(session, listOf(operationId)).isEmpty())
    }

    @Test
    fun operationsAreAskedInBatchesOfAtMostTwoHundred() = runBlocking {
        val ids = (1..450).map { UUID.randomUUID() }
        val transport = rpc { _, _ -> DeliveryRpcResponse(200, """{"confirmed_receipts":[]}""") }
        DeliveryRpcAdapter(transport).checkSyncOperations(session, ids)

        assertEquals("450 operaciones -> 3 lotes", 3, transport.calls.size)
        val sizes = transport.calls.map { (_, body) -> Regex("\"[0-9a-f-]{36}\"").findAll(body).count() }
        assertEquals(listOf(200, 200, 50), sizes)
        assertTrue("Ningun lote supera el limite de la matriz", sizes.all { it <= DeliveryRpcAdapter.BATCH })
    }

    // -------------------------------------------------------------------------------- head

    @Test
    fun aNullHeadHashIsReportedAsNullAndNeverInvented() = runBlocking {
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"delivery":{"version":9},"head_payload_hash":null}""")
        }
        val (version, hash) = DeliveryRpcAdapter(transport).getOwnDeliveryV2(session, deliveryId)
        assertEquals(9L, version)
        assertNull("Sin coincidencia unica el hash es null, no se adivina", hash)
    }

    @Test
    fun remoteStateJoinsTheHistoricalReceiptWithTheCurrentHead() = runBlocking {
        val transport = rpc { name, _ ->
            when (name) {
                DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS -> DeliveryRpcResponse(200,
                    """{"confirmed_receipts":[{"operation_id":"$operationId","entity_id":"$deliveryId",
                       "entity_type":"DELIVERY","remote_version":7,"payload_version":2,
                       "payload_hash":"${"c".repeat(64)}"}]}""")
                else -> DeliveryRpcResponse(200,
                    """{"delivery":{"version":11},"head_payload_hash":"${"d".repeat(64)}"}""")
            }
        }
        val state = DeliveryRpcAdapter(transport).remoteDeliveryState(session, operationId, deliveryId)

        // The receipt is the HISTORICAL one, even though the aggregate moved to a later version.
        assertEquals(7L, state.operationReceipt!!.remoteVersion)
        assertEquals(11L, state.headVersion)
        assertEquals("d".repeat(64), state.headPayloadHash)
    }

    // ------------------------------------------------------------------------------ errors

    @Test
    fun postgresErrorsMapToTheContractOfTheMatrix() = runBlocking {
        val cases = listOf(
            Triple(409, "23505", DeliveryRpcError.CONFLICT),
            Triple(403, "42501", DeliveryRpcError.FORBIDDEN),
            Triple(400, "22023", DeliveryRpcError.INVALID_PAYLOAD),
            Triple(500, null, DeliveryRpcError.NETWORK),
            Triple(503, null, DeliveryRpcError.NETWORK),
            Triple(429, null, DeliveryRpcError.NETWORK),
            Triple(418, null, DeliveryRpcError.PROTOCOL),
        )
        for ((status, code, expected) in cases) {
            val body = if (code == null) "{}" else """{"code":"$code","message":"x"}"""
            val transport = rpc { _, _ -> DeliveryRpcResponse(status, body) }
            val failure = failureOf {
                DeliveryRpcAdapter(transport).syncOwnDelivery(session, operationId, "{}")
            }
            assertEquals("status=$status code=$code", expected, failure.error)
        }
    }

    @Test
    fun aCutConnectionIsAnUnknownOutcomeAndNeverAFailedDispatch() = runBlocking {
        val transport = object : DeliveryRpcTransport {
            override suspend fun rpc(name: String, bodyJson: String, accessToken: String) =
                throw IOException("conexion cortada")
        }
        val failure = failureOf { DeliveryRpcAdapter(transport).syncOwnDelivery(session, operationId, "{}") }
        assertEquals(DeliveryRpcError.NETWORK, failure.error)
        assertTrue("Una respuesta perdida deja el resultado DESCONOCIDO", failure.outcomeUnknown)
    }

    @Test
    fun anAnswerOutsideTheContractIsUnknownAndNeverSuccess() = runBlocking {
        for (body in listOf("no es json", "[]", "\"texto\"", """{"operation_id":"no-es-uuid"}""")) {
            val transport = rpc { _, _ -> DeliveryRpcResponse(200, body) }
            val failure = failureOf {
                DeliveryRpcAdapter(transport).syncOwnDelivery(session, operationId, "{}")
            }
            assertEquals("body=$body", DeliveryRpcError.PROTOCOL, failure.error)
            assertTrue("body=$body", failure.outcomeUnknown)
        }
    }

    @Test
    fun onlyTheContractedRpcNamesAreEverCalled() = runBlocking {
        val transport = rpc { name, _ ->
            assertTrue("RPC fuera del contrato: $name", name in DeliveryRpcAdapter.NAMES)
            DeliveryRpcResponse(200, """{"confirmed_receipts":[]}""")
        }
        val adapter = DeliveryRpcAdapter(transport)
        adapter.checkSyncOperations(session, listOf(operationId))
        assertEquals(setOf(DeliveryRpcAdapter.CHECK_SYNC_OPERATIONS), transport.calls.map { it.first }.toSet())
        assertEquals(
            setOf("sync_own_delivery_v2", "check_sync_operations", "get_own_delivery_v2", "get_own_delivery_receive_v1"),
            DeliveryRpcAdapter.NAMES,
        )
    }


    // --------------------------------------------------- regresiones de la revision de #774

    @Test
    fun cancellationTravelsUntouchedAndIsNeverReportedAsANetworkError() = runBlocking {
        // Review #774: a cancelled request is cooperative control flow, not a lost response.
        // Turning it into NETWORK would report OUTCOME_UNKNOWN for something the caller itself
        // cancelled, and would hide the cancellation from the enclosing scope.
        val transport = object : DeliveryRpcTransport {
            override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse =
                throw kotlinx.coroutines.CancellationException("cancelado por el llamante")
        }
        val adapter = DeliveryRpcAdapter(transport)

        for (call in listOf<suspend () -> Any?>(
            { adapter.syncOwnDelivery(session, operationId, "{}") },
            { adapter.checkSyncOperations(session, listOf(operationId)) },
            { adapter.getOwnDeliveryV2(session, deliveryId) },
        )) {
            var cancelled = false
            try {
                call()
            } catch (expected: kotlinx.coroutines.CancellationException) {
                cancelled = true
            } catch (failure: DeliveryRpcFailure) {
                fail("La cancelacion no puede convertirse en ${failure.error}")
            }
            assertTrue("La CancellationException debe propagarse tal cual", cancelled)
        }
    }

    @Test
    fun aRealCoroutineCancellationIsNotSwallowedByTheAdapter() = runBlocking {
        // The same thing, but with a cancellation the runtime raises rather than a thrown
        // instance: the adapter must not convert a cancelled scope into an unknown outcome.
        val started = kotlinx.coroutines.CompletableDeferred<Unit>()
        val transport = object : DeliveryRpcTransport {
            override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse {
                started.complete(Unit)
                kotlinx.coroutines.delay(60_000)
                return DeliveryRpcResponse(200, "{}")
            }
        }
        val adapter = DeliveryRpcAdapter(transport)
        var seen: Throwable? = null
        val job = kotlinx.coroutines.GlobalScope.launch {
            try {
                adapter.checkSyncOperations(session, listOf(operationId))
            } catch (error: Throwable) {
                seen = error
                throw error
            }
        }
        started.await()
        job.cancelAndJoin()
        assertTrue(
            "Se esperaba una cancelacion, no ${seen?.javaClass?.simpleName}",
            seen is kotlinx.coroutines.CancellationException,
        )
    }

    // ----------------------------------------------- D4: vector literal v2 confirmado con #773

    @Test
    fun theV2ReceiptVectorUsesTheServersLiteralDeliveryAndVersionTwo() = runBlocking {
        // D4 closed 25/10/2026 against #773@846a882a: `sync_delivery` inserts entity_type
        // 'DELIVERY' literally into magina_g2_private.sync_operations, and
        // check_sync_operations copies the stored value unchanged. This is the exact v2 vector
        // Android must accept, byte for byte, with no normalization on either side.
        val hash = "e".repeat(64)
        val transport = rpc { _, _ ->
            DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"$operationId",
                "entity_id":"$deliveryId","entity_type":"DELIVERY","remote_version":7,
                "payload_version":2,"payload_hash":"$hash"}]}""")
        }
        val receipt = DeliveryRpcAdapter(transport).checkSyncOperations(session, listOf(operationId))[operationId]!!

        assertEquals(SyncEntityType.DELIVERY, receipt.entityType)
        assertEquals("DELIVERY", receipt.entityType.name)
        assertEquals("El vector v2 lleva payload_version=2", 2, receipt.payloadVersion)
        assertEquals(hash, receipt.payloadHash)
        assertEquals(operationId, receipt.operationId)
        assertEquals(deliveryId, receipt.deliveryId)
        assertEquals(userId.toString(), receipt.accountId)
    }

    @Test
    fun theEntityTypeIsMatchedLiterallyAndNeverNormalized() = runBlocking {
        // The guard must not be relaxed: any spelling other than the server's literal DELIVERY
        // is a protocol failure, not a quietly accepted Delivery. If the server ever changed
        // vocabulary, this must break loudly instead of adopting a foreign row.
        for (spelling in listOf("delivery", "Delivery", "DELIVERIES", "deliveries", "DELIVERY ", " DELIVERY")) {
            val transport = rpc { _, _ ->
                DeliveryRpcResponse(200, """{"confirmed_receipts":[{"operation_id":"$operationId",
                    "entity_id":"$deliveryId","entity_type":"$spelling","remote_version":7,
                    "payload_version":2,"payload_hash":"${"f".repeat(64)}"}]}""")
            }
            val failure = failureOf {
                DeliveryRpcAdapter(transport).checkSyncOperations(session, listOf(operationId))
            }
            assertEquals("entity_type='$spelling'", DeliveryRpcError.PROTOCOL, failure.error)
        }
    }

    // ------------------------------- lectura completa para la segunda instalacion (solo lectura)

    @Test
    fun installationBReadsTheAggregateByKnownUuidWithItsParcelShares() = runBlocking {
        val north = UUID.fromString("30000000-0000-0000-0000-00000000000a")
        val south = UUID.fromString("30000000-0000-0000-0000-00000000000b")
        val farm = UUID.fromString("20000000-0000-0000-0000-00000000000a")
        val transport = rpc { _, body ->
            assertTrue("Se lee por el UUID conocido", body.contains(deliveryId.toString()))
            DeliveryRpcResponse(200, """{"delivery":{"id":"$deliveryId","farm_id":"$farm",
                "campaign_id":"40000000-0000-0000-0000-00000000000a","delivery_date":"2026-11-18",
                "destination_name":"Cooperativa San Isidro","net_grams":2800000,"version":7},
                "parcels":[{"parcel_id":"$north","weight_grams":1800000},
                           {"parcel_id":"$south","weight_grams":null}],
                "head_payload_hash":"${"a".repeat(64)}"}""")
        }
        val aggregate = DeliveryRpcAdapter(transport).readOwnDeliveryAggregate(session, deliveryId)!!

        assertEquals(deliveryId, aggregate.deliveryId)
        assertEquals(7L, aggregate.version)
        assertEquals(farm, aggregate.farmId)
        assertEquals(2_800_000L, aggregate.netGrams)
        assertEquals("2026-11-18", aggregate.deliveryDate)
        assertEquals("Cooperativa San Isidro", aggregate.destinationName)
        assertEquals("a".repeat(64), aggregate.headPayloadHash)
        assertEquals(2, aggregate.parcels.size)
        assertEquals(1_800_000L, aggregate.parcels.first { it.parcelId == north }.weightGrams)
        assertNull(
            "weightGrams null significa kilos no repartidos, nunca un cero inventado",
            aggregate.parcels.first { it.parcelId == south }.weightGrams,
        )
        // Exactly one read RPC, and only the contracted one: no listing, no cursor, no discovery.
        assertEquals(listOf(DeliveryRpcAdapter.GET_OWN_DELIVERY_V2), transport.calls.map { it.first })
    }

    @Test
    fun theAggregateReadRefusesABodyForAnotherDeliveryAndReportsAbsenceAsNull() = runBlocking {
        val other = UUID.randomUUID()
        val wrong = rpc { _, _ -> DeliveryRpcResponse(200, """{"delivery":{"id":"$other","version":1}}""") }
        assertEquals(
            DeliveryRpcError.PROTOCOL,
            failureOf { DeliveryRpcAdapter(wrong).readOwnDeliveryAggregate(session, deliveryId) }.error,
        )

        val absent = rpc { _, _ -> DeliveryRpcResponse(200, """{"delivery":null,"head_payload_hash":null}""") }
        assertNull(
            "Otra cuenta o una pesada inexistente se leen como ausencia, no como error",
            DeliveryRpcAdapter(absent).readOwnDeliveryAggregate(session, deliveryId),
        )
    }
    private suspend fun failureOf(block: suspend () -> Any?): DeliveryRpcFailure {
        try {
            block()
        } catch (failure: DeliveryRpcFailure) {
            return failure
        }
        fail("Se esperaba DeliveryRpcFailure")
        error("inalcanzable")
    }
}
