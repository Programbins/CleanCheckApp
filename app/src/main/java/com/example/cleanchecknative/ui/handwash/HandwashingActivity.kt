package com.example.cleanchecknative.ui.handwash

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.AspectRatio
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import coil.compose.rememberAsyncImagePainter
import android.util.Log
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.ui.common.BitmapUtils
import com.example.cleanchecknative.ui.recognize.RecognizeUserActivity
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.min
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class HandwashingActivity : ComponentActivity() {

    private val viewModel: HandwashingViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        val userId = intent.getStringExtra("USER_ID_EXTRA")
        if (userId == null) {
            finish()
            return
        }
        viewModel.loadUser(userId)

        setContent {
            CleanCheckNativeTheme {
                val context = LocalContext.current
                val permissionsToRequest = arrayOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                var hasPermissions by remember {
                    mutableStateOf(permissionsToRequest.all {
                        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
                    })
                }
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    hasPermissions = permissions.values.all { it }
                }

                LaunchedEffect(Unit) {
                    if (!hasPermissions) {
                        permissionLauncher.launch(permissionsToRequest)
                    }
                }

                if (hasPermissions) {
                    LaunchedEffect(Unit) {
                        viewModel.startInitialDetection()
                    }

                    val user by viewModel.user.collectAsState()
                    val handLandmarkerResult by viewModel.handLandmarkerResult.collectAsState()
                    val detectedActionName by viewModel.detectedActionName.collectAsState()
                    val totalTime by viewModel.totalTime.collectAsState()
                    val totalProgress by viewModel.totalProgress.collectAsState()
                    val palmProgress by viewModel.palmProgress.collectAsState()
                    val backProgress by viewModel.backProgress.collectAsState()
                    val feedbackMessage by viewModel.feedbackMessage.collectAsState()
                    val cleansedState by viewModel.cleansedState.collectAsState()
                    val yoloClassId by viewModel.yoloDetectedClassId.collectAsState()
                    val isWashingStarted by viewModel.isWashingStarted.collectAsState()
                    val initialHandNotDetected by viewModel.initialHandNotDetected.collectAsState()
                    val analysisFinished by viewModel.analysisFinished.collectAsState()

                    val navigateToRecognize = {
                        val intent = Intent(this@HandwashingActivity, RecognizeUserActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        startActivity(intent)
                        finish()
                    }

                    LaunchedEffect(initialHandNotDetected) {
                        if (initialHandNotDetected) {
                            navigateToRecognize()
                        }
                    }

                    HandwashingScreen(
                        user = user,
                        onBitmapReady = { bitmap -> if (!analysisFinished) viewModel.processBitmap(bitmap) },
                        handLandmarkerResult = handLandmarkerResult,
                        remainingTime = totalTime,
                        totalProgress = totalProgress,
                        feedbackMessage = feedbackMessage,
                        cleansedState = cleansedState,
                        yoloClassId = yoloClassId,
                        isWashingStarted = isWashingStarted,
                        analysisFinished = analysisFinished,
                        onSaveAndFinish = { videoPath, screenshotPath ->
                            viewModel.saveWashResult(applicationContext, videoPath, screenshotPath)
                            navigateToRecognize()
                        }
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("카메라와 오디오 권한이 필요합니다.")
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(onClick = { permissionLauncher.launch(permissionsToRequest) }) {
                                Text("권한 재요청")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HandwashingScreen(
    user: UserEntity?,
    onBitmapReady: (android.graphics.Bitmap) -> Unit,
    handLandmarkerResult: Pair<List<List<NormalizedLandmark>>, Map<String, String>>?,
    remainingTime: Int,
    totalProgress: Float,
    feedbackMessage: String,
    cleansedState: Map<String, Map<String, List<Boolean>>> ,
    yoloClassId: Int,
    isWashingStarted: Boolean,
    analysisFinished: Boolean,
    onSaveAndFinish: (String?, String?) -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var recording: Recording? by remember { mutableStateOf(null) }
    var isFinalizing by remember { mutableStateOf(false) }
    var videoPath by remember { mutableStateOf<String?>(null) }

    val displayMessage = if (isFinalizing || analysisFinished) "저장중 입니다..." else feedbackMessage

    fun startRecording() {
        videoCapture?.let { vc ->
            val name = "CleanCheck-VID-${System.currentTimeMillis()}"
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            }
            val mediaStoreOutput = MediaStoreOutputOptions.Builder(
                context.contentResolver,
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            ).setContentValues(contentValues).build()

            recording = vc.output
                .prepareRecording(context, mediaStoreOutput)
                .apply { if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) withAudioEnabled() }
                .start(ContextCompat.getMainExecutor(context)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            // Recording started
                        }
                        is VideoRecordEvent.Finalize -> {
                            if (!event.hasError()) {
                                videoPath = event.outputResults.outputUri.toString()
                                Toast.makeText(context, "녹화가 저장되었습니다.", Toast.LENGTH_SHORT).show()
                            }
                            isFinalizing = true
                        }
                    }
                }
        }
    }

    LaunchedEffect(isFinalizing) {
        if (isFinalizing) {
            delay(2000)
            var screenshotPath: String? = null
            try {
                val screenshot = BitmapUtils.captureWindow(activity.window)
                screenshotPath = BitmapUtils.saveBitmapAsImage(screenshot, activity)
                if (screenshotPath != null) {
                    Toast.makeText(context, "스크린샷이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("HandwashingScreen", "Screenshot failed", e)
                Toast.makeText(context, "스크린샷 실패: ${e.message}", Toast.LENGTH_LONG).show()
            }
            onSaveAndFinish(videoPath, screenshotPath)
        }
    }

    LaunchedEffect(videoCapture, isWashingStarted) {
        if (videoCapture != null && isWashingStarted && recording == null) {
            startRecording()
        }
    }

    LaunchedEffect(analysisFinished) {
        if (analysisFinished && recording != null && !isFinalizing) {
            recording?.stop()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            recording?.stop()
        }
    }

    val totalDuration = 180
    val elapsedTime = if (isWashingStarted) totalDuration - remainingTime else 0

    val defaultColor = Color(0xFFEBB74F)
    val highlightColor = Color(0xFF4F83EB)

    val progressTimeColor = if (elapsedTime > 30) highlightColor else defaultColor
    val totalProgressColor = if (totalProgress > 0.8f) highlightColor else defaultColor

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Left Column: Camera and User Info
        Column(
            modifier = Modifier
                .weight(4f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Camera View
            Box(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth()
            ) {
                CameraViewWithRecording(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp)),
                    onBitmapReady = onBitmapReady,
                    onVideoCaptureReady = {
                        videoCapture = it
                    }
                )
                Text(
                    text = "CleanCheck",
                    color = Color(0xFF4F83EB),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 8.dp, top = 8.dp)
                )
            }
            // Combined Box for Recommendation and User Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Box for the feedback message
                Card(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        InfoCard(
                            title = "",
                            value = displayMessage,
                            isFeedback = true,
                            horizontalAlignment = Alignment.CenterHorizontally
                        )
                    }
                }
                // Adjusted User Info Card
                UserInfoCard(
                    user = user,
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxWidth()
                )
            }
        }

        // Right Column: Hand Displays, Gauges, and Progress
        Column(
            modifier = Modifier
                .weight(8f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .weight(2f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(
                    modifier = Modifier
                        .weight(7f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize()
                        ) {
                            Text(
                                text = "손바닥",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    SvgHandDisplay(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 30.dp, start = 100.dp, end = 100.dp, bottom = 100.dp),
                                        handLabel = "Left", handType = "palm", cleansedState = cleansedState, handLandmarkerResult = handLandmarkerResult
                                    )
                                    SvgHandDisplay(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 30.dp, start = 100.dp, end = 100.dp, bottom = 100.dp),
                                        handLabel = "Right", handType = "palm", cleansedState = cleansedState, handLandmarkerResult = handLandmarkerResult
                                    )
                                }
                                Text(
                                    text = "왼손",
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = -20.dp)
                                        .padding(bottom = 8.dp),
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "오른손",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxSize()
                        ) {
                            Text(
                                text = "손등",
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.Gray,
                                fontSize = 32.sp,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.SpaceEvenly,
                                    verticalAlignment = Alignment.Top
                                ) {
                                    SvgHandDisplay(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 30.dp, start = 95.dp, end = 95.dp, bottom = 95.dp),
                                        handLabel = "Left", handType = "back", cleansedState = cleansedState, handLandmarkerResult = handLandmarkerResult
                                    )
                                    SvgHandDisplay(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(top = 30.dp, start = 95.dp, end = 95.dp, bottom = 95.dp),
                                        handLabel = "Right", handType = "back", cleansedState = cleansedState, handLandmarkerResult = handLandmarkerResult
                                    )
                                }
                                Text(
                                    text = "왼손",
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .offset(x = -20.dp)
                                        .padding(bottom = 8.dp),
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelLarge
                                )
                                Text(
                                    text = "오른손",
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(8.dp),
                                    color = Color.DarkGray,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    GaugeBox(
                        yoloClassId = yoloClassId,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TotalProgressCard(
                    progress = totalProgress,
                    color = totalProgressColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
                ProgressTimeCard(
                    elapsedTime = elapsedTime,
                    color = progressTimeColor,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@Composable
fun CameraViewWithRecording(
    modifier: Modifier = Modifier,
    onBitmapReady: (android.graphics.Bitmap) -> Unit,
    onVideoCaptureReady: (VideoCapture<Recorder>) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        factory = {
            val previewView = PreviewView(it).apply { scaleX = -1f }
            val executor = ContextCompat.getMainExecutor(it)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .build()
                    .also { pv -> pv.setSurfaceProvider(previewView.surfaceProvider) }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setTargetAspectRatio(AspectRatio.RATIO_16_9)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analyzer ->
                        analyzer.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                            BitmapUtils.toBitmap(imageProxy)?.let { bitmap -> onBitmapReady(bitmap) }
                            imageProxy.close()
                        }
                    }

                val qualitySelector = QualitySelector.from(Quality.FHD,
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))
                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                val videoCapture = VideoCapture.withOutput(recorder)
                onVideoCaptureReady(videoCapture)

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        preview,
                        imageAnalyzer,
                        videoCapture
                    )
                } catch (exc: Exception) {
                    // Log error
                }
            }, executor)
            previewView
        },
        modifier = modifier
    )
}

@Composable
fun TotalProgressCard(
    progress: Float,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = progress)
                    .background(color.copy(alpha = 0.2f))
                    .align(Alignment.CenterStart)
            )
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(16.dp)
            ) {
                Text("전체 진행률", style = MaterialTheme.typography.titleMedium, color = Color.Gray, fontSize = 32.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    fontSize = 64.sp
                )
            }
        }
    }
}


@Composable
fun ProgressTimeCard(
    elapsedTime: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("진행 시간", style = MaterialTheme.typography.titleMedium, color = Color.Gray, fontSize = 32.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$elapsedTime 초",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                color = color,
                fontSize = 64.sp
            )
        }
    }
}

@Composable
fun GaugeBox(
    yoloClassId: Int,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            // Text removed as per user request
        }
    }
}

@Composable
fun VerticalProgressBar(
    progress: Float,
    color: Color,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(40.dp)
                .weight(1f)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.LightGray.copy(alpha = 0.4f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(fraction = progress)
                    .background(color)
                    .align(Alignment.BottomCenter)
            )
        }
    }
}


@Composable
fun InfoCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    isFeedback: Boolean = false,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = if (isFeedback) {
            CardDefaults.cardColors(containerColor = Color.Transparent)
        } else {
            CardDefaults.cardColors()
        }
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = horizontalAlignment
        ) {
            if (title.isNotEmpty()) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            val textStyle = if (isFeedback) {
                MaterialTheme.typography.bodyLarge.copy(fontSize = 24.sp)
            } else {
                MaterialTheme.typography.headlineSmall
            }
            Text(
                text = value,
                style = textStyle,
                fontWeight = FontWeight.Bold,
                color = if (isFeedback) Color.Black else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

private data class SvgPathInfo(val id: String, val path: Path)
private data class SvgInfo(val paths: List<SvgPathInfo>, val bounds: Rect)

@Composable
private fun rememberSvgInfo(handLabel: String, handType: String): SvgInfo? {
    val context = LocalContext.current
    return remember(handLabel, handType) {
        val fileName = "${handType}_hand경로.txt"
        try {
            val svgPaths = context.assets.open(fileName).bufferedReader().useLines { lines ->
                val pathParser = PathParser()
                lines.drop(1)
                    .mapNotNull { line ->
                        val parts = line.split(Regex("[,\\t]" ), limit = 2)
                        if (parts.size == 2) {
                            val id = parts[0].trim()
                            val pathData = parts[1].trim().removeSurrounding("\"")
                            if (id.startsWith("${handLabel}_${handType}", ignoreCase = true)) {
                                SvgPathInfo(id, pathParser.parsePathString(pathData).toPath())
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }.toList()
            }

            if (svgPaths.isEmpty()) {
                null
            } else {
                var combinedBounds = svgPaths.first().path.getBounds()
                svgPaths.drop(1).forEach { pathInfo ->
                     combinedBounds = combinedBounds.let {
                        val new = pathInfo.path.getBounds()
                        Rect(
                            left = minOf(it.left, new.left),
                            top = minOf(it.top, new.top),
                            right = maxOf(it.right, new.right),
                            bottom = maxOf(it.bottom, new.bottom)
                        )
                    }
                }
                SvgInfo(svgPaths, combinedBounds)
            }
        } catch (e: Exception) {
            null
        }
    }
}

@Composable
fun UserInfoCard(
    user: UserEntity?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Card(
        modifier = modifier,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        if (user == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Text(
                    text = "사용자",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.TopStart)
                )

                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(File(user.photoPath)),
                        contentDescription = "User Photo",
                        modifier = Modifier
                            .size(80.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = user.name,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "사용자가 다를 시\n다시인식 버튼을 눌러주세요",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.BottomStart)
                )

                Button(
                    onClick = {
                        val intent = Intent(context, RecognizeUserActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        context.startActivity(intent)
                        (context as? Activity)?.finish()
                    },
                    modifier = Modifier.align(Alignment.BottomEnd)
                ) {
                    Text("다시 인식")
                }
            }
        }
    }
}



@Composable
fun SvgHandDisplay(
    modifier: Modifier = Modifier,
    handLabel: String,
    handType: String,
    cleansedState: Map<String, Map<String, List<Boolean>>> ,
    handLandmarkerResult: Pair<List<List<NormalizedLandmark>>, Map<String, String>>?
) {
    val svgInfo = rememberSvgInfo(handLabel = handLabel, handType = handType)
    val isHandVisible = handLandmarkerResult?.second?.containsKey(handLabel) ?: false

    val defaultColor = Color(0xFFEBB74F)
    val cleansedColor = Color(0xFF4F83EB)
    val nonCleansablePartColor = Color.LightGray

    if (svgInfo == null) {
        Box(modifier) { /* Placeholder for missing asset */ }
        return
    }

    Canvas(modifier = modifier.aspectRatio(svgInfo.bounds.width / svgInfo.bounds.height)) {
        val svgBounds = svgInfo.bounds
        if (svgBounds.width <= 0f || svgBounds.height <= 0f) return@Canvas

        val scale = min(size.width / svgBounds.width, size.height / svgBounds.height)

        translate(left = 0f, top = 0f) {
            scale(scale) {
                translate(left = -svgBounds.left, top = -svgBounds.top) {
                    svgInfo.paths.forEach { pathInfo ->
                        val landmarkIndex = pathInfo.id.substringAfterLast('_').toIntOrNull()

                        val color = if (isHandVisible) {
                            if (landmarkIndex != null) {
                                val isCleansed = cleansedState[handLabel]?.get(handType)?.getOrNull(landmarkIndex) ?: false
                                if (isCleansed) cleansedColor else defaultColor
                            } else {
                                nonCleansablePartColor
                            }
                        } else {
                            Color.LightGray.copy(alpha = 0.2f)
                        }
                        drawPath(path = pathInfo.path, color = color)
                    }
                }
            }
        }
    }
}
