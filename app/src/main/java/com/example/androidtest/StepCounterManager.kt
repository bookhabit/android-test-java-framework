package com.example.androidtest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.example.androidtest.data.repository.StepCounterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 걸음수 측정 로직을 통합 관리하는 매니저 클래스
 * MainActivity와 StepCounterService에서 공통으로 사용
 * 싱글톤 패턴으로 구현하여 데이터 동기화 보장
 */
class StepCounterManager private constructor(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) : SensorEventListener {
    
    private val repository = StepCounterRepository(context)
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    
    // 걸음수 측정 상태
    data class StepData(
        var todaySteps: Long = 0L,           // DB에 저장된 오늘 걸음수
        var liveSteps: Long = 0L,            // 실시간 걸음수 증가분
        var currentSensorValue: Long = 0L,   // 현재 센서 값
        var baselineSteps: Long = -1L,       // 앱 시작시 기준점
        var monthlySteps: Long = 0L          // 이번 달 총 걸음수
    )
    
    private val stepData = StepData()
    
    // 콜백 인터페이스
    interface StepCounterCallback {
        fun onStepsUpdated(stepData: StepData)
        fun onStepsSaved(totalSteps: Long)
        fun onNewDayDetected()
        fun onRebootDetected()
    }
    
    private var callback: StepCounterCallback? = null
    
    // 싱글톤 인스턴스
    companion object {
        @Volatile
        private var INSTANCE: StepCounterManager? = null
        
        fun getInstance(context: Context, coroutineScope: CoroutineScope): StepCounterManager {
            return INSTANCE ?: synchronized(this) {
                val instance = StepCounterManager(context.applicationContext, coroutineScope)
                INSTANCE = instance
                instance
            }
        }
    }
    
    // 초기화
    suspend fun initialize() {
        Log.d("StepCounterManager", "🔧 StepCounterManager 초기화")
        
        // DB에서 오늘 걸음수 로드
        stepData.todaySteps = repository.getTodaySteps()
        Log.d("StepCounterManager", "📊 DB에서 로드된 오늘 걸음수: ${stepData.todaySteps}")
        
        // 이번 달 걸음수 로드
        stepData.monthlySteps = repository.getCurrentMonthSteps()
        Log.d("StepCounterManager", "📊 이번 달 총 걸음수: ${stepData.monthlySteps}")
        
        // 센서 등록
        registerStepSensor()
    }
    
    // 센서 등록
    private fun registerStepSensor() {
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            Log.d("StepCounterManager", "🔄 걸음 센서 등록")
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.e("StepCounterManager", "❌ 걸음 센서 없음")
        }
    }
    
    // 센서 해제
    fun unregisterSensor() {
        Log.d("StepCounterManager", "🔄 센서 리스너 해제")
        sensorManager.unregisterListener(this)
    }
    
    // 콜백 설정
    fun setCallback(callback: StepCounterCallback) {
        this.callback = callback
    }
    
    // 현재 걸음수 데이터 가져오기
    fun getStepData(): StepData = stepData.copy()
    
    // UI에 표시할 총 걸음수
    fun getDisplaySteps(): Long = stepData.todaySteps + stepData.liveSteps
    
    // 수동 저장
    suspend fun saveSteps() {
        val totalStepsToSave = stepData.todaySteps + stepData.liveSteps
        repository.saveTodaySteps(totalStepsToSave, stepData.currentSensorValue)
        stepData.todaySteps = totalStepsToSave
        stepData.liveSteps = 0L
        stepData.baselineSteps = stepData.currentSensorValue
        
        Log.d("StepCounterManager", "💾 수동 저장 완료: $totalStepsToSave")
        
        // 월별 걸음수 업데이트
        stepData.monthlySteps = repository.getCurrentMonthSteps()
        
        callback?.onStepsSaved(totalStepsToSave)
    }
    
    // 데이터 새로고침
    suspend fun refreshData() {
        stepData.todaySteps = repository.getTodaySteps()
        stepData.liveSteps = 0L
        stepData.monthlySteps = repository.getCurrentMonthSteps()
        
        Log.d("StepCounterManager", "🔄 데이터 새로고침 완료")
    }
    
    // 초기화 (기준점 리셋)
    fun reset() {
        stepData.baselineSteps = -1L
        stepData.liveSteps = 0L
        Log.d("StepCounterManager", "🔄 초기화 완료")
    }
    
    // 센서 이벤트 처리
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorSteps = event.values[0].toLong()
            stepData.currentSensorValue = currentSensorSteps
            
            Log.d("StepCounterManager", "👣 센서 데이터: $currentSensorSteps")
            
            // 첫 센서 데이터 수신시 초기화
            if (stepData.baselineSteps == -1L) {
                coroutineScope.launch {
                    handleFirstSensorData(currentSensorSteps)
                }
                return
            }
            
            // 재부팅 감지 (센서값이 기준점보다 작아짐)
            if (currentSensorSteps < stepData.baselineSteps) {
                Log.d("StepCounterManager", "🔄 재부팅 감지")
                coroutineScope.launch {
                    handleRebootDetection(currentSensorSteps)
                }
                return
            }
            
            // 실시간 걸음수 계산
            val newLiveSteps = currentSensorSteps - stepData.baselineSteps
            
            Log.d("StepCounterManager", "🔢 실시간 계산: $currentSensorSteps - ${stepData.baselineSteps} = $newLiveSteps")
            Log.d("StepCounterManager", "📊 화면 표시: ${stepData.todaySteps} + $newLiveSteps = ${stepData.todaySteps + newLiveSteps}")
            
            if (newLiveSteps >= 0 && newLiveSteps != stepData.liveSteps) {
                stepData.liveSteps = newLiveSteps
                
                // 콜백으로 UI 업데이트
                callback?.onStepsUpdated(stepData)
                
                // DB에 저장 (10걸음마다)
                if (stepData.liveSteps > 0 && stepData.liveSteps % 10 == 0L) {
                    coroutineScope.launch {
                        handlePeriodicSave(currentSensorSteps)
                    }
                }
            }
        }
    }
    
    // 첫 센서 데이터 처리
    private suspend fun handleFirstSensorData(currentSensorSteps: Long) {
        // 날짜 변경 체크 및 백그라운드 걸음수 처리
        val isNewDay = repository.handleDateChange()
        if (isNewDay) {
            Log.d("StepCounterManager", "🌅 새로운 날 시작")
            callback?.onNewDayDetected()
        }
        
        // 오늘 데이터 초기화 (백그라운드 걸음수 포함)
        stepData.todaySteps = repository.initializeTodayData(currentSensorSteps)
        stepData.baselineSteps = currentSensorSteps
        stepData.liveSteps = 0L
        
        Log.d("StepCounterManager", "🎯 초기화 완료: todaySteps=${stepData.todaySteps}, baselineSteps=${stepData.baselineSteps}")
        
        // 월별 걸음수 업데이트
        stepData.monthlySteps = repository.getCurrentMonthSteps()
        
        // 콜백으로 UI 업데이트
        callback?.onStepsUpdated(stepData)
    }
    
    // 재부팅 감지 처리
    private suspend fun handleRebootDetection(currentSensorSteps: Long) {
        stepData.todaySteps = repository.initializeTodayData(currentSensorSteps)
        stepData.baselineSteps = currentSensorSteps
        stepData.liveSteps = 0L
        
        Log.d("StepCounterManager", "🔄 재부팅 후 초기화: todaySteps=${stepData.todaySteps}, baselineSteps=${stepData.baselineSteps}")
        
        callback?.onRebootDetected()
        callback?.onStepsUpdated(stepData)
    }
    
    // 주기적 저장 처리
    private suspend fun handlePeriodicSave(currentSensorSteps: Long) {
        val totalStepsToSave = stepData.todaySteps + stepData.liveSteps
        repository.saveTodaySteps(totalStepsToSave, currentSensorSteps)
        stepData.todaySteps = totalStepsToSave
        stepData.liveSteps = 0L
        stepData.baselineSteps = currentSensorSteps
        
        Log.d("StepCounterManager", "💾 DB 저장 완료: $totalStepsToSave, 새 기준점: ${stepData.baselineSteps}")
        
        // 월별 걸음수도 업데이트
        stepData.monthlySteps = repository.getCurrentMonthSteps()
        
        callback?.onStepsSaved(totalStepsToSave)
        callback?.onStepsUpdated(stepData)
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("StepCounterManager", "🎯 센서 정확도: $accuracy")
    }
}
