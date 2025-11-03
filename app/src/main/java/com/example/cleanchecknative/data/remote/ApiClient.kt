package com.example.cleanchecknative.data.remote

import com.example.cleanchecknative.BuildConfig
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Retrofit 인스턴스를 생성하고 관리하는 싱글톤 객체
 */
object ApiClient {

    val instance: ApiService by lazy {
        retrofit.create(ApiService::class.java)
    }

    // 네트워크 통신 로그를 확인하기 위한 인터셉터
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.HEADERS
    }

    // OkHttp 클라이언트 설정 (타임아웃 등)
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    // Retrofit 객체 생성
    private val retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.SERVER_URL) // local.properties에 정의된 서버 주소 사용
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
}