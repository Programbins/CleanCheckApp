package com.example.cleanchecknative.ui.userlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserListViewModel(application: Application) : AndroidViewModel(application) {

    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val _users = MutableStateFlow<List<UserEntity>>(emptyList())
    val users = _users.asStateFlow()

    init {
        loadUsers()
    }

    fun loadUsers() {
        viewModelScope.launch {
            _users.value = userDao.getAllUsers()
        }
    }

    fun deleteUser(user: UserEntity) {
        viewModelScope.launch {
            userDao.deleteUser(user)
            loadUsers() // Refresh the list
        }
    }
}
