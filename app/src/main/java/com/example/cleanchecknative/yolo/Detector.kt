package com.example.cleanchecknative.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.SystemClock
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.ops.CastOp
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import kotlin.math.max
import kotlin.math.min

class Detector(
    private val context: Context,
    private val modelPath: String,
    private val labelsPath: String,
    private var detectorListener: DetectorListener
) {

    private lateinit var interpreter: Interpreter
    private var labels = mutableListOf<String>()

    private var tensorWidth = 0
    private var tensorHeight = 0
    private var numChannel = 0
    private var numElements = 0

    private val imageProcessor = ImageProcessor.Builder()
        .add(NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
        .add(CastOp(INPUT_IMAGE_TYPE))
        .build()

    init {
        val options = Interpreter.Options().apply{
            addDelegate(NnApiDelegate())
        }

        try {
            val model = FileUtil.loadMappedFile(context, modelPath)
            interpreter = Interpreter(model, options)

            val inputShape = interpreter.getInputTensor(0)?.shape()
            val outputShape = interpreter.getOutputTensor(0)?.shape()

            if (inputShape != null) {
                tensorWidth = inputShape[1]
                tensorHeight = inputShape[2]

                if (inputShape[1] == 3) {
                    tensorWidth = inputShape[2]
                    tensorHeight = inputShape[3]
                }
            }

            if (outputShape != null) {
                numChannel = outputShape[1]
                numElements = outputShape[2]
            }

            try {
                val inputStream: InputStream = context.assets.open(labelsPath)
                val reader = BufferedReader(InputStreamReader(inputStream))

                var line: String? = reader.readLine()
                while (line != null && line != "") {
                    labels.add(line)
                    line = reader.readLine()
                }

                reader.close()
                inputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun setDetectorListener(listener: DetectorListener) {
        this.detectorListener = listener
    }

    fun restart() {
        interpreter.close()

        val options = Interpreter.Options().apply{
            addDelegate(NnApiDelegate())
        }

        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model, options)
    }

    fun close() {
        interpreter.close()
    }

    fun detect(frame: Bitmap) {
        if (!::interpreter.isInitialized) return
        if (tensorWidth == 0) return
        if (tensorHeight == 0) return
        if (numChannel == 0) return
        if (numElements == 0) return

        var inferenceTime = SystemClock.uptimeMillis()

        val letterboxBitmap = letterbox(frame)

        val tensorImage = TensorImage(INPUT_IMAGE_TYPE)
        tensorImage.load(letterboxBitmap)
        val processedImage = imageProcessor.process(tensorImage)
        val imageBuffer = processedImage.buffer

        val output = TensorBuffer.createFixedSize(intArrayOf(1, numChannel, numElements), OUTPUT_IMAGE_TYPE)
        interpreter.run(imageBuffer, output.buffer)

        val bestBoxes = bestBox(output.floatArray, frame.width, frame.height)
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime

        if (bestBoxes == null) {
            detectorListener.onEmptyDetect()
            return
        }

        detectorListener.onDetect(bestBoxes, inferenceTime)
    }

    private fun letterbox(source: Bitmap): Bitmap {
        val srcWidth = source.width
        val srcHeight = source.height
        val dstWidth = tensorWidth.toFloat()
        val dstHeight = tensorHeight.toFloat()

        val matrix = Matrix()
        val scale: Float
        var dx = 0f
        var dy = 0f

        if (srcWidth / dstWidth > srcHeight / dstHeight) {
            scale = dstWidth / srcWidth
            dy = (dstHeight - srcHeight * scale) / 2f
        } else {
            scale = dstHeight / srcHeight
            dx = (dstWidth - srcWidth * scale) / 2f
        }

        matrix.postScale(scale, scale)
        matrix.postTranslate(dx, dy)

        val letterboxBitmap = Bitmap.createBitmap(dstWidth.toInt(), dstHeight.toInt(), Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(letterboxBitmap)
        canvas.drawBitmap(source, matrix, null)
        return letterboxBitmap
    }

    private fun bestBox(array: FloatArray, originalWidth: Int, originalHeight: Int) : List<BoundingBox>? {
        val boundingBoxes = mutableListOf<BoundingBox>()
        val scaleW = tensorWidth.toFloat() / originalWidth
        val scaleH = tensorHeight.toFloat() / originalHeight
        val scale = min(scaleW, scaleH)

        for (c in 0 until numElements) {
            var maxConf = CONFIDENCE_THRESHOLD
            var maxIdx = -1
            var j = 4
            var arrayIdx = c + numElements * j
            while (j < numChannel){
                if (array[arrayIdx] > maxConf) {
                    maxConf = array[arrayIdx]
                    maxIdx = j - 4
                }
                j++
                arrayIdx += numElements
            }

            if (maxConf > CONFIDENCE_THRESHOLD) {
                val clsName = labels[maxIdx]
                val cx = array[c]
                val cy = array[c + numElements]
                val w = array[c + numElements * 2]
                val h = array[c + numElements * 3]

                val x1 = (cx - w/2F)
                val y1 = (cy - h/2F)
                val x2 = (cx + w/2F)
                val y2 = (cy + h/2F)

                val x1_orig = (x1 * tensorWidth - (tensorWidth - originalWidth * scale) / 2) / scale
                val y1_orig = (y1 * tensorHeight - (tensorHeight - originalHeight * scale) / 2) / scale
                val x2_orig = (x2 * tensorWidth - (tensorWidth - originalWidth * scale) / 2) / scale
                val y2_orig = (y2 * tensorHeight - (tensorHeight - originalHeight * scale) / 2) / scale

                if (x1_orig < 0F || x1_orig > originalWidth) continue
                if (y1_orig < 0F || y1_orig > originalHeight) continue
                if (x2_orig < 0F || x2_orig > originalWidth) continue
                if (y2_orig < 0F || y2_orig > originalHeight) continue

                boundingBoxes.add(
                    BoundingBox(
                        x1 = x1_orig / originalWidth,
                        y1 = y1_orig / originalHeight,
                        x2 = x2_orig / originalWidth,
                        y2 = y2_orig / originalHeight,
                        cx = (x1_orig + x2_orig) / (2 * originalWidth),
                        cy = (y1_orig + y2_orig) / (2 * originalHeight),
                        w = (x2_orig - x1_orig) / originalWidth,
                        h = (y2_orig - y1_orig) / originalHeight,
                        cnf = maxConf,
                        cls = maxIdx,
                        clsName = clsName
                    )
                )
            }
        }

        if (boundingBoxes.isEmpty()) return null

        return applyNMS(boundingBoxes)
    }

    private fun applyNMS(boxes: List<BoundingBox>) : MutableList<BoundingBox> {
        val sortedBoxes = boxes.sortedByDescending { it.cnf }.toMutableList()
        val selectedBoxes = mutableListOf<BoundingBox>()

        while(sortedBoxes.isNotEmpty()) {
            val first = sortedBoxes.first()
            selectedBoxes.add(first)
            sortedBoxes.remove(first)

            val iterator = sortedBoxes.iterator()
            while (iterator.hasNext()) {
                val nextBox = iterator.next()
                val iou = calculateIoU(first, nextBox)
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove()
                }
            }
        }

        return selectedBoxes
    }

    private fun calculateIoU(box1: BoundingBox, box2: BoundingBox): Float {
        val x1 = maxOf(box1.x1, box2.x1)
        val y1 = maxOf(box1.y1, box2.y1)
        val x2 = minOf(box1.x2, box2.x2)
        val y2 = minOf(box1.y2, box2.y2)
        val intersectionArea = maxOf(0F, x2 - x1) * maxOf(0F, y2 - y1)
        val box1Area = box1.w * box1.h
        val box2Area = box2.w * box2.h
        return intersectionArea / (box1Area + box2Area - intersectionArea)
    }

    interface DetectorListener {
        fun onEmptyDetect()
        fun onDetect(boundingBoxes: List<BoundingBox>, inferenceTime: Long)
    }

    companion object {
        private const val INPUT_MEAN = 0f
        private const val INPUT_STANDARD_DEVIATION = 255f
        private val INPUT_IMAGE_TYPE = DataType.FLOAT32
        private val OUTPUT_IMAGE_TYPE = DataType.FLOAT32
        private const val CONFIDENCE_THRESHOLD = 0.08F
        private const val IOU_THRESHOLD = 0.45F
    }
}

data class BoundingBox(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val cx: Float,
    val cy: Float,
    val w: Float,
    val h: Float,
    val cnf: Float,
    val cls: Int,
    val clsName: String
)
