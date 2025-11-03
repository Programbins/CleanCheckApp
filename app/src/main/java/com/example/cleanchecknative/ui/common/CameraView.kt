package com.example.cleanchecknative.ui.common

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Composable
fun CameraView(
    modifier: Modifier = Modifier,
    cameraProvider: ProcessCameraProvider,
    onFaceDetected: (Bitmap, Rect) -> Unit,
    onNoFaceDetected: () -> Unit,
    onCameraReady: () -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleX = -1f } }

    LaunchedEffect(cameraProvider) {
        val preview = Preview.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .build()
            .also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

        val imageAnalyzer = ImageAnalysis.Builder()
            .setTargetAspectRatio(AspectRatio.RATIO_16_9)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            .build()
            .also {
                it.setAnalyzer(
                    Executors.newSingleThreadExecutor(),
                    FaceAnalyzer(onFaceDetected, onNoFaceDetected)
                )
            }

        val cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            onCameraReady() // Signal that the camera is ready
        } catch (exc: Exception) {
            // Log error
        }
    }

    AndroidView({ previewView }, modifier = modifier)
}

private class FaceAnalyzer(
    private val onFaceDetected: (Bitmap, Rect) -> Unit,
    private val onNoFaceDetected: () -> Unit
) : ImageAnalysis.Analyzer {

    private val detector = FaceDetection.getClient()
    private var isProcessing = false

    @androidx.camera.core.ExperimentalGetImage
    override fun analyze(imageProxy: ImageProxy) {
        if (isProcessing) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

            isProcessing = true
            detector.process(image)
                .addOnSuccessListener { faces ->
                    if (faces.isNotEmpty()) {
                        val largestFace = faces.maxByOrNull { it.boundingBox.width() * it.boundingBox.height() }
                        if (largestFace != null) {
                            val bitmap = BitmapUtils.toBitmap(imageProxy)
                            if (bitmap != null) {
                                onFaceDetected(bitmap, largestFace.boundingBox)
                            }
                        }
                    } else {
                        onNoFaceDetected()
                    }
                }
                .addOnFailureListener {
                    onNoFaceDetected()
                }
                .addOnCompleteListener {
                    isProcessing = false
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}

private suspend fun Context.getCameraProvider(): ProcessCameraProvider = suspendCoroutine { continuation ->
    ProcessCameraProvider.getInstance(this).also { cameraProviderFuture ->
        cameraProviderFuture.addListener({
            continuation.resume(cameraProviderFuture.get())
        }, ContextCompat.getMainExecutor(this))
    }
}

