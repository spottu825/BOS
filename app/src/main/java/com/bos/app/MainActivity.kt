package com.bos.app

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {
    private lateinit var urlText: TextView
    private lateinit var globalCodeText: TextView
    private lateinit var statusText: TextView
    private lateinit var permissionText: TextView
    private var startAfterPermission = false
    private var autoSetupStarted = false
    private var promptedAccessibilityThisLaunch = false
    private var promptedBrightnessThisLaunch = false

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        continueAutoSetup()
    }

    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val approved = result.resultCode == RESULT_OK && result.data != null
        if (approved) {
            val captureIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(CaptureService.EXTRA_PROJECTION_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, captureIntent)
            statusText.text = "Screen permission allowed. Starting session..."
            if (startAfterPermission) startLocalSession()
        } else {
            statusText.text = "Screen permission denied. BOS cannot share the screen without it."
        }
        startAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSimpleScreen()
    }

    private fun showSimpleScreen() {
        val padding = dp(18)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(padding, padding, padding, padding)
            setBackgroundColor(Color.WHITE)
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(content)
        }

        content.addView(text("BOS", 32f, Gravity.CENTER, Color.BLACK))
        content.addView(text("URL", 14f, Gravity.CENTER, Color.DKGRAY).apply { setPadding(0, dp(12), 0, 0) })

        urlText = text("Local URL: not started\nGlobal URL: not connected yet", 18f, Gravity.CENTER, Color.rgb(20, 90, 170)).apply {
            setTextIsSelectable(true)
            setPadding(0, dp(8), 0, dp(16))
        }
        content.addView(urlText)

        globalCodeText = text(globalLine(), 20f, Gravity.CENTER, Color.rgb(120, 60, 180)).apply {
            setTextIsSelectable(true)
            setPadding(0, 0, 0, dp(14))
        }
        content.addView(globalCodeText)
        content.addView(button("Refresh global code") {
            newGlobalPairCode()
            globalCodeText.text = globalLine()
        })

        permissionText = text("Permissions: checking...", 14f, Gravity.CENTER, Color.DKGRAY).apply {
            setPadding(0, 0, 0, dp(10))
        }
        content.addView(permissionText)

        statusText = text("Open BOS and allow setup permissions. Then tap Start local session.", 15f, Gravity.CENTER, Color.BLACK).apply {
            setPadding(0, 0, 0, dp(20))
        }
        content.addView(statusText)

        content.addView(button("Start local session") { startSharingFlow() }.apply {
            textSize = 18f
            setPadding(0, dp(10), 0, dp(10))
        })

        content.addView(button("Stop") {
            LocalSessionServer.stop()
            stopService(Intent(this, CaptureService::class.java))
            urlText.text = "Local URL: not started\nGlobal URL: stopped"
            statusText.text = "Stopped."
        })

        content.addView(text("Device ID: ${permanentIdentity()}", 13f, Gravity.CENTER, Color.GRAY).apply {
            setPadding(0, dp(18), 0, 0)
        })

        content.addView(text(
            "Permanent global URL works only when this APK is built with BOS_RELAY_URL and the relay is hosted online.",
            12f,
            Gravity.CENTER,
            Color.GRAY
        ).apply { setPadding(0, dp(12), 0, 0) })

        setContentView(scroll)
        refreshPermissionStatus()
        scroll.post { beginAutoSetup() }
    }

    private fun beginAutoSetup() {
        if (autoSetupStarted) return
        autoSetupStarted = true
        continueAutoSetup()
    }

    private fun continueAutoSetup() {
        refreshPermissionStatus()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            statusText.text = "Allow notification permission so BOS can show sharing status."
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        if (!BuildConfig.BOS_SAFE_BUILD && !RemoteControlAccessibilityService.enabled() && !promptedAccessibilityThisLaunch) {
            promptedAccessibilityThisLaunch = true
            statusText.text = "Enable BOS Remote Control if you want browser taps/swipes."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (!BuildConfig.BOS_SAFE_BUILD && !Settings.System.canWrite(this) && !promptedBrightnessThisLaunch) {
            promptedBrightnessThisLaunch = true
            statusText.text = "Allow brightness control if you want brightness +/- buttons."
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
            return
        }
        statusText.text = "Setup checked. Tap Start local session."
    }

    private fun startSharingFlow() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        startAfterPermission = true
        statusText.text = "Approve Android screen capture."
        requestScreenCapturePermission()
    }

    private fun startLocalSession() {
        try {
            val session = LocalSessionServer.start(this)
            val global = GlobalRelayClient.viewerUrl ?: if (GlobalRelayClient.relayConfigured()) "connecting..." else "relay not configured in APK"
            urlText.text = "Local URL: ${session.url}\nGlobal URL: $global"
            statusText.text = if (GlobalRelayClient.relayConfigured()) {
                "Session started. Wait a few seconds; global URL will appear if the hosted relay is reachable."
            } else {
                "Local session started. To get internet URL, build APK with BOS_RELAY_URL pointing at your hosted relay."
            }
            urlText.postDelayed({ refreshGlobalUrlLine(session.url) }, 2500)
        } catch (error: Exception) {
            statusText.text = "Could not start session: ${error.message ?: "unknown error"}"
        }
    }

    private fun globalLine(): String {
        val relay = if (GlobalRelayClient.relayConfigured()) "relay ready" else "relay not built in"
        return "Global code: ${globalPairCode()}\n$relay"
    }

    private fun refreshGlobalUrlLine(localUrl: String) {
        val global = GlobalRelayClient.viewerUrl
            ?: GlobalRelayClient.lastError?.let { "error: $it" }
            ?: if (GlobalRelayClient.relayConfigured()) "connecting..." else "relay not configured in APK"
        urlText.text = "Local URL: $localUrl\nGlobal URL: $global"
        globalCodeText.text = globalLine()
        if (GlobalRelayClient.relayConfigured() && GlobalRelayClient.viewerUrl == null && GlobalRelayClient.lastError == null) {
            urlText.postDelayed({ refreshGlobalUrlLine(localUrl) }, 2500)
        }
    }

    private fun requestScreenCapturePermission() {
        val projection = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermission.launch(projection.createScreenCaptureIntent())
    }

    private fun permanentIdentity(): String {
        val prefs = getSharedPreferences("bos", MODE_PRIVATE)
        return prefs.getString("identity", null) ?: run {
            val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val random = SecureRandom()
            val value = "BOS-" + (1..10).joinToString("") { chars[random.nextInt(chars.length)].toString() }
            prefs.edit().putString("identity", value).apply()
            value
        }
    }

    private fun globalPairCode(): String {
        val prefs = getSharedPreferences("bos", MODE_PRIVATE)
        val expiresAt = prefs.getLong("global_code_expires_at", 0L)
        val saved = prefs.getString("global_pair_code", null)
        return if (saved != null && System.currentTimeMillis() < expiresAt) saved else newGlobalPairCode()
    }

    private fun newGlobalPairCode(): String {
        val code = SecureRandom().nextInt(900_000).plus(100_000).toString()
        getSharedPreferences("bos", MODE_PRIVATE).edit()
            .putString("global_pair_code", code)
            .putLong("global_code_expires_at", System.currentTimeMillis() + 5 * 60 * 1000)
            .apply()
        return code
    }

    private fun refreshPermissionStatus() {
        if (!::permissionText.isInitialized) return
        val notification = if (Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        ) "notification on" else "notification off"
        if (BuildConfig.BOS_SAFE_BUILD) {
            permissionText.text = "Permissions: $notification • safe view-only build"
        } else {
            val touch = if (RemoteControlAccessibilityService.enabled()) "touch on" else "touch off"
            val brightness = if (Settings.System.canWrite(this)) "brightness on" else "brightness off"
            permissionText.text = "Permissions: $notification • $touch • $brightness"
        }
    }

    private fun text(value: String, size: Float, gravity: Int, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        this.gravity = gravity
        setTextColor(color)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(6), 0, dp(6))
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
        if (autoSetupStarted && ::statusText.isInitialized) {
            statusText.post { continueAutoSetup() }
        }
    }
}
