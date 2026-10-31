package com.isivoltpro.maginaolivo.data.repository

import com.isivoltpro.maginaolivo.feature.account.AuthSession
import java.io.ByteArrayOutputStream
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only, pinned RPC transport. It never sends agricultural data or creates outbox intents. */
internal class HttpsWorkspaceAuthorizationRemote(
    private val projectUrl: String,
    private val publicKey: String,
) : WorkspaceAuthorizationRemote {
    override suspend fun list(session: AuthSession): String = withContext(Dispatchers.IO) {
        require(projectUrl.startsWith("https://") && publicKey.isNotBlank())
        val connection = URL("$projectUrl/rest/v1/rpc/list_authorized_workspaces_v1").openConnection() as HttpsURLConnection
        try {
            connection.requestMethod = "POST"
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000
            connection.setRequestProperty("apikey", publicKey)
            connection.setRequestProperty("Authorization", "Bearer ${session.accessToken}")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
            check(connection.responseCode == 200) { "workspace_authorization_http_${connection.responseCode}" }
            connection.inputStream.use { input ->
                val output = ByteArrayOutputStream()
                val chunk = ByteArray(4096)
                while (true) {
                    val count = input.read(chunk)
                    if (count < 0) break
                    require(output.size() + count <= 131_072)
                    output.write(chunk, 0, count)
                }
                output.toString("UTF-8")
            }
        } finally {
            connection.disconnect()
        }
    }
}
