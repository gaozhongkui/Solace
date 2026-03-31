package com.getsolace.ai.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import kotlin.math.sqrt

/**
 * 基于 MediaPipe HandLandmarker 的空中手势控制器
 *
 * 手势映射：
 *  - 拇指+食指距离增大  →  放大（onZoom factor > 1）
 *  - 拇指+食指距离减小  →  缩小（onZoom factor < 1）
 *  - 快速捏合（合拢瞬间）→  点击（onTap，坐标已归一化 0..1）
 *  - 食指尖位置          →  光标（onCursor，归一化）
 *
 * ⚠️ 需要将 hand_landmarker.task 放到 app/src/main/assets/
 */
class HandGestureController(
    private val context: Context,
    val onZoom:   (scaleFactor: Float) -> Unit,
    val onTap:    (nx: Float, ny: Float) -> Unit,
    val onCursor: (nx: Float?, ny: Float?) -> Unit
) {
    private var landmarker: HandLandmarker? = null
    private var prevPinchDist = -1f
    private var isPinching    = false

    // 所有回调必须在主线程执行，否则 Compose State 更新会静默失败
    private val mainHandler = Handler(Looper.getMainLooper())

    companion object {
        private const val TAG        = "HandGesture"
        private const val MODEL      = "hand_landmarker.task"
        private const val PINCH_CLOSE = 0.06f   // 距离 < 此值 = 捏合
        private const val PINCH_OPEN  = 0.10f   // 距离 > 此值 = 释放（防抖留滞区）
    }

    /**
     * 必须在**后台线程**调用（加载 ~8MB 模型，会阻塞）
     */
    fun init() {
        try {
            val options = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL).build())
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumHands(1)
                .setMinHandDetectionConfidence(0.5f)
                .setMinHandPresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener(::onResult)
                .setErrorListener { e -> Log.e(TAG, "MediaPipe error: $e") }
                .build()
            landmarker = HandLandmarker.createFromOptions(context, options)
            Log.d(TAG, "HandLandmarker 初始化成功")
        } catch (e: Exception) {
            Log.e(TAG, "HandLandmarker 初始化失败: ${e.message}")
        }
    }

    /**
     * 在 CameraX ImageAnalysis 回调里调用。
     * 使用 RGBA_8888 格式，不需要 @ExperimentalGetImage（未调用 imageProxy.image）
     */
    fun processFrame(imageProxy: ImageProxy) {
        val lm = landmarker ?: run { imageProxy.close(); return }
        try {
            // RGBA_8888: plane[0] 包含完整像素数据
            val raw = Bitmap.createBitmap(
                imageProxy.width, imageProxy.height, Bitmap.Config.ARGB_8888
            )
            imageProxy.use { raw.copyPixelsFromBuffer(imageProxy.planes[0].buffer) }

            // 旋转 + 水平镜像（前置摄像头画面是镜像的）
            val deg = imageProxy.imageInfo.rotationDegrees.toFloat()
            val mat = Matrix().apply {
                postRotate(deg)
                postScale(-1f, 1f)
            }
            val processed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, mat, false)

            lm.detectAsync(BitmapImageBuilder(processed).build(), SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "帧处理失败: $e")
        } finally {
            imageProxy.close()
        }
    }

    private fun onResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: Any) {
        if (result.landmarks().isEmpty()) {
            prevPinchDist = -1f
            isPinching    = false
            mainHandler.post { onCursor(null, null) }
            return
        }

        val hand  = result.landmarks()[0]
        val thumb = hand[4]   // THUMB_TIP
        val index = hand[8]   // INDEX_FINGER_TIP

        val dx   = thumb.x() - index.x()
        val dy   = thumb.y() - index.y()
        val dist = sqrt(dx * dx + dy * dy)

        // 光标位置（食指尖）
        val curX = index.x()
        val curY = index.y()

        // 缩放：拇指↔食指距离的相对变化
        val zoomFactor = if (prevPinchDist > PINCH_CLOSE && dist > PINCH_CLOSE) {
            val f = dist / prevPinchDist
            if (f !in 0.97f..1.03f && f in 0.5f..2.0f) f else null
        } else null

        // 点击：合拢瞬间触发一次
        val shouldTap = !isPinching && dist < PINCH_CLOSE
        if (shouldTap)          isPinching = true
        if (isPinching && dist > PINCH_OPEN) isPinching = false

        prevPinchDist = dist

        // 全部在主线程回调，保证 Compose State 安全
        mainHandler.post {
            onCursor(curX, curY)
            zoomFactor?.let { onZoom(it) }
            if (shouldTap) onTap(curX, curY)
        }
    }

    fun close() {
        landmarker?.close()
        landmarker = null
    }
}
