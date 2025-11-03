package com.example.cleanchecknative.ui.hand

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult

class HandDetector(
    private val context: Context,
    private val runningMode: RunningMode = RunningMode.IMAGE,
    private val listener: HandDetectorListener? = null
) {
    private var handLandmarker: HandLandmarker? = null

    init {
        setupHandLandmarker()
    }

    private fun setupHandLandmarker() {
        val modelName = "hand_landmarker.task"
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .setDelegate(Delegate.GPU)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(runningMode)
                .setNumHands(1) // We process one hand at a time

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener?.onError("Hand Landmarker failed to initialize. See error logs for details")
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
        }
    }

    fun detect(imageBitmap: Bitmap): HandLandmarkerResult? {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("Attempting to call detect in a non-image running mode.")
        }
        if (handLandmarker == null) {
            setupHandLandmarker()
        }

        val mpImage = BitmapImageBuilder(imageBitmap).build()
        return handLandmarker?.detect(mpImage)
    }

    fun detectAsync(imageBitmap: Bitmap, frameTime: Long) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("Attempting to call detectAsync in a non-live stream running mode.")
        }
        if (handLandmarker == null) {
            setupHandLandmarker()
        }
        val mpImage = BitmapImageBuilder(imageBitmap).build()
        handLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: HandLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        listener?.onResults(result)
    }

    private fun returnLivestreamError(error: RuntimeException) {
        listener?.onError(error.message ?: "An unknown error has occurred")
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
    }

    interface HandDetectorListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(results: HandLandmarkerResult?)
    }

    companion object {
        private const val TAG = "HandDetector"
    }
}
