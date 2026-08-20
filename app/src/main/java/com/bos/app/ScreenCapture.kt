package com.bos.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.os.Handler
import android.os.HandlerThread
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.ByteArrayOutputStream

/**
 * Captures the approved screen into a VirtualDisplay and keeps the latest frame
 * available as JPEG bytes for the local MJPEG stream.
 */
object ScreenCapture {
    @Volatile private var latestJpeg: ByteArray? = null
    @Volatile var frameCount: Long = 0L
        private set

    /** Real device screen size, used to scale remote touch coordinates back to the phone. */
    @Volatile var deviceWidth: Int = 0
        private set
    @Volatile var deviceHeight: Int = 0
        private set

    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var projection: MediaProjection? = null
    private var reusableBitmap: Bitmap? = null

    private const val TARGET_MAX_WIDTH = 720
    private const val JPEG_QUALITY = 55

    val isRunning: Boolean get() = virtualDisplay != null

    fun start(context: Context, mediaProjection: MediaProjection) {
        stop()
        projection = mediaProjection

        val metrics = DisplayMetrics()
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
        deviceWidth = metrics.widthPixels
        deviceHeight = metrics.heightPixels

        // Scale down for bandwidth; keep aspect ratio and even dimensions.
        val scale = if (deviceWidth > TARGET_MAX_WIDTH) TARGET_MAX_WIDTH.toFloat() / deviceWidth else 1f
        val width = ((deviceWidth * scale).toInt() / 2) * 2
        val height = ((deviceHeight * scale).toInt() / 2) * 2
        val density = metrics.densityDpi

        thread = HandlerThread("BOS-Capture").also { it.start() }
        val handler = Handler(thread!!.looper)

        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).apply {
            setOnImageAvailableListener({ imageReader ->
                var image: android.media.Image? = null
                try {
                    image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                    latestJpeg = encode(image, width, height)
                    frameCount++
                } catch (_: Throwable) {
                    // A dropped frame must never kill the capture loop.
                } finally {
                    image?.close()
                }
            }, handler)
        }

        mediaProjection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() { stop() }
        }, handler)

        virtualDisplay = mediaProjection.createVirtualDisplay(
            "BOS",
            width,
            height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader!!.surface,
            null,
            handler
        )
    }

    private fun encode(image: android.media.Image, width: Int, height: Int): ByteArray? {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val bufferWidth = width + rowPadding / pixelStride

        val bitmap = reusableBitmap?.takeIf { it.width == bufferWidth && it.height == height }
            ?: Bitmap.createBitmap(bufferWidth, height, Bitmap.Config.ARGB_8888).also { reusableBitmap = it }

        bitmap.copyPixelsFromBuffer(plane.buffer)

        val output = ByteArrayOutputStream(96 * 1024)
        if (rowPadding == 0) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
        } else {
            val cropped = Bitmap.createBitmap(bitmap, 0, 0, width, height)
            cropped.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, output)
            cropped.recycle()
        }
        return output.toByteArray()
    }

    fun latestFrame(): ByteArray? = latestJpeg

    fun stop() {
        val projectionToStop = projection
        projection = null
        virtualDisplay?.release()
        virtualDisplay = null
        reader?.close()
        reader = null
        try { projectionToStop?.stop() } catch (_: Throwable) { }
        thread?.quitSafely()
        thread = null
        reusableBitmap?.recycle()
        reusableBitmap = null
        latestJpeg = null
    }
}
