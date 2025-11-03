package com.example.cleanchecknative.ui.handwash

import android.content.Context
import com.example.cleanchecknative.yolo.Constants
import com.example.cleanchecknative.yolo.Detector
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object ModelLoader {
    @Volatile
    private var handLandmarker: HandLandmarker? = null
    @Volatile
    private var detector: Detector? = null

    private var modelsLoaded = false

    suspend fun loadModels(context: Context, detectorListener: Detector.DetectorListener) {
        if (modelsLoaded) return
        withContext(Dispatchers.IO) {
            // Load Hand Landmarker
            if (handLandmarker == null) {
                val baseOptions = BaseOptions.builder()
                    .setModelAssetPath("hand_landmarker.task")
                    .setDelegate(Delegate.GPU)
                    .build()
                val options = HandLandmarker.HandLandmarkerOptions.builder()
                    .setBaseOptions(baseOptions)
                    .setRunningMode(RunningMode.IMAGE)
                    .setNumHands(2)
                    .build()
                handLandmarker = HandLandmarker.createFromOptions(context, options)
            }

            // Load YOLO Detector
            if (detector == null) {
                detector = Detector(context, Constants.MODEL_PATH, Constants.LABELS_PATH, detectorListener)
            }
            modelsLoaded = true
        }
    }

    fun getHandLandmarker(): HandLandmarker {
        return handLandmarker ?: throw IllegalStateException("HandLandmarker not loaded")
    }

    fun getDetector(): Detector {
        return detector ?: throw IllegalStateException("Detector not loaded")
    }
}
