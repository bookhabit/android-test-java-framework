package com.example.androidtest

import android.content.pm.PackageManager
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

        val packageManager = packageManager

        setContent {
            MaterialTheme {
                InstalledAppsScreen(packageManager)
            }
        }
    }
}

@Composable
fun InstalledAppsScreen(packageManager: PackageManager) {
    data class AppInfo(val name: String, val version: String)

    var appList by remember { mutableStateOf(listOf<AppInfo>()) }

    LaunchedEffect(Unit) {
        val packages = packageManager.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val appsInfo = packages.map { pkg ->
            val appName = pkg.applicationInfo?.loadLabel(packageManager).toString()
            val appVersion = pkg.versionName ?: "Unknown"
            // val permissions = pkg.requestedPermissions?.joinToString(", ") ?: "No Permissions"
            AppInfo(name = appName, version = appVersion)
        }
        appList = appsInfo
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("📦 Installed Apps", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(appList) { app ->
                Row(modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)) {
                    Text(app.name, modifier = Modifier.weight(1f))
                    Text(app.version, modifier = Modifier.weight(0.5f))
                }
                Divider()
            }
        }
    }
}