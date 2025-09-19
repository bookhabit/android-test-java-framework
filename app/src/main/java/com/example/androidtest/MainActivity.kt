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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDatePickerState
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

// 기간별 조회 결과 데이터 클래스
data class PeriodResult(
    val startDate: String,
    val endDate: String,
    val totalSteps: Long,
    val dailyData: List<DailyStepData>
)

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

@OptIn(ExperimentalMaterial3Api::class)
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
    var todaySteps by remember { mutableStateOf(0L) }          // DB에 저장된 오늘 걸음수
    var liveSteps by remember { mutableStateOf(0L) }           // 실시간 걸음수 증가분
    var currentSensorValue by remember { mutableStateOf(0L) }   // 현재 센서 값
    var baselineSteps by remember { mutableStateOf(-1L) }      // 앱 시작시 기준점
    var monthlySteps by remember { mutableStateOf(0L) }        // 이번 달 총 걸음수
    var recentData by remember { mutableStateOf<List<DailyStepData>>(emptyList()) }
    
    // DatePicker 관련 상태
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var showResultDialog by remember { mutableStateOf(false) }
    var selectedStartDate by remember { mutableStateOf<Long?>(null) }
    var selectedEndDate by remember { mutableStateOf<Long?>(null) }
    var periodResult by remember { mutableStateOf<PeriodResult?>(null) }
    
    val startDatePickerState = rememberDatePickerState()
    val endDatePickerState = rememberDatePickerState()
    
    // UI에 표시할 총 걸음수 (DB 저장값 + 실시간 증가분)
    val displaySteps = todaySteps + liveSteps
    
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
            todaySteps = repository.getTodaySteps()
            Log.d("StepCounter", "📊 DB에서 로드된 오늘 걸음수: $todaySteps")
            
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
                    val currentSensorSteps = event.values[0].toLong()
                    currentSensorValue = currentSensorSteps
                    
                    Log.d("StepCounter", "👣 센서 데이터: $currentSensorSteps")
                    
                    // 첫 센서 데이터 수신시 백그라운드 걸음수 처리
                    if (baselineSteps == -1L) {
                        coroutineScope.launch {
                            // 날짜 변경 체크 및 백그라운드 걸음수 처리
                            val isNewDay = repository.handleDateChange()
                            if (isNewDay) {
                                Log.d("StepCounter", "🌅 새로운 날 시작")
                            }
                            
                            // 오늘 데이터 초기화 (백그라운드 걸음수 포함)
                            todaySteps = repository.initializeTodayData(currentSensorSteps)
                            baselineSteps = currentSensorSteps
                            liveSteps = 0L
                            
                            Log.d("StepCounter", "🎯 초기화 완료: todaySteps=$todaySteps, baselineSteps=$baselineSteps")
                            
                            // 월별 걸음수 업데이트
                            monthlySteps = repository.getCurrentMonthSteps()
                        }
                        return
                    }
                    
                    // 재부팅 감지 (센서값이 기준점보다 작아짐)
                    if (currentSensorSteps < baselineSteps) {
                        Log.d("StepCounter", "🔄 재부팅 감지")
                        coroutineScope.launch {
                            todaySteps = repository.initializeTodayData(currentSensorSteps)
                            baselineSteps = currentSensorSteps
                            liveSteps = 0L
                            Log.d("StepCounter", "🔄 재부팅 후 초기화: todaySteps=$todaySteps, baselineSteps=$baselineSteps")
                        }
                        return
                    }
                    
                    // 실시간 걸음수 계산: 현재 센서값 - 기준점
                    val newLiveSteps = currentSensorSteps - baselineSteps
                    
                    Log.d("StepCounter", "🔢 실시간 계산: $currentSensorSteps - $baselineSteps = $newLiveSteps")
                    Log.d("StepCounter", "📊 화면 표시: $todaySteps + $newLiveSteps = ${todaySteps + newLiveSteps}")
                    
                    if (newLiveSteps >= 0 && newLiveSteps != liveSteps) {
                        liveSteps = newLiveSteps
                        
                        // DB에 저장 (10걸음마다)
                        if (liveSteps > 0 && liveSteps % 10 == 0L) {
                    coroutineScope.launch {
                                val totalStepsToSave = todaySteps + liveSteps
                                repository.saveTodaySteps(totalStepsToSave, currentSensorSteps)
                                todaySteps = totalStepsToSave
                                liveSteps = 0L
                                baselineSteps = currentSensorSteps // 새로운 기준점 설정
                                Log.d("StepCounter", "💾 DB 저장 완료: $totalStepsToSave, 새 기준점: $baselineSteps")
                                
                                // 월별 걸음수도 업데이트
                                monthlySteps = repository.getCurrentMonthSteps()
                            }
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
                    text = "$displaySteps",
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
                        val totalStepsToSave = todaySteps + liveSteps
                        repository.saveTodaySteps(totalStepsToSave, currentSensorValue)
                        todaySteps = totalStepsToSave
                        liveSteps = 0L
                        monthlySteps = repository.getCurrentMonthSteps()
                        recentData = repository.getRecentSteps(7)
                        Log.d("StepCounter", "💾 수동 저장 완료: $totalStepsToSave")
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
                        liveSteps = 0L
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
        
        // 기간별 조회 섹션
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "📅 기간별 걸음수 조회",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                // 날짜 선택 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showStartDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = selectedStartDate?.let { 
                                SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(it))
                            } ?: "시작일"
                        )
                    }
                    
                    Text(
                        text = "~",
                        modifier = Modifier.align(Alignment.CenterVertically)
                    )
                    
                    Button(
                        onClick = { showEndDatePicker = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = selectedEndDate?.let { 
                                SimpleDateFormat("MM/dd", Locale.getDefault()).format(Date(it))
                            } ?: "종료일"
                        )
                    }
                }
                
                // 조회 버튼
                Button(
                    onClick = {
                        if (selectedStartDate != null && selectedEndDate != null) {
                            coroutineScope.launch {
                                val startDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(selectedStartDate!!))
                                val endDateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(selectedEndDate!!))
                                
                                Log.d("StepCounter", "📊 기간별 조회 시작: $startDateStr ~ $endDateStr")
                                
                                // 기간별 데이터 조회
                                val dailyData = repository.getDailyStepsInRange(startDateStr, endDateStr)
                                val totalSteps = repository.getTotalStepsInRange(startDateStr, endDateStr)
                                
                                // 결과 저장
                                periodResult = PeriodResult(
                                    startDate = startDateStr,
                                    endDate = endDateStr,
                                    totalSteps = totalSteps,
                                    dailyData = dailyData
                                )
                                
                                // 콘솔 로그 출력
                                Log.d("StepCounter", "📊 =========================")
                                Log.d("StepCounter", "📊 기간별 걸음수 조회 결과")
                                Log.d("StepCounter", "📊 기간: $startDateStr ~ $endDateStr")
                                Log.d("StepCounter", "📊 총 걸음수: $totalSteps")
                                Log.d("StepCounter", "📊 일별 데이터:")
                                dailyData.forEach { data ->
                                    Log.d("StepCounter", "📊   ${data.date}: ${data.steps} 걸음")
                                }
                                Log.d("StepCounter", "📊 =========================")
                                
                                // 알림창 표시
                                showResultDialog = true
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = selectedStartDate != null && selectedEndDate != null
                ) {
                    Text("기간별 걸음수 조회")
                }
                
                // 선택된 기간 표시
                if (selectedStartDate != null && selectedEndDate != null) {
                    val startStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(selectedStartDate!!))
                    val endStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(selectedEndDate!!))
                    Text(
                        text = "선택된 기간: $startStr ~ $endStr",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
                Text("저장된 걸음수: $todaySteps", fontSize = 10.sp)
                Text("실시간 증가: +$liveSteps", fontSize = 10.sp)
                Text("화면 표시: $displaySteps", fontSize = 10.sp)
                Text("기준점: $baselineSteps", fontSize = 10.sp)
                Text("센서값: $currentSensorValue", fontSize = 10.sp)
            }
        }
    }
    
    // 시작일 DatePicker
    if (showStartDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedStartDate = startDatePickerState.selectedDateMillis
                        showStartDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showStartDatePicker = false }
                ) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = startDatePickerState)
        }
    }
    
    // 종료일 DatePicker
    if (showEndDatePicker) {
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        selectedEndDate = endDatePickerState.selectedDateMillis
                        showEndDatePicker = false
                    }
                ) {
                    Text("확인")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showEndDatePicker = false }
                ) {
                    Text("취소")
                }
            }
        ) {
            DatePicker(state = endDatePickerState)
        }
    }
    
    // 결과 알림창
    if (showResultDialog && periodResult != null) {
        AlertDialog(
            onDismissRequest = { showResultDialog = false },
            title = {
                Text("📊 기간별 걸음수 결과")
            },
            text = {
                Column {
                    Text("기간: ${periodResult!!.startDate} ~ ${periodResult!!.endDate}")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "총 걸음수: ${periodResult!!.totalSteps} 걸음",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "평균: ${periodResult!!.totalSteps / periodResult!!.dailyData.size} 걸음/일",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showResultDialog = false }
                ) {
                    Text("확인")
                }
            }
        )
    }
}