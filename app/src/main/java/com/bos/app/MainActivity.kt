package com.bos.app

import android.content.Context
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var status: TextView

    private val capturePermission = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        status.text = if (result.resultCode == RESULT_OK && result.data != null) {
            "Screen permission granted. Local sharing transport is the next BOS milestone."
        } else {
            "Screen permission was not granted. BOS cannot share without it."
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val padding = (24 * resources.displayMetrics.density).toInt()

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(padding, padding, padding, padding)
        }
        val title = TextView(this).apply {
            text = "BOS"
            textSize = 32f
            gravity = Gravity.CENTER
        }
        status = TextView(this).apply {
            text = "Local-first screen sharing for Android 10+."
            textSize = 16f
            gravity = Gravity.CENTER
            setPadding(0, padding, 0, padding)
        }
        val start = Button(this).apply {
            text = "Start sharing"
            setOnClickListener { requestScreenCapturePermission() }
        }

        content.addView(title)
        content.addView(status)
        content.addView(start)
        setContentView(content)
    }

    private fun requestScreenCapturePermission() {
        val projection = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        capturePermission.launch(projection.createScreenCaptureIntent())
    }
}
