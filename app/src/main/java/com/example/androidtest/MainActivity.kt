package com.example.androidtest

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
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
    
    private val requestBodySensorsPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Log.d("StepCounter", "🫀 BODY_SENSORS 권한 요청 결과: $isGranted")
        }
    
    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            Log.d("StepCounter", "🔔 알림 권한 요청 결과: $isGranted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StepCounterApp(
                    checkPermission = { checkPermission() },
                    requestPermission = { requestPermission() },
                    checkBodySensorsPermission = { checkBodySensorsPermission() },
                    requestBodySensorsPermission = { requestBodySensorsPermission() },
                    checkNotificationPermission = { checkNotificationPermission() },
                    requestNotificationPermission = { requestNotificationPermission() },
                    startService = { startStepCounterService() },
                    stopService = { stopStepCounterService() }
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
    
    private fun checkBodySensorsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BODY_SENSORS
        ) == PackageManager.PERMISSION_GRANTED
    }
    
    private fun requestBodySensorsPermission() {
        requestBodySensorsPermissionLauncher.launch(Manifest.permission.BODY_SENSORS)
    }
    
    private fun checkNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 13 미만에서는 알림 권한이 필요 없음
        }
    }
    
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    
    private fun startStepCounterService() {
        Log.d("StepCounter", "🚀 Foreground Service 시작")
        StepCounterService.startService(this)
    }
    
    private fun stopStepCounterService() {
        Log.d("StepCounter", "🛑 Foreground Service 중지")
        StepCounterService.stopService(this)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepCounterApp(
    checkPermission: () -> Boolean,
    requestPermission: () -> Unit,
    checkBodySensorsPermission: () -> Boolean,
    requestBodySensorsPermission: () -> Unit,
    checkNotificationPermission: () -> Boolean,
    requestNotificationPermission: () -> Unit,
    startService: () -> Unit,
    stopService: () -> Unit
) {
    val context = LocalContext.current
    val repository = remember { StepCounterRepository(context) }
    val coroutineScope = rememberCoroutineScope()
    
    // StepCounterManager 싱글톤 인스턴스
    val stepCounterManager = remember { StepCounterManager.getInstance(context, coroutineScope) }

    // 상태 변수들
    var stepData by remember { mutableStateOf(StepCounterManager.StepData()) }
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
    val displaySteps = stepData.todaySteps + stepData.liveSteps
    
    // 날짜 포맷터
    val dateFormatter = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }
    val todayDateString = remember { dateFormatter.format(Date()) }

    // StepCounterManager 콜백 설정
    LaunchedEffect(Unit) {
        stepCounterManager.setCallback(object : StepCounterManager.StepCounterCallback {
            override fun onStepsUpdated(updatedStepData: StepCounterManager.StepData) {
                stepData = updatedStepData
            }
            
            override fun onStepsSaved(totalSteps: Long) {
                Log.d("StepCounter", "💾 걸음수 저장됨: $totalSteps")
            }
            
            override fun onNewDayDetected() {
                Log.d("StepCounter", "🌅 새로운 날 감지됨")
            }
            
            override fun onRebootDetected() {
                Log.d("StepCounter", "🔄 재부팅 감지됨")
            }
        })
    }

    // 앱 시작시 초기화
    LaunchedEffect(Unit) {
        Log.d("StepCounter", "📱 앱 시작 - 권한 체크 및 데이터 로드")
        
        // 만보기 앱에 필요한 권한 체크
        val hasActivityRecognition = checkPermission()
        val hasBodySensors = checkBodySensorsPermission()
        val hasNotification = checkNotificationPermission()
        
        if (!hasActivityRecognition) {
            Log.w("StepCounter", "❌ ACTIVITY_RECOGNITION 권한이 없음 - 권한 요청")
            requestPermission()
        } else if (!hasBodySensors) {
            Log.w("StepCounter", "❌ BODY_SENSORS 권한이 없음 - 권한 요청")
            requestBodySensorsPermission()
        } else if (!hasNotification) {
            Log.w("StepCounter", "❌ 알림 권한이 없음 - 권한 요청")
            requestNotificationPermission()
        } else {
            Log.d("StepCounter", "✅ 모든 권한 있음 - 데이터 로드 시작")
            
            // StepCounterManager는 서비스에서 초기화되므로 여기서는 데이터만 로드
            recentData = repository.getRecentSteps(7)
            Log.d("StepCounter", "📊 최근 7일 데이터 로드: ${recentData.size}건")
            
            // Foreground Service 자동 시작
            Log.d("StepCounter", "🚀 Foreground Service 자동 시작")
            startService()
        }
    }
    
    // 권한이 승인되면 서비스 시작
    LaunchedEffect(checkPermission(), checkBodySensorsPermission(), checkNotificationPermission()) {
        if (checkPermission() && checkBodySensorsPermission() && checkNotificationPermission()) {
            Log.d("StepCounter", "🚀 모든 권한 승인됨 - Foreground Service 시작")
            startService()
            
            // 서비스 시작 후 현재 걸음수 데이터 가져오기
            stepData = stepCounterManager.getStepData()
        }
    }

    // 주기적으로 걸음수 데이터 업데이트 (서비스에서 측정된 데이터 반영)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(1000) // 1초마다 업데이트
            stepData = stepCounterManager.getStepData()
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
                if (stepData.liveSteps > 0) {
                    Text(
                        text = "+${stepData.liveSteps} (실시간)",
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
                        text = "${stepData.monthlySteps}",
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
                        text = "${stepData.currentSensorValue}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
        
        // 서비스 상태 정보
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "🔔 Foreground Service 상태",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
                
                Text(
                    text = "✅ 서비스가 자동으로 실행 중입니다.\n알림센터에서 걸음 수 측정 상태를 확인할 수 있습니다.",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // 서비스 중지 버튼 (선택사항)
                Button(
                    onClick = { stopService() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text("서비스 중지 (선택사항)")
                }
                
                Text(
                    text = "💡 서비스를 중지하면 백그라운드에서 걸음 수 측정이 중단됩니다.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
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
                        stepCounterManager.saveSteps()
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
                        stepCounterManager.refreshData()
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
                    stepCounterManager.reset()
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
                Text("저장된 걸음수: ${stepData.todaySteps}", fontSize = 10.sp)
                Text("실시간 증가: +${stepData.liveSteps}", fontSize = 10.sp)
                Text("화면 표시: $displaySteps", fontSize = 10.sp)
                Text("기준점: ${stepData.baselineSteps}", fontSize = 10.sp)
                Text("센서값: ${stepData.currentSensorValue}", fontSize = 10.sp)
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