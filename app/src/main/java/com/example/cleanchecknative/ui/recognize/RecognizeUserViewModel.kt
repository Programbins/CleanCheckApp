package com.example.cleanchecknative.ui.recognize

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.domain.FaceNet
import com.example.cleanchecknative.ui.common.BitmapUtils
import com.google.mlkit.vision.face.Face
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class RecognizeUserViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow<RecognitionState>(RecognitionState.Idle)
    val uiState = _uiState.asStateFlow()

    private val _countdown = MutableStateFlow<Int?>(null)
    val countdown = _countdown.asStateFlow()

    private val _shouldDimScreen = MutableStateFlow(true)
    val shouldDimScreen = _shouldDimScreen.asStateFlow()

    private val _feedbackMessage = MutableStateFlow("얼굴이 인식되면 시작됩니다")
    val feedbackMessage = _feedbackMessage.asStateFlow()

    private val faceNet = FaceNet(application)
    private val userDao = AppDatabase.getDatabase(application).userDao()

    private var trackedUserId: String? = null
    private var trackingStartTime: Long = 0L
    private var recognitionJob: Job? = null

    companion object {
        private const val RECOGNITION_THRESHOLD = 1.0f
        private const val TRACKING_DURATION_MS = 1000L // 1초 검증 시간은 유지
        private const val CENTER_THRESHOLD = 0.2f // 화면 중앙에서 20% 이내
        private const val MIN_FACE_SIZE_RATIO = 0.05f // 얼굴이 화면 너비의 5% 이상 차지
        private const val MAX_EULER_ANGLE_Y = 10.0f // 좌우 최대 각도
        private const val MAX_EULER_ANGLE_X = 10.0f // 상하 최대 각도
    }

    fun processFaces(faces: List<Face>, bitmap: Bitmap) {
        if (_uiState.value is RecognitionState.Success) return

        if (faces.isEmpty()) {
            resetTracking()
            _feedbackMessage.value = "얼굴이 인식되면 시작됩니다"
            return
        }

        if (faces.size > 1) {
            resetTracking()
            _feedbackMessage.value = "한 명만 화면에 나와야 합니다"
            return
        }

        val face = faces.first()
        val faceWidthRatio = face.boundingBox.width().toFloat() / bitmap.width

        if (faceWidthRatio < MIN_FACE_SIZE_RATIO) {
            resetTracking()
            _feedbackMessage.value = "조금 더 가까이 다가와 주세요"
            return
        }

        if (!isFaceCentered(face, bitmap.width)) {
            resetTracking()
            _feedbackMessage.value = "얼굴을 중앙으로 이동시켜주세요"
            return
        }

        if (kotlin.math.abs(face.headEulerAngleY) > MAX_EULER_ANGLE_Y || kotlin.math.abs(face.headEulerAngleX) > MAX_EULER_ANGLE_X) {
            resetTracking()
            _feedbackMessage.value = "얼굴을 정면으로 향하게 해주세요"
            return
        }

        _feedbackMessage.value = "잠시 유지해주세요..."
        recognizeFace(bitmap, face.boundingBox)
    }

    private fun isFaceCentered(face: Face, imageWidth: Int): Boolean {
        val faceBox = face.boundingBox
        val faceCenterX = faceBox.centerX()
        val imageCenterX = imageWidth / 2
        val dx = (faceCenterX - imageCenterX).toFloat() / imageWidth
        return kotlin.math.abs(dx) < CENTER_THRESHOLD
    }

    private fun recognizeFace(bitmap: Bitmap, faceBoundingBox: Rect) {
        if (recognitionJob?.isActive == true) return

        recognitionJob = viewModelScope.launch {
            try {
                val croppedBitmap = BitmapUtils.cropBitmapWithPadding(bitmap, faceBoundingBox, 1.8f)
                val embedding = faceNet.getFaceEmbedding(croppedBitmap)
                val bestMatch = findBestMatch(embedding)

                if (bestMatch != null) {
                    if (bestMatch.id == trackedUserId) {
                        val elapsedTime = System.currentTimeMillis() - trackingStartTime
                        if (elapsedTime >= TRACKING_DURATION_MS) {
                            _uiState.value = RecognitionState.Found(bestMatch)
                        }
                    } else {
                        resetTracking()
                        trackedUserId = bestMatch.id
                        trackingStartTime = System.currentTimeMillis()
                    }
                } else {
                    resetTracking()
                }
            } catch (e: Exception) {
                Log.e("RecognizeUserVM", "Error during face recognition", e)
                resetTracking()
            }
        }
    }

    private suspend fun findBestMatch(embedding: FloatArray): UserEntity? {
        val allUsers = userDao.getAllUsers()
        if (allUsers.isEmpty()) {
            _uiState.value = RecognitionState.Error("등록된 사용자가 없습니다.")
            delay(2000)
            return null
        }

        var bestMatch: UserEntity? = null
        var bestDistance = Float.MAX_VALUE

        for (user in allUsers) {
            val distance = faceNet.getDistance(embedding, user.embedding)
            if (distance < bestDistance) {
                bestDistance = distance
                bestMatch = user
            }
        }

        return if (bestDistance < RECOGNITION_THRESHOLD) {
            bestMatch
        } else {
            null
        }
    }

    private fun resetTracking() {
        if (trackedUserId != null) {
            trackedUserId = null
            trackingStartTime = 0L
        }
    }

    fun resetState() {
        _uiState.value = RecognitionState.Idle
        _feedbackMessage.value = "얼굴이 인식되면 시작됩니다"
        resetTracking()
    }

    override fun onCleared() {
        super.onCleared()
        faceNet.close()
    }
}

sealed class RecognitionState {
    object Idle : RecognitionState()
    object Processing : RecognitionState()
    data class Found(val user: UserEntity) : RecognitionState()
    data class Success(val user: UserEntity) : RecognitionState() // Kept for compatibility, but Found is now used
    data class Error(val message: String) : RecognitionState()
}
