package com.bos.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.webrtc.CameraVideoCapturer
import org.webrtc.EglBase
import org.webrtc.PeerConnectionFactory
import org.webrtc.ScreenCapturerAndroid
import org.webrtc.SurfaceTextureHelper

/**
 * Owns the user-approved screen capture while BOS is sharing.
 * The service stays visible through an ongoing notification and releases capture on stop.
 */
class CaptureService : Service() {
    private var capturer: ScreenCapturerAndroid? = null
    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var factory: PeerConnectionFactory? = null
    private var eglBase: EglBase? = null

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

        val projectionData = intent?.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
        if (projectionData != null && capturer == null) startCapture(projectionData)
        return START_NOT_STICKY
    }

    private fun startCapture(projectionData: Intent) {
        PeerConnectionFactory.initialize(
            PeerConnectionFactory.InitializationOptions.builder(applicationContext).createInitializationOptions()
        )
        factory = PeerConnectionFactory.builder().createPeerConnectionFactory()
        eglBase = EglBase.create()
        surfaceTextureHelper = SurfaceTextureHelper.create("BOS-Capture", eglBase!!.eglBaseContext)

        val videoSource = factory!!.createVideoSource(false)
        capturer = ScreenCapturerAndroid(projectionData, object : MediaProjection.Callback() {
            override fun onStop() {
                stopSelf()
            }
        })
        capturer!!.initialize(surfaceTextureHelper, this, videoSource.capturerObserver)
        capturer!!.startCapture(1280, 720, 24)
        CaptureState.videoSource = videoSource
        CaptureState.factory = factory
        CaptureState.eglBase = eglBase
    }

    override fun onDestroy() {
        try { capturer?.stopCapture() } catch (_: Exception) { }
        capturer?.dispose()
        surfaceTextureHelper?.dispose()
        factory?.dispose()
        eglBase?.release()
        CaptureState.clear()
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
        private const val CHANNEL = "bos_capture"
        private const val NOTIFICATION_ID = 8080
    }
}

object CaptureState {
    @Volatile var videoSource: org.webrtc.VideoSource? = null
    @Volatile var factory: PeerConnectionFactory? = null
    @Volatile var eglBase: EglBase? = null
    fun clear() { videoSource = null; factory = null; eglBase = null }
}
