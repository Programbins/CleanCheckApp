package com.example.cleanchecknative.domain

import android.content.Context
import android.graphics.Bitmap
import com.example.cleanchecknative.R
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorOperator
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import org.tensorflow.lite.support.tensorbuffer.TensorBufferFloat
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.Channels
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

class FaceNet(context: Context) {

    private val interpreter: Interpreter
    private val imageProcessor: ImageProcessor

    init {
        val interpreterOptions = Interpreter.Options().apply {
            setNumThreads(4)
            setUseNNAPI(true)
        }
        val modelBuffer = loadModelFile(context)
        interpreter = Interpreter(modelBuffer, interpreterOptions)

        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(IMAGE_SIZE, IMAGE_SIZE, ResizeOp.ResizeMethod.BILINEAR))
            .add(StandardizeOp())
            .build()
    }

    companion object {
        private const val MODEL_FILE = "MobileFaceNet.tflite"
        const val EMBEDDING_SIZE = 192
        private const val IMAGE_SIZE = 112
    }

    @Throws(IOException::class)
    private fun loadModelFile(context: Context): ByteBuffer {
        return FileUtil.loadMappedFile(context, MODEL_FILE)
    }

    fun getFaceEmbedding(bitmap: Bitmap): FloatArray {
        var tensorImage = TensorImage.fromBitmap(bitmap)
        tensorImage = imageProcessor.process(tensorImage)
        val outputEmbedding = Array(1) { FloatArray(EMBEDDING_SIZE) }
        interpreter.run(tensorImage.buffer, outputEmbedding)
        // L2 Normalization
        l2Normalize(outputEmbedding, 1e-10)
        return outputEmbedding[0]
    }

    private fun l2Normalize(embeddings: Array<FloatArray>, epsilon: Double) {
        for (i in embeddings.indices) {
            var sum = 0.0f
            for (j in embeddings[i].indices) {
                sum += embeddings[i][j] * embeddings[i][j]
            }
            val mag = sqrt(sum.toDouble()).toFloat()
            for (j in embeddings[i].indices) {
                embeddings[i][j] /= mag
            }
        }
    }

    fun getDistance(embedding1: FloatArray, embedding2: FloatArray): Float {
        var sum = 0.0f
        for (i in embedding1.indices) {
            sum += (embedding1[i] - embedding2[i]).pow(2)
        }
        return sqrt(sum)
    }

    fun close() {
        interpreter.close()
    }

    private class StandardizeOp : TensorOperator {
        override fun apply(p0: TensorBuffer?): TensorBuffer {
            val pixels = p0!!.floatArray
            val mean = pixels.average().toFloat()
            var std = sqrt(pixels.map { pi -> (pi - mean).pow(2) }.sum() / pixels.size.toFloat())
            std = max(std, 1f / sqrt(pixels.size.toFloat()))
            for (i in pixels.indices) {
                pixels[i] = (pixels[i] - mean) / std
            }
            val output = TensorBufferFloat.createFixedSize(p0.shape, DataType.FLOAT32)
            output.loadArray(pixels)
            return output
        }
    }
}
