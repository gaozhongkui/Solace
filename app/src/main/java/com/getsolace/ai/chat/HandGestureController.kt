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
    private var smoothDist    = -1f   // EMA 平滑距离，消除 MediaPipe 抖动
    private var isPinching    = false
    private var frameCount    = 0     // 帧计数，用于限制日志频率

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
        // 检查 assets 里模型文件是否存在
        val assetExists = try { context.assets.open(MODEL).close(); true } catch (e: Exception) { false }
        Log.d(TAG, "init: 模型文件 $MODEL ${if (assetExists) "存在 ✓" else "不存在 ✗ — 请把模型放到 app/src/main/assets/"}")
        if (!assetExists) return

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
            Log.d(TAG, "init: HandLandmarker 初始化成功 ✓")
        } catch (e: Exception) {
            Log.e(TAG, "init: HandLandmarker 初始化失败 ✗ ${e.message}")
        }
    }

    /**
     * 在 CameraX ImageAnalysis 回调里调用。
     * 使用 RGBA_8888 格式，不需要 @ExperimentalGetImage（未调用 imageProxy.image）
     */
    fun processFrame(imageProxy: ImageProxy) {
        frameCount++
        val lm = landmarker ?: run {
            if (frameCount == 1) Log.e(TAG, "processFrame: landmarker 为 null，帧被丢弃（init() 未成功？）")
            imageProxy.close()
            return
        }
        // 只在第1帧和每隔100帧打印一次，避免刷屏
        val shouldLog = frameCount == 1 || frameCount % 100 == 0
        // 在 close 之前先读好所有元数据
        val width  = imageProxy.width
        val height = imageProxy.height
        val deg    = imageProxy.imageInfo.rotationDegrees.toFloat()
        val format = imageProxy.format
        if (shouldLog) Log.d(TAG, "processFrame: 第 $frameCount 帧 size=${width}x${height} rotation=$deg format=$format")

        try {
            // 先把像素数据完整拷贝到 Bitmap，再 close ImageProxy（只 close 一次）
            val raw = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            raw.copyPixelsFromBuffer(imageProxy.planes[0].buffer)
            imageProxy.close()   // ← 唯一的 close，数据已拷走

            // 旋转 + 水平镜像（前置摄像头画面是镜像的）
            val mat = Matrix().apply {
                postRotate(deg)
                postScale(-1f, 1f)
            }
            val processed = Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, mat, false)

            lm.detectAsync(BitmapImageBuilder(processed).build(), SystemClock.uptimeMillis())
        } catch (e: Exception) {
            Log.e(TAG, "processFrame: 帧处理失败 $e")
            imageProxy.close()   // 异常时补 close
        }
    }

    private var resultCount = 0  // onResult 调用计数
    private var noHandCount = 0  // 连续无手帧计数（避免刷屏）

    private fun onResult(result: HandLandmarkerResult, @Suppress("UNUSED_PARAMETER") input: Any) {
        resultCount++

        if (result.landmarks().isEmpty()) {
            noHandCount++
            // 刚离开时打一次，之后每100帧打一次
            if (noHandCount == 1 || noHandCount % 100 == 0) {
                Log.d(TAG, "onResult: 未检测到手 (连续 $noHandCount 帧)")
            }
            prevPinchDist = -1f
            smoothDist    = -1f
            isPinching    = false
            mainHandler.post { onCursor(null, null) }
            return
        }

        noHandCount = 0
        val hand  = result.landmarks()[0]
        val thumb = hand[4]   // THUMB_TIP
        val index = hand[8]   // INDEX_FINGER_TIP

        val dx   = thumb.x() - index.x()
        val dy   = thumb.y() - index.y()
        val dist = sqrt(dx * dx + dy * dy)

        // EMA 平滑：消除 MediaPipe 单帧抖动，同时保留连续变化趋势
        smoothDist = if (smoothDist < 0f) dist else smoothDist * 0.6f + dist * 0.4f

        // 光标位置（食指尖）
        val curX = index.x()
        val curY = index.y()

        // 缩放：用平滑距离的帧间比值，与触屏 detectTransformGestures 逻辑一致
        // 不设死区，只限每帧合理变化范围（0.85..1.18），防止突变
        val zoomFactor = if (prevPinchDist > PINCH_CLOSE && smoothDist > PINCH_CLOSE) {
            val f = smoothDist / prevPinchDist
            if (f in 0.85f..1.18f) f else null
        } else null

        // 点击：合拢瞬间触发一次（用原始 dist 判断，响应更灵敏）
        val shouldTap = !isPinching && dist < PINCH_CLOSE
        if (shouldTap)                       isPinching = true
        if (isPinching && dist > PINCH_OPEN) isPinching = false

        // 每30帧打印一次手部状态
        if (resultCount % 30 == 0) {
            Log.d(TAG, "onResult: 手已检测 | cursor=(%.2f, %.2f) | dist=%.3f smooth=%.3f prev=%.3f | zoom=${"%.3f".format(zoomFactor ?: 1f)} pinching=$isPinching".format(curX, curY, dist, smoothDist, prevPinchDist))
        }
        if (zoomFactor != null) {
            Log.d(TAG, "onResult: ▶ ZOOM factor=${"%.4f".format(zoomFactor)} (smooth=%.3f prev=%.3f)".format(smoothDist, prevPinchDist))
        }
        if (shouldTap) {
            Log.d(TAG, "onResult: ▶ TAP at (%.2f, %.2f) dist=${"%.3f".format(dist)}".format(curX, curY))
        }

        prevPinchDist = smoothDist

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
