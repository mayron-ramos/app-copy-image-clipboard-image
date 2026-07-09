package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "copied_images")
data class CopiedImage(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val localFilePath: String,
    val mimeType: String,
    val originalFileName: String?,
    val timestamp: Long = System.currentTimeMillis(),
    val isCopied: Boolean = true,
    val sourcePackage: String? = null,
    val sourceAppName: String? = null
)

