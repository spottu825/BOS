package com.bos.app

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
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
    private lateinit var status: TextView
    private lateinit var sessionUrl: TextView
    private lateinit var remoteControlStatus: TextView
    private lateinit var brightnessStatus: TextView
    private var startAfterPermission = false

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
            status.text = "Screen permission granted. BOS is starting sharing."
            if (startAfterPermission) startLocalSession()
        } else {
            status.text = "Screen permission was denied. BOS cannot show the phone screen without it."
        }
        startAfterPermission = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showMainScreen()
    }

    private fun showMainScreen() {
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(text("BOS", 30f, Gravity.CENTER))
        content.addView(text("Same-Wi‑Fi screen sharing", 16f, Gravity.CENTER))

        content.addView(text("Live browser address", 14f).apply { setPadding(0, dp(12), 0, 0) })
        sessionUrl = text("Not sharing yet", 20f).apply { setTextIsSelectable(true) }
        content.addView(sessionUrl)

        status = text("Enable what you want, then tap Start sharing. Same-Wi‑Fi devices can open the live address directly.", 15f)
        status.setPadding(0, dp(8), 0, dp(12))
        content.addView(status)

        content.addView(text("Permanent BOS identity", 14f))
        content.addView(text(permanentIdentity(), 18f))

        remoteControlStatus = text("Remote control: checking...", 15f).apply { setPadding(0, dp(12), 0, 0) }
        content.addView(remoteControlStatus)
        content.addView(button("Enable touch control") {
            status.text = "Android Settings will ask you to allow or deny BOS Remote Control."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        brightnessStatus = text("Brightness control: checking...", 15f).apply { setPadding(0, dp(10), 0, 0) }
        content.addView(brightnessStatus)
        content.addView(button("Enable brightness control") {
            status.text = "Android Settings will ask you to allow BOS to change system brightness."
            startActivity(Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:$packageName")))
        })

        content.addView(button("Start sharing") { startSharingFlow() }.apply { textSize = 18f })
        content.addView(button("Stop sharing") {
            LocalSessionServer.stop()
            stopService(Intent(this, CaptureService::class.java))
            sessionUrl.text = "Not sharing yet"
            status.text = "BOS sharing stopped."
        })

        content.addView(text("Keep BOS running", 18f).apply { setPadding(0, dp(16), 0, 0) })
        content.addView(text("While sharing, BOS runs as a foreground service with a notification so it can stay active in the background. Android may still stop capture after reboot, force close, battery restrictions, or if you revoke screen capture.", 13f))

        setContentView(scroll)
        refreshPermissionStatus()
    }

    private fun startSharingFlow() {
        startAfterPermission = true
        status.text = "Approve Android screen capture, then BOS will show the local URL."
        requestScreenCapturePermission()
    }

    private fun startLocalSession() {
        try {
            val session = LocalSessionServer.start(this)
            sessionUrl.text = session.url
            status.text = "Sharing is live. On another device on the same Wi‑Fi, open the address above in Chrome."
        } catch (error: Exception) {
            status.text = "Could not start sharing: ${error.message ?: "unknown error"}"
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

    private fun refreshPermissionStatus() {
        if (::remoteControlStatus.isInitialized) {
            remoteControlStatus.text = if (RemoteControlAccessibilityService.enabled()) {
                "Touch control: enabled"
            } else {
                "Touch control: disabled — tap Enable touch control"
            }
        }
        if (::brightnessStatus.isInitialized) {
            brightnessStatus.text = if (Settings.System.canWrite(this)) {
                "Brightness control: enabled"
            } else {
                "Brightness control: disabled — tap Enable brightness control"
            }
        }
    }

    private fun text(value: String, size: Float, gravity: Int = Gravity.START) = TextView(this).apply {
        text = value
        textSize = size
        this.gravity = gravity
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        setOnClickListener { action() }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onResume() {
        super.onResume()
        refreshPermissionStatus()
    }
}
