package com.bos.app

import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

/** Local-only password gate available directly at http://PHONE-IP:8080. */
object LocalSessionServer {
    private const val PORT = 8080
    private var engine: ApplicationEngine? = null

    data class Session(val url: String)

    fun start(password: String): Session {
        stop()
        require(password.length >= 6) { "Use a password with at least 6 characters." }
        val verifier = PasswordVerifier(password)

        engine = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
            routing {
                get("/") { call.respondText(viewerPage(), ContentType.Text.Html) }
                post("/unlock") {
                    val supplied = call.receiveParameters()["password"].orEmpty()
                    if (verifier.matches(supplied)) {
                        call.respondText(connectedPage(), ContentType.Text.Html)
                    } else {
                        call.respondText("Wrong BOS password.", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                    }
                }
            }
        }.start(wait = false)

        return Session("http://${localIpv4Address()}:$PORT")
    }

    fun stop() {
        engine?.stop(300, 1_000)
        engine = null
    }

    private fun localIpv4Address(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val network = interfaces.nextElement()
            if (!network.isUp || network.isLoopback) continue
            val addresses = network.inetAddresses
            while (addresses.hasMoreElements()) {
                val address = addresses.nextElement()
                if (address is Inet4Address && address.isSiteLocalAddress) return address.hostAddress
            }
        }
        return "PHONE-IP"
    }

    private class PasswordVerifier(password: String) {
        private val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        private val expected = hash(salt, password)
        fun matches(candidate: String): Boolean = MessageDigest.isEqual(expected, hash(salt, candidate))
        private fun hash(salt: ByteArray, value: String): ByteArray =
            MessageDigest.getInstance("SHA-256").digest(salt + value.toByteArray(Charsets.UTF_8))
    }

    private fun viewerPage() = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1">
        <title>BOS Viewer</title><style>body{font-family:sans-serif;background:#101114;color:#fff;display:grid;place-items:center;min-height:90vh}.card{width:min(420px,90vw);padding:24px;background:#1c1e24;border-radius:16px}input,button{box-sizing:border-box;width:100%;padding:12px;margin-top:12px;border-radius:8px}button{background:#7c4dff;color:white;border:0;font-weight:bold}</style></head>
        <body><main class="card"><h1>BOS</h1><p>Enter the sender's password to join this local session.</p>
        <form method="post" action="/unlock"><input name="password" type="password" autocomplete="current-password" required placeholder="BOS password"><button type="submit">Connect</button></form>
        <p><small>This sender is only visible on the same Wi-Fi network.</small></p></main></body></html>
    """.trimIndent()

    private fun connectedPage() = """
        <!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1"><title>BOS connected</title></head>
        <body style="font-family:sans-serif;background:#101114;color:#fff;display:grid;place-items:center;min-height:90vh"><main><h1>BOS connected</h1><p>Viewer authentication succeeded. The screen stream will appear here when the WebRTC transport is enabled.</p></main></body></html>
    """.trimIndent()
}
