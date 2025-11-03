package com.example.cleanchecknative.ui.register

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.cleanchecknative.ui.common.CameraView
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import com.example.cleanchecknative.ui.user.UserDetailsActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

class RegistrationActivity : ComponentActivity() {

    private val viewModel: RegistrationViewModel by viewModels()
    private var cameraProvider: ProcessCameraProvider? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // --- Start: Fullscreen & Immersive Mode ---
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        // --- End: Fullscreen & Immersive Mode ---

        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            setContent {
                CleanCheckNativeTheme {
                    val uiState by viewModel.uiState.collectAsState()
                    val context = LocalContext.current
                    val coroutineScope = rememberCoroutineScope()

                    RegistrationScreen(
                        uiState = uiState,
                        cameraProvider = cameraProvider,
                        onFaceDetected = { bitmap, rect ->
                            viewModel.onFaceDetected(bitmap, rect)
                        },
                        onCameraReady = {
                            viewModel.startRegistrationProcess()
                        }
                    )

                    LaunchedEffect(uiState) {
                        when (val state = uiState) {
                            is RegistrationState.Success -> {
                                coroutineScope.launch {
                                    cameraProvider?.unbindAll()
                                    val intent = Intent(context, UserDetailsActivity::class.java).apply {
                                        putExtra(UserDetailsActivity.EXTRA_IMAGE_PATH, state.imagePath)
                                        putExtra(UserDetailsActivity.EXTRA_EMBEDDING, state.embedding)
                                    }
                                    startActivity(intent)
                                    finish()
                                }
                            }
                            is RegistrationState.Error -> {
                                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                                viewModel.resetState()
                                // Restart the process after error
                                viewModel.startRegistrationProcess()
                            }
                            else -> Unit
                        }
                    }
                }
            }
        }, ContextCompat.getMainExecutor(this))
    }
}

@Composable
fun RegistrationScreen(
    uiState: RegistrationState,
    cameraProvider: ProcessCameraProvider?,
    onFaceDetected: (android.graphics.Bitmap, android.graphics.Rect) -> Unit,
    onCameraReady: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (cameraProvider != null) {
            CameraView(
                modifier = Modifier.fillMaxSize(),
                cameraProvider = cameraProvider,
                onFaceDetected = onFaceDetected,
                onNoFaceDetected = {},
                onCameraReady = onCameraReady
            )
        }

        Text(
            text = "CleanCheck",
            color = Color(0xFF4F83EB),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = 8.dp)
        )

        OverlayForState(uiState)
    }
}

@Composable
private fun OverlayForState(uiState: RegistrationState) {
    if (uiState is RegistrationState.Processing || uiState is RegistrationState.Countdown) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is RegistrationState.Countdown -> {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "정면을 응시해주세요",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 35.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "${uiState.seconds}",
                            color = Color.White,
                            fontSize = 240.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                is RegistrationState.Processing -> {
                    CircularProgressIndicator()
                }
                else -> Unit
            }
        }
    }
}