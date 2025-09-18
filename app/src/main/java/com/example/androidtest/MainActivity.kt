package com.example.androidtest

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                StepCounterScreen()
            }
        }
    }
}

@Composable
fun StepCounterScreen() {
    val context = LocalContext.current
    val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    var baseline by remember { mutableStateOf(-1) }   // 앱 시작 시점 걸음 수
    var todaySteps by remember { mutableStateOf(0) }  // 오늘 걸음 수
    var liveSteps by remember { mutableStateOf(0) }   // 실시간 걸음 수

    DisposableEffect(Unit) {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                event ?: return
                if (event.sensor.type == Sensor.TYPE_STEP_COUNTER) {
                    val totalSteps = event.values[0].toInt()

                    if (baseline == -1) {
                        // 처음 실행 시 baseline 저장
                        baseline = totalSteps
                    }

                    // 오늘 걸음 수 = 현재 누적 - baseline
                    todaySteps = totalSteps - baseline

                    // 실시간 걸음 = 1 증가하는 값 → 그냥 totalSteps로부터 계산 가능
                    liveSteps++
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // 센서 등록
        sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)?.also {
            sensorManager.registerListener(listener, it, SensorManager.SENSOR_DELAY_UI)
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
