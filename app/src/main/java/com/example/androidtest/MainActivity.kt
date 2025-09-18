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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import android.content.Context
import com.example.androidtest.data.repository.DailyStepData
import com.example.androidtest.data.repository.StepCounterRepository
import kotlinx.coroutines.launch
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
                StepCounterApp(
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
fun StepCounterApp(
    checkPermission: () -> Boolean,
    requestPermission: () -> Unit
) {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    val repository = remember { StepCounterRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // 상태 변수들
    var todaySteps by remember { mutableStateOf(0L) }          // 오늘 총 걸음수 (DB + 실시간)
    var liveSteps by remember { mutableStateOf(0L) }           // 실시간 걸음수 증가분
    var currentSensorValue by remember { mutableStateOf(0L) }   // 현재 센서 값
    var baselineSteps by remember { mutableStateOf(-1L) }      // 앱 시작시 기준점
    var monthlySteps by remember { mutableStateOf(0L) }        // 이번 달 총 걸음수
    var recentData by remember { mutableStateOf<List<DailyStepData>>(emptyList()) }
    
    // 날짜 포맷터
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayDateString = remember { dateFormatter.format(Date()) }

    // 앱 시작시 초기화
    LaunchedEffect(Unit) {
        Log.d("StepCounter", "📱 앱 시작 - 권한 체크 및 데이터 로드")
        if (!checkPermission()) {
            Log.w("StepCounter", "❌ 권한이 없음 - 권한 요청")
            requestPermission()
        } else {
            Log.d("StepCounter", "✅ 권한 있음 - 데이터 로드 시작")
            
            // DB에서 오늘 걸음수 로드
            val savedTodaySteps = repository.getTodaySteps()
            todaySteps = savedTodaySteps
            Log.d("StepCounter", "📊 DB에서 로드된 오늘 걸음수: $savedTodaySteps")
            
            // 이번 달 걸음수 로드
            monthlySteps = repository.getCurrentMonthSteps()
            Log.d("StepCounter", "📊 이번 달 총 걸음수: $monthlySteps")
            
            // 최근 7일 데이터 로드
            recentData = repository.getRecentSteps(7)
            Log.d("StepCounter", "📊 최근 7일 데이터 로드: ${recentData.size}건")
        }
    }

    // 센서 리스너 설정
    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && checkPermission()) {
                    val currentStepCount = event.values[0].toLong()
                    currentSensorValue = currentStepCount
                    
                    Log.d("StepCounter", "👣 센서 데이터: $currentStepCount, 기준점: $baselineSteps")
                    
                    // 기준점 설정 (앱 시작시 또는 재부팅 후)
                    if (baselineSteps == -1L) {
                        // 기준점 = 현재 센서값 - DB에 저장된 오늘 걸음수
                        baselineSteps = currentStepCount - todaySteps
                        Log.d("StepCounter", "🎯 기준점 설정: $baselineSteps (센서: $currentStepCount - 저장된 걸음수: $todaySteps)")
                        return
                    }
                    
                    // 재부팅 감지 (센서값이 급격히 작아짐)
                    if (currentStepCount < baselineSteps) {
                        Log.d("StepCounter", "🔄 재부팅 감지 - 기준점 재설정")
                        baselineSteps = currentStepCount - todaySteps
                        return
                    }
                    
                    // 실시간 걸음수 계산
                    val calculatedTotalSteps = currentStepCount - baselineSteps
                    
                    if (calculatedTotalSteps >= 0 && calculatedTotalSteps != todaySteps) {
                        val previousTodaySteps = todaySteps
                        todaySteps = calculatedTotalSteps
                        liveSteps = calculatedTotalSteps - previousTodaySteps
                        
                        Log.d("StepCounter", "🚶 걸음수 업데이트: $todaySteps (증가: +$liveSteps)")
                        
                        // DB에 저장
                        coroutineScope.launch {
                            repository.saveTodaySteps(todaySteps)
                            // 월별 걸음수도 업데이트
                            monthlySteps = repository.getCurrentMonthSteps()
                        }
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                Log.d("StepCounter", "🎯 센서 정확도: $accuracy")
            }
        }

        // 센서 등록
        if (checkPermission()) {
            val stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
            if (stepSensor != null) {
                Log.d("StepCounter", "🔄 걸음 센서 등록")
                sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_UI)
            } else {
                Log.e("StepCounter", "❌ 걸음 센서 없음")
            }
        }

        onDispose {
            Log.d("StepCounter", "🔄 센서 리스너 해제")
            sensorManager.unregisterListener(listener)
        }
    }

    // UI 구성
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 제목
        Text(
            text = "🏃 Step Counter",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        
        // 메인 걸음수 카드
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "오늘 걸음수",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$todaySteps",
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (liveSteps > 0) {
                    Text(
                        text = "+$liveSteps (실시간)",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
        
        // 통계 카드들
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 이번 달 걸음수
            Card(
                modifier = Modifier.weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "이번 달",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$monthlySteps",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            // 센서 상태
            Card(
                modifier = Modifier.weight(1f),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "센서값",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$currentSensorValue",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 기능 버튼들
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    coroutineScope.launch {
                        Log.d("StepCounter", "🔄 수동 저장 버튼 클릭")
                        repository.saveTodaySteps(todaySteps)
                        monthlySteps = repository.getCurrentMonthSteps()
                        recentData = repository.getRecentSteps(7)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("저장")
            }
            
            Button(
                onClick = {
                    coroutineScope.launch {
                        Log.d("StepCounter", "🔄 데이터 새로고침")
                        todaySteps = repository.getTodaySteps()
                        monthlySteps = repository.getCurrentMonthSteps()
                        recentData = repository.getRecentSteps(7)
                    }
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("새로고침")
            }
            
            Button(
                onClick = {
                    Log.d("StepCounter", "🔄 초기화")
                    baselineSteps = -1L
                    liveSteps = 0L
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("초기화")
            }
        }
        
        // 최근 데이터 표시
        if (recentData.isNotEmpty()) {
            Text(
                text = "최근 7일 걸음수",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium
            )
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier.padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(recentData) { data ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = data.date,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "${data.steps} 걸음",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
        
        // 디버깅 정보
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    text = "디버깅 정보",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text("날짜: $todayDateString", fontSize = 10.sp)
                Text("기준점: $baselineSteps", fontSize = 10.sp)
                Text("센서값: $currentSensorValue", fontSize = 10.sp)
                Text("실시간 증가: +$liveSteps", fontSize = 10.sp)
            }
        }
    }
}