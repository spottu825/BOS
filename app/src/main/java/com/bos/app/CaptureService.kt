package com.bos.app

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

/**
 * Owns the user-approved screen capture while BOS is sharing.
 * Stays visible through an ongoing notification; releases capture on stop.
 */
class CaptureService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle("BOS screen sharing is active")
            .setContentText("Tap to return to BOS and stop sharing")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
        val projectionData = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
        if (projectionData != null && !ScreenCapture.isRunning) {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val projection = manager.getMediaProjection(resultCode, projectionData)
            if (projection != null) {
                ScreenCapture.start(applicationContext, projection)
                val id = getSharedPreferences("bos", MODE_PRIVATE).getString("identity", null) ?: "BOS-UNKNOWN"
                GlobalRelayClient.start(id)
            } else stopSelf()
        } else if (projectionData == null && !ScreenCapture.isRunning) {
            // Android may restart a sticky foreground service without the original MediaProjection token.
            // We cannot recreate screen capture silently, so stop and let the user start sharing again.
            stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        GlobalRelayClient.stop()
        ScreenCapture.stop()
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(CHANNEL, "BOS screen sharing", NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_PROJECTION_DATA = "bos.projection.data"
        const val EXTRA_RESULT_CODE = "bos.projection.result_code"
        private const val CHANNEL = "bos_capture"
        private const val NOTIFICATION_ID = 8080
    }
}
