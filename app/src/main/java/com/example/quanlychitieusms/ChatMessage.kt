package com.example.quanlychitieusms

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_history")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val text: String,
    val isAI: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)