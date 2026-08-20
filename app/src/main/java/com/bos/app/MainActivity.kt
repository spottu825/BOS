package com.bos.app

import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
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
    private lateinit var passwordInput: EditText
    private lateinit var startButton: Button
    private lateinit var remoteControlStatus: TextView
    private var approvedProjection = false

    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        approvedProjection = result.resultCode == RESULT_OK && result.data != null
        if (approvedProjection) {
            val captureIntent = Intent(this, CaptureService::class.java).apply {
                putExtra(CaptureService.EXTRA_PROJECTION_DATA, result.data)
            }
            ContextCompat.startForegroundService(this, captureIntent)
        }
        status.text = if (approvedProjection) {
            "Screen permission granted. Tap Start local session."
        } else {
            "Screen permission was denied. BOS cannot share without it."
        }
        startButton.isEnabled = approvedProjection
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSenderScreen()
    }

    private fun showSenderScreen() {
        val padding = dp(16)
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val scroll = ScrollView(this).apply { addView(content) }

        content.addView(text("BOS", 30f, Gravity.CENTER))
        content.addView(text("Live browser address", 14f))
        sessionUrl = text("No active session", 19f).apply { setTextIsSelectable(true) }
        content.addView(sessionUrl)
        status = text("Create a password, allow capture, then start a local session.", 15f)
        status.setPadding(0, dp(6), 0, dp(10))
        content.addView(status)

        content.addView(text("Permanent BOS identity", 14f))
        content.addView(text(permanentIdentity(), 18f))
        content.addView(text("Use this identity later inside the BOS Android viewer app. Browser connection uses the live address above.", 13f))

        content.addView(button("Use this device as a viewer") { showViewerScreen() })
        remoteControlStatus = text("Remote control: Disabled", 15f).apply { setPadding(0, dp(8), 0, 0) }
        content.addView(remoteControlStatus)
        content.addView(button("Allow remote control") {
            status.text = "Android Settings will ask you to allow or deny BOS Remote Control."
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })

        content.addView(text("Share this device's screen", 22f).apply { setPadding(0, padding, 0, 0) })
        passwordInput = EditText(this).apply {
            hint = "BOS password (6+ characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        content.addView(passwordInput)
        content.addView(button("1. Allow screen permission") { requestScreenCapturePermission() })
        startButton = button("2. Start local session") { startLocalSession() }.apply { isEnabled = false }
        content.addView(startButton)
        content.addView(button("Stop session") {
            LocalSessionServer.stop()
            sessionUrl.text = "No active session"
            status.text = "BOS local session stopped."
        })

        setContentView(scroll)
    }

    private fun showViewerScreen() {
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        content.addView(text("BOS Viewer", 30f, Gravity.CENTER))
        content.addView(text("Open a sender's local address in your browser. Native BOS pairing and live viewing are the next implementation step.", 16f))
        content.addView(button("Back to sender") { showSenderScreen() })
        setContentView(ScrollView(this).apply { addView(content) })
    }

    private fun startLocalSession() {
        try {
            val session = LocalSessionServer.start(passwordInput.text.toString())
            sessionUrl.text = session.url
            status.text = "Session is live. On another device using the same Wi-Fi, open the address above in Chrome."
        } catch (error: Exception) {
            status.text = "Could not start session: ${error.message ?: "unknown error"}"
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
        if (::remoteControlStatus.isInitialized) {
            remoteControlStatus.text = if (RemoteControlAccessibilityService.enabled()) {
                "Remote control: Enabled — BOS will accept touch input only for an authenticated session."
            } else {
                "Remote control: Disabled — tap Allow remote control to open Android Settings."
            }
        }
    }

    override fun onDestroy() {
        LocalSessionServer.stop()
        super.onDestroy()
    }
}
