package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.data.local.entity.CampaignEntity
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Local revision and issuer are durable guards; only `campaign` reaches the remote RPC. */
internal fun buildCampaignCasEnvelope(row: CampaignEntity, issuer: String, expectedVersion: Long): String =
    buildJsonObject {
        put("issuer", issuer)
        put("localVersion", row.metadata.version)
        put("expectedVersion", expectedVersion)
        put("campaign", buildJsonObject {
            put("id", row.id.toString())
            put("workspaceId", row.workspaceId.toString())
            put("farmId", row.farmId.toString())
            put("name", row.name)
            put("startDate", row.startDate.toString())
            put("endDate", row.endDate?.let { JsonPrimitive(it.toString()) } ?: JsonNull)
            put("status", row.status.name)
            put("notes", row.notes?.let(::JsonPrimitive) ?: JsonNull)
        })
    }.toString()

internal data class CampaignCasSnapshot(
    val issuer: String,
    val localVersion: Long,
    val expectedVersion: Long,
    val campaign: JsonObject,
    val hash: String,
)

/** Matches sha256(jsonb_build_object('expectedVersion', n, 'campaign', payload)::text). */
internal fun parseCampaignCasEnvelope(payload: String): CampaignCasSnapshot? = runCatching {
    require(payload.length <= 65_536)
    val root = Json.parseToJsonElement(payload) as JsonObject
    require(root.keys == setOf("issuer", "localVersion", "expectedVersion", "campaign"))
    val issuer = root.getValue("issuer").jsonPrimitive.content
    val localVersion = root.getValue("localVersion").jsonPrimitive.longOrNull ?: error("localVersion")
    val expectedVersion = root.getValue("expectedVersion").jsonPrimitive.longOrNull ?: error("expectedVersion")
    require(localVersion > 0 && expectedVersion > 0)
    val campaign = root.getValue("campaign") as JsonObject
    val text = setOf("id", "workspaceId", "farmId", "name", "startDate", "status")
    require(campaign.keys == text + setOf("endDate", "notes"))
    val renderedCampaign = pgObject(campaign) { key, value ->
        pgScalar(value, text = true, nullable = key in setOf("endDate", "notes"))
    }
    // The two outer JSONB keys are sorted by UTF-8 byte length, as in pgObject.
    val canonical = "{\"campaign\": $renderedCampaign, \"expectedVersion\": $expectedVersion}"
    val hash = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
    require(issuer.isNotBlank())
    CampaignCasSnapshot(issuer, localVersion, expectedVersion, campaign, hash)
}.getOrNull()
