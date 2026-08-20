package com.bos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.security.SecureRandom

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView
    private lateinit var sessionUrl: TextView
    private lateinit var sessionQr: ImageView
    private lateinit var passwordInput: EditText
    private lateinit var startButton: Button
    private var approvedProjection = false

    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        approvedProjection = result.resultCode == RESULT_OK && result.data != null
        status.text = if (approvedProjection) {
            "Screen permission granted. Choose Start local session to create your BOS link."
        } else {
            "Screen permission was not granted. BOS cannot share without it."
        }
        startButton.isEnabled = approvedProjection
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showSenderScreen()
    }

    private fun showSenderScreen() {
        val padding = dp(18)
        val screen = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        val identity = permanentIdentity()
        screen.addView(text("BOS", 32f, Gravity.CENTER))
        status = text("Start here: create a password, allow capture, then start a local session.", 15f)
        status.setPadding(0, dp(8), 0, 0)
        screen.addView(status)
        screen.addView(text("Live browser link", 14f).apply { setPadding(0, dp(8), 0, 0) })
        sessionUrl = text("No active session", 15f).apply { setTextColor(Color.rgb(130, 195, 255)) }
        screen.addView(sessionUrl)
        sessionQr = ImageView(this).apply {
            visibility = View.GONE
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(220))
        }
        screen.addView(sessionQr)

        screen.addView(text("Your permanent BOS identity", 14f))
        screen.addView(text(identity, 20f).apply { setTextColor(Color.rgb(98, 214, 158)) })
        screen.addView(text("This QR is for BOS-to-BOS pairing. It is not your temporary browser session.", 13f))
        screen.addView(ImageView(this).apply {
            setImageBitmap(qrBitmap("bos://pair/$identity", 360))
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(210))
        })

        screen.addView(button("Use this device as a viewer") { showViewerScreen() })
        screen.addView(text("Share this device's screen", 22f).apply { setPadding(0, padding, 0, 0) })
        passwordInput = EditText(this).apply {
            hint = "Create / enter BOS password (6+ characters)"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        screen.addView(passwordInput)
        screen.addView(button("1. Allow screen permission") { requestScreenCapturePermission() })
        startButton = button("2. Start local session") { startLocalSession() }.apply { isEnabled = false }
        screen.addView(startButton)
        screen.addView(button("Stop session") {
            LocalSessionServer.stop()
            sessionUrl.text = "No active session"
            sessionQr.visibility = View.GONE
            status.text = "BOS local session stopped."
        })

        setContentView(screen)
    }

    private fun showViewerScreen() {
        val padding = dp(20)
        val screen = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(padding, padding, padding, padding) }
        screen.addView(text("BOS Viewer", 30f, Gravity.CENTER))
        screen.addView(text("Scan a permanent BOS QR in the future, or open a sender's temporary browser link now.", 16f))
        screen.addView(button("Back to sender") { showSenderScreen() })
        setContentView(screen)
    }

    private fun startLocalSession() {
        val password = passwordInput.text.toString()
        try {
            val session = LocalSessionServer.start(password)
            sessionUrl.text = session.url
            sessionQr.setImageBitmap(qrBitmap(session.url, 420))
            sessionQr.visibility = View.VISIBLE
            status.text = "Local session is live. Open or scan the link from another device on the same Wi-Fi."
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

    private fun qrBitmap(value: String, size: Int): Bitmap {
        val matrix: BitMatrix = MultiFormatWriter().encode(value, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565).also { bitmap ->
            for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
        }
    }

    private fun text(value: String, size: Float, gravity: Int = Gravity.START) = TextView(this).apply {
        text = value; textSize = size; this.gravity = gravity
    }
    private fun button(value: String, action: () -> Unit) = Button(this).apply { text = value; setOnClickListener { action() } }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    override fun onDestroy() {
        LocalSessionServer.stop()
        super.onDestroy()
    }
}
