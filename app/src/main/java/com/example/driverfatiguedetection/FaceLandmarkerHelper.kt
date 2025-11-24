package com.example.driverfatiguedetection

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker.FaceLandmarkerOptions
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class FaceLandmarkerHelper(private val ctx: Context) {

    private val closed = AtomicBoolean(false)
    private val landmarker: FaceLandmarker

    private fun loadAssetToDirectBuffer(assetName: String): ByteBuffer {
        val bytes = ctx.assets.open(assetName).use { it.readBytes() }  // works even if compressed in APK
        val buf = ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder())
        buf.put(bytes)
        buf.rewind()
        return buf
    }

    init {
        // Use model buffer instead of model path (path fails if asset is compressed)
        val base = BaseOptions.builder()
            .setModelAssetBuffer(loadAssetToDirectBuffer("face_landmarker.task"))
            .build()

        val options = FaceLandmarkerOptions.builder()
            .setBaseOptions(base)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setNumFaces(1)
            .setRunningMode(RunningMode.IMAGE)
            .build()

        landmarker = FaceLandmarker.createFromOptions(ctx, options)
    }

    fun detect(bitmap: Bitmap): List<NormalizedLandmark>? {
        if (closed.get()) return null
        val mpImg = BitmapImageBuilder(bitmap).build()
        val result: FaceLandmarkerResult = landmarker.detect(mpImg)
        return result.faceLandmarks().firstOrNull()
    }

    fun close() {
        if (closed.compareAndSet(false, true)) landmarker.close()
    }
}
