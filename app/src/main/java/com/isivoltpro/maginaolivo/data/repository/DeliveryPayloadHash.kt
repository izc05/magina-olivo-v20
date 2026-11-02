package com.isivoltpro.maginaolivo.data.repository

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Restricted JSONB byte contract for DeliveryWriter snapshots, not upload authorization. */
internal fun deliveryPayloadHash(payload: String, payloadVersion: Int = 1): String? = try {
    check(payloadVersion in 1..2 && payload.length <= 65_536)
    val value = Json.parseToJsonElement(payload) as? JsonObject ?: error("Object required")
    val text = setOf("id", "farmId", "campaignId", "deliveryDate", "destinationName",
        "deliveryNumber", "ticketNumber", "source", "notes", "harvestId", "deliveryTime", "harvestOrigin") +
        if (payloadVersion == 2) setOf("destinationOrganizationId") else emptySet()
    val integers = setOf("netGrams", "grossGrams", "tareGrams", "baseVersion")
    val allowed = text + integers + if (payloadVersion == 2) setOf("parcels") else emptySet()
    check(value.isNotEmpty() && value.keys.all { it in allowed })
    if (payloadVersion == 2) check("parcels" in value)
    val rendered = pgObject(value) { key, element ->
        if (key == "parcels") {
            val parcels = element as? JsonArray ?: error("Array required")
            check(parcels.size in 1..1000)
            parcels.joinToString(", ", "[", "]") { child ->
                val share = child as? JsonObject ?: error("Parcel object required")
                val strings = setOf("id", "parcelId", "campaignParcelId", "parcelNameAtDelivery", "allocationMode")
                check(share.keys == strings + "weightGrams")
                pgObject(share) { name, scalar ->
                    pgScalar(scalar, name in strings, name in setOf("campaignParcelId", "weightGrams"))
                }
            }
        } else pgScalar(element, key in text, false)
    }
    MessageDigest.getInstance("SHA-256").digest(rendered.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it.toInt() and 255) }
} catch (_: IllegalArgumentException) { null
} catch (_: IllegalStateException) { null }

private fun pgObject(value: JsonObject, render: (String, kotlinx.serialization.json.JsonElement) -> String): String =
    // Supported keys are ASCII: byte length then byte order equals length then lexical order.
    value.entries.sortedWith(compareBy({ it.key.length }, { it.key })).joinToString(", ", "{", "}") {
        pgQuoted(it.key) + ": " + render(it.key, it.value)
    }

private fun pgScalar(value: kotlinx.serialization.json.JsonElement, text: Boolean, nullable: Boolean): String {
    if (nullable && value == JsonNull) return "null"
    val primitive = value as? JsonPrimitive ?: error("Scalar required")
    return if (text) {
        check(primitive.isString)
        pgQuoted(primitive.content)
    } else {
        check(!primitive.isString && Regex("-?(0|[1-9][0-9]*)").matches(primitive.content))
        primitive.content.toLong().toString()
    }
}

private fun pgQuoted(value: String): String = buildString {
    append('"')
    var index = 0
    while (index < value.length) {
        val c = value[index++]
        when (c) {
            '\u0000' -> error("PostgreSQL JSONB rejects NUL")
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\b' -> append("\\b")
            '\u000c' -> append("\\f")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> when {
                c.code < 32 -> append("\\u%04x".format(c.code))
                c.isHighSurrogate() -> {
                    check(index < value.length && value[index].isLowSurrogate())
                    append(c); append(value[index++])
                }
                c.isLowSurrogate() -> error("Unpaired surrogate")
                else -> append(c)
            }
        }
    }
    append('"')
}
