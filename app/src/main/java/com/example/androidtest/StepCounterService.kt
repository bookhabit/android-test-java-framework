package com.example.androidtest

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StepCounterService : Service() {
    
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
    
    private lateinit var notificationManager: NotificationManager
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    
    // StepCounterManager 인스턴스
    private lateinit var stepCounterManager: StepCounterManager
    
    override fun onCreate() {
        super.onCreate()
        Log.d("StepCounterService", "🔧 서비스 생성")
        
        // 초기화
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        stepCounterManager = StepCounterManager.getInstance(this, serviceScope)
        
        // 알림 채널 생성
        createNotificationChannel()
        
        // StepCounterManager 콜백 설정
        stepCounterManager.setCallback(object : StepCounterManager.StepCounterCallback {
            override fun onStepsUpdated(stepData: StepCounterManager.StepData) {
                updateNotification(
                    "걸음 수 측정 중",
                    "오늘 걸음수: ${stepData.todaySteps + stepData.liveSteps}보 (+${stepData.liveSteps} 실시간)"
                )
            }
            
            override fun onStepsSaved(totalSteps: Long) {
                updateNotification(
                    "걸음 수 측정 중",
                    "오늘 걸음수: ${totalSteps}보"
                )
            }
            
            override fun onNewDayDetected() {
                Log.d("StepCounterService", "🌅 새로운 날 감지됨")
            }
            
            override fun onRebootDetected() {
                Log.d("StepCounterService", "🔄 재부팅 감지됨")
            }
        })
    }
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("StepCounterService", "🚀 서비스 시작")
        
        // Foreground Service로 시작
        val notification = createNotification("걸음 수 측정 중...", "서비스가 시작되었습니다.")
        startForeground(NOTIFICATION_ID, notification)
        
        // StepCounterManager 초기화
        serviceScope.launch {
            stepCounterManager.initialize()
        }
        
        return START_STICKY // 서비스가 종료되면 자동으로 재시작
    }
    
    override fun onBind(intent: Intent?): IBinder? = null
    
    override fun onDestroy() {
        super.onDestroy()
        Log.d("StepCounterService", "🛑 서비스 종료")
        stepCounterManager.unregisterSensor()
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
}