package com.example.cleanchecknative.ui.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import com.google.mediapipe.tasks.components.containers.Connection
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import kotlin.math.max

class OverlayView(context: Context?, attrs: AttributeSet?) :
    View(context, attrs) {

    private var poseResults: List<List<NormalizedLandmark>>? = null
    private var leftHandResults: List<NormalizedLandmark>? = null
    private var rightHandResults: List<NormalizedLandmark>? = null
    private var handRois: List<RectF> = emptyList()

    private val posePointPaint: Paint
    private val poseLinePaint: Paint
    private val handPointPaint: Paint
    private val handLinePaint: Paint
    private val roiPaint: Paint

    private var scaleFactor: Float = 1f
    private var imageWidth: Int = 1
    private var imageHeight: Int = 1

    init {
        posePointPaint = Paint().apply {
            color = Color.BLUE
            strokeWidth = 8f
            style = Paint.Style.FILL
        }
        poseLinePaint = Paint().apply {
            color = Color.WHITE
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        handPointPaint = Paint().apply {
            color = Color.YELLOW
            strokeWidth = 8f
            style = Paint.Style.FILL
        }
        handLinePaint = Paint().apply {
            color = Color.CYAN
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
        roiPaint = Paint().apply {
            color = Color.RED
            strokeWidth = 5f
            style = Paint.Style.STROKE
        }
    }

    fun clear() {
        poseResults = null
        leftHandResults = null
        rightHandResults = null
        handRois = emptyList()
        invalidate()
    }

    override fun draw(canvas: Canvas) {
        super.draw(canvas)

        // Draw Pose
        poseResults?.let {
            for (personLandmarks in it) {
                drawLandmarks(canvas, personLandmarks, posePointPaint)
                drawPoseConnections(canvas, personLandmarks, poseLinePaint)
            }
        }

        // Draw Hands
        leftHandResults?.let {
            drawLandmarks(canvas, it, handPointPaint)
            drawHandConnections(canvas, it, handLinePaint)
        }
        rightHandResults?.let {
            drawLandmarks(canvas, it, handPointPaint)
            drawHandConnections(canvas, it, handLinePaint)
        }

        // Draw ROIs for debugging
        for (roi in handRois) {
            val rect = RectF(
                roi.left * imageWidth * scaleFactor,
                roi.top * imageHeight * scaleFactor,
                roi.right * imageWidth * scaleFactor,
                roi.bottom * imageHeight * scaleFactor
            )
            canvas.drawRect(rect, roiPaint)
        }
    }

    private fun drawLandmarks(canvas: Canvas, landmarks: List<NormalizedLandmark>, paint: Paint) {
        for (landmark in landmarks) {
            canvas.drawPoint(
                landmark.x() * imageWidth * scaleFactor,
                landmark.y() * imageHeight * scaleFactor,
                paint
            )
        }
    }

    private fun drawHandConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>, paint: Paint) {
        for (connection in HandLandmarker.HAND_CONNECTIONS) {
            val start = landmarks.getOrNull(connection.start())
            val end = landmarks.getOrNull(connection.end())
            if (start != null && end != null) {
                canvas.drawLine(
                    start.x() * imageWidth * scaleFactor,
                    start.y() * imageHeight * scaleFactor,
                    end.x() * imageWidth * scaleFactor,
                    end.y() * imageHeight * scaleFactor,
                    paint
                )
            }
        }
    }

    private fun drawPoseConnections(canvas: Canvas, landmarks: List<NormalizedLandmark>, paint: Paint) {
        for (connection in POSE_CONNECTIONS) {
            val start = landmarks.getOrNull(connection.first)
            val end = landmarks.getOrNull(connection.second)
            if (start != null && end != null) {
                canvas.drawLine(
                    start.x() * imageWidth * scaleFactor,
                    start.y() * imageHeight * scaleFactor,
                    end.x() * imageWidth * scaleFactor,
                    end.y() * imageHeight * scaleFactor,
                    paint
                )
            }
        }
    }

    fun setResults(
        poseResults: List<List<NormalizedLandmark>>?,
        leftHandResults: List<NormalizedLandmark>?,
        rightHandResults: List<NormalizedLandmark>?,
        imageHeight: Int,
        imageWidth: Int,
        handRois: List<RectF> = emptyList()
    ) {
        this.poseResults = poseResults
        this.leftHandResults = leftHandResults
        this.rightHandResults = rightHandResults
        this.imageHeight = imageHeight
        this.imageWidth = imageWidth
        this.handRois = handRois

        scaleFactor = max(width * 1f / imageWidth, height * 1f / imageHeight)
        invalidate()
    }

    companion object {
        private val POSE_CONNECTIONS = setOf(
            Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7), Pair(0, 4),
            Pair(4, 5), Pair(5, 6), Pair(6, 8), Pair(9, 10), Pair(11, 12),
            Pair(11, 13), Pair(13, 15), Pair(15, 17), Pair(15, 19), Pair(15, 21),
            Pair(17, 19), Pair(12, 14), Pair(14, 16), Pair(16, 18), Pair(16, 20),
            Pair(16, 22), Pair(18, 20), Pair(11, 23), Pair(12, 24), Pair(23, 24),
            Pair(23, 25), Pair(25, 27), Pair(27, 29), Pair(29, 31), Pair(27, 31),
            Pair(24, 26), Pair(26, 28), Pair(28, 30), Pair(30, 32), Pair(28, 32)
        )
    }
}
