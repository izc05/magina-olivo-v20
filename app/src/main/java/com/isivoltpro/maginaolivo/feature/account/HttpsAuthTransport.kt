package com.isivoltpro.maginaolivo.feature.account

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Auth-only transport. No Data API/Storage route, custom trust manager or credential logging. */
class HttpsAuthTransport(private val configuration: AuthConfiguration) : AuthHttpTransport {
    override suspend fun request(method: String, path: String, body: String?, accessToken: String?): AuthHttpResponse = withContext(Dispatchers.IO) {
        check(path.substringBefore('?') in setOf("/token", "/signup", "/recover", "/user", "/logout"))
        val connection = URL("${configuration.projectUrl}/auth/v1$path").openConnection() as HttpsURLConnection
        try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000; connection.readTimeout = 10_000
            connection.requestMethod = method
            connection.setRequestProperty("apikey", configuration.publicKey)
            connection.setRequestProperty("Accept", "application/json")
            accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val output = ByteArrayOutputStream()
            stream?.use { input ->
                val buffer = ByteArray(4096)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    if (output.size() + count > 65_536) throw AuthFailure(AuthFailure.Kind.PROTOCOL)
                    output.write(buffer,0,count)
                }
            }
            AuthHttpResponse(status,output.toString("UTF-8"))
        } catch (_: IOException) { throw AuthFailure(AuthFailure.Kind.NETWORK) }
        finally { connection.disconnect() }
    }
}
