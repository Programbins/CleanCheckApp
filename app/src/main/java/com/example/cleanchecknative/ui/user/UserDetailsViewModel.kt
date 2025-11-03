package com.example.cleanchecknative.ui.user

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.cleanchecknative.data.db.AppDatabase
import com.example.cleanchecknative.data.db.UserEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class UserDetailsViewModel(application: Application) : AndroidViewModel(application) {

    private val userDao = AppDatabase.getDatabase(application).userDao()

    private val _user = MutableStateFlow<UserEntity?>(null)
    val user = _user.asStateFlow()

    fun loadUser(userId: String) {
        viewModelScope.launch {
            _user.value = userDao.getUserById(userId)
        }
    }

    fun saveOrUpdateUser(
        id: String?,
        name: String,
        age: Int,
        gender: String,
        photoPath: String,
        embedding: FloatArray?
    ) {
        viewModelScope.launch {
            if (id == null) {
                // New user
                if (embedding != null) {
                    val newUser = UserEntity(
                        name = name,
                        age = age,
                        gender = gender,
                        photoPath = photoPath,
                        embedding = embedding
                    )
                    userDao.insertUser(newUser)
                }
            } else {
                // Existing user
                val existingUser = userDao.getUserById(id)
                if (existingUser != null) {
                    val updatedUser = existingUser.copy(
                        name = name,
                        age = age,
                        gender = gender
                    )
                    userDao.updateUser(updatedUser)
                }
            }
        }
    }
}