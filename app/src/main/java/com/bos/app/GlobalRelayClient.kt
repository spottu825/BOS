package com.bos.app

import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Global relay client.
 * Same phone identity => same public viewer URL on the same hosted relay (permanent pair/link).
 * It uploads JPEG frames and polls browser controls. No fake global URL is shown unless relay is configured.
 */
object GlobalRelayClient {
    private val running = AtomicBoolean(false)
    @Volatile private var worker: Thread? = null
    @Volatile private var publicId: String? = null
    @Volatile var viewerUrl: String? = null
        private set
    @Volatile var lastError: String? = null
        private set

    fun relayConfigured(): Boolean = BuildConfig.BOS_RELAY_URL.trim().isNotEmpty()
    fun isRunning(): Boolean = running.get()

    fun start(deviceId: String) {
        val relayBase = BuildConfig.BOS_RELAY_URL.trim().trimEnd('/')
        if (relayBase.isEmpty()) {
            lastError = "Relay URL is not built into this APK yet."
            return
        }
        if (!running.compareAndSet(false, true)) return
        lastError = null
        worker = thread(name = "bos-global-relay", isDaemon = true) {
            try {
                val registered = register(relayBase, deviceId)
                publicId = registered.publicId
                viewerUrl = registered.viewerUrl
                while (running.get()) {
                    val id = publicId
                    val frame = ScreenCapture.latestFrame()
                    if (id != null && frame != null) {
                        postFrame(relayBase, id, frame)
                        pollControls(relayBase, id)
                    }
                    Thread.sleep(180)
                }
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                running.set(false)
            }
        }
    }

    fun stop() {
        running.set(false)
        worker?.interrupt()
        worker = null
    }

    private data class Registered(val publicId: String, val viewerUrl: String)

    private fun register(relayBase: String, deviceId: String): Registered {
        val body = "{\"deviceId\":\"${jsonEscape(deviceId)}\",\"name\":\"BOS phone\"}"
        val response = request("POST", "$relayBase/api/phone/register", body.toByteArray(StandardCharsets.UTF_8), "application/json")
        val publicId = extractJsonString(response, "publicId") ?: error("relay missing publicId")
        val viewerUrl = extractJsonString(response, "viewerUrl") ?: "$relayBase/view/$publicId"
        return Registered(publicId, viewerUrl)
    }

    private fun postFrame(relayBase: String, publicId: String, frame: ByteArray) {
        request("POST", "$relayBase/api/phone/${url(publicId)}/frame", frame, "image/jpeg")
    }

    private fun pollControls(relayBase: String, publicId: String) {
        val response = request("GET", "$relayBase/api/phone/${url(publicId)}/control", null, null)
        val controls = Regex("\\{\\s*\\\"action\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"params\\\"\\s*:\\s*\\{([^}]*)}").findAll(response)
        for (control in controls) {
            val action = control.groupValues[1]
            val paramsText = control.groupValues[2]
            val params = mutableMapOf<String, String>()
            Regex("\\\"([^\\\"]+)\\\"\\s*:\\s*(?:\\\"([^\\\"]*)\\\"|([0-9.\\-]+))").findAll(paramsText).forEach { match ->
                params[match.groupValues[1]] = match.groupValues[2].ifEmpty { match.groupValues[3] }
            }
            LocalSessionServer.dispatchInput(action, params)
        }
    }

    private fun request(method: String, address: String, body: ByteArray?, contentType: String?): String {
        val conn = (URL(address).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 6_000
            readTimeout = 6_000
            useCaches = false
            if (body != null) {
                doOutput = true
                setRequestProperty("content-type", contentType ?: "application/octet-stream")
                setRequestProperty("content-length", body.size.toString())
            }
        }
        try {
            if (body != null) conn.outputStream.use { out: OutputStream -> out.write(body) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val response = stream?.use { input -> BufferedReader(InputStreamReader(input)).readText() }.orEmpty()
            if (code !in 200..299) error("relay HTTP $code: $response")
            return response
        } finally {
            conn.disconnect()
        }
    }

    private fun extractJsonString(text: String, key: String): String? {
        return Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"").find(text)?.groupValues?.get(1)
    }

    private fun jsonEscape(value: String): String = value.replace("\\", "\\\\").replace("\"", "\\\"")
    private fun url(value: String): String = URLEncoder.encode(value, "UTF-8")
}
