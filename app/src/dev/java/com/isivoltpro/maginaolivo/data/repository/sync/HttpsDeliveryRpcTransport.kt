package com.isivoltpro.maginaolivo.data.repository.sync

import com.isivoltpro.maginaolivo.feature.account.AuthConfiguration
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The real HTTPS route for the three G2.4 Delivery RPCs, modelled on `HttpsAuthTransport`: no
 * redirects, no custom trust manager, no credential logging, a hard path allowlist and a bounded
 * response.
 *
 * **NOT WIRED.** Nothing in the app constructs this class. Agricultural data reaches Supabase
 * only once something builds it with a real [AuthConfiguration] and hands it to
 * [DeliveryRpcAdapter]. That wiring is a separate, explicitly authorized step; until then the
 * worker runs against a test transport and the app cannot send a Pesada even by accident.
 *
 * The caller still owes the G2.4 contract: a durable PREPARE before the first request, the lease
 * held across the request and the persistence of its result, and consent rechecked after every
 * wait. This class only moves bytes.
 */
internal class HttpsDeliveryRpcTransport(
    private val configuration: AuthConfiguration,
    private val connectTimeoutMillis: Int = 10_000,
    private val readTimeoutMillis: Int = 20_000,
) : DeliveryRpcTransport {

    override suspend fun rpc(name: String, bodyJson: String, accessToken: String): DeliveryRpcResponse =
        withContext(Dispatchers.IO) {
            check(name in DeliveryRpcAdapter.NAMES) { "RPC fuera del contrato G2.4: $name" }
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
