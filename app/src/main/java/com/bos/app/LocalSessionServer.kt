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

/**
 * Local same-Wi-Fi server on http://PHONE-IP:8080.
 * No password in this build: anyone on the same Wi-Fi who can reach the URL can view/control.
 */
object LocalSessionServer {
    private const val PORT = 8080
    private var engine: ApplicationEngine? = null
    private var appContext: Context? = null

    data class Session(val url: String)

    fun start(context: Context): Session {
        stop()
        appContext = context.applicationContext

        engine = embeddedServer(CIO, host = "0.0.0.0", port = PORT) {
            routing {
                get("/") { call.respondText(pageHtml(), ContentType.Text.Html) }

                get("/stream") {
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
                    val form = call.receiveParameters()
                    val ok = handleInput(form["action"].orEmpty(), form)
                    call.respondText(
                        if (ok) "ok" else "unsupported",
                        status = if (ok) HttpStatusCode.OK else HttpStatusCode.NotImplemented
                    )
                }
            }
        }.start(wait = false)

        return Session("http://${localIpv4Address()}:$PORT")
    }

    private fun handleInput(action: String, form: io.ktor.http.Parameters): Boolean {
        val ctx = appContext ?: return false
        return when (action) {
            "tap" -> {
                val point = scaledPoint(form["x"], form["y"]) ?: return false
                RemoteControlAccessibilityService.tap(point.first, point.second)
            }
            "long_press" -> {
                val point = scaledPoint(form["x"], form["y"]) ?: return false
                RemoteControlAccessibilityService.longPress(point.first, point.second)
            }
            "swipe" -> {
                val start = scaledPoint(form["x1"], form["y1"]) ?: return false
                val end = scaledPoint(form["x2"], form["y2"]) ?: return false
                RemoteControlAccessibilityService.swipe(start.first, start.second, end.first, end.second, 220)
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
            "shutdown" -> false
            else -> false
        }
    }

    private fun scaledPoint(xValue: String?, yValue: String?): Pair<Float, Float>? {
        val x = xValue?.toFloatOrNull() ?: return null
        val y = yValue?.toFloatOrNull() ?: return null
        val width = ScreenCapture.deviceWidth.takeIf { it > 0 } ?: return null
        val height = ScreenCapture.deviceHeight.takeIf { it > 0 } ?: return null
        return Pair(x.coerceIn(0f, 1f) * width, y.coerceIn(0f, 1f) * height)
    }

    private fun wakeScreen(ctx: Context): Boolean {
        return try {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            val wl = pm.newWakeLock(
                PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "bos:wake"
            )
            wl.acquire(3_000)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun volume(ctx: Context, direction: Int): Boolean {
        return try {
            val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            am.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
            true
        } catch (_: Throwable) {
            false
        }
    }

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

    private fun pageHtml() = """
<!doctype html><html><head><meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
<title>BOS</title>
<style>
body{margin:0;font-family:sans-serif;background:#101114;color:#fff}
#screen{width:100%;display:block;touch-action:none;background:#000;min-height:60vh;object-fit:contain}
#bar{display:flex;flex-wrap:wrap;gap:6px;padding:8px;background:#1c1e24;position:sticky;top:0;z-index:2}
button{background:#7c4dff;color:#fff;font-weight:bold;border:0;border-radius:8px;width:auto;flex:1 1 70px;margin:0;padding:10px 4px;font-size:12px}
button:disabled{background:#555;color:#aaa}
#status{padding:6px 10px;font-size:12px;color:#9aa0ad}
</style></head>
<body>
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
    <button onclick="send('brightness_down')">Bright-</button>
    <button onclick="send('power_menu')">Power menu</button>
    <button onclick="fullscreen()">Fullscreen</button>
    <button disabled title="File manager needs a sender-side folder picker later">Files</button>
    <button disabled title="Not available on normal phones without ADB/root/device-owner">Shutdown</button>
  </div>
  <img id="screen" src="/stream" alt="BOS screen stream">
  <div id="status">Same Wi-Fi mode. Tap screen to tap phone; drag to swipe.</div>
<script>
async function send(action, extra) {
  const body = new URLSearchParams(Object.assign({ action }, extra || {}));
  const res = await fetch('/input', { method: 'POST', body });
  document.getElementById('status').textContent = res.ok ? action + ' sent' : action + ' not supported or permission not enabled';
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
img.addEventListener('pointermove', () => { moved = true; });
img.addEventListener('pointerup', e => {
  const r = img.getBoundingClientRect();
  const x = (e.clientX - r.left) / r.width;
  const y = (e.clientY - r.top) / r.height;
  if (!moved) send('tap', { x, y });
  else send('swipe', { x1: startX, y1: startY, x2: x, y2: y });
});
</script>
</body></html>
    """.trimIndent()
}
