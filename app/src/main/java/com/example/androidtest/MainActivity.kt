package com.example.androidtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                NetworkAndApiScreen()
            }
        }
    }
}

@Composable
fun NetworkAndApiScreen() {
    val context = LocalContext.current

    var networkType by remember { mutableStateOf("Unknown") }
    var apiResponse by remember { mutableStateOf("Loading...") }

    // 1️⃣ 네트워크 상태 확인
    LaunchedEffect(Unit) {
        networkType = getNetworkType(context)
    }

    // 2️⃣ 서버 요청
    LaunchedEffect(Unit) {
        apiResponse = fetchTodo()
    }

    Column(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        Text("🌐 Network Info", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connection Type: $networkType")
        Spacer(modifier = Modifier.height(16.dp))
        Text("📡 API Response", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text(apiResponse)
    }
}

// ------------------- Helper Functions -------------------

fun getNetworkType(context: Context): String {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val activeNetwork = connectivityManager.activeNetwork
    val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
    return when {
        capabilities == null -> "No Connection"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
        else -> "Other"
    }
}

suspend fun fetchTodo(): String = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val request = Request.Builder()
        .url("https://jsonplaceholder.typicode.com/todos/1")
        .build()
    try {
        val response = client.newCall(request).execute()
        if (response.isSuccessful) {
            response.body?.string() ?: "Empty Response"
        } else {
            "Error: ${response.code}"
        }
    } catch (e: IOException) {
        "Exception: ${e.message}"
    }
}