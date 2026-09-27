package com.isivoltpro.maginaolivo.data.remote.weather

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * CR-006 — the one HTTPS POST the weather Edge Functions need: JSON in, JSON out, the
 * project's public anon key in `apikey` and `Authorization`, a size cap on the answer.
 * Blocking; callers run it on the IO dispatcher.
 */
internal object EdgeFunctionHttp {
    private const val MAX_BYTES = 64 * 1024

    fun post(functionsUrl: String, function: String, anonKey: String, body: String, timeoutMillis: Int): String {
        val connection = URL("$functionsUrl/$function").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("apikey", anonKey)
            connection.setRequestProperty("Authorization", "Bearer $anonKey")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            if (status !in 200..299) throw IOException("$function answered $status")
            return connection.inputStream.use { readCapped(it, function) }
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(input: InputStream, function: String): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_BYTES) throw IOException("$function response too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }
}
