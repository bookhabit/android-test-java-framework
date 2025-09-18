package com.example.androidtest

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import android.os.Bundle
import android.telephony.TelephonyManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                NetworkInfoScreen()
            }
        }
    }
}

@Composable
fun NetworkInfoScreen() {
    val context = LocalContext.current

    var networkType by remember { mutableStateOf("Unknown") }
    var wifiInfo by remember { mutableStateOf("N/A") }
    var simInfo by remember { mutableStateOf("N/A") }

    LaunchedEffect(Unit) {
        // 1️⃣ ConnectivityManager
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        networkType = when {
            capabilities == null -> "No Connection"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Cellular"
            else -> "Other"
        }

        // 2️⃣ WifiManager
        val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val wifiInfoObj = wifiManager.connectionInfo
        wifiInfo = "SSID: ${wifiInfoObj.ssid}, RSSI: ${wifiInfoObj.rssi} dBm"

        // 3️⃣ TelephonyManager
        val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        simInfo = "SIM Operator: ${telephonyManager.networkOperatorName}, SIM State: ${telephonyManager.simState}"
    }

    Column(modifier = Modifier.padding(16.dp)) {
        Text("🌐 Network Info", style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Connection Type: $networkType")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Wi-Fi Info: $wifiInfo")
        Spacer(modifier = Modifier.height(8.dp))
        Text("Cellular Info: $simInfo")
    }
}
