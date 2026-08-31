package com.sundwaeji.jarvis.tools

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

data class ToolResult(val tool: String, val englishResponse: String)

/** Routes translated English commands to device or no-key network tools. */
class ToolRouter(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun handlesLocally(englishInput: String): Boolean {
        val command = englishInput.lowercase(Locale.UK)
        return command.contains("battery") || command.contains("time") ||
            command.contains("device") || command.contains("phone status") ||
            command.contains("weather") || command.contains("rain") ||
            command.contains("umbrella") || command.startsWith("search") ||
            command.startsWith("find") || command.contains("look up")
    }

    fun route(
        englishInput: String,
        onExecuting: (String) -> Unit,
        onSuccess: (ToolResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        val command = englishInput.lowercase(Locale.UK)
        when {
            command.contains("battery") -> executeLocal("DEVICE", onExecuting, onSuccess) {
                val value = (context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager)
                    .getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                ToolResult("DEVICE", if (value in 0..100) "Your battery is currently at $value percent." else "Battery information is currently unavailable.")
            }
            command.contains("time") -> executeLocal("TIME", onExecuting, onSuccess) {
                ToolResult("TIME", "It is currently ${SimpleDateFormat("h:mm a", Locale.UK).format(Date())}.")
            }
            command.contains("device") || command.contains("phone status") -> executeLocal("DEVICE", onExecuting, onSuccess) {
                ToolResult("DEVICE", "This is a ${Build.MANUFACTURER} ${Build.MODEL}, running Android ${Build.VERSION.RELEASE}, with ${networkLabel()} connectivity.")
            }
            command.contains("weather") || command.contains("rain") || command.contains("umbrella") -> {
                onExecuting("WEATHER")
                executeNetwork(onSuccess, onFailure) { fetchSeoulWeather() }
            }
            command.startsWith("search") || command.startsWith("find") || command.contains("look up") -> {
                onExecuting("SEARCH")
                val query = command.removePrefix("search for").removePrefix("search").removePrefix("find").replace("look up", "").trim()
                executeNetwork(onSuccess, onFailure) { fetchInstantAnswer(query) }
            }
            else -> onSuccess(ToolResult("LOCAL", "I understand. The cloud intelligence link is being prepared."))
        }
    }

    fun close() = executor.shutdownNow()

    private fun executeLocal(tool: String, onExecuting: (String) -> Unit, onSuccess: (ToolResult) -> Unit, block: () -> ToolResult) {
        onExecuting(tool)
        onSuccess(block())
    }

    private fun executeNetwork(onSuccess: (ToolResult) -> Unit, onFailure: (String) -> Unit, request: () -> ToolResult) {
        executor.execute {
            runCatching(request)
                .onSuccess { result -> main.post { onSuccess(result) } }
                .onFailure { main.post { onFailure("네트워크 도구를 실행하지 못했습니다. 연결을 확인해 주세요.") } }
        }
    }

    private fun fetchSeoulWeather(): ToolResult {
        val json = getJson("https://api.open-meteo.com/v1/forecast?latitude=37.5665&longitude=126.9780&current=temperature_2m,precipitation&daily=precipitation_probability_max&timezone=Asia%2FSeoul&forecast_days=1")
        val temperature = json.getJSONObject("current").getDouble("temperature_2m")
        val rainChance = json.getJSONObject("daily").getJSONArray("precipitation_probability_max").optInt(0, 0)
        val advice = if (rainChance >= 40) "I recommend taking an umbrella." else "An umbrella is unlikely to be necessary."
        return ToolResult("WEATHER", "In Seoul, it is ${temperature.toInt()} degrees Celsius. The chance of rain is $rainChance percent. $advice")
    }

    private fun fetchInstantAnswer(query: String): ToolResult {
        if (query.isBlank()) return ToolResult("SEARCH", "Please tell me what you would like me to search for.")
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = getJson("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&skip_disambig=1")
        val answer = json.optString("AbstractText").ifBlank { json.optString("Answer") }
        return ToolResult("SEARCH", answer.ifBlank { "I could not find a concise result for that search." })
    }

    private fun getJson(address: String): JSONObject {
        val connection = URL(address).openConnection() as HttpURLConnection
        return try {
            connection.connectTimeout = 8_000
            connection.readTimeout = 8_000
            connection.setRequestProperty("User-Agent", "JARVIS-Mobile/1.0-RC")
            if (connection.responseCode !in 200..299) error("HTTP ${connection.responseCode}")
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun networkLabel(): String {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return "no active network"
        return when {
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "mobile data"
            else -> "network"
        }
    }
}
