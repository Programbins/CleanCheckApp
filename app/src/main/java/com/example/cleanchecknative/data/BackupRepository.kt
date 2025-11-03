package com.example.cleanchecknative.data

import android.util.Log
import com.example.cleanchecknative.data.db.WashResultDao
import com.example.cleanchecknative.data.remote.ApiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 백업 관련 데이터 처리를 담당하는 Repository
 * 로컬 DB(DAO)와 원격 서버(API) 사이의 데이터 흐름을 관리합니다.
 */
class BackupRepository(private val washResultDao: WashResultDao) {

    private val apiService = ApiClient.instance

    /**
     * 서버와 로컬 DB의 백업 상태를 동기화합니다.
     * 1. 서버에서 백업 완료된 ID 목록을 가져옵니다.
     * 2. 로컬 DB의 모든 데이터에 대해 is_uploaded 상태를 업데이트합니다.
     */
    suspend fun syncWithServer() {
        withContext(Dispatchers.IO) {
            try {
                Log.d("BackupRepository", "서버와 백업 상태 동기화를 시작합니다.")
                val completedIds = apiService.getCompletedBackups()
                Log.d("BackupRepository", "서버로부터 ${completedIds.size}개의 백업 완료 목록을 받았습니다.")
                washResultDao.syncBackupStatus(completedIds)
                Log.d("BackupRepository", "로컬 DB 상태 동기화를 완료했습니다.")
            } catch (e: Exception) {
                Log.e("BackupRepository", "서버 동기화 중 오류 발생", e)
                // 네트워크 오류 등이 발생했을 때 예외 처리
            }
        }
    }

    /**
     * 백업해야 할 데이터 목록(백업 대기열)을 반환합니다.
     */
    suspend fun getBackupQueue() = withContext(Dispatchers.IO) {
        washResultDao.getUploadPendingResults()
    }
}
