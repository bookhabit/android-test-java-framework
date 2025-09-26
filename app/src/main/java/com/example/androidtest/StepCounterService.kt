package com.example.androidtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.example.androidtest.data.repository.StepCounterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StepCounterService : Service(), SensorEventListener {
    
    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "step_counter_channel"
        private const val CHANNEL_NAME = "걸음 수 측정"
        private const val CHANNEL_DESCRIPTION = "걸음 수 측정 서비스 알림"
        
        fun startService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }
        
        fun stopService(context: Context) {
            val intent = Intent(context, StepCounterService::class.java)
            context.stopService(intent)
        }
    }
    
    private lateinit var sensorManager: SensorManager
    private lateinit var repository: StepCounterRepository
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // 센서 관련 변수들
    private var currentSensorValue = 0L
    private var baselineSteps = -1L
    private var todaySteps = 0L
    private var liveSteps = 0L
    
    override fun onCreate() {
        super.onCreate()
        Log.d("StepCounterService", "🔧 서비스 생성")
        
        // 초기화
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        repository = StepCounterRepository(this)
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        
        // 알림 채널 생성
        createNotificationChannel()
        
        // 센서 등록
        registerStepSensor()
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StepCounterService", "🚀 서비스 시작")
        
        // Foreground Service로 시작
        val notification = createNotification("걸음 수 측정 중...", "서비스가 시작되었습니다.")
        startForeground(NOTIFICATION_ID, notification)
        
        // 초기 데이터 로드
        serviceScope.launch {
            todaySteps = repository.getTodaySteps()
            Log.d("StepCounterService", "📊 DB에서 로드된 오늘 걸음수: $todaySteps")
        }
        
        return START_STICKY // 서비스가 종료되면 자동으로 재시작
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("StepCounterService", "🛑 서비스 종료")
        sensorManager.unregisterListener(this)
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_DESCRIPTION
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun createNotification(title: String, content: String): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setOngoing(true) // 사용자가 스와이프로 제거할 수 없음
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
    
    private fun updateNotification(title: String, content: String) {
        val notification = createNotification(title, content)
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun registerStepSensor() {
        val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        if (stepSensor != null) {
            Log.d("StepCounterService", "🔄 걸음 센서 등록")
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            Log.e("StepCounterService", "❌ 걸음 센서 없음")
        }
    }
    
    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
            val currentSensorSteps = event.values[0].toLong()
            currentSensorValue = currentSensorSteps
            
            Log.d("StepCounterService", "👣 센서 데이터: $currentSensorSteps")
            
            // 첫 센서 데이터 수신시 초기화
            if (baselineSteps == -1L) {
                serviceScope.launch {
                    // 날짜 변경 체크
                    val isNewDay = repository.handleDateChange()
                    if (isNewDay) {
                        Log.d("StepCounterService", "🌅 새로운 날 시작")
                    }
                    
                    // 오늘 데이터 초기화
                    todaySteps = repository.initializeTodayData(currentSensorSteps)
                    baselineSteps = currentSensorSteps
                    liveSteps = 0L
                    
                    Log.d("StepCounterService", "🎯 초기화 완료: todaySteps=$todaySteps, baselineSteps=$baselineSteps")
                    
                    // 알림 업데이트
                    updateNotification(
                        "걸음 수 측정 중",
                        "오늘 걸음수: $todaySteps 보"
                    )
                }
                return
            }
            
            // 재부팅 감지
            if (currentSensorSteps < baselineSteps) {
                Log.d("StepCounterService", "🔄 재부팅 감지")
                serviceScope.launch {
                    todaySteps = repository.initializeTodayData(currentSensorSteps)
                    baselineSteps = currentSensorSteps
                    liveSteps = 0L
                    Log.d("StepCounterService", "🔄 재부팅 후 초기화: todaySteps=$todaySteps, baselineSteps=$baselineSteps")
                }
                return
            }
            
            // 실시간 걸음수 계산
            val newLiveSteps = currentSensorSteps - baselineSteps
            
            if (newLiveSteps >= 0 && newLiveSteps != liveSteps) {
                liveSteps = newLiveSteps
                val totalSteps = todaySteps + liveSteps
                
                Log.d("StepCounterService", "🔢 실시간 계산: $currentSensorSteps - $baselineSteps = $newLiveSteps")
                Log.d("StepCounterService", "📊 총 걸음수: $totalSteps")
                
                // 알림 업데이트
                updateNotification(
                    "걸음 수 측정 중",
                    "오늘 걸음수: $totalSteps 보 (+$liveSteps 실시간)"
                )
                
                // DB에 저장 (10걸음마다)
                if (liveSteps > 0 && liveSteps % 10 == 0L) {
                    serviceScope.launch {
                        val totalStepsToSave = todaySteps + liveSteps
                        repository.saveTodaySteps(totalStepsToSave, currentSensorSteps)
                        todaySteps = totalStepsToSave
                        liveSteps = 0L
                        baselineSteps = currentSensorSteps
                        
                        Log.d("StepCounterService", "💾 DB 저장 완료: $totalStepsToSave, 새 기준점: $baselineSteps")
                        
                        // 알림 업데이트 (저장 후)
                        updateNotification(
                            "걸음 수 측정 중",
                            "오늘 걸음수: $totalStepsToSave 보"
                        )
                    }
                }
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        Log.d("StepCounterService", "🎯 센서 정확도: $accuracy")
    }
}
