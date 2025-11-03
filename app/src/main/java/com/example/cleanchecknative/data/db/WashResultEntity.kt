package com.example.cleanchecknative.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "wash_results",
    foreignKeys = [ForeignKey(
        entity = UserEntity::class,
        parentColumns = ["id"],
        childColumns = ["user_id"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class WashResultEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,

    @ColumnInfo(name = "user_id", index = true)
    val userId: String,

    @ColumnInfo(name = "user_name")
    val userName: String,

    @ColumnInfo(name = "user_age")
    val userAge: Int,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "elapsed_time")
    val elapsedTime: Int,

    @ColumnInfo(name = "total_progress")
    val totalProgress: Float,

    @ColumnInfo(name = "cleansed_left_palm")
    val cleansedLeftPalm: String,

    @ColumnInfo(name = "cleansed_right_palm")
    val cleansedRightPalm: String,

    @ColumnInfo(name = "cleansed_left_back")
    val cleansedLeftBack: String,

    @ColumnInfo(name = "cleansed_right_back")
    val cleansedRightBack: String,

    @ColumnInfo(name = "video_path")
    val videoPath: String?,

    @ColumnInfo(name = "screenshot_path")
    val screenshotPath: String?,

    @ColumnInfo(name = "metadata_path")
    val metadataPath: String?,

        @ColumnInfo(name = "is_uploaded", defaultValue = "0")
    val isUploaded: Boolean = false
)