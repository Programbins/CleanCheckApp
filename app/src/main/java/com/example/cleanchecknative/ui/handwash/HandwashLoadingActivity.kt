package com.example.cleanchecknative.ui.handwash

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.example.cleanchecknative.ui.theme.CleanCheckNativeTheme
import kotlinx.coroutines.launch

class HandwashLoadingActivity : ComponentActivity() {
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

        setContent {
            CleanCheckNativeTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("손씻기 모델을 불러오는 중입니다...")
                    }
                }
            }
        }

        lifecycleScope.launch {
            // We need a dummy listener for the model loader, as the real one is in the ViewModel.
            // The listener will be replaced when the ViewModel gets the detector instance.
            val dummyListener = object : com.example.cleanchecknative.yolo.Detector.DetectorListener {
                override fun onEmptyDetect() {}
                override fun onDetect(boundingBoxes: List<com.example.cleanchecknative.yolo.BoundingBox>, inferenceTime: Long) {}
            }
            ModelLoader.loadModels(applicationContext, dummyListener)

            val intent = Intent(this@HandwashLoadingActivity, HandwashingActivity::class.java).apply {
                putExtra("USER_ID_EXTRA", userId)
            }
            startActivity(intent)
            finish()
        }
    }
}
