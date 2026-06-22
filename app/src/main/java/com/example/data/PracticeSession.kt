package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "practice_sessions")
data class PracticeSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val songTitle: String,
    val timestamp: Long,
    val accuracy: Float,
    val correctNotes: Int,
    val totalNotes: Int,
    val isCompleted: Boolean,
    val audioFilePath: String? = null,
    val username: String? = null // Associated user (null means Guest/Anonymous)
)
