package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The DEV-only HTTPS route for the G2.4 Delivery RPCs and, when explicitly scoped, the G2.6
 * CAMPAIGN CAS RPCs. Modelled on `HttpsAuthTransport`: no
 * redirects, no custom trust manager, no credential logging, a hard path allowlist and a bounded
 * response.
 *
 * **NOT WIRED.** Only isolated probes construct this class. Agricultural writes reach Supabase
 * only once something builds it with a real [AuthConfiguration] and hands it to
 * an adapter. That wiring is a separate, explicitly authorized step; until then the app cannot
 * send a Pesada or CAMPAIGN update through this client.
 *
 * The caller still owes its outbox contract: durable staging before the first request, the lease
 * held across the request and receipt persistence, and authorization rechecked after every wait.
 * This class only moves bytes.
 */
internal class HttpsDeliveryRpcTransport(
    private val configuration: AuthConfiguration,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000,
    private val scope: RpcScope = RpcScope.DELIVERY,
) : DeliveryRpcTransport {

    internal enum class RpcScope(val allowedNames: Set<String>) {
        DELIVERY(DeliveryRpcAdapter.NAMES),
        CAMPAIGN_CAS(CampaignCasRpcAdapter.NAMES),
        CAMPAIGN_RECEIVE(setOf("get_own_campaign_receive_v1")),
        PARENT_RECEIVE(setOf(ParentReceiveCoordinator.RPC)),
    }

    override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse =
        withContext(Dispatchers.IO) {
            check(name in scope.allowedNames) { "RPC fuera del contrato ${scope.name}: $name" }
            check(accessToken.isNotBlank())
            val connection = URL("${configuration.projectUrl}/rest/v1/rpc/$name")
                .openConnection() as HttpsURLConnection
            try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = connectTimeoutMillis
                connection.readTimeout = readTimeoutMillis
                connection.requestMethod = "POST"
                connection.setRequestProperty("apikey", configuration.publicKey)
                connection.setRequestProperty("Authorization", "Bearer $accessToken")
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true
                connection.outputStream.use { it.write(bodyJson.toByteArray(Charsets.UTF_8)) }

                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val output = ByteArrayOutputStream()
                stream?.use { input ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size() + count > MAX_RESPONSE_BYTES) {
                            throw DeliveryRpcFailure(DeliveryRpcError.PROTOCOL, status)
                        }
                        output.write(buffer, 0, count)
                    }
                }
                DeliveryRpcResponse(status, output.toString("UTF-8"))
            } catch (_: IOException) {
                // The outcome is unknown: the server may have committed before the cut.
                throw DeliveryRpcFailure(DeliveryRpcError.NETWORK)
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val MAX_RESPONSE_BYTES = 1_048_576
    }
}
