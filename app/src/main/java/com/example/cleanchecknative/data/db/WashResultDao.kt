package com.example.cleanchecknative.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface WashResultDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWashResult(result: WashResultEntity): Long

    @Query("SELECT * FROM wash_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<WashResultEntity>>

    @Query("SELECT * FROM wash_results WHERE id = :resultId")
    suspend fun getResultById(resultId: Int): WashResultEntity?

    @Query("UPDATE wash_results SET is_uploaded = 1 WHERE id = :resultId")
    suspend fun markAsUploaded(resultId: Int)

    @Query("DELETE FROM wash_results WHERE id = :resultId")
    suspend fun deleteResultById(resultId: Int)

    /**
     * 백업되지 않은 (is_uploaded = 0) 모든 손 씻기 결과 목록을 반환합니다.
     */
    @Query("SELECT * FROM wash_results WHERE is_uploaded = 0")
    suspend fun getUploadPendingResults(): List<WashResultEntity>

    /**
     * 서버로부터 받은 백업 완료 ID 목록을 기반으로 로컬 데이터의 백업 상태를 업데이트합니다.
     * 서버에 있는 ID는 is_uploaded = 1로, 없는 ID는 is_uploaded = 0으로 설정합니다.
     */
    @Transaction
    @Query("UPDATE wash_results SET is_uploaded = (CASE WHEN (user_name || '_' || timestamp) IN (:backedUpIds) THEN 1 ELSE 0 END)")
    suspend fun syncBackupStatus(backedUpIds: List<String>)
}
