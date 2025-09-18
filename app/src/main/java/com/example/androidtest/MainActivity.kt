package com.example.androidtest

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.Context
import java.text.SimpleDateFormat
import java.util.*


class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Log.d("StepCounter", "🔐 권한 요청 결과: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StepCounterScreen(
                    checkPermission = { checkPermission() },
                    requestPermission = { requestPermission() }
                )
            }
        }
    }

    private fun checkPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        requestPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }
}

@Composable
fun StepCounterScreen(
    checkPermission: () -> Boolean,
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val prefs = context.getSharedPreferences("step_prefs", Context.MODE_PRIVATE)

    var previousStepCount by remember { mutableStateOf(-1) }  // 앱 시작시점의 센서 수치
    var todaySteps by remember { mutableStateOf(0) }          // 오늘 하루 걸음수
    var currentSensorValue by remember { mutableStateOf(0) }   // 현재 센서 수치 (디버깅용)
    var lastSavedDate by remember { mutableStateOf("") }      // 마지막 저장 날짜

    // 오늘 날짜 문자열 (yyyy-MM-dd)
    fun getTodayDate(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    
    // 현재 시간이 자정(00:00)인지 확인
    fun isNewDay(lastDate: String): Boolean {
        val today = getTodayDate()
        return lastDate != today
    }

    // 앱 실행 시점: 권한 체크 후 데이터 초기화
    LaunchedEffect(Unit) {
        Log.d("StepCounter", "📱 앱 시작 - 권한 체크 시작")
        if (!checkPermission()) {
            Log.w("StepCounter", "❌ 권한이 없음 - 권한 요청")
            requestPermission()
        } else {
            Log.d("StepCounter", "✅ 권한 있음 - 데이터 복원 시작")
            val today = getTodayDate()
            Log.d("StepCounter", "📅 오늘 날짜: $today")
            
            val savedDate = prefs.getString("last_date", "")
            val savedPreviousStep = prefs.getInt("previous_step", -1)
            val savedTodaySteps = prefs.getInt("today_steps", 0)
            
            Log.d("StepCounter", "💾 저장된 데이터 - 마지막 날짜: $savedDate, 이전 센서값: $savedPreviousStep, 오늘 걸음수: $savedTodaySteps")

            if (isNewDay(savedDate ?: "")) {
                Log.d("StepCounter", "🌅 새로운 날 시작 - 걸음수 초기화")
                // 새로운 날이면 오늘 걸음수를 0으로 초기화
                todaySteps = 0
                previousStepCount = -1 // 센서값은 다시 설정될 예정
                lastSavedDate = today
                prefs.edit()
                    .putString("last_date", today)
                    .putInt("today_steps", 0)
                    .putInt("previous_step", -1)
                    .apply()
            } else {
                Log.d("StepCounter", "📊 같은 날 - 저장된 데이터 복원")
                // 같은 날이면 저장된 데이터 복원
                todaySteps = savedTodaySteps
                previousStepCount = savedPreviousStep
                lastSavedDate = savedDate ?: today
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && checkPermission()) {
                    val currentStepCount = event.values[0].toInt()
                    currentSensorValue = currentStepCount
                    
                    Log.d("StepCounter", "👣 센서 데이터 수신 - 현재 센서값: $currentStepCount, 저장된 이전값: $previousStepCount")
                    
                    // 날짜 변경 체크 (자정이 지났는지 확인)
                    // 단, previousStepCount가 -1이 아닐 때만 체크 (초기화 완료 후)
                    val today = getTodayDate()
                    if (previousStepCount != -1 && isNewDay(lastSavedDate)) {
                        Log.d("StepCounter", "🌅 자정이 지남 - 새로운 날 시작")
                        // 어제까지의 걸음수를 저장하고 오늘 걸음수 초기화
                        val yesterdaySteps = todaySteps
                        Log.d("StepCounter", "📊 어제 총 걸음수: $yesterdaySteps")
                        
                        todaySteps = 0
                        previousStepCount = currentStepCount // 새로운 기준점 설정
                        lastSavedDate = today
                        
                        prefs.edit()
                            .putString("last_date", today)
                            .putInt("today_steps", 0)
                            .putInt("previous_step", currentStepCount)
                            .apply()
                        
                        Log.d("StepCounter", "🔄 새로운 날 설정 완료 - 새 기준점: $currentStepCount")
                        return
                    }
                    
                    // 첫 실행인 경우 (앱 최초 실행 또는 데이터 초기화 후)
                    if (previousStepCount == -1) {
                        // 저장된 오늘 걸음수가 있다면, 그것을 유지하면서 기준점을 계산
                        val savedTodaySteps = prefs.getInt("today_steps", 0)
                        if (savedTodaySteps > 0) {
                            // 기존 걸음수를 유지하면서 기준점 계산: 현재 센서값 - 저장된 걸음수
                            previousStepCount = currentStepCount - savedTodaySteps
                            todaySteps = savedTodaySteps
                            Log.d("StepCounter", "🔄 앱 재시작 - 기존 걸음수 복원: $savedTodaySteps, 기준점: $previousStepCount")
                        } else {
                            // 새로운 시작
                            previousStepCount = currentStepCount
                            todaySteps = 0
                            Log.d("StepCounter", "🎯 새로운 시작 - 기준점 설정: $previousStepCount")
                        }
                        prefs.edit()
                            .putInt("previous_step", previousStepCount)
                            .putInt("today_steps", todaySteps)
                            .apply()
                        return
                    }
                    
                    // 핸드폰 재부팅 감지 (센서값이 이전값보다 현저히 작으면 재부팅됨)
                    // 단, 저장된 걸음수보다 현재 센서값이 작을 때만 재부팅으로 판단
                    if (currentStepCount < previousStepCount && currentStepCount < todaySteps) {
                        Log.d("StepCounter", "🔄 재부팅 감지 - 센서값 초기화됨 (이전: $previousStepCount, 현재: $currentStepCount)")
                        // 재부팅 시에는 현재까지의 걸음수를 유지하고, 새로운 기준점을 설정
                        previousStepCount = currentStepCount - todaySteps
                        Log.d("StepCounter", "📊 재부팅 후 기준점 재계산: $previousStepCount")
                        prefs.edit().putInt("previous_step", previousStepCount).apply()
                        return
                    }
                    
                    // 정상적인 걸음수 계산 (현재 센서값 - 오늘 시작시점 센서값)
                    val calculatedTodaySteps = currentStepCount - previousStepCount
                    
                    if (calculatedTodaySteps >= 0 && calculatedTodaySteps != todaySteps) {
                        todaySteps = calculatedTodaySteps
                        Log.d("StepCounter", "🚶 오늘 걸음수 업데이트: $todaySteps (현재: $currentStepCount - 시작: $previousStepCount)")
                        
                        // 데이터 저장
                        prefs.edit()
                            .putInt("today_steps", todaySteps)
                            .apply()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d("StepCounter", "🎯 센서 정확도 변경: $accuracy")
            }
        }

        if (checkPermission()) {
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepSensor != null) {
                Log.d("StepCounter", "🔄 걸음 센서 등록 시작")
                sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                Log.e("StepCounter", "❌ 걸음 센서를 찾을 수 없습니다")
            }
        } else {
            Log.w("StepCounter", "⚠️ 권한이 없어서 센서를 등록할 수 없습니다")
        }

        onDispose {
            Log.d("StepCounter", "🔄 센서 리스너 해제")
            sensorManager.unregisterListener(listener)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("🏃 Step Counter", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("오늘 걸은 걸음 수: $todaySteps")
        Spacer(Modifier.height(8.dp))
        Text("현재 센서 값: $currentSensorValue (디버깅용)")
        Spacer(Modifier.height(8.dp))
        Text("시작 기준점: $previousStepCount (디버깅용)")
        Spacer(Modifier.height(8.dp))
        Text("저장된 날짜: $lastSavedDate (디버깅용)")
        Spacer(Modifier.height(16.dp))
        
        Button(
            onClick = {
                Log.d("StepCounter", "🔄 데이터 초기화 버튼 클릭")
                previousStepCount = -1
                todaySteps = 0
                val today = getTodayDate()
                lastSavedDate = today
                prefs.edit()
                    .putString("last_date", today)
                    .putInt("previous_step", -1)
                    .putInt("today_steps", 0)
                    .apply()
                Log.d("StepCounter", "✅ 모든 데이터 초기화 완료")
            }
        ) {
            Text("데이터 초기화")
        }
    }
}
