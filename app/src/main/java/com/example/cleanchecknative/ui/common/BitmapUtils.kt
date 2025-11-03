package com.example.cleanchecknative.ui.common

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.view.PixelCopy
import android.view.Window
import android.widget.Toast
import androidx.camera.core.ImageProxy
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

object BitmapUtils {

    @SuppressLint("UnsafeOptInUsageError")
    fun toBitmap(imageProxy: ImageProxy): Bitmap? {
        val image = imageProxy.image ?: return null

        val yBuffer = image.planes[0].buffer
        val uBuffer = image.planes[1].buffer
        val vBuffer = image.planes[2].buffer

        val ySize = yBuffer.remaining()
        val uSize = uBuffer.remaining()
        val vSize = vBuffer.remaining()

        val nv21 = ByteArray(ySize + uSize + vSize)

        yBuffer.get(nv21, 0, ySize)
        vBuffer.get(nv21, ySize, vSize)
        uBuffer.get(nv21, ySize + vSize, uSize)

        val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        var bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

        // Rotate the bitmap if needed
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees
        if (rotationDegrees != 0) {
            val matrix = Matrix()
            matrix.postRotate(rotationDegrees.toFloat())
            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
        return bitmap
    }

    fun cropBitmapWithPadding(bitmap: Bitmap, rect: Rect, paddingFactor: Float): Bitmap {
        val newWidth = (rect.width() * paddingFactor).toInt()
        val newHeight = (rect.height() * paddingFactor).toInt()

        val widthPadding = (newWidth - rect.width()) / 2
        val heightPadding = (newHeight - rect.height()) / 2

        val newLeft = rect.left - widthPadding
        val newTop = rect.top - heightPadding

        val finalRect = Rect(
            maxOf(0, newLeft),
            maxOf(0, newTop),
            minOf(bitmap.width, newLeft + newWidth),
            minOf(bitmap.height, newTop + newHeight)
        )

        return Bitmap.createBitmap(
            bitmap,
            finalRect.left,
            finalRect.top,
            finalRect.width(),
            finalRect.height()
        )
    }

    suspend fun captureWindow(window: Window): Bitmap {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            throw IllegalStateException("PixelCopy is not supported on this API level.")
        }

        return suspendCancellableCoroutine { continuation ->
            val view = window.decorView.rootView
            if (view.width == 0 || view.height == 0) {
                continuation.resumeWithException(IllegalStateException("View dimensions are zero, cannot create bitmap."))
                return@suspendCancellableCoroutine
            }
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)

            val locationOfViewInWindow = IntArray(2)
            view.getLocationInWindow(locationOfViewInWindow)

            val x = locationOfViewInWindow[0]
            val y = locationOfViewInWindow[1]
            val scope = Rect(x, y, x + view.width, y + view.height)

            PixelCopy.request(window, scope, bitmap, { copyResult ->
                if (copyResult == PixelCopy.SUCCESS) {
                    continuation.resume(bitmap)
                } else {
                    continuation.resumeWithException(RuntimeException("PixelCopy failed with result: $copyResult"))
                }
            }, Handler(Looper.getMainLooper()))
        }
    }

    fun saveBitmapAsImage(bitmap: Bitmap, activity: Activity): String? {
        val name = "CleanCheck-SCR-${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/CleanCheck")
            }
        }

        val resolver = activity.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { stream ->
                    if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                        activity.runOnUiThread {
                            Toast.makeText(activity, "스크린샷 압축에 실패했습니다.", Toast.LENGTH_LONG).show()
                        }
                        return null
                    }
                }
                return it.toString() // Success, return URI string
            } catch (e: Exception) {
                e.printStackTrace()
                activity.runOnUiThread {
                    Toast.makeText(activity, "스크린샷 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return null
            }
        }
        activity.runOnUiThread {
            Toast.makeText(activity, "스크린샷 저장 경로를 만들지 못했습니다.", Toast.LENGTH_LONG).show()
        }
        return null
    }

    fun saveWashResultAsText(context: Context, resultData: String): String? {
        val name = "CleanCheck-Result-${System.currentTimeMillis()}.txt"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.MediaColumns.RELATIVE_PATH, "Documents/CleanCheck")
            }
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Files.getContentUri("external"), contentValues)

        uri?.let {
            try {
                resolver.openOutputStream(it)?.use { stream ->
                    stream.write(resultData.toByteArray())
                }
                return it.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                (context as? Activity)?.runOnUiThread {
                    Toast.makeText(context, "결과 파일 저장에 실패했습니다: ${e.message}", Toast.LENGTH_LONG).show()
                }
                return null
            }
        }
        (context as? Activity)?.runOnUiThread {
            Toast.makeText(context, "결과 파일 저장 경로를 만들지 못했습니다.", Toast.LENGTH_LONG).show()
        }
        return null
    }
}