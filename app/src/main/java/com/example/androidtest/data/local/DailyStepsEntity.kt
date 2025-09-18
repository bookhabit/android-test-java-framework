package com.example.androidtest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey
    val date: String, // "yyyy-MM-dd" 형식
    val steps: Long,
    val timestamp: Long = System.currentTimeMillis()
)
