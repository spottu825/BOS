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

                get("/status") {
                    call.respondText(
                        "{\"capture\":${ScreenCapture.isRunning},\"frames\":${ScreenCapture.frameCount},\"touch\":${RemoteControlAccessibilityService.enabled()},\"width\":${ScreenCapture.deviceWidth},\"height\":${ScreenCapture.deviceHeight}}",
                        ContentType.Application.Json
                    )
                }

                post("/input") {
                    val form = call.receiveParameters()
                    val params = form.names().associateWith { name -> form[name].orEmpty() }
                    val ok = dispatchInput(params["action"].orEmpty(), params)
                    call.respondText(
                        if (ok) "ok" else "unsupported",
                        status = if (ok) HttpStatusCode.OK else HttpStatusCode.NotImplemented
                    )
                }
            }
        }.start(wait = false)

        return Session("http://${localIpv4Address()}:$PORT")
    }

    fun dispatchInput(action: String, params: Map<String, String>): Boolean {
        if (BuildConfig.BOS_SAFE_BUILD) return false
        val ctx = appContext ?: return false
        return when (action) {
            "tap" -> {
                val point = scaledPoint(params["x"], params["y"]) ?: return false
                RemoteControlAccessibilityService.tap(point.first, point.second)
            }
            "long_press" -> {
                val point = scaledPoint(params["x"], params["y"]) ?: return false
                RemoteControlAccessibilityService.longPress(point.first, point.second)
            }
            "swipe" -> {
                val start = scaledPoint(params["x1"], params["y1"]) ?: return false
                val end = scaledPoint(params["x2"], params["y2"]) ?: return false
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
#bar{display:flex;flex-wrap:wrap;gap:5px;padding:6px;background:#1c1e24;position:sticky;top:0;z-index:2}
button{background:#7c4dff;color:#fff;font-weight:bold;border:0;border-radius:8px;width:auto;flex:1 1 64px;margin:0;padding:8px 3px;font-size:11px}
button:disabled{background:#555;color:#aaa}
#stage{display:flex;justify-content:center;align-items:flex-start;padding:8px;background:#101114}
#screen{display:block;touch-action:none;background:#000;width:auto;max-width:min(96vw,430px);max-height:calc(100dvh - 145px);height:auto;object-fit:contain;border:1px solid #333;border-radius:10px}@supports not (height:100dvh){#screen{max-height:calc(100vh - 145px)}}
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
  <div id="stage"><img id="screen" src="/stream" alt="BOS screen stream"></div>
  <div id="status">Same Wi-Fi mode. Phone preview is fit-to-screen. Tap preview to tap phone; drag to swipe.</div>
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
img.draggable = false;
let startX = 0, startY = 0, startClientX = 0, startClientY = 0, pointerDown = false, longPressTimer = null, longPressSent = false;
function normalizedPoint(e) {
  const r = img.getBoundingClientRect();
  return {
    x: Math.max(0, Math.min(1, (e.clientX - r.left) / r.width)),
    y: Math.max(0, Math.min(1, (e.clientY - r.top) / r.height))
  };
}
img.addEventListener('pointerdown', e => {
  e.preventDefault();
  img.setPointerCapture?.(e.pointerId);
  const p = normalizedPoint(e);
  startX = p.x; startY = p.y;
  startClientX = e.clientX; startClientY = e.clientY;
  pointerDown = true;
  longPressSent = false;
  clearTimeout(longPressTimer);
  longPressTimer = setTimeout(() => {
    if (pointerDown) {
      longPressSent = true;
      send('long_press', { x: startX, y: startY });
    }
  }, 650);
});
img.addEventListener('pointermove', e => {
  if (pointerDown) {
    e.preventDefault();
    if (Math.hypot(e.clientX - startClientX, e.clientY - startClientY) >= 12) clearTimeout(longPressTimer);
  }
});
img.addEventListener('pointerup', e => {
  e.preventDefault();
  if (!pointerDown) return;
  pointerDown = false;
  clearTimeout(longPressTimer);
  if (longPressSent) return;
  const p = normalizedPoint(e);
  const dist = Math.hypot(e.clientX - startClientX, e.clientY - startClientY);
  if (dist < 12) send('tap', { x: p.x, y: p.y });
  else send('swipe', { x1: startX, y1: startY, x2: p.x, y2: p.y });
});
img.addEventListener('pointercancel', () => { pointerDown = false; clearTimeout(longPressTimer); });
async function pollStatus() {
  try {
    const s = await (await fetch('/status', { cache: 'no-store' })).json();
    const touch = s.touch ? 'touch enabled' : 'touch disabled: enable BOS Remote Control in Android Accessibility settings';
    const cap = s.capture ? 'capture on' : 'capture off';
    document.getElementById('status').textContent = cap + ' • ' + touch + ' • frames: ' + s.frames;
  } catch (_) {}
}
setInterval(pollStatus, 2000);
pollStatus();
</script>
</body></html>
    """.trimIndent()
}
