package com.example.cleanchecknative.ui.register

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanchecknative.domain.FaceNet
import com.example.cleanchecknative.ui.common.BitmapUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

class RegistrationViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<RegistrationState>(RegistrationState.Idle)
    val uiState = _uiState.asStateFlow()

    private val faceNet: FaceNet by lazy {
        FaceNet(getApplication())
    }

    fun startRegistrationProcess() {
        if (_uiState.value !is RegistrationState.Idle) return

        viewModelScope.launch {
            for (i in 3 downTo 1) {
                _uiState.value = RegistrationState.Countdown(i)
                delay(1000)
            }
            _uiState.value = RegistrationState.Detecting
        }
    }

    fun onFaceDetected(bitmap: Bitmap, faceBoundingBox: Rect) {
        if (_uiState.value !is RegistrationState.Detecting) return

        viewModelScope.launch {
            _uiState.value = RegistrationState.Processing
            try {
                val croppedBitmap = BitmapUtils.cropBitmapWithPadding(bitmap, faceBoundingBox, 1.8f)
                val embedding = faceNet.getFaceEmbedding(croppedBitmap)
                val imagePath = saveImageToFile(croppedBitmap)
                _uiState.value = RegistrationState.Success(imagePath, embedding)
            } catch (e: Exception) {
                _uiState.value = RegistrationState.Error("캡처에 실패했습니다. 다시 시도해주세요.")
            }
        }
    }

    fun resetState() {
        _uiState.value = RegistrationState.Idle
    }

    private fun saveImageToFile(bitmap: Bitmap): String {
        val file = File(getApplication<Application>().cacheDir, "face_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
        }
        return file.absolutePath
    }

    override fun onCleared() {
        super.onCleared()
        faceNet.close()
    }
}

sealed class RegistrationState {
    object Idle : RegistrationState()
    data class Countdown(val seconds: Int) : RegistrationState()
    object Detecting : RegistrationState()
    object Processing : RegistrationState()
    data class Success(val imagePath: String, val embedding: FloatArray) : RegistrationState()
    data class Error(val message: String) : RegistrationState()
}
