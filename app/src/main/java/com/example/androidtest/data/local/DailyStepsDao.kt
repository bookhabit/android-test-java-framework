package com.example.androidtest.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyStepsDao {
    
    // 특정 날짜의 걸음수 조회
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    suspend fun getStepsByDate(date: String): DailyStepsEntity?
    
    // 특정 날짜의 걸음수 조회 (Flow)
    @Query("SELECT * FROM daily_steps WHERE date = :date")
    fun getStepsByDateFlow(date: String): Flow<DailyStepsEntity?>
    
    // 기간별 걸음수 조회
    @Query("SELECT * FROM daily_steps WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    suspend fun getStepsBetweenDates(startDate: String, endDate: String): List<DailyStepsEntity>
    
    // 기간별 걸음수 합계
    @Query("SELECT SUM(todaySteps) FROM daily_steps WHERE date BETWEEN :startDate AND :endDate")
    suspend fun getTotalStepsBetweenDates(startDate: String, endDate: String): Long?
    
    // 월별 걸음수 합계 (yyyy-MM 형식으로 조회)
    @Query("SELECT SUM(todaySteps) FROM daily_steps WHERE date LIKE :monthPrefix || '%'")
    suspend fun getMonthlySteps(monthPrefix: String): Long?
    
    // 데이터 삽입 또는 업데이트
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateSteps(dailySteps: DailyStepsEntity)
    
    // 걸음수 업데이트
    @Update
    suspend fun updateSteps(dailySteps: DailyStepsEntity)
    
    // 특정 날짜의 걸음수만 업데이트
    @Query("UPDATE daily_steps SET todaySteps = :todaySteps, timestamp = :timestamp WHERE date = :date")
    suspend fun updateStepsByDate(date: String, todaySteps: Long, timestamp: Long = System.currentTimeMillis())
    
    // 특정 날짜의 걸음수와 센서값 업데이트
    @Query("UPDATE daily_steps SET todaySteps = :todaySteps, sensorSteps = :sensorSteps, timestamp = :timestamp WHERE date = :date")
    suspend fun updateStepsAndSensorValue(date: String, todaySteps: Long, sensorSteps: Long, timestamp: Long = System.currentTimeMillis())
    
    // 모든 걸음수 데이터 조회 (최근 순)
    @Query("SELECT * FROM daily_steps ORDER BY date DESC")
    suspend fun getAllSteps(): List<DailyStepsEntity>
    
    // 최근 N일 걸음수 조회
    @Query("SELECT * FROM daily_steps ORDER BY date DESC LIMIT :limit")
    suspend fun getRecentSteps(limit: Int): List<DailyStepsEntity>
    
    // 특정 날짜 데이터 삭제
    @Query("DELETE FROM daily_steps WHERE date = :date")
    suspend fun deleteStepsByDate(date: String)
    
    // 모든 데이터 삭제
    @Query("DELETE FROM daily_steps")
    suspend fun deleteAllSteps()
}
