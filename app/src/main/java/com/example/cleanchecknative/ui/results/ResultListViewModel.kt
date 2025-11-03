package com.example.cleanchecknative.ui.results

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.*
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.WashResultEntity
import com.example.cleanchecknative.data.worker.BackupWorker
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class ResultListViewModel(application: Application) : AndroidViewModel(application) {

    private val washResultDao = AppDatabase.getDatabase(application).washResultDao()
    private val workManager = WorkManager.getInstance(application)

    val allResults: StateFlow<List<WashResultEntity>> = washResultDao.getAllResults()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _backupProgress = MutableStateFlow(0)
    val backupProgress: StateFlow<Int> = _backupProgress.asStateFlow()

    private val _isBackupRunning = MutableStateFlow(false)
    val isBackupRunning: StateFlow<Boolean> = _isBackupRunning.asStateFlow()


    init {
        observeBackupStatus()
    }

    fun startBackup() {
        if (_isBackupRunning.value) return

        val backupWorkRequest = OneTimeWorkRequestBuilder<BackupWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueueUniqueWork(
            "unique_backup_work",
            ExistingWorkPolicy.REPLACE,
            backupWorkRequest
        )
        observeBackupStatus()
    }

    private fun observeBackupStatus() {
        workManager.getWorkInfosForUniqueWorkLiveData("unique_backup_work")
            .observeForever { workInfos ->
                val workInfo = workInfos?.firstOrNull() ?: return@observeForever

                _isBackupRunning.value = when (workInfo.state) {
                    WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> true
                    else -> false
                }

                val progress = workInfo.progress.getInt(BackupWorker.Progress, 0)
                _backupProgress.value = progress

                if (workInfo.state == WorkInfo.State.SUCCEEDED) {
                    _backupProgress.value = 100
                }
            }
    }

    fun deleteResult(result: WashResultEntity) {
        viewModelScope.launch {
            washResultDao.deleteResultById(result.id)
        }
    }
}
