package com.isivoltpro.maginaolivo.data.repository

import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** PostgreSQL JSONB text hashing for the flat string/Long snapshot emitted by DeliveryWriter.
 * Unsupported input fails closed. This does not validate a session or authorize dispatch.
 */
internal fun deliveryPayloadHash(payload: String): String? = try {
    if (payload.length > 65_536) null else {
        val value = Json.parseToJsonElement(payload) as? JsonObject
        val textFields = setOf("id", "farmId", "campaignId", "deliveryDate", "destinationName",
            "deliveryNumber", "ticketNumber", "source", "notes", "harvestId", "deliveryTime", "harvestOrigin")
        val integerFields = setOf("netGrams", "grossGrams", "tareGrams", "baseVersion")
        if (value == null || value.isEmpty() || value.keys.any { it !in textFields && it !in integerFields }) null
        else {
            // All supported keys are ASCII: JSONB orders by UTF-8 byte length, then bytes.
            val fields = value.entries.sortedWith(compareBy({ it.key.length }, { it.key }))
            val rendered = fields.map { (key, element) ->
                val primitive = element as? JsonPrimitive ?: error("Non-scalar payload")
                val scalar = if (key in textFields) {
                    check(primitive.isString)
                    pgQuoted(primitive.content)
                } else {
                    check(!primitive.isString && Regex("-?(0|[1-9][0-9]*)").matches(primitive.content))
                    primitive.content.toLong().toString()
                }
                pgQuoted(key) + ": " + scalar
            }.joinToString(", ", "{", "}")
            MessageDigest.getInstance("SHA-256").digest(rendered.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 255) }
        }
    }
} catch (_: IllegalArgumentException) { null
} catch (_: IllegalStateException) { null }

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
