package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.time.Duration
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

fun interface WorkspaceAuthorizationRemote {
    suspend fun list(session: AuthSession): String
}

internal data class AuthorizedWorkspace(
    val id: UUID,
    val name: String,
    val permissions: Set<String>,
    val expiresAt: Instant,
    val version: Long,
)

/** Parsed server evidence. Neither Room owner UUIDs nor G2.5 receipts enter this contract. */
internal data class WorkspaceAuthorizationContract(
    val issuer: String,
    val subject: UUID,
    val verifiedAt: Instant,
    val tokenExpiresAt: Instant,
    val workspaces: List<AuthorizedWorkspace>,
) {
    fun validFor(expectedIssuer: String, accountId: UUID, now: Instant): Boolean =
        issuer == "$expectedIssuer/auth/v1" && subject == accountId &&
            !verifiedAt.isAfter(now.plus(Duration.ofMinutes(2))) &&
            !verifiedAt.isBefore(now.minus(Duration.ofMinutes(17))) &&
            tokenExpiresAt.isAfter(now)

    fun available(now: Instant): List<AuthorizedWorkspace> = workspaces.filter {
        it.expiresAt.isAfter(now) && verifiedAt.plus(Duration.ofMinutes(13)).isAfter(now) && tokenExpiresAt.isAfter(now)
    }

    companion object {
        fun parse(body: String): WorkspaceAuthorizationContract {
            require(body.length <= 131_072)
            val root = Json.parseToJsonElement(body) as JsonObject
            require(root.getValue("contract_version").jsonPrimitive.long == 1L)
            val grants = (root.getValue("workspaces") as JsonArray).map { raw ->
                val row = raw as JsonObject
                val permissions = (row.getValue("permissions") as JsonArray).map { it.jsonPrimitive.content }.toSet()
                require("read" in permissions && permissions.all { it in setOf("read", "write", "manage_members") })
                AuthorizedWorkspace(
                    UUID.fromString(row.getValue("id").jsonPrimitive.content),
                    row.getValue("name").jsonPrimitive.content.take(256),
                    permissions,
                    Instant.parse(row.getValue("authorization_expires_at").jsonPrimitive.content),
                    row.getValue("grant_version").jsonPrimitive.long.also { require(it > 0) },
                )
            }
            require(grants.size <= 256 && grants.map { it.id }.distinct().size == grants.size)
            return WorkspaceAuthorizationContract(
                root.getValue("issuer").jsonPrimitive.content,
                UUID.fromString(root.getValue("subject").jsonPrimitive.content),
                Instant.parse(root.getValue("verified_at").jsonPrimitive.content),
                Instant.parse(root.getValue("token_expires_at").jsonPrimitive.content),
                grants,
            )
        }
    }
}
