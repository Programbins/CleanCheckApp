package com.example.cleanchecknative.ui.recognize

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.ui.common.BitmapUtils
import com.example.cleanchecknative.ui.handwash.HandwashLoadingActivity
import com.example.cleanchecknative.ui.handwash.HandwashingActivity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors

class RecognizeUserActivity : ComponentActivity() {

    private val viewModel: RecognizeUserViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContent {
            CleanCheckNativeTheme {
                val uiState by viewModel.uiState.collectAsState()
                val feedbackMessage by viewModel.feedbackMessage.collectAsState()

                RecognizeUserScreen(
                    uiState = uiState,
                    feedbackMessage = feedbackMessage,
                    onFacesDetected = { faces, bitmap ->
                        viewModel.processFaces(faces, bitmap)
                    },
                    onReset = { viewModel.resetState() }
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun RecognizeUserScreen(
    uiState: RecognitionState,
    feedbackMessage: String,
    onFacesDetected: (List<com.google.mlkit.vision.face.Face>, android.graphics.Bitmap) -> Unit,
    onReset: () -> Unit
) {
    val context = LocalContext.current
    val isConfirmation = uiState is RecognitionState.Found

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(if (isConfirmation) MaterialTheme.colorScheme.surfaceVariant else Color.Black)
    ) {
        // Layer 1: Camera (only when not in confirmation)
        if (!isConfirmation) {
            CameraView(onFacesDetected = onFacesDetected)
        }

        // Layer 2: Logo (always visible)
        Text(
            text = "CleanCheck",
            color = Color(0xFF4F83EB),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
        )

        // Layer 3: Content (Confirmation Dialog or Feedback Text)
        when (uiState) {
            is RecognitionState.Found -> {
                UserConfirmationDialog(
                    user = uiState.user,
                    onConfirm = {
                        val intent = Intent(context, HandwashLoadingActivity::class.java).apply {
                            putExtra("USER_ID_EXTRA", uiState.user.id)
                        }
                        context.startActivity(intent)
                        (context as? ComponentActivity)?.finish()
                    },
                    onReject = onReset
                )
            }
            is RecognitionState.Error -> {
                Toast.makeText(context, uiState.message, Toast.LENGTH_SHORT).show()
                onReset()
            }
            else -> { // Idle, Processing
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = feedbackMessage,
                        color = Color.White,
                        fontSize = 35.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun CameraView(
    onFacesDetected: (List<com.google.mlkit.vision.face.Face>, android.graphics.Bitmap) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    val highAccuracyOpts = FaceDetectorOptions.Builder()
        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
        .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
        .build()

    val faceDetector = remember { FaceDetection.getClient(highAccuracyOpts) }
    val executor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            faceDetector.close()
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply { scaleX = -1f }
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(executor) { imageProxy ->
                            val image = imageProxy.image
                            if (image != null) {
                                val processImage = InputImage.fromMediaImage(image, imageProxy.imageInfo.rotationDegrees)
                                faceDetector.process(processImage)
                                    .addOnSuccessListener { faces ->
                                        BitmapUtils.toBitmap(imageProxy)?.let { bitmap ->
                                            onFacesDetected(faces, bitmap)
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    // Log error
                }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize()
    )
}


@Composable
fun UserConfirmationDialog(
    user: UserEntity,
    onConfirm: () -> Unit,
    onReject: () -> Unit
) {
    var countdown by remember { mutableStateOf(5) }

    LaunchedEffect(Unit) {
        while (countdown > 0) {
            kotlinx.coroutines.delay(1000L)
            countdown--
        }
        if (countdown == 0) {
            onReject()
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .width(700.dp)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "사용자 확인",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(32.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left side: Photo and Name
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(0.5f)
                        ) {
                            Image(
                                painter = rememberAsyncImagePainter(File(user.photoPath)),
                                contentDescription = "User Photo",
                                modifier = Modifier
                                    .size(150.dp)
                                    .clip(CircleShape),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = user.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // Right side: Description
                        Text(
                            text = "손소독제를 덜어 주세요.\n\n본인이 맞다면 '손씻기 시작'\n\n본인이 아니면 '다시 인식'",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Start,
                            modifier = Modifier
                                .weight(0.5f)
                                .padding(start = 24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(32.dp))

                    // Buttons at the bottom
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onReject,
                            modifier = Modifier.height(56.dp),
                            shape = MaterialTheme.shapes.medium,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEBB74F),
                                contentColor = Color.Black
                            )
                        ) {
                            Text("다시 인식", style = MaterialTheme.typography.titleMedium)
                        }
                        Button(
                            onClick = onConfirm,
                            modifier = Modifier.height(56.dp),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Text("손씻기 시작", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                Text(
                    text = "${countdown}초 후 자동 재인식",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.Gray,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 16.dp, end = 24.dp)
                )
            }
        }
    }
}
