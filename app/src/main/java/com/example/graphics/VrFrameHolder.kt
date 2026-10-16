package com.example.graphics

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.PixelCopy
import android.view.View
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared, thread-safe frame buffer that holds the latest clean captured frame of the app
 * (captured directly from MainActivity's Window surface via PixelCopy / software Canvas when in-app,
 * or from MediaProjection's ImageReader when over external apps), guaranteeing that the VR SBS
 * renderer ALWAYS displays the app clearly in both eyes without black-screen or feedback loops.
 */
object VrFrameHolder {

    private const val TAG = "VrFrameHolder"

    @Volatile
    var latestBitmap: Bitmap? = null
        private set

    @Volatile
    var frameVersion: Long = 0L
        private set

    private val _frameVersionFlow = MutableStateFlow(0L)
    val frameVersionFlow: StateFlow<Long> = _frameVersionFlow.asStateFlow()

    @Volatile
    var isMainActivityVisible: Boolean = false

    @Volatile
    var onFrameUpdated: (() -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var isPixelCopyInFlight = false

    private var reusableCaptureBitmap: Bitmap? = null
    private var reusableCanvas: Canvas? = null

    /**
     * Captures MainActivity's live Window into a clean ARGB_8888 Bitmap.
     * Uses hardware-accelerated PixelCopy on API 26+ (which captures Compose RenderNodes,
     * WebViews, and images directly from the Activity Window surface without ANY overlay windows),
     * plus synchronous captureFromView so the very first frame is available immediately.
     */
    fun captureFromActivity(activity: Activity) {
        val window = activity.window ?: return
        val rootView = window.decorView.rootView ?: return
        val w = rootView.width
        val h = rootView.height
        if (w <= 10 || h <= 10) return

        // Always ensure we have an immediate synchronous frame if latestBitmap is null
        if (latestBitmap == null) {
            captureFromView(rootView)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (isPixelCopyInFlight) return
            val targetW = (w / 2).coerceIn(360, 720)
            val targetH = (h / 2).coerceIn(480, 1440)
            try {
                val destBitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                isPixelCopyInFlight = true
                val srcRect = Rect(0, 0, w, h)
                PixelCopy.request(
                    window,
                    srcRect,
                    destBitmap,
                    { copyResult ->
                        isPixelCopyInFlight = false
                        if (copyResult == PixelCopy.SUCCESS) {
                            updateLatestBitmapInternal(destBitmap)
                        } else {
                            destBitmap.recycle()
                            captureFromView(rootView)
                        }
                    },
                    mainHandler
                )
            } catch (e: Exception) {
                isPixelCopyInFlight = false
                captureFromView(rootView)
            }
        } else {
            captureFromView(rootView)
        }
    }

    /**
     * Captures a live View hierarchy directly into a clean ARGB_8888 Bitmap without including
     * any system overlay windows.
     */
    @Synchronized
    fun captureFromView(rootView: View) {
        val w = rootView.width
        val h = rootView.height
        if (w <= 10 || h <= 10) return

        val targetW = (w / 2).coerceIn(360, 720)
        val targetH = (h / 2).coerceIn(480, 1440)

        try {
            var bmp = reusableCaptureBitmap
            if (bmp == null || bmp.width != targetW || bmp.height != targetH || bmp.isRecycled) {
                bmp = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                reusableCaptureBitmap = bmp
                reusableCanvas = Canvas(bmp)
            }

            val canvas = reusableCanvas ?: return
            canvas.save()
            canvas.scale(targetW.toFloat() / w.toFloat(), targetH.toFloat() / h.toFloat())
            canvas.drawColor(Color.parseColor("#090D14"))
            rootView.draw(canvas)
            canvas.restore()

            val copy = bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (copy != null) {
                updateLatestBitmapInternal(copy)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Software view draw fallback error: ${e.message}")
        }
    }

    /**
     * Updates the shared VR frame from a clean MediaProjection ImageReader bitmap
     * (verified free of SBS overlay feedback).
     */
    @Synchronized
    fun updateFromCleanProjection(bitmap: Bitmap) {
        if (bitmap.isRecycled || bitmap.width <= 10 || bitmap.height <= 10) return
        updateLatestBitmapInternal(bitmap)
    }

    @Synchronized
    private fun updateLatestBitmapInternal(bitmap: Bitmap) {
        latestBitmap = bitmap
        frameVersion++
        _frameVersionFlow.value = frameVersion
        onFrameUpdated?.invoke()
    }
}
