package com.bos.app

import android.content.Context
import android.media.AudioManager
import android.os.PowerManager
import android.provider.Settings
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.receiveParameters
import io.ktor.server.response.respondBytesWriter
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.utils.io.writeFully
import kotlinx.coroutines.delay
import java.net.Inet4Address
import java.net.NetworkInterface
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * Local-only server on http://PHONE-IP:8080. Serves the password page, the MJPEG
 * screen stream, and control endpoints (tap/swipe/keys/volume/brightness/wake/power).
 * The stream and controls are only reachable after a client authenticates.
 */
object LocalSessionServer {
    private const val PORT = 8080
    private var engine: ApplicationEngine? = null
    private var appContext: Context? = null
    @Volatile private var authorized = false

    data class Session(val url: String)

    fun start(context: Context, password: String): Session {
        stop()
        require(password.length >= 6) { "Use a password with at least 6 characters." }
        appContext = context.applicationContext
        val verifier = PasswordVerifier(password)
        authorized = false

        engine = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
            routing {
                get("/") { call.respondText(pageHtml(), ContentType.Text.Html) }

                post("/unlock") {
                    val supplied = call.receiveParameters()["password"].orEmpty()
                    if (verifier.matches(supplied)) {
                        authorized = true
                        call.respondText("ok", ContentType.Text.Plain)
                    } else {
                        call.respondText("Wrong password", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                    }
                }

                get("/stream") {
                    if (!authorized) {
                        call.respondText("Locked", ContentType.Text.Plain, HttpStatusCode.Unauthorized)
                        return@get
                    }
                    call.respondBytesWriter(contentType = ContentType.parse("multipart/x-mixed-replace; boundary=bosframe")) {
                        while (true) {
                            val frame = ScreenCapture.latestFrame()
                            if (frame != null) {
                                writeFully(("--bosframe\r\nContent-Type: image/jpeg\r\nContent-Length: ${frame.size}\r\n\r\n").toByteArray())
                                writeFully(frame)
                                writeFully("\r\n".toByteArray())
                            }
                            delay(70)
                        }
                    }
                }

                post("/input") {
                    if (!authorized) return@post call.respondText("locked", status = HttpStatusCode.Unauthorized)
                    val form = call.receiveParameters()
                    val action = form["action"].orEmpty()
                    val ok = handleInput(action, form)
                    call.respondText(if (ok) "ok" else "unsupported", status = if (ok) HttpStatusCode.OK else HttpStatusCode.NotImplemented)
                }
            }
        }.start(wait = false)

        return Session("http://${localIpv4Address()}:$PORT")
    }

    private fun handleInput(action: String, form: io.ktor.http.Parameters): Boolean {
        val ctx = appContext ?: return false
        val dw = ScreenCapture.deviceWidth.takeIf { it > 0 } ?: return false
        val dh = ScreenCapture.deviceHeight.takeIf { it > 0 } ?: return false

        fun scaleX(nx: Float) = nx * dw
        fun scaleY(ny: Float) = ny * dh

        return when (action) {
            "tap" -> {
                val x = form["x"]?.toFloatOrNull() ?: return false
                val y = form["y"]?.toFloatOrNull() ?: return false
                RemoteControlAccessibilityService.tap(scaleX(x), scaleY(y))
            }
            "long_press" -> {
                val x = form["x"]?.toFloatOrNull() ?: return false
                val y = form["y"]?.toFloatOrNull() ?: return false
                RemoteControlAccessibilityService.longPress(scaleX(x), scaleY(y))
            }
            "swipe" -> {
                val x1 = form["x1"]?.toFloatOrNull() ?: return false
                val y1 = form["y1"]?.toFloatOrNull() ?: return false
                val x2 = form["x2"]?.toFloatOrNull() ?: return false
                val y2 = form["y2"]?.toFloatOrNull() ?: return false
                RemoteControlAccessibilityService.swipe(scaleX(x1), scaleY(y1), scaleX(x2), scaleY(y2), 220)
            }
            "back" -> RemoteControlAccessibilityService.back()
            "home" -> RemoteControlAccessibilityService.home()
            "recents" -> RemoteControlAccessibilityService.recents()
            "notifications" -> RemoteControlAccessibilityService.notifications()
            "power_menu" -> RemoteControlAccessibilityService.powerDialog()
            "lock" -> RemoteControlAccessibilityService.lockScreen()
            "wake" -> wakeScreen(ctx)
            "volume_up" -> volume(ctx, AudioManager.ADJUST_RAISE)
            "volume_down" -> volume(ctx, AudioManager.ADJUST_LOWER)
            "brightness_up" -> brightness(ctx, +20)
            "brightness_down" -> brightness(ctx, -20)
            "shutdown" -> false // Not available on a normal, non-rooted, non-device-owner phone.
            else -> false
        }
    }

    private fun wakeScreen(ctx: Context): Boolean = try {
        val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "bos:wake"
        )
        wl.acquire(3_000)
        true
    } catch (_: Throwable) { false }

    private fun volume(ctx: Context, direction: Int): Boolean = try {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        true
    } catch (_: Throwable) { false }

    private fun brightness(ctx: Context, delta: Int): Boolean {
        return try {
            if (!Settings.System.canWrite(ctx)) {
                false
            } else {
                val current = Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128)
                val next = (current + delta).coerceIn(0, 255)
                Settings.System.putInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, next)
                true
            }
        } catch (_: Throwable) {
            false
        }
    }

    fun stop() {
        engine?.stop(300, 1_000)
        engine = null
        authorized = false
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

    private fun pageHtml() = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>BOS</title>
<style>
body{margin:0;font-family:sans-serif;background:#101114;color:#fff}
#lock{display:grid;place-items:center;min-height:100vh}
.card{width:min(420px,90vw);padding:24px;background:#1c1e24;border-radius:16px}
input,button{box-sizing:border-box;width:100%;padding:12px;margin-top:12px;border-radius:8px;border:0}
button{background:#7c4dff;color:#fff;font-weight:bold}
#viewer{display:none}
#screen{width:100%;display:block;touch-action:none;background:#000}
#bar{display:flex;flex-wrap:wrap;gap:6px;padding:8px;background:#1c1e24}
#bar button{width:auto;flex:1 1 70px;margin:0;padding:10px 4px;font-size:12px}
#status{padding:6px 10px;font-size:12px;color:#9aa0ad}
</style></head>
<body>
<div id="lock"><div class="card">
  <h1>BOS</h1>
  <p>Enter the sender's password to view and control this screen.</p>
  <input id="pw" type="password" placeholder="BOS password">
  <button onclick="unlock()">Connect</button>
  <p id="err" style="color:#ff8686"></p>
</div></div>

<div id="viewer">
  <div id="bar">
    <button onclick="send('back')">Back</button>
    <button onclick="send('home')">Home</button>
    <button onclick="send('recents')">Recents</button>
    <button onclick="send('notifications')">Notif</button>
    <button onclick="send('lock')">Lock</button>
    <button onclick="send('wake')">Wake</button>
    <button onclick="send('volume_up')">Vol+</button>
    <button onclick="send('volume_down')">Vol-</button>
    <button onclick="send('brightness_up')">Bright+</button>
    <button onclick="fullscreen()">Fullscreen</button>
    <button onclick="send('brightness_down')">Bright-</button>
    <button onclick="send('power_menu')">Power menu</button>
    <button disabled title="File manager will require a user-approved Android folder picker in the sender app">Files</button>
    <button disabled title="Not available on a normal phone without root/device-owner">Shutdown</button>
  </div>
  <img id="screen" src="/stream">
  <div id="status">Tap the screen image to send a tap. Drag to swipe.</div>
</div>

<script>
async function unlock() {
  const pw = document.getElementById('pw').value;
  const body = new URLSearchParams({ password: pw });
  const res = await fetch('/unlock', { method: 'POST', body });
  if (res.ok) {
    document.getElementById('lock').style.display = 'none';
    document.getElementById('viewer').style.display = 'block';
  } else {
    document.getElementById('err').textContent = 'Wrong password.';
  }
}
async function send(action, extra) {
  const body = new URLSearchParams(Object.assign({ action }, extra || {}));
  const res = await fetch('/input', { method: 'POST', body });
  document.getElementById('status').textContent = res.ok ? action + ' sent' : action + ' not supported on this phone';
}
function fullscreen() {
  const el = document.documentElement;
  if (el.requestFullscreen) el.requestFullscreen();
}
const img = document.getElementById('screen');
let startX = 0, startY = 0, moved = false;
img.addEventListener('pointerdown', e => {
  const r = img.getBoundingClientRect();
  startX = (e.clientX - r.left) / r.width;
  startY = (e.clientY - r.top) / r.height;
  moved = false;
});
img.addEventListener('pointerup', e => {
  const r = img.getBoundingClientRect();
  const x = (e.clientX - r.left) / r.width;
  const y = (e.clientY - r.top) / r.height;
  if (!moved) {
    send('tap', { x, y });
  } else {
    send('swipe', { x1: startX, y1: startY, x2: x, y2: y });
  }
});
img.addEventListener('pointermove', e => { moved = true; });
</script>
</body></html>
    """.trimIndent()
}
