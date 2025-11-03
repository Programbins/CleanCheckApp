package com.example.cleanchecknative.data.worker

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.cleanchecknative.data.BackupRepository
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.WashResultEntity
import com.example.cleanchecknative.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.IOException

class BackupWorker(
    val appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    private val washResultDao = AppDatabase.getDatabase(appContext).washResultDao()
    private val backupRepository = BackupRepository(washResultDao)
    private val apiService = ApiClient.instance

    companion object {
        const val Progress = "Progress"
        private const val TAG = "BackupWorker"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "백업 작업을 시작합니다.")
            backupRepository.syncWithServer()

            val backupQueue = backupRepository.getBackupQueue()
            val totalItems = backupQueue.size
            if (totalItems == 0) {
                Log.d(TAG, "백업할 항목이 없습니다. 작업을 성공적으로 마칩니다.")
                setProgress(workDataOf(Progress to 100))
                return@withContext Result.success()
            }
            Log.d(TAG, "총 ${totalItems}개의 항목을 백업합니다.")

            for ((index, item) in backupQueue.withIndex()) {
                Log.d(TAG, "항목 ${index + 1}/$totalItems 백업 중: ${item.userName}_${item.timestamp}")

                val isSuccess = uploadItem(item)

                if (isSuccess) {
                    washResultDao.markAsUploaded(item.id)
                    Log.d(TAG, "항목 ${item.id}가 성공적으로 업로드되어 DB에 반영했습니다.")
                    // 항목 하나 완료 시 진행률 업데이트
                    val progress = ((index + 1) * 100 / totalItems)
                    setProgress(workDataOf(Progress to progress))
                } else {
                    Log.e(TAG, "항목 ${item.id} 업로드 실패. 작업을 중단하고 실패로 처리합니다.")
                    return@withContext Result.failure()
                }
            }

            Log.d(TAG, "모든 백업 작업을 성공적으로 완료했습니다.")
            setProgress(workDataOf(Progress to 100))
            Result.success()

        } catch (e: Exception) {
            Log.e(TAG, "백업 작업 중 심각한 오류 발생", e)
            Result.failure()
        }
    }

    private suspend fun uploadItem(item: WashResultEntity): Boolean {
        val filesToUpload = listOf(
            Triple("metadata", item.metadataPath, "text/plain"),
            Triple("video", item.videoPath, "video/mp4"),
            Triple("screenshot", item.screenshotPath, "image/jpeg")
        )

        for ((fileType, path, mediaType) in filesToUpload) {
            if (path == null) {
                Log.w(TAG, "항목 ${item.id}의 '${fileType}' 파일 경로가 null입니다. 건너뜁니다.")
                continue
            }

            val (fileName, requestBody) = createStreamingRequestBody(path, mediaType)
            if (fileName == null || requestBody == null) {
                Log.e(TAG, "파일을 읽을 수 없습니다: $path. 이 항목의 업로드를 중단합니다.")
                return false
            }

            val response = apiService.uploadFile(
                userName = item.userName.toRequestBody("text/plain".toMediaTypeOrNull()),
                timestamp = item.timestamp.toString().toRequestBody("text/plain".toMediaTypeOrNull()),
                fileType = fileType.toRequestBody("text/plain".toMediaTypeOrNull()),
                file = MultipartBody.Part.createFormData("file", fileName, requestBody)
            )

            if (!response.isSuccessful) {
                Log.e(TAG, "'${fileType}' 파일 업로드 실패: ${response.code()} - ${response.message()}")
                return false
            }
            Log.d(TAG, "'${fileType}' 파일 업로드 성공.")
        }
        return true
    }

    private fun createStreamingRequestBody(path: String, mediaType: String): Pair<String?, RequestBody?> {
        return try {
            val uri = Uri.parse(path)
            val contentResolver = appContext.contentResolver

            val fileName = when (mediaType) {
                "text/plain" -> "metadata.txt"
                "video/mp4" -> "video.mp4"
                "image/jpeg" -> "screenshot.jpg"
                else -> getFileName(uri) ?: path.substringAfterLast('/')
            }

            val requestBody = object : RequestBody() {
                override fun contentType(): MediaType? = mediaType.toMediaTypeOrNull()
                override fun contentLength(): Long =
                    contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: -1

                @Throws(IOException::class)
                override fun writeTo(sink: BufferedSink) {
                    contentResolver.openInputStream(uri)?.use { inputStream ->
                        sink.writeAll(inputStream.source())
                    }
                }
            }
            Pair(fileName, requestBody)
        } catch (e: Exception) {
            Log.e(TAG, "파일로부터 RequestBody 생성 실패", e)
            Pair(null, null)
        }
    }

    private fun getFileName(uri: Uri): String? {
        if (uri.scheme == "content") {
            appContext.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        return cursor.getString(nameIndex)
                    }
                }
            }
        }
        return uri.path?.substringAfterLast('/')
    }
}
