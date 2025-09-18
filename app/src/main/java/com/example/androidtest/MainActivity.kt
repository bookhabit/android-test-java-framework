package com.example.androidtest

import android.Manifest
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
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


class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            // 권한 요청 결과 처리
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

    var baseline by remember { mutableStateOf(-1) }   // 오늘 걸음수 기준
    var todaySteps by remember { mutableStateOf(0) }  // 오늘 걸음 수
    var liveSteps by remember { mutableStateOf(0) }   // 실시간 걸음 수

    // 오늘 날짜 문자열 (yyyy-MM-dd)
    fun getTodayDate(): String = java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date())

    // 앱 실행 시점: 권한 체크 후 baseline 초기화
    LaunchedEffect(Unit) {
        if (!checkPermission()) {
            requestPermission()
        } else {
            val today = getTodayDate()
            print("today $today")
            val lastDate = prefs.getString("last_date", null)
            val savedBaseline = prefs.getInt("baseline", -1)

            if (lastDate != today) {
                // 날짜가 바뀌면 baseline 초기화
                baseline = -1
                prefs.edit().putString("last_date", today).apply()
            } else {
                baseline = savedBaseline
            }
        }
    }

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER && checkPermission()) {
                    val totalSteps = event.values[0].toInt()
                    print("totalSteps $totalSteps")
                    // baseline 설정: 앱 최초 실행 시 또는 날짜 변경 시
                    if (baseline == -1) {
                        baseline = totalSteps
                        prefs.edit().putInt("baseline", baseline).apply()
                    }

                    // 오늘 걸음 수 계산
                    todaySteps = totalSteps - baseline

                    // 실시간 걸음수 증가
                    liveSteps++
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        if (checkPermission()) {
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
                sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
            }
        }

        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("🏃 Step Counter", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text("오늘 걸은 걸음 수: $todaySteps")
        Spacer(Modifier.height(8.dp))
        Text("실시간 걸음 수: $liveSteps")
    }
}
