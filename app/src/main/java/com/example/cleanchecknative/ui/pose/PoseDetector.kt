package com.example.cleanchecknative.ui.pose

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlin.math.max
import kotlin.math.min

// Result bundle to hold pose landmarks and calculated hand ROIs
data class PoseResultBundle(
    val landmarks: List<List<NormalizedLandmark>>,
    val leftHandRoi: RectF?,
    val rightHandRoi: RectF?,
    val inferenceTime: Long = 0L
)

class PoseDetector(
    private val context: Context,
    private val runningMode: RunningMode = RunningMode.IMAGE,
    private val listener: PoseDetectorListener? = null
) {

    private var poseLandmarker: PoseLandmarker? = null
    private val lowPassFilter = LowPassFilter()

    init {
        setupPoseLandmarker()
    }

    private fun setupPoseLandmarker() {
        val modelName = "pose_landmarker_lite.task"
        val baseOptionsBuilder = BaseOptions.builder()
            .setModelAssetPath(modelName)
            .setDelegate(Delegate.GPU)

        try {
            val baseOptions = baseOptionsBuilder.build()
            val optionsBuilder = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(runningMode)
                .setNumPoses(1) // Detect only one person

            if (runningMode == RunningMode.LIVE_STREAM) {
                optionsBuilder.setResultListener(this::returnLivestreamResult)
                    .setErrorListener(this::returnLivestreamError)
            }

            val options = optionsBuilder.build()
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
        } catch (e: IllegalStateException) {
            listener?.onError("Pose Landmarker failed to initialize. See error logs for details")
            Log.e(TAG, "MediaPipe failed to load the task with error: ${e.message}")
        }
    }

    fun detect(imageBitmap: Bitmap) {
        if (runningMode != RunningMode.IMAGE) {
            throw IllegalArgumentException("Attempting to call detect in a non-image running mode.")
        }
        if (poseLandmarker == null) {
            setupPoseLandmarker()
        }

        val mpImage = BitmapImageBuilder(imageBitmap).build()
        val result = poseLandmarker?.detect(mpImage)
        processResults(result)
    }

    fun detectAsync(imageBitmap: Bitmap, frameTime: Long) {
        if (runningMode != RunningMode.LIVE_STREAM) {
            throw IllegalArgumentException("Attempting to call detectAsync in a non-live stream running mode.")
        }
        if (poseLandmarker == null) {
            setupPoseLandmarker()
        }

        val mpImage = BitmapImageBuilder(imageBitmap).build()
        poseLandmarker?.detectAsync(mpImage, frameTime)
    }

    private fun returnLivestreamResult(result: PoseLandmarkerResult, input: com.google.mediapipe.framework.image.MPImage) {
        val finishTimeMs = SystemClock.uptimeMillis()
        val inferenceTime = finishTimeMs - result.timestampMs()
        processResults(result, inferenceTime)
    }

    private fun processResults(result: PoseLandmarkerResult?, inferenceTime: Long = 0L) {
        if (result == null || result.landmarks().isEmpty()) {
            listener?.onEmpty()
            return
        }

        val smoothedLandmarks = lowPassFilter.apply(result.landmarks())
        val personLandmarks = smoothedLandmarks.first() // Assuming one person

        val leftHandRoi = getHandRoi(personLandmarks, HandType.LEFT)
        val rightHandRoi = getHandRoi(personLandmarks, HandType.RIGHT)

        val resultBundle = PoseResultBundle(
            landmarks = smoothedLandmarks,
            leftHandRoi = leftHandRoi,
            rightHandRoi = rightHandRoi,
            inferenceTime = inferenceTime
        )
        listener?.onResults(resultBundle)
    }

    private fun getHandRoi(landmarks: List<NormalizedLandmark>, handType: HandType): RectF? {
        val indices = if (handType == HandType.LEFT) LEFT_HAND_INDICES else RIGHT_HAND_INDICES
        
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var maxX = Float.MIN_VALUE
        var maxY = Float.MIN_VALUE
        var minConfidence = Float.MAX_VALUE

        indices.forEach { index ->
            val lm = landmarks.getOrNull(index)
            if (lm != null) {
                minX = min(minX, lm.x())
                minY = min(minY, lm.y())
                maxX = max(maxX, lm.x())
                maxY = max(maxY, lm.y())
                minConfidence = min(minConfidence, lm.visibility().orElse(0f))
            }
        }

        // If landmarks are not visible, don't create an ROI
        if (minConfidence < 0.5) return null

        // Add padding to the ROI
        val padding = 0.05f
        val roi = RectF(
            max(0f, minX - padding),
            max(0f, minY - padding),
            min(1f, maxX + padding),
            min(1f, maxY + padding)
        )

        // Ensure ROI is valid
        if (roi.width() <= 0 || roi.height() <= 0) return null

        return roi
    }


    private fun returnLivestreamError(error: RuntimeException) {
        listener?.onError(error.message ?: "An unknown error has occurred")
    }

    fun close() {
        poseLandmarker?.close()
        poseLandmarker = null
    }

    interface PoseDetectorListener {
        fun onError(error: String, errorCode: Int = 0)
        fun onResults(results: PoseResultBundle)
        fun onEmpty() {} // Add onEmpty for cases where no pose is detected
    }

    companion object {
        private const val TAG = "PoseDetector"
        private val LEFT_HAND_INDICES = listOf(15, 17, 19, 21)
        private val RIGHT_HAND_INDICES = listOf(16, 18, 20, 22)
    }
}

enum class HandType { LEFT, RIGHT }

/**
 * Applies a low-pass filter to the landmarks to smooth them over time.
 */
class LowPassFilter {
    private var previousSmoothedLandmarks: List<List<NormalizedLandmark>>? = null
    private val alpha = 0.2f // Smoothing factor. Lower value means more smoothing.

    fun apply(landmarks: List<List<NormalizedLandmark>>): List<List<NormalizedLandmark>> {
        if (previousSmoothedLandmarks == null) {
            previousSmoothedLandmarks = landmarks
            return landmarks
        }

        val smoothedLandmarks = mutableListOf<List<NormalizedLandmark>>()
        for (personIndex in landmarks.indices) {
            val personLandmarks = landmarks[personIndex]
            val prevPersonLandmarks = previousSmoothedLandmarks?.getOrNull(personIndex)

            if (prevPersonLandmarks == null) {
                smoothedLandmarks.add(personLandmarks)
                continue
            }

            val smoothedPersonLandmarks = mutableListOf<NormalizedLandmark>()
            for (landmarkIndex in personLandmarks.indices) {
                val current = personLandmarks[landmarkIndex]
                val previous = prevPersonLandmarks[landmarkIndex]

                val smoothedX = alpha * current.x() + (1 - alpha) * previous.x()
                val smoothedY = alpha * current.y() + (1 - alpha) * previous.y()
                val smoothedZ = alpha * current.z() + (1 - alpha) * previous.z()

                smoothedPersonLandmarks.add(
                    NormalizedLandmark.create(smoothedX, smoothedY, smoothedZ)
                )
            }
            smoothedLandmarks.add(smoothedPersonLandmarks)
        }

        previousSmoothedLandmarks = smoothedLandmarks
        return smoothedLandmarks
    }
}