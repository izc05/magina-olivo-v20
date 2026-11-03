package com.isivoltpro.maginaolivo.data.repository.sync

import java.time.LocalDate
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * #808 R3-b: the strict R2 parser against REAL responses captured from a disposable local server (`r3b/receive-fixture.json`),
 * and one mutation per rule. Every deviation is PROTOCOL: nothing would be written.
 */
class HarvestReceiveParserTest {
    private val fixture = Json.parseToJsonElement(javaClass.classLoader!!.getResource("r3b/receive-fixture.json")!!.readText()).jsonObject
    private val req = fixture.getValue("request").jsonObject
    private fun s(o: JsonObject, k: String) = o.getValue(k).jsonPrimitive.content
    private val ids = fixture.getValue("ids").jsonObject
    private val manifestRequest = ManifestRequest(s(req, "issuer"), UUID.fromString(s(req, "account")), UUID.fromString(s(req, "workspace")),
        UUID.fromString(s(req, "farm")), UUID.fromString(s(req, "campaign")), LocalDate.parse(s(req, "from")), LocalDate.parse(s(req, "to")))
    private fun bundleRequest(key: String) = BundleRequest(manifestRequest.issuer, manifestRequest.accountId, UUID.fromString(s(ids, key)),
        manifestRequest.workspaceId, manifestRequest.farmId, manifestRequest.campaignId)
    private val manifestBody = s(fixture, "manifest")
    private fun bundleBody(key: String) = s(fixture.getValue("bundles").jsonObject, key)

    private fun protocol(block: () -> Unit) {
        try { block(); fail("expected HarvestReceiveProtocol") } catch (_: HarvestReceiveProtocol) { }
    }

    /** Rewrites the JSON at [path] (object keys and array indexes) with [change]. */
    private fun mutate(body: String, vararg path: Any, change: (JsonElement?) -> JsonElement?): String {
        fun go(e: JsonElement, i: Int): JsonElement = when {
            i == path.size -> change(e) ?: JsonNull
            e is JsonObject -> {
                val key = path[i] as String
                val next = if (i + 1 == path.size) change(e[key]) else go(e.getValue(key), i + 1)
                JsonObject(if (next == null && i + 1 == path.size) e - key else e + (key to (next ?: JsonNull)))
            }
            e is JsonArray -> JsonArray(e.mapIndexed { index, child -> if (index == path[i]) go(child, i + 1) else child })
            else -> error("bad path")
        }
        return go(Json.parseToJsonElement(body), 0).toString()
    }

    @Test fun theRealManifestParsesExactlyWithItsPartitionAndRelations() {
        val m = HarvestReceiveParser.parseManifest(manifestBody, manifestRequest)
        assertEquals(setOf(UUID.fromString(s(ids, "E")), UUID.fromString(s(ids, "N"))), m.harvests.map { it.id }.toSet())
        assertEquals(6, m.deliveries.size)
        assertTrue(m.deliveries.all { it.recordedBySelf } && m.harvests.all { it.recordedBySelf })
        assertTrue(m.deliveries.single { it.id == UUID.fromString(s(ids, "gone")) }.deleted)
        assertEquals(2L, m.deliveries.single { it.id == UUID.fromString(s(ids, "touched")) }.version)
    }

    @Test fun theRealBundlesParseWithHeadV2AndStrictPesadaHeads() {
        val e = HarvestReceiveParser.parseBundle(bundleBody("E"), bundleRequest("E"))
        assertEquals("AUTO_DAY", e.harvest.dayOrigin)
        assertTrue(e.head.derivedFingerprint != null && e.head.headOperationId != null)
        assertEquals(5, e.deliveries.size)
        val gone = e.deliveries.single { it.id == UUID.fromString(s(ids, "gone")) }
        assertEquals(DeliveryHeadKind.DELETE, gone.head!!.kind)
        assertEquals(null, e.deliveries.single { it.id == UUID.fromString(s(ids, "touched")) }.head)
        val n = HarvestReceiveParser.parseBundle(bundleBody("N"), bundleRequest("N"))
        assertEquals(listOf("MANUAL", null), listOf(n.harvest.dayOrigin, n.head.derivedFingerprint))
        assertEquals(3_333L, n.serverWeightGrams)
    }

    @Test fun identityScopeAndPartitionMismatchesArePROTOCOL() {
        protocol { HarvestReceiveParser.parseManifest(manifestBody, manifestRequest.copy(issuer = "https://otro.example/auth/v1")) }
        protocol { HarvestReceiveParser.parseManifest(manifestBody, manifestRequest.copy(accountId = UUID.randomUUID())) }
        protocol { HarvestReceiveParser.parseManifest(manifestBody, manifestRequest.copy(workspaceId = UUID.randomUUID())) }
        protocol { HarvestReceiveParser.parseManifest(manifestBody, manifestRequest.copy(to = null)) }
        protocol { HarvestReceiveParser.parseBundle(bundleBody("E"), bundleRequest("N")) }
        protocol { HarvestReceiveParser.parseBundle(bundleBody("E"), bundleRequest("E").copy(campaignId = UUID.randomUUID())) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "harvests", 0, "harvest_date") { JsonPrimitive("2026-10-27") },
            manifestRequest) }
    }

    @Test fun keysTypesAndValuesAreExact() {
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "extra") { JsonPrimitive(1) }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "deliveries", 0, "partition_date") { null }, manifestRequest) }
        // recorded_by_self must be a JSON boolean (aclaración 4): a string or null is out of contract.
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "deliveries", 0, "recorded_by_self") { JsonPrimitive("true") }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "harvests", 0, "recorded_by_self") { JsonNull }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "harvests", 0, "id") { JsonPrimitive(it!!.jsonPrimitive.content.uppercase()) }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "harvests", 0, "version") { JsonPrimitive(0) }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "harvests", 0, "version") { JsonPrimitive(1.5) }, manifestRequest) }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "manifest_contract_version") { JsonPrimitive(2) }, manifestRequest) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "deliveries", 0, "delivery", "new_column") { JsonPrimitive(1) }, bundleRequest("E")) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "harvest_head", "contract_version") { JsonPrimitive(1) }, bundleRequest("E")) }
    }

    @Test fun relationsHeadsAndLimitsAreEnforced() {
        // A duplicated id, or a Pesada whose Jornada is not in the manifest.
        val m = Json.parseToJsonElement(manifestBody).jsonObject
        val dup = JsonObject(m + ("deliveries" to JsonArray(m.getValue("deliveries").let { it as JsonArray } + (m.getValue("deliveries") as JsonArray)[0])))
        protocol { HarvestReceiveParser.parseManifest(dup.toString(), manifestRequest) }
        val withHarvest = (m.getValue("deliveries") as JsonArray).indexOfFirst { (it as JsonObject)["harvest_id"] != JsonNull }
        protocol { HarvestReceiveParser.parseManifest(mutate(manifestBody, "deliveries", withHarvest, "harvest_id") {
            JsonPrimitive(UUID.randomUUID().toString()) }, manifestRequest) }
        // A Pesada head partly null, at another version, or whose kind disagrees with deleted_at.
        val e = Json.parseToJsonElement(bundleBody("E")).jsonObject.getValue("deliveries") as JsonArray
        val withHead = e.indexOfFirst { (it as JsonObject)["head"] != JsonNull && (it.getValue("head") as JsonObject)["kind"]!!.jsonPrimitive.content == "WRITE" }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "deliveries", withHead, "head", "version") { JsonPrimitive(99) }, bundleRequest("E")) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "deliveries", withHead, "head", "kind") { JsonPrimitive("DELETE") }, bundleRequest("E")) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "deliveries", withHead, "head", "payload_hash") { JsonNull }, bundleRequest("E")) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "harvest_head", "head_payload_hash") { JsonNull }, bundleRequest("E")) }
        // Derived fingerprint present on a MANUAL, or a server weight that is not the row's.
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("N"), "harvest_head", "derived_fingerprint") { JsonPrimitive("a".repeat(64)) }, bundleRequest("N")) }
        protocol { HarvestReceiveParser.parseBundle(mutate(bundleBody("E"), "server_weight_grams") { JsonPrimitive(1) }, bundleRequest("E")) }
        // Over 4 MiB is refused before parsing.
        protocol { HarvestReceiveParser.parseManifest(manifestBody + " ".repeat(HarvestReceiveContract.MAX_BYTES), manifestRequest) }
        assertFalse(HarvestReceiveContract.NAMES.isEmpty())
    }
}
