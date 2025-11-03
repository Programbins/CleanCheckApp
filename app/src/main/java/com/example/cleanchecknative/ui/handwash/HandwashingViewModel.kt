package com.example.cleanchecknative.ui.handwash

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.UserEntity
import com.example.cleanchecknative.data.db.WashResultEntity
import com.example.cleanchecknative.domain.KalmanFilter2D
import com.example.cleanchecknative.ui.common.BitmapUtils
import com.example.cleanchecknative.yolo.BoundingBox
import com.example.cleanchecknative.yolo.Constants
import com.example.cleanchecknative.yolo.Detector
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

data class WashZone(
    val name: String,
    val keypoints: Map<String, List<Int>>,
    val totalPoints: Int,
    val recommendationText: String
)

class HandwashingViewModel(application: Application) : AndroidViewModel(application), Detector.DetectorListener {

    // --- UI State Flows ---
    val handLandmarkerResult = MutableStateFlow<Pair<List<List<NormalizedLandmark>>, Map<String, String>>?>(null)
    val detectedActionName = MutableStateFlow("탐지 중...")
    val totalTime = MutableStateFlow(180)
    val totalProgress = MutableStateFlow(0f)
    val palmProgress = MutableStateFlow(0f)
    val backProgress = MutableStateFlow(0f)
    val feedbackMessage = MutableStateFlow("손을 화면에 보여주세요.")
    val cleansedState = MutableStateFlow<Map<String, Map<String, List<Boolean>>>>(emptyMap())
    val activeScenario = MutableStateFlow(0)
    private val _user = MutableStateFlow<UserEntity?>(null)
    val user = _user.asStateFlow()

    // --- Control State Flows for Activity ---
    private val _isWashingStarted = MutableStateFlow(false)
    val isWashingStarted = _isWashingStarted.asStateFlow()
    private val _initialHandNotDetected = MutableStateFlow(false)
    val initialHandNotDetected = _initialHandNotDetected.asStateFlow()
    private val _analysisFinished = MutableStateFlow(false)
    val analysisFinished = _analysisFinished.asStateFlow()

    // --- Models and Processors ---
    private var handLandmarker: HandLandmarker
    private var detector: Detector
    private val userDao = AppDatabase.getDatabase(application).userDao()
    private val washResultDao = AppDatabase.getDatabase(application).washResultDao()

    // --- Kalman Filters ---
    private var kalmanFiltersLeft = mutableMapOf<Int, KalmanFilter2D>()
    private var kalmanFiltersRight = mutableMapOf<Int, KalmanFilter2D>()

    // --- Internal State Management ---
    private var contactTimers = mutableMapOf<String, MutableMap<String, MutableList<Long>>>()
    private var swipeCounters = mutableMapOf<String, MutableMap<String, MutableList<Int>>>()
    private var lastContactState = mutableMapOf<String, MutableMap<String, MutableList<Boolean>>>()
    private var stableOrientations = mutableMapOf("Left" to "palm", "Right" to "palm")
    private var orientationCounters = mutableMapOf("Left" to 0, "Right" to 0)
    private var lastKnownSmoothedLandmarks = mutableMapOf<String, List<PointF>>()
    private val _yoloDetectedClassId = MutableStateFlow(-1)
    val yoloDetectedClassId = _yoloDetectedClassId.asStateFlow()
    private var frameCounter = 0

    // --- New State Management for Advanced YOLO Logic ---
    private var currentYoloClass: Int = -1
    private var yoloClassCounter: Int = 0 // For consecutive frames
    private var yoloCumulativeCounters = mutableMapOf<String, Int>() // For cumulative frames
    private var lastYoloDetectionTime: Long = 0L
    private var lastDetectedYoloClass: Int = -1


    // --- Timer Jobs ---
    private var initialDetectionJob: Job? = null
    private var mainWashingJob: Job? = null
    private var inactivityJob: Job? = null
    private val _lastHandDetectedTime = MutableStateFlow(0L)
    private val _lastMovementDetectedTime = MutableStateFlow(0L)

    // --- Internal State for Movement Detection ---
    private var lastFrameLandmarks = mutableMapOf<String, PointF>()

    companion object {
        private const val TOTAL_TIME_SECONDS = 180
        private const val INITIAL_DETECTION_TIMEOUT_MS = 10000L
        private const val NO_ACTIVITY_TIMEOUT_MS = 2000L
        private const val MOVEMENT_THRESHOLD_PX = 5f
        private const val CONTACT_TIME_THRESHOLD_MS = 50L
        private const val SWIPE_THRESHOLD = 5
        private const val ORIENTATION_CONFIRM_FRAMES = 5
        private const val YOLO_INFERENCE_INTERVAL = 1

        // --- New Thresholds ---
        private const val YOLO_CONSECUTIVE_FRAME_THRESHOLD = 5
        private const val YOLO_CUMULATIVE_FRAME_THRESHOLD = 8
        private const val DEACTIVATION_HOLD_MS = 100L // 0.1 seconds

        val WASH_ZONES = mapOf(
            "STEP_1" to WashZone("손바닥", mapOf("Left-palm" to (0..20).toList().minus(setOf(4, 8, 12, 16, 20)), "Right-palm" to (0..20).toList().minus(setOf(4, 8, 12, 16, 20))), 32, "손바닥끼리 문질러 주세요"),
            "STEP_2" to WashZone("손등", mapOf("Left-back" to (0..20).toList().minus(setOf(2, 3, 4)), "Right-back" to (0..20).toList().minus(setOf(2, 3, 4))), 36, "손등과 손가락 사이를 문질러 주세요"),
            "STEP_3" to WashZone("손가락 사이", mapOf("Left-palm" to listOf(5, 9, 13, 17), "Right-palm" to listOf(5, 9, 13, 17), "Left-back" to listOf(7, 8, 11, 12, 15, 16, 19, 20), "Right-back" to listOf(7, 8, 11, 12, 15, 16, 19, 20)), 24, "손깍지를 끼고 문질러 주세요"),
            "STEP_4" to WashZone("두 손 모아", mapOf("Left-palm" to listOf(6,7,8, 10,11,12, 14,15,16, 18,19,20), "Right-palm" to listOf(6,7,8, 10,11,12, 14,15,16, 18,19,20)), 24, "손톱 밑을 깨끗이 문질러 주세요"),
            "STEP_5" to WashZone("엄지", mapOf("Left-palm" to (1..4).toList(), "Right-palm" to (1..4).toList(), "Left-back" to (1..4).toList(), "Right-back" to (1..4).toList()), 16, "엄지를 돌리며 문질러 주세요"),
            "STEP_6" to WashZone("손톱 밑", mapOf("Left-palm" to listOf(8, 12, 16, 20), "Right-palm" to listOf(8, 12, 16, 20)), 8, "손톱을 반대 손바닥에 문질러 주세요")
        )
    }

    init {
        handLandmarker = ModelLoader.getHandLandmarker()
        detector = ModelLoader.getDetector()
        detector.setDetectorListener(this)
        resetWashStatus()
    }

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _user.value = userDao.getUserById(userId)
        }
    }

    private fun finishAnalysis(message: String) {
        if (_analysisFinished.value) return
        feedbackMessage.value = message
        _analysisFinished.value = true
        mainWashingJob?.cancel()
        inactivityJob?.cancel()
    }

    fun startInitialDetection() {
        resetWashStatus()
        initialDetectionJob?.cancel()
        initialDetectionJob = viewModelScope.launch {
            delay(INITIAL_DETECTION_TIMEOUT_MS)
            if (!_isWashingStarted.value) {
                _initialHandNotDetected.value = true
            }
        }
    }

    private fun startWashingTimers() {
        initialDetectionJob?.cancel()
        mainWashingJob?.cancel()
        inactivityJob?.cancel()

        totalTime.value = TOTAL_TIME_SECONDS
        val now = System.currentTimeMillis()
        _lastHandDetectedTime.value = now
        _lastMovementDetectedTime.value = now

        mainWashingJob = viewModelScope.launch {
            while (totalTime.value > 0 && isActive) {
                delay(1000)
                if (_isWashingStarted.value) {
                    totalTime.value--
                }
            }
            if (isActive) {
                finishAnalysis("손씻기 완료!")
            }
        }

        inactivityJob = viewModelScope.launch {
            while (isActive) {
                delay(500)
                if (_isWashingStarted.value && System.currentTimeMillis() - _lastMovementDetectedTime.value > NO_ACTIVITY_TIMEOUT_MS) {
                    finishAnalysis("움직임이 없어 종료합니다.")
                    break
                }
            }
        }
    }

    private fun resetWashStatus() {
        val hands = listOf("Left", "Right")
        val sides = listOf("palm", "back")
        val tempCleansedState = mutableMapOf<String, MutableMap<String, MutableList<Boolean>>>()
        hands.forEach { hand ->
            tempCleansedState[hand] = mutableMapOf()
            contactTimers[hand] = mutableMapOf()
            swipeCounters[hand] = mutableMapOf()
            lastContactState[hand] = mutableMapOf()
            sides.forEach { side ->
                tempCleansedState[hand]!![side] = MutableList(21) { false }
                contactTimers[hand]!![side] = MutableList(21) { 0L }
                swipeCounters[hand]!![side] = MutableList(21) { 0 }
                lastContactState[hand]!![side] = MutableList(21) { false }
            }
        }
        cleansedState.value = tempCleansedState
        lastKnownSmoothedLandmarks.clear()
        lastFrameLandmarks.clear()
        kalmanFiltersLeft.clear()
        kalmanFiltersRight.clear()
        totalTime.value = TOTAL_TIME_SECONDS
        totalProgress.value = 0f
        palmProgress.value = 0f
        backProgress.value = 0f
        feedbackMessage.value = "손을 화면에 보여주세요."
        activeScenario.value = 0
        _yoloDetectedClassId.value = -1
        frameCounter = 0

        currentYoloClass = -1
        yoloClassCounter = 0
        yoloCumulativeCounters.clear()
        lastYoloDetectionTime = 0L
        lastDetectedYoloClass = -1

        _isWashingStarted.value = false
        _initialHandNotDetected.value = false
        _analysisFinished.value = false

        initialDetectionJob?.cancel()
        mainWashingJob?.cancel()
        inactivityJob?.cancel()
    }

    fun processBitmap(bitmap: Bitmap) {
        if (_analysisFinished.value) return

        if (frameCounter % YOLO_INFERENCE_INTERVAL == 0) {
            detector.detect(bitmap)
        }
        frameCounter++

        viewModelScope.launch(Dispatchers.IO) {
            val mpImage = com.google.mediapipe.framework.image.BitmapImageBuilder(bitmap).build()
            val result = handLandmarker.detect(mpImage)

            withContext(Dispatchers.Main) {
                var handsDetected = false
                val detectedHands = mutableMapOf<String, List<NormalizedLandmark>>()

                if (result != null && result.landmarks().isNotEmpty()) {
                    handsDetected = true
                    _lastHandDetectedTime.value = System.currentTimeMillis()

                    if (!_isWashingStarted.value) {
                        _isWashingStarted.value = true
                        feedbackMessage.value = "손씻기를 시작합니다!"
                        startWashingTimers()
                    }

                    when (result.landmarks().size) {
                        1 -> {
                            val label = result.handedness().first().first().displayName()
                            detectedHands[label] = result.landmarks().first()
                        }
                        2 -> {
                            val hand1Landmarks = result.landmarks()[0]
                            val hand2Landmarks = result.landmarks()[1]
                            val hand1WristX = hand1Landmarks[0].x()
                            val hand2WristX = hand2Landmarks[0].x()
                            if (hand1WristX < hand2WristX) {
                                detectedHands["Right"] = hand1Landmarks
                                detectedHands["Left"] = hand2Landmarks
                            } else {
                                detectedHands["Right"] = hand2Landmarks
                                detectedHands["Left"] = hand1Landmarks
                            }
                        }
                    }
                }

                val smoothedLeft = detectedHands["Left"]?.let { updateKalmanFilters(kalmanFiltersLeft, it, bitmap.width, bitmap.height) }
                val smoothedRight = detectedHands["Right"]?.let { updateKalmanFilters(kalmanFiltersRight, it, bitmap.width, bitmap.height) }

                if (smoothedLeft != null) lastKnownSmoothedLandmarks["Left"] = smoothedLeft
                if (smoothedRight != null) lastKnownSmoothedLandmarks["Right"] = smoothedRight

                checkForMovement(smoothedLeft, smoothedRight)

                updateHybridCleansedStatus(
                    lastKnownSmoothedLandmarks["Left"],
                    lastKnownSmoothedLandmarks["Right"],
                    detectedHands["Left"],
                    detectedHands["Right"]
                )

                if (handsDetected) {
                    handLandmarkerResult.value = Pair(detectedHands.values.toList(), stableOrientations)
                }
            }
        }
    }

    private fun checkForMovement(leftHand: List<PointF>?, rightHand: List<PointF>?) {
        var movementDetected = false

        leftHand?.getOrNull(0)?.let {
            if (hasMoved("Left", it)) {
                movementDetected = true
            }
        }
        rightHand?.getOrNull(0)?.let {
            if (hasMoved("Right", it)) {
                movementDetected = true
            }
        }

        if (movementDetected) {
            _lastMovementDetectedTime.value = System.currentTimeMillis()
        }
    }

    private fun hasMoved(handLabel: String, currentWrist: PointF): Boolean {
        val lastWrist = lastFrameLandmarks[handLabel]
        lastFrameLandmarks[handLabel] = currentWrist // Update for next frame

        if (lastWrist == null) {
            return true // First time seeing this hand, count as movement
        }

        val dx = currentWrist.x - lastWrist.x
        val dy = currentWrist.y - lastWrist.y
        val distance = kotlin.math.sqrt(dx * dx + dy * dy)

        return distance > MOVEMENT_THRESHOLD_PX
    }

    private fun updateKalmanFilters(filters: MutableMap<Int, KalmanFilter2D>, landmarks: List<NormalizedLandmark>, width: Int, height: Int): List<PointF> {
        val smoothedPoints = mutableListOf<PointF>()
        landmarks.forEachIndexed { index, landmark ->
            val px = landmark.x() * width
            val py = landmark.y() * height
            if (filters[index] == null) {
                filters[index] = KalmanFilter2D(px, py)
            }
            val kf = filters[index]!!
            kf.predict()
            kf.update(floatArrayOf(px, py))
            val (smoothedX, smoothedY) = kf.getPosition()
            smoothedPoints.add(PointF(smoothedX, smoothedY))
        }
        return smoothedPoints
    }

    override fun onEmptyDetect() {
        _yoloDetectedClassId.value = -1
        detectedActionName.value = "탐지 중..."
    }

    override fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long) {
        val bestBox = boundingBoxes.maxByOrNull { it.cnf }
        val detectedClass = bestBox?.cls ?: -1
        _yoloDetectedClassId.value = detectedClass
        detectedActionName.value = bestBox?.clsName ?: "탐지 중..."

        if (detectedClass != -1) {
            lastYoloDetectionTime = System.currentTimeMillis()
            lastDetectedYoloClass = detectedClass
        }

        if (detectedClass == currentYoloClass) {
            yoloClassCounter++
        } else {
            currentYoloClass = detectedClass
            yoloClassCounter = 1
        }
    }

    private fun getZoneKeyForYoloId(yoloId: Int): String? {
        return when (yoloId) {
            0 -> "STEP_1"
            1 -> "STEP_2"
            2 -> "STEP_3"
            3 -> "STEP_4"
            4 -> "STEP_5"
            5 -> "STEP_6"
            else -> null
        }
    }

    private fun updateHybridCleansedStatus(
        leftSmoothed: List<PointF>?,
        rightSmoothed: List<PointF>?,
        leftOriginal: List<NormalizedLandmark>?,
        rightOriginal: List<NormalizedLandmark>?
    ) {
        val yoloId = _yoloDetectedClassId.value
        val isHoldActive = System.currentTimeMillis() - lastYoloDetectionTime < DEACTIVATION_HOLD_MS
        val effectiveYoloId = if (yoloId != -1) yoloId else if (isHoldActive) lastDetectedYoloClass else -1

        val topHand = getTopHand(leftOriginal, rightOriginal)
        val cumulativeKey = when (effectiveYoloId) {
            1, 5 -> topHand?.let { "$effectiveYoloId-$it" }
            -1 -> null
            else -> effectiveYoloId.toString()
        }
        if (cumulativeKey != null) {
            yoloCumulativeCounters[cumulativeKey] = (yoloCumulativeCounters[cumulativeKey] ?: 0) + 1
        }

        processYoloBasedCleansing(effectiveYoloId, cumulativeKey, leftOriginal, rightOriginal)

        val now = System.currentTimeMillis()

        if (leftSmoothed == null || rightSmoothed == null || leftOriginal == null || rightOriginal == null) {
            resetAllContactStates()
            activeScenario.value = 0
            return
        }

        val leftHull = getConvexHull(leftSmoothed)
        val rightHull = getConvexHull(rightSmoothed)
        if (leftHull.isEmpty() || rightHull.isEmpty()) {
            resetAllContactStates()
            activeScenario.value = 0
            return
        }

        val activeZoneKey = getZoneKeyForYoloId(effectiveYoloId)
        val activeWashZone = activeZoneKey?.let { WASH_ZONES[it] }

        if (activeWashZone != null) {
            activeScenario.value = effectiveYoloId + 1
            checkContactForScenario("Left", leftSmoothed, rightHull, now, "palm", activeWashZone.keypoints["Left-palm"])
            checkContactForScenario("Right", rightSmoothed, leftHull, now, "palm", activeWashZone.keypoints["Right-palm"])
            checkContactForScenario("Left", leftSmoothed, rightHull, now, "back", activeWashZone.keypoints["Left-back"])
            checkContactForScenario("Right", rightSmoothed, leftHull, now, "back", activeWashZone.keypoints["Right-back"])
        } else {
            val leftIsPalm = estimateAndStabilizeOrientation(leftOriginal, "Left")
            val rightIsPalm = estimateAndStabilizeOrientation(rightOriginal, "Right")
            when {
                (leftIsPalm && !rightIsPalm) || (!leftIsPalm && rightIsPalm) -> {
                    activeScenario.value = 2
                    checkContactForScenario("Left", leftSmoothed, rightHull, now, "palm", null)
                    checkContactForScenario("Right", rightSmoothed, leftHull, now, "palm", null)
                }
                !leftIsPalm && !rightIsPalm -> {
                    if (yoloId == 0 || yoloId == 2) {
                        // YOLO가 0 또는 2단계를 감지하면 손등+손등 알고리즘 비활성화
                        activeScenario.value = 0
                    } else {
                        activeScenario.value = 3
                        checkContactForScenario("Left", leftSmoothed, rightHull, now, "back", null)
                        checkContactForScenario("Right", rightSmoothed, leftHull, now, "back", null)
                    }
                }
                else -> {
                    activeScenario.value = 1
                    checkContactForScenario("Left", leftSmoothed, rightHull, now, "palm", null)
                    checkContactForScenario("Right", rightSmoothed, leftHull, now, "palm", null)
                }
            }
        }
        updateProgressAndFeedback()
    }

    private fun processYoloBasedCleansing(
        yoloId: Int,
        cumulativeKey: String?,
        leftOriginal: List<NormalizedLandmark>?,
        rightOriginal: List<NormalizedLandmark>?
    ) {
        val consecutiveThreshold = if (yoloId == 5) 3 else YOLO_CONSECUTIVE_FRAME_THRESHOLD
        val cumulativeThreshold = if (yoloId == 5) 4 else YOLO_CUMULATIVE_FRAME_THRESHOLD

        val consecutiveMet = yoloClassCounter >= consecutiveThreshold && yoloId == currentYoloClass
        val cumulativeMet = cumulativeKey != null && (yoloCumulativeCounters[cumulativeKey] ?: 0) >= cumulativeThreshold

        if (consecutiveMet || cumulativeMet) {
            when (yoloId) {
                0 -> markZoneAsCleansed("STEP_1")
                2 -> markZoneAsCleansed("STEP_3")
                3 -> markZoneAsCleansed("STEP_4")
                1 -> {
                    val topHand = getTopHand(leftOriginal, rightOriginal)
                    val bottomHand = if (topHand == "Left") "Right" else "Left"
                    WASH_ZONES["STEP_2"]?.keypoints?.forEach { (key, points) ->
                        val (hand, surface) = key.split('-')
                        if (hand == bottomHand && surface == "back") {
                            markSpecificPointsAsCleansed(hand, surface, points)
                        }
                    }
                }
                5 -> {
                    val topHand = getTopHand(leftOriginal, rightOriginal)
                    topHand?.let {
                        WASH_ZONES["STEP_6"]?.keypoints?.forEach { (key, points) ->
                            val (hand, surface) = key.split('-')
                            if (hand == it && surface == "palm") {
                                markSpecificPointsAsCleansed(hand, surface, points)
                            }
                        }
                    }
                }
                4 -> markZoneAsCleansed("STEP_5")
            }
            yoloClassCounter = 0
            if (cumulativeKey != null) {
                yoloCumulativeCounters[cumulativeKey] = 0
            }
        }
    }

    private fun getTopHand(left: List<NormalizedLandmark>?, right: List<NormalizedLandmark>?): String? {
        if (left.isNullOrEmpty() || right.isNullOrEmpty()) return null
        val leftAvgZ = left.map { it.z() }.average()
        val rightAvgZ = right.map { it.z() }.average()
        return if (leftAvgZ < rightAvgZ) "Left" else "Right"
    }

    private fun markZoneAsCleansed(zoneKey: String) {
        val zone = WASH_ZONES[zoneKey] ?: return
        val currentCleansedState = cleansedState.value.toMutableMap()
        var needsUpdate = false
        zone.keypoints.forEach { (sideKey, points) ->
            val (hand, surface) = sideKey.split('-')
            val handSideState = currentCleansedState[hand]?.get(surface)?.toMutableList() ?: return@forEach
            points.forEach { pointIndex ->
                if (!handSideState[pointIndex]) {
                    handSideState[pointIndex] = true
                    needsUpdate = true
                }
            }
            currentCleansedState[hand] = currentCleansedState[hand]!!.toMutableMap().apply { this[surface] = handSideState }
        }
        if (needsUpdate) {
            cleansedState.value = currentCleansedState
        }
    }

    private fun markSpecificPointsAsCleansed(hand: String, surface: String, pointsToCleanse: List<Int>) {
        val currentCleansedState = cleansedState.value.toMutableMap()
        val handSideState = currentCleansedState[hand]?.get(surface)?.toMutableList() ?: return
        var needsUpdate = false
        pointsToCleanse.forEach { pointIndex ->
            if (pointIndex < handSideState.size && !handSideState[pointIndex]) {
                handSideState[pointIndex] = true
                needsUpdate = true
            }
        }
        if (needsUpdate) {
            currentCleansedState[hand] = currentCleansedState[hand]!!.toMutableMap().apply { this[surface] = handSideState }
            cleansedState.value = currentCleansedState
        }
    }

    private fun checkContactForScenario(handLabel: String, points: List<PointF>, hull: List<PointF>, now: Long, targetSide: String, allowedKeypoints: List<Int>?) {
        if (allowedKeypoints != null && allowedKeypoints.isEmpty()) return

        val currentCleansedSide = cleansedState.value[handLabel]?.get(targetSide)?.toMutableList() ?: return
        val currentSwipeCounters = swipeCounters[handLabel]?.get(targetSide)?.toMutableList() ?: return
        val currentLastContactState = lastContactState[handLabel]?.get(targetSide)?.toMutableList() ?: return
        val currentContactTimers = contactTimers[handLabel]?.get(targetSide)?.toMutableList() ?: return
        var needsUpdate = false

        points.forEachIndexed { i, point ->
            if (currentCleansedSide[i]) {
                currentLastContactState[i] = isPointInPolygon(point, hull)
                return@forEachIndexed
            }

            val isInContact = isPointInPolygon(point, hull)
            val wasInContact = currentLastContactState[i]

            var cleansedByTime = false
            var cleansedBySwipe = false

            if (isInContact) {
                if (currentContactTimers[i] == 0L) {
                    currentContactTimers[i] = now
                }
                if (now - currentContactTimers[i] > CONTACT_TIME_THRESHOLD_MS) {
                    cleansedByTime = true
                }
            } else {
                currentContactTimers[i] = 0L
            }

            if (isInContact && !wasInContact) {
                currentSwipeCounters[i]++
            }

            if (currentSwipeCounters[i] >= SWIPE_THRESHOLD) {
                cleansedBySwipe = true
            }

            if (cleansedByTime || cleansedBySwipe) {
                if (allowedKeypoints == null || i in allowedKeypoints) {
                    currentCleansedSide[i] = true
                    needsUpdate = true
                }
            }
            currentLastContactState[i] = isInContact
        }

        if (needsUpdate) {
            val newCleansedState = cleansedState.value.toMutableMap().apply {
                this[handLabel] = this[handLabel]!!.toMutableMap().apply { this[targetSide] = currentCleansedSide }
            }
            cleansedState.value = newCleansedState
        }

        contactTimers[handLabel]!![targetSide] = currentContactTimers
        swipeCounters[handLabel]!![targetSide] = currentSwipeCounters
        lastContactState[handLabel]!![targetSide] = currentLastContactState
    }

    private fun resetAllContactStates() {
        for (hand in listOf("Left", "Right")) {
            for (side in listOf("palm", "back")) {
                for (i in 0..20) {
                    contactTimers[hand]!![side]!![i] = 0L
                    swipeCounters[hand]!![side]!![i] = 0
                    lastContactState[hand]!![side]!![i] = false
                }
            }
        }
    }

    private fun estimateAndStabilizeOrientation(landmarks: List<NormalizedLandmark>, label: String): Boolean {
        val rawIsPalm = estimatePalmDirection(landmarks, label)
        if (rawIsPalm == (stableOrientations[label] == "palm")) {
            orientationCounters[label] = 0
        } else {
            orientationCounters[label] = (orientationCounters[label] ?: 0) + 1
        }
        if ((orientationCounters[label] ?: 0) > ORIENTATION_CONFIRM_FRAMES) {
            stableOrientations[label] = if (rawIsPalm) "palm" else "back"
            orientationCounters[label] = 0
        }
        return stableOrientations[label] == "palm"
    }

    private fun estimatePalmDirection(landmarks: List<NormalizedLandmark>, label: String): Boolean {
        if (landmarks.size < 21) return true
        val wrist = landmarks[0]
        val indexMcp = landmarks[5]
        val pinkyMcp = landmarks[17]
        val v1x = indexMcp.x() - wrist.x()
        val v1y = indexMcp.y() - wrist.y()
        val v2x = pinkyMcp.x() - wrist.x()
        val v2y = pinkyMcp.y() - wrist.y()
        val normalZ = v1x * v2y - v1y * v2x
        return if (label == "Left") normalZ > 0 else normalZ < 0
    }

    private fun updateProgressAndFeedback() {
        val totalPointsPerSide = 42
        var washedPalmPoints = 0
        var washedBackPoints = 0

        cleansedState.value.forEach { (_, sideMap) ->
            washedPalmPoints += sideMap["palm"]?.count { it } ?: 0
            washedBackPoints += sideMap["back"]?.count { it } ?: 0
        }

        palmProgress.value = if (totalPointsPerSide > 0) washedPalmPoints.toFloat() / totalPointsPerSide else 0f
        backProgress.value = if (totalPointsPerSide > 0) washedBackPoints.toFloat() / totalPointsPerSide else 0f

        val totalWashedPoints = washedPalmPoints + washedBackPoints
        val totalPoints = totalPointsPerSide * 2
        totalProgress.value = if (totalPoints > 0) totalWashedPoints.toFloat() / totalPoints else 0f

        if (!_isWashingStarted.value) {
            feedbackMessage.value = "손을 화면에 보여주세요."
            return
        }

        var minPercentage = 101f
        var weakestZoneKey: String? = null
        WASH_ZONES.forEach { (key, zone) ->
            var washedCount = 0
            zone.keypoints.forEach { (sideKey, points) ->
                val (hand, surface) = sideKey.split('-')
                points.forEach { pointIndex ->
                    if (cleansedState.value[hand]?.get(surface)?.get(pointIndex) == true) {
                        washedCount++
                    }
                }
            }
            val percentage = if (zone.totalPoints > 0) (washedCount.toFloat() / zone.totalPoints) * 100 else 100f
            if (percentage < minPercentage) {
                minPercentage = percentage
                weakestZoneKey = key
            }
        }
        feedbackMessage.value = weakestZoneKey?.let { WASH_ZONES[it]?.recommendationText } ?: "골고루 씻어주세요"
    }

    private fun getConvexHull(points: List<PointF>): List<PointF> {
        if (points.size < 3) return emptyList()
        val sortedPoints = points.sortedWith(compareBy({ it.x }, { it.y }))
        val upper = mutableListOf<PointF>()
        val lower = mutableListOf<PointF>()
        for (point in sortedPoints) {
            while (lower.size >= 2 && crossProduct(lower[lower.size - 2], lower.last(), point) <= 0) lower.removeLast()
            lower.add(point)
        }
        for (point in sortedPoints.reversed()) {
            while (upper.size >= 2 && crossProduct(upper[upper.size - 2], upper.last(), point) <= 0) upper.removeLast()
            upper.add(point)
        }
        return lower.dropLast(1) + upper.dropLast(1)
    }

    private fun crossProduct(a: PointF, b: PointF, c: PointF): Float = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x)

    private fun isPointInPolygon(point: PointF, polygon: List<PointF>): Boolean {
        if (polygon.isEmpty()) return false
        var isInside = false
        var i = 0
        var j = polygon.size - 1
        while (i < polygon.size) {
            if ((polygon[i].y > point.y) != (polygon[j].y > point.y) &&
                (point.x < (polygon[j].x - polygon[i].x) * (point.y - polygon[i].y) / (polygon[j].y - polygon[i].y) + polygon[i].x)
            ) {
                isInside = !isInside
            }
            j = i++
        }
        return isInside
    }

    override fun onCleared() {
        super.onCleared()
        // Do not close the models here as they are managed by ModelLoader singleton
        // handLandmarker.close()
        // detector.close()
    }

    fun saveWashResult(
        context: Context,
        videoPath: String?,
        screenshotPath: String?
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentUser = _user.value ?: return@launch
            val currentCleansedState = cleansedState.value

            val sdf = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm:ss", Locale.getDefault())
            val dateString = sdf.format(Date(System.currentTimeMillis()))
            val elapsedTime = TOTAL_TIME_SECONDS - totalTime.value
            val progress = totalProgress.value * 100

            val textContent = buildString {
                appendLine("--- 손씻기 분석 결과 ---")
                appendLine("사용자: ${currentUser.name} (${currentUser.age}세)")
                appendLine("분석 시간: $dateString")
                appendLine("--------------------")
                appendLine("진행 시간: $elapsedTime 초")
                appendLine("전체 진행률: ${progress.toInt()}%")
                appendLine()
                appendLine("--- 세척된 키포인트 ---")
                appendLine("왼손 손바닥: ${getCleansedIndices(currentCleansedState["Left"]?.get("palm"))}")
                appendLine("오른손 손바닥: ${getCleansedIndices(currentCleansedState["Right"]?.get("palm"))}")
                appendLine("왼손 손등: ${getCleansedIndices(currentCleansedState["Left"]?.get("back"))}")
                appendLine("오른손 손등: ${getCleansedIndices(currentCleansedState["Right"]?.get("back"))}")
                appendLine()
                appendLine("--- 파일 경로 (내부 URI) ---")
                appendLine("영상: $videoPath")
                appendLine("스크린샷: $screenshotPath")
            }

            val metadataPath = BitmapUtils.saveWashResultAsText(context, textContent)

            val cleansedLeftPalm = currentCleansedState["Left"]?.get("palm")?.joinToString(",") ?: ""
            val cleansedRightPalm = currentCleansedState["Right"]?.get("palm")?.joinToString(",") ?: ""
            val cleansedLeftBack = currentCleansedState["Left"]?.get("back")?.joinToString(",") ?: ""
            val cleansedRightBack = currentCleansedState["Right"]?.get("back")?.joinToString(",") ?: ""

            val result = WashResultEntity(
                userId = currentUser.id,
                userName = currentUser.name,
                userAge = currentUser.age,
                elapsedTime = elapsedTime,
                totalProgress = totalProgress.value,
                cleansedLeftPalm = cleansedLeftPalm,
                cleansedRightPalm = cleansedRightPalm,
                cleansedLeftBack = cleansedLeftBack,
                cleansedRightBack = cleansedRightBack,
                videoPath = videoPath,
                screenshotPath = screenshotPath,
                metadataPath = metadataPath
            )
            val newResultId = washResultDao.insertWashResult(result)
        }
    }

    private fun getCleansedIndices(data: List<Boolean>?): String {
        if (data == null) return "없음"
        val indices = data.mapIndexedNotNull { index, cleansed -> if (cleansed) index else null }
        return if (indices.isNotEmpty()) indices.joinToString(", ") else "없음"
    }
}