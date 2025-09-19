package com.example.androidtest.data.repository

import android.content.Context
import android.util.Log
import com.example.androidtest.data.local.DailyStepsDao
import com.example.androidtest.data.local.DailyStepsEntity
import com.example.androidtest.data.local.StepCounterDatabase
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.*

class StepCounterRepository(context: Context) {
    
    private val database = StepCounterDatabase.getDatabase(context)
    private val dailyStepsDao: DailyStepsDao = database.dailyStepsDao()
    
    // 날짜 포맷터
    private val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    
    /**
     * 오늘 날짜의 데이터 초기화 또는 업데이트
     * @param currentSensorSteps 현재 센서에서 읽은 걸음수
     */
    suspend fun initializeTodayData(currentSensorSteps: Long): Long {
        val today = getTodayDateString()
        val existingData = dailyStepsDao.getStepsByDate(today)
        
        if (existingData == null) {
            // 새로운 날 - 데이터 생성
            val newData = DailyStepsEntity(
                date = today,
                todaySteps = 0L,
                sensorSteps = currentSensorSteps,
                timestamp = System.currentTimeMillis()
            )
            dailyStepsDao.insertOrUpdateSteps(newData)
            Log.d("StepRepository", "🆕 새로운 날 데이터 생성: date=$today, sensorSteps=$currentSensorSteps")
            return 0L
        } else {
            // 기존 날짜 - 백그라운드 걸음수 계산
            val backgroundSteps = currentSensorSteps - existingData.sensorSteps
            val newTodaySteps = existingData.todaySteps + backgroundSteps
            
            if (backgroundSteps > 0) {
                Log.d("StepRepository", "🚶 백그라운드 걸음 감지: +$backgroundSteps (센서: $currentSensorSteps - 저장된: ${existingData.sensorSteps})")
                
                // 백그라운드 걸음수 반영
                dailyStepsDao.updateStepsAndSensorValue(today, newTodaySteps, currentSensorSteps)
                return newTodaySteps
            } else if (backgroundSteps < 0) {
                // 재부팅 감지 - 센서값 업데이트만
                Log.d("StepRepository", "🔄 재부팅 감지 - 센서값 업데이트: $currentSensorSteps")
                dailyStepsDao.updateStepsAndSensorValue(today, existingData.todaySteps, currentSensorSteps)
                return existingData.todaySteps
            } else {
                // 변화 없음
                return existingData.todaySteps
            }
        }
    }
    
    /**
     * 오늘 걸음수를 DB에 저장
     */
    suspend fun saveTodaySteps(todaySteps: Long, currentSensorSteps: Long = 0L) {
        val today = getTodayDateString()
        val existingData = dailyStepsDao.getStepsByDate(today)
        
        if (existingData != null) {
            if (todaySteps > existingData.todaySteps) {
                dailyStepsDao.updateStepsAndSensorValue(today, todaySteps, currentSensorSteps)
                Log.d("StepRepository", "💾 오늘 걸음수 DB 업데이트: $todaySteps (센서: $currentSensorSteps)")
            }
        } else {
            val newData = DailyStepsEntity(
                date = today,
                todaySteps = todaySteps,
                sensorSteps = currentSensorSteps,
                timestamp = System.currentTimeMillis()
            )
            dailyStepsDao.insertOrUpdateSteps(newData)
            Log.d("StepRepository", "💾 오늘 걸음수 DB 저장: $todaySteps (센서: $currentSensorSteps)")
        }
    }
    
    /**
     * 오늘 걸음수 조회
     */
    suspend fun getTodaySteps(): Long {
        val today = getTodayDateString()
        return dailyStepsDao.getStepsByDate(today)?.todaySteps ?: 0L
    }
    
    /**
     * 오늘 센서 걸음수 조회
     */
    suspend fun getTodaySensorSteps(): Long {
        val today = getTodayDateString()
        return dailyStepsDao.getStepsByDate(today)?.sensorSteps ?: 0L
    }
    
    /**
     * 특정 날짜의 걸음수 조회
     */
    suspend fun getStepsByDate(date: String): Long {
        return dailyStepsDao.getStepsByDate(date)?.todaySteps ?: 0L
    }
    
    /**
     * 특정 날짜의 걸음수 조회 (Flow)
     */
    fun getTodayStepsFlow(): Flow<DailyStepsEntity?> {
        val today = getTodayDateString()
        return dailyStepsDao.getStepsByDateFlow(today)
    }
    
    /**
     * 기간별 걸음수 조회 (날짜별 상세 데이터)
     */
    suspend fun getDailyStepsInRange(startDate: String, endDate: String): List<DailyStepData> {
        val dbResults = dailyStepsDao.getStepsBetweenDates(startDate, endDate)
        val resultMap = dbResults.associateBy { it.date }
        
        // 기간 내 모든 날짜에 대해 데이터 생성 (걸음수가 없는 날도 0으로 포함)
        val result = mutableListOf<DailyStepData>()
        val calendar = Calendar.getInstance()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        
        calendar.time = sdf.parse(startDate) ?: Date()
        val endCalendar = Calendar.getInstance()
        endCalendar.time = sdf.parse(endDate) ?: Date()
        
        while (calendar.time <= endCalendar.time) {
            val dateString = sdf.format(calendar.time)
            val steps = resultMap[dateString]?.todaySteps ?: 0L
            
            result.add(DailyStepData(
                date = dateString,
                steps = steps
            ))
            
            calendar.add(Calendar.DAY_OF_MONTH, 1)
        }
        
        Log.d("StepRepository", "📊 기간별 조회: $startDate ~ $endDate → ${result.size}일 데이터")
        return result
    }
    
    /**
     * 기간별 총 걸음수 합계
     */
    suspend fun getTotalStepsInRange(startDate: String, endDate: String): Long {
        return dailyStepsDao.getTotalStepsBetweenDates(startDate, endDate) ?: 0L
    }
    
    /**
     * 월별 걸음수 합계
     */
    suspend fun getMonthlySteps(year: Int, month: Int): Long {
        val monthPrefix = String.format("%04d-%02d", year, month)
        return dailyStepsDao.getMonthlySteps(monthPrefix) ?: 0L
    }
    
    /**
     * 이번 달 걸음수 합계
     */
    suspend fun getCurrentMonthSteps(): Long {
        val calendar = Calendar.getInstance()
        return getMonthlySteps(calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH) + 1)
    }
    
    /**
     * 최근 N일 걸음수 조회
     */
    suspend fun getRecentSteps(days: Int): List<DailyStepData> {
        val recentData = dailyStepsDao.getRecentSteps(days)
        return recentData.map { entity ->
            DailyStepData(
                date = entity.date,
                steps = entity.todaySteps
            )
        }
    }
    
    /**
     * 모든 걸음수 데이터 조회
     */
    suspend fun getAllSteps(): List<DailyStepData> {
        val allData = dailyStepsDao.getAllSteps()
        return allData.map { entity ->
            DailyStepData(
                date = entity.date,
                steps = entity.todaySteps
            )
        }
    }
    
    /**
     * 특정 날짜 데이터 삭제
     */
    suspend fun deleteStepsByDate(date: String) {
        dailyStepsDao.deleteStepsByDate(date)
        Log.d("StepRepository", "🗑️ 날짜별 데이터 삭제: $date")
    }
    
    /**
     * 모든 데이터 삭제
     */
    suspend fun deleteAllSteps() {
        dailyStepsDao.deleteAllSteps()
        Log.d("StepRepository", "🗑️ 모든 걸음수 데이터 삭제")
    }
    
    /**
     * 날짜 변경 감지 및 처리
     */
    suspend fun handleDateChange(): Boolean {
        val today = getTodayDateString()
        val existingData = dailyStepsDao.getStepsByDate(today)
        
        if (existingData == null) {
            Log.d("StepRepository", "🌅 새로운 날 감지: $today")
            return true
        }
        return false
    }
    
    /**
     * 오늘 날짜 문자열 반환
     */
    private fun getTodayDateString(): String {
        return dateFormatter.format(Date())
    }
}

/**
 * 날짜별 걸음수 데이터 클래스
 */
data class DailyStepData(
    val date: String,
    val steps: Long
)