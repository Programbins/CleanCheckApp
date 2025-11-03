package com.example.cleanchecknative.ui.pose

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.cleanchecknative.databinding.ActivityPoseAndHandBinding
import com.example.cleanchecknative.ui.hand.HandDetector
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class PoseAndHandActivity : AppCompatActivity(), PoseDetector.PoseDetectorListener {

    private lateinit var binding: ActivityPoseAndHandBinding
    private lateinit var poseDetector: PoseDetector
    private lateinit var handDetector: HandDetector
    private lateinit var cameraExecutor: ExecutorService

    private var latestPoseResult: List<List<NormalizedLandmark>>? = null
    private var latestLeftHandResult: List<NormalizedLandmark>? = null
    private var latestRightHandResult: List<NormalizedLandmark>? = null
    
    private var isProcessing = AtomicBoolean(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoseAndHandBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

            poseDetector = PoseDetector(this, RunningMode.LIVE_STREAM, this)
            // HandDetector will be used synchronously, so no listener is needed here.
            handDetector = HandDetector(this, RunningMode.IMAGE)

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, this::analyzeImage)
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalyzer)
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun analyzeImage(image: ImageProxy) {
        if (isProcessing.get()) {
            image.close()
            return
        }
        isProcessing.set(true)

        val bitmapBuffer = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        image.use { bitmapBuffer.copyPixelsFromBuffer(it.planes[0].buffer) }
        
        val matrix = Matrix().apply { postRotate(image.imageInfo.rotationDegrees.toFloat()) }
        val rotatedBitmap = Bitmap.createBitmap(bitmapBuffer, 0, 0, image.width, image.height, matrix, true)

        poseDetector.detectAsync(rotatedBitmap, image.imageInfo.timestamp)
    }

    // PoseDetectorListener methods
    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, "Detector Error: $error", Toast.LENGTH_SHORT).show()
        }
        isProcessing.set(false)
    }

    override fun onEmpty() {
        latestPoseResult = null
        latestLeftHandResult = null
        latestRightHandResult = null
        updateOverlay()
        isProcessing.set(false)
    }

    override fun onResults(results: PoseResultBundle) {
        val image = binding.viewFinder.bitmap
        if (image == null) {
            isProcessing.set(false)
            return
        }

        latestPoseResult = results.landmarks
        latestLeftHandResult = null
        latestRightHandResult = null
        
        val rois = mutableListOf<RectF>()

        // Process left hand
        results.leftHandRoi?.let { roi ->
            rois.add(roi)
            val croppedHand = cropBitmap(image, roi)
            val handResult = handDetector.detect(croppedHand) // Synchronous call
            handResult?.let {
                if (it.landmarks().isNotEmpty()) latestLeftHandResult = it.landmarks().first()
            }
        }

        // Process right hand
        results.rightHandRoi?.let { roi ->
            rois.add(roi)
            val croppedHand = cropBitmap(image, roi)
            val handResult = handDetector.detect(croppedHand) // Synchronous call
            handResult?.let {
                if (it.landmarks().isNotEmpty()) latestRightHandResult = it.landmarks().first()
            }
        }

        updateOverlay(rois)
        isProcessing.set(false)
    }

    private fun updateOverlay(rois: List<RectF> = emptyList()) {
        runOnUiThread {
            binding.overlay.setResults(
                poseResults = latestPoseResult,
                leftHandResults = latestLeftHandResult,
                rightHandResults = latestRightHandResult,
                imageHeight = binding.viewFinder.height,
                imageWidth = binding.viewFinder.width,
                handRois = rois
            )
        }
    }

    private fun cropBitmap(source: Bitmap, roi: RectF): Bitmap {
        // Ensure ROI is within bitmap bounds
        val x = (roi.left * source.width).toInt().coerceIn(0, source.width)
        val y = (roi.top * source.height).toInt().coerceIn(0, source.height)
        val width = ((roi.right - roi.left) * source.width).toInt()
        val height = ((roi.bottom - roi.top) * source.height).toInt()

        if (x + width > source.width || y + height > source.height || width <= 0 || height <= 0) {
            // Return a small empty bitmap if ROI is invalid
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

        return Bitmap.createBitmap(source, x, y, width, height)
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
        handDetector.close()
    }
}