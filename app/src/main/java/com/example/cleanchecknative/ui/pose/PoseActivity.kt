package com.example.cleanchecknative.ui.pose

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.cleanchecknative.databinding.ActivityPoseBinding
import com.google.mediapipe.tasks.vision.core.RunningMode
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class PoseActivity : AppCompatActivity(), PoseDetector.PoseDetectorListener {

    private lateinit var binding: ActivityPoseBinding
    private lateinit var poseDetector: PoseDetector
    private lateinit var cameraExecutor: ExecutorService

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPoseBinding.inflate(layoutInflater)
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

            poseDetector = PoseDetector(
                context = this,
                runningMode = RunningMode.LIVE_STREAM,
                listener = this
            )

            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor) { image ->
                        val bitmapBuffer = Bitmap.createBitmap(
                            image.width,
                            image.height,
                            Bitmap.Config.ARGB_8888
                        )
                        image.use { bitmapBuffer.copyPixelsFromBuffer(image.planes[0].buffer) }
                        // The image is rotated, so we need to handle it if needed.
                        // For simplicity, we'll pass it as is.
                        poseDetector.detectAsync(bitmapBuffer, image.imageInfo.timestamp)
                    }
                }

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )
            } catch (exc: Exception) {
                Toast.makeText(this, "Failed to start camera.", Toast.LENGTH_SHORT).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    override fun onError(error: String, errorCode: Int) {
        runOnUiThread {
            Toast.makeText(this, error, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResults(results: PoseResultBundle) {
        runOnUiThread {
            binding.overlay.setResults(
                poseResults = results.landmarks,
                leftHandResults = null, // Not used in this activity
                rightHandResults = null, // Not used in this activity
                imageHeight = binding.viewFinder.height,
                imageWidth = binding.viewFinder.width
            )
        }
    }

    override fun onEmpty() {
        runOnUiThread {
            binding.overlay.clear()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
        poseDetector.close()
    }
}