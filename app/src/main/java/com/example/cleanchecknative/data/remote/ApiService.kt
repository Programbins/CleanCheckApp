package com.example.cleanchecknative.data.remote

import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.*

/**
 * 백업 서버와 통신하기 위한 API 서비스 인터페이스
 */
interface ApiService {

    /**
     * 서버의 상태를 확인합니다.
     */
    @GET("health")
    suspend fun checkHealth(): Response<Unit>

    /**
     * 서버에 백업이 완료된 데이터 ID 목록을 요청합니다.
     * ID 형식: "이름_타임스탬프"
     */
    @GET("completed-backups")
    suspend fun getCompletedBackups(): List<String>

    /**
     * 파일을 Multipart 형식으로 서버에 업로드합니다.
     */
    @Multipart
    @POST("upload")
    suspend fun uploadFile(
        @Part("user_name") userName: RequestBody,
        @Part("timestamp") timestamp: RequestBody,
        @Part("file_type") fileType: RequestBody, // "metadata", "video", "screenshot"
        @Part file: MultipartBody.Part
    ): Response<Unit>
}
