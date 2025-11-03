package com.example.cleanchecknative.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String = UUID.randomUUID().toString(),

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "age")
    val age: Int,

    @ColumnInfo(name = "gender")
    val gender: String,

    @ColumnInfo(name = "photo_path")
    val photoPath: String,

    @ColumnInfo(name = "embedding")
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as UserEntity
        if (id != other.id) return false
        if (name != other.name) return false
        if (age != other.age) return false
        if (gender != other.gender) return false
        if (photoPath != other.photoPath) return false
        if (!embedding.contentEquals(other.embedding)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + age
        result = 31 * result + gender.hashCode()
        result = 31 * result + photoPath.hashCode()
        result = 31 * result + embedding.contentHashCode()
        return result
    }
}
