package com.example.androidtest.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date

@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey
    val date: String, // "yyyy-MM-dd" 형식
    val todaySteps: Long, // 앱에서 측정 시작한 시점부터의 오늘 걸음수
    val sensorSteps: Long, // 측정 시작 시점의 센서 걸음수 (부팅 후 누적)
    val timestamp: Long = System.currentTimeMillis()
)
