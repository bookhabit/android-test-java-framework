package com.example.androidtest

import android.app.ActivityManager
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        setContent {
            MaterialTheme {
                ActivityManagerScreen(activityManager)
            }
        }
    }
}

@Composable
fun ActivityManagerScreen(activityManager: ActivityManager) {
    var runningProcesses by remember { mutableStateOf(listOf<ActivityManager.RunningAppProcessInfo>()) }
    var recentTasks by remember { mutableStateOf(listOf<ActivityManager.RecentTaskInfo>()) }
    var memoryInfoText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        // 현재 실행 중인 앱 프로세스
        runningProcesses = activityManager.runningAppProcesses ?: emptyList()

        // 최근 태스크 (최신 5개)
        recentTasks = try {
            @Suppress("DEPRECATION")
            activityManager.getRecentTasks(5, ActivityManager.RECENT_IGNORE_UNAVAILABLE) ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }

        // 메모리 상태
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        memoryInfoText = "Available Memory: ${memoryInfo.availMem / (1024 * 1024)} MB\n" +
                "Low Memory: ${memoryInfo.lowMemory}"
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("💻 Running Processes", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(runningProcesses) { process ->
                Text("Process: ${process.processName}\n PID: ${process.pid}\n Importance: ${process.importance}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("📝 Recent Tasks", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.height(150.dp)) {
            items(recentTasks) { task ->
                Text("Package: ${task.baseIntent?.component?.packageName}")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("📊 Memory Info", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(memoryInfoText)
    }
}
