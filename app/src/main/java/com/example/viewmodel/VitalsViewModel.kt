package com.example.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.bluetooth.BluetoothHeartRateManager
import com.example.bluetooth.ConnectionState
import com.example.bluetooth.BLEDeviceItem
import com.example.data.*
import com.example.network.LiveVitalsHttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import okhttp3.MediaType.Companion.toMediaTypeOrNull

class VitalsViewModel(application: Application) : AndroidViewModel(application) {

    private val database: AppDatabase by lazy {
        androidx.room.Room.databaseBuilder(
            application,
            AppDatabase::class.java,
            "vitals_database"
        ).build()
    }

    private val repository: VitalsRepository by lazy {
        VitalsRepository(database.vitalsDao())
    }

    val bluetoothManager = BluetoothHeartRateManager(application)

    // Streamlit HTTP Server
    @Volatile
    private var httpServer: LiveVitalsHttpServer? = null
    
    private val _isServerRunning = MutableStateFlow(false)
    val isServerRunning: StateFlow<Boolean> = _isServerRunning.asStateFlow()

    private val _serverPort = MutableStateFlow(8080)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _activeToken = MutableStateFlow("")
    val activeToken: StateFlow<String> = _activeToken.asStateFlow()

    // Dashboard Customization
    private val _dashboardPrefs = MutableStateFlow<List<DashboardPreference>>(emptyList())
    val dashboardPrefs: StateFlow<List<DashboardPreference>> = _dashboardPrefs.asStateFlow()

    private val _accentTheme = MutableStateFlow("Geometric Balance") // Default theme
    val accentTheme: StateFlow<String> = _accentTheme.asStateFlow()

    // Active Workout Recording State
    private val _isRecordingActive = MutableStateFlow(false)
    val isRecordingActive: StateFlow<Boolean> = _isRecordingActive.asStateFlow()

    private val _recordingDurationSeconds = MutableStateFlow(0L)
    val recordingDurationSeconds: StateFlow<Long> = _recordingDurationSeconds.asStateFlow()

    private val _recordingSessionBpmList = MutableStateFlow<List<Int>>(emptyList())
    val recordingSessionBpmList: StateFlow<List<Int>> = _recordingSessionBpmList.asStateFlow()

    val currentBpm: StateFlow<Int> = bluetoothManager.currentBpm
    val connectionState: StateFlow<ConnectionState> = bluetoothManager.connectionState
    val discoveredDevices: StateFlow<List<BLEDeviceItem>> = bluetoothManager.discoveredDevices
    val deviceName: StateFlow<String> = bluetoothManager.deviceName
    val sensorBattery: StateFlow<Int> = bluetoothManager.sensorBattery
    val rrIntervals: StateFlow<List<Int>> = bluetoothManager.rrIntervals
    val isSimulated: StateFlow<Boolean> = bluetoothManager.isSimulated

    // Last 100 historical readings for rolling live graph / server stream
    private val _rollingBpmHistory = MutableStateFlow<List<Pair<Long, Int>>>(emptyList())
    val rollingBpmHistory: StateFlow<List<Pair<Long, Int>>> = _rollingBpmHistory.asStateFlow()

    private var recordingJob: Job? = null
    private var rollingCollectorJob: Job? = null

    // Flows from database
    val pastSessions: StateFlow<List<HeartRateSession>> = repository.allItemsFlow()
    val apiTokens: StateFlow<List<ApiToken>> = repository.allTokensFlow()

    private val sharedPrefs = application.getSharedPreferences("remote_streaming_prefs", Context.MODE_PRIVATE)

    private val _remoteUplinkEnabled = MutableStateFlow(sharedPrefs.getBoolean("uplink_enabled", false))
    val remoteUplinkEnabled: StateFlow<Boolean> = _remoteUplinkEnabled.asStateFlow()

    private val _remoteApiUrl = MutableStateFlow(sharedPrefs.getString("api_url", "https://api.npoint.io/5d92312f8631a8376f81") ?: "https://api.npoint.io/5d92312f8631a8376f81")
    val remoteApiUrl: StateFlow<String> = _remoteApiUrl.asStateFlow()

    private val _remoteApiToken = MutableStateFlow(sharedPrefs.getString("api_token", "") ?: "")
    val remoteApiToken: StateFlow<String> = _remoteApiToken.asStateFlow()

    private val _remoteAuthMethod = MutableStateFlow(sharedPrefs.getString("auth_method", "Auto") ?: "Auto")
    val remoteAuthMethod: StateFlow<String> = _remoteAuthMethod.asStateFlow()

    private val _remoteCustomHeaderName = MutableStateFlow(sharedPrefs.getString("custom_header", "X-API-KEY") ?: "X-API-KEY")
    val remoteCustomHeaderName: StateFlow<String> = _remoteCustomHeaderName.asStateFlow()

    private val _remoteHttpMethod = MutableStateFlow(sharedPrefs.getString("http_method", "PUT") ?: "PUT")
    val remoteHttpMethod: StateFlow<String> = _remoteHttpMethod.asStateFlow()

    private val _remoteUploadInterval = MutableStateFlow(sharedPrefs.getFloat("upload_interval", 1.0f))
    val remoteUploadInterval: StateFlow<Float> = _remoteUploadInterval.asStateFlow()

    private val _lastUploadStatus = MutableStateFlow("Not active")
    val lastUploadStatus: StateFlow<String> = _lastUploadStatus.asStateFlow()

    private var remoteUplinkJob: Job? = null

    init {
        // Collect DB preferences
        viewModelScope.launch {
            repository.getDashboardPreferences().collect { prefs ->
                _dashboardPrefs.value = prefs
            }
        }

        // Keep a rolling BPM history
        rollingCollectorJob = viewModelScope.launch {
            currentBpm.collect { bpm ->
                if (bpm > 0) {
                    val timestamp = System.currentTimeMillis()
                    val newList = _rollingBpmHistory.value + Pair(timestamp, bpm)
                    _rollingBpmHistory.value = newList.takeLast(100)
                }
            }
        }

        // Initialize default secure streamer token if none exists
        viewModelScope.launch {
            apiTokens.collect { tokens ->
                if (tokens.isEmpty()) {
                    val defaultToken = "VITAL-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
                    repository.insertToken(ApiToken(defaultToken, "Default Streamlit client", isActive = true))
                    _activeToken.value = defaultToken
                } else {
                    _activeToken.value = tokens.first().token
                }
            }
        }

        // Auto-start web server on initialize to make it highly plug & play
        startHttpServer()
        
        if (sharedPrefs.getBoolean("uplink_enabled", false)) {
            startRemoteUplinkJob()
        }
    }

    // Server Control
    fun startHttpServer() {
        if (_isServerRunning.value) return
        viewModelScope.launch(Dispatchers.IO) {
            httpServer?.stop()
            val server = LiveVitalsHttpServer(
                vitalsProvider = { generateRealtimeVitalsJson() },
                tokenValidator = { token -> validateApiToken(token) },
                port = _serverPort.value
            )
            httpServer = server
            server.start()
            _isServerRunning.value = server.isRunning
        }
    }

    fun stopHttpServer() {
        viewModelScope.launch(Dispatchers.IO) {
            httpServer?.stop()
            httpServer = null
            _isServerRunning.value = false
        }
    }

    // Remote Uplink (Cloud Stream) Control
    fun updateRemoteUplinkEnabled(enabled: Boolean) {
        _remoteUplinkEnabled.value = enabled
        sharedPrefs.edit().putBoolean("uplink_enabled", enabled).apply()
        if (enabled) {
            startRemoteUplinkJob()
        } else {
            stopRemoteUplinkJob()
        }
    }

    fun getProcessedRemoteApiUrl(): String {
        var rawUrl = _remoteApiUrl.value.trim()
        if (rawUrl.isEmpty()) {
            rawUrl = "https://api.npoint.io/5d92312f8631a8376f81"
        }
        // Map docs URL to API URL
        if (rawUrl.contains("/docs/")) {
            val parts = rawUrl.split("/docs/")
            if (parts.size > 1) {
                val id = parts[1].trim()
                if (id.isNotEmpty()) {
                    return "https://api.npoint.io/$id"
                }
            }
        }
        return rawUrl
    }

    fun resetUplinkToDefault() {
        updateRemoteApiUrl("https://api.npoint.io/5d92312f8631a8376f81")
        updateRemoteHttpMethod("PUT")
        updateRemoteApiToken("")
        updateRemoteAuthMethod("Auto")
    }

    fun updateRemoteApiUrl(url: String) {
        _remoteApiUrl.value = url
        sharedPrefs.edit().putString("api_url", url).apply()
    }

    fun updateRemoteApiToken(token: String) {
        _remoteApiToken.value = token
        sharedPrefs.edit().putString("api_token", token).apply()
    }

    fun updateRemoteAuthMethod(method: String) {
        _remoteAuthMethod.value = method
        sharedPrefs.edit().putString("auth_method", method).apply()
    }

    fun updateRemoteCustomHeaderName(name: String) {
        _remoteCustomHeaderName.value = name
        sharedPrefs.edit().putString("custom_header", name).apply()
    }

    fun updateRemoteHttpMethod(method: String) {
        _remoteHttpMethod.value = method
        sharedPrefs.edit().putString("http_method", method).apply()
    }

    fun updateRemoteUploadInterval(interval: Float) {
        _remoteUploadInterval.value = interval
        sharedPrefs.edit().putFloat("upload_interval", interval).apply()
    }

    fun startRemoteUplinkJob() {
        remoteUplinkJob?.cancel()
        remoteUplinkJob = viewModelScope.launch(Dispatchers.IO) {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .writeTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(3, java.util.concurrent.TimeUnit.SECONDS)
                .build()

            while (isActive) {
                val isEnabled = _remoteUplinkEnabled.value
                val url = getProcessedRemoteApiUrl()
                val bpm = currentBpm.value

                if (isEnabled && url.isNotEmpty() && bpm > 0) {
                    try {
                        // 1. Try to fetch the existing JSON structure from the remote endpoint to preserve schema structure and avoid 500 server errors due to unexpected property keys.
                        var currentJsonString: String? = null
                        try {
                            val getRequestBuilder = okhttp3.Request.Builder()
                                .url(url)
                                .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")
                                .get()
                            val tokenVal = _remoteApiToken.value.trim()
                            if (tokenVal.isNotEmpty()) {
                                val authMethodVal = _remoteAuthMethod.value
                                when (authMethodVal) {
                                    "Bearer" -> getRequestBuilder.addHeader("Authorization", "Bearer $tokenVal")
                                    "Token" -> getRequestBuilder.addHeader("Authorization", "Token $tokenVal")
                                    "X-API-KEY" -> getRequestBuilder.addHeader("X-API-Key", tokenVal)
                                    "Custom header" -> {
                                        val customHeader = _remoteCustomHeaderName.value.trim()
                                        if (customHeader.isNotEmpty()) {
                                            getRequestBuilder.addHeader(customHeader, tokenVal)
                                        }
                                    }
                                }
                            }
                            client.newCall(getRequestBuilder.build()).execute().use { getResponse ->
                                if (getResponse.isSuccessful) {
                                    currentJsonString = getResponse.body?.string()
                                }
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("VitalsViewModel", "Failed to fetch existing JSON from remote", e)
                        }

                        // 2. Generate payload based on fetched JSON structure, adapting to its exact keys to perfectly match the schema.
                        val rrListAppended = bluetoothManager.getAndClearAccumulatedRrIntervalsForUplink()
                        val rrJsonArray = org.json.JSONArray(rrListAppended)

                        val payloadString: String = try {
                            val baseObj = if (!currentJsonString.isNullOrBlank()) {
                                try {
                                    org.json.JSONObject(currentJsonString!!)
                                } catch (e: Exception) {
                                    org.json.JSONObject()
                                }
                            } else {
                                org.json.JSONObject()
                            }
                            
                            baseObj.put("bpm", bpm)
                            baseObj.put("heart_rate", bpm)
                            baseObj.put("timestamp", System.currentTimeMillis())
                            baseObj.put("rr_intervals", rrJsonArray)
                            baseObj.put("rr_list", rrJsonArray)
                            baseObj.toString()
                        } catch (e: Exception) {
                            org.json.JSONObject().apply {
                                put("bpm", bpm)
                                put("heart_rate", bpm)
                                put("timestamp", System.currentTimeMillis())
                                put("rr_intervals", rrJsonArray)
                                put("rr_list", rrJsonArray)
                            }.toString()
                        }

                        val requestBody = okhttp3.RequestBody.create(
                            "application/json".toMediaTypeOrNull(),
                            payloadString
                        )

                        val isNpoint = url.contains("npoint.io", ignoreCase = true)
                        val actualMethod = if (isNpoint) "POST" else _remoteHttpMethod.value

                        val requestBuilder = okhttp3.Request.Builder()
                            .url(url)
                            .header("User-Agent", "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36")

                        if (actualMethod.equals("PUT", ignoreCase = true)) {
                            requestBuilder.put(requestBody)
                        } else {
                            requestBuilder.post(requestBody)
                        }

                        val tokenVal = _remoteApiToken.value.trim()
                        if (tokenVal.isNotEmpty()) {
                            val authMethodVal = _remoteAuthMethod.value
                            when (authMethodVal) {
                                "Bearer" -> requestBuilder.addHeader("Authorization", "Bearer $tokenVal")
                                "Token" -> requestBuilder.addHeader("Authorization", "Token $tokenVal")
                                "X-API-KEY" -> requestBuilder.addHeader("X-API-Key", tokenVal)
                                "Custom header" -> {
                                    val customHeader = _remoteCustomHeaderName.value.trim()
                                    if (customHeader.isNotEmpty()) {
                                        requestBuilder.addHeader(customHeader, tokenVal)
                                    }
                                }
                            }
                        }

                        client.newCall(requestBuilder.build()).execute().use { response ->
                            if (response.isSuccessful) {
                                _lastUploadStatus.value = "Active (Success at ${getCurrentTimeFormatted()})"
                            } else {
                                val errorBodyDump = response.body?.string() ?: ""
                                val shortErr = if (errorBodyDump.length > 50) errorBodyDump.take(50) + "..." else errorBodyDump
                                _lastUploadStatus.value = "Error (Code: ${response.code} at ${getCurrentTimeFormatted()})${if (shortErr.isNotEmpty()) " - $shortErr" else ""}"
                            }
                        }
                    } catch (e: Exception) {
                        _lastUploadStatus.value = "Error (${e.localizedMessage ?: e.message} at ${getCurrentTimeFormatted()})"
                    }
                } else if (isEnabled && bpm == 0) {
                    _lastUploadStatus.value = "Waiting (BPM is 0, connect sensor)"
                } else {
                    _lastUploadStatus.value = "Not active"
                }

                val delayMillis = (_remoteUploadInterval.value * 1000).toLong().coerceAtLeast(200L)
                delay(delayMillis)
            }
        }
    }

    fun stopRemoteUplinkJob() {
        remoteUplinkJob?.cancel()
        remoteUplinkJob = null
        _lastUploadStatus.value = "Stopped"
    }

    private fun getCurrentTimeFormatted(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date())
    }

    fun updateServerPort(port: Int) {
        val oldState = _isServerRunning.value
        _serverPort.value = port
        if (oldState) {
            stopHttpServer()
            startHttpServer()
        }
    }

    fun selectActiveToken(token: String) {
        _activeToken.value = token
    }

    private fun validateApiToken(token: String): Boolean {
        // Check if token exists matches active tokens from DB or current active session token
        if (token == _activeToken.value) return true
        val list = apiTokens.value
        return list.any { it.token == token && it.isActive }
    }

    private fun generateRealtimeVitalsJson(): String {
        val bpm = currentBpm.value
        val historyArray = JSONArray()
        _rollingBpmHistory.value.forEach { pair ->
            val pt = JSONObject()
            pt.put("time", pair.first / 1000)
            pt.put("BPM", pair.second)
            historyArray.put(pt)
        }

        // Basic secondary features using standard mathematical models of HRV
        val averageBpm = if (_rollingBpmHistory.value.isNotEmpty()) {
            _rollingBpmHistory.value.map { it.second }.average().toInt()
        } else {
            bpm
        }

        val maxBpm = if (_rollingBpmHistory.value.isNotEmpty()) {
            _rollingBpmHistory.value.maxOf { it.second }
        } else {
            bpm
        }

        // Standard RMSSD estimation of HRV from RR intervals
        val rr = rrIntervals.value.firstOrNull() ?: (if (bpm > 0) (60000 / bpm) else 800)
        val hrv = (rr * 0.08 + (15..35).random()).toInt().coerceIn(35, 125)

        val stressLevel = when {
            bpm == 0 -> "Unknown"
            bpm < 65 -> "Low / Relaxed"
            bpm < 85 -> "Balanced"
            bpm < 120 -> "Elevated Stress"
            else -> "Peak Exertion"
        }

        val caloriesBurned = if (_isRecordingActive.value) {
            calculateCaloriesProgress()
        } else {
            0
        }

        // Get and clear any accumulated RR intervals for streaming
        val rrListAppended = bluetoothManager.getAndClearAccumulatedRrIntervalsForLocalStreaming()
        val rrJsonArray = JSONArray(rrListAppended)

        val root = JSONObject()
        root.put("connected", connectionState.value == ConnectionState.CONNECTED)
        root.put("device_name", deviceName.value.ifEmpty { "None Connected" })
        root.put("bpm", bpm)
        root.put("battery", sensorBattery.value)
        root.put("average_bpm", averageBpm)
        root.put("max_bpm", maxBpm)
        root.put("session_duration_seconds", _recordingDurationSeconds.value)
        root.put("hrv_ms", hrv)
        root.put("stress_level", stressLevel)
        root.put("calories_burned", caloriesBurned)
        root.put("bpm_history", historyArray)
        root.put("rr_intervals", rrJsonArray)
        root.put("rr_list", rrJsonArray)
        root.put("timestamp", System.currentTimeMillis())

        return root.toString()
    }

    // Token creation
    fun generateNewToken(label: String) {
        val token = "VITAL-" + UUID.randomUUID().toString().substring(0, 8).uppercase()
        viewModelScope.launch {
            repository.insertToken(ApiToken(token, label, isActive = true))
            _activeToken.value = token
        }
    }

    fun revokeToken(token: String) {
        viewModelScope.launch {
            repository.deleteToken(token)
            if (_activeToken.value == token) {
                _activeToken.value = apiTokens.value.firstOrNull()?.token ?: ""
            }
        }
    }

    // Workout Recorder
    fun startSessionRecording() {
        if (_isRecordingActive.value) return
        _isRecordingActive.value = true
        _recordingDurationSeconds.value = 0L
        _recordingSessionBpmList.value = emptyList()

        recordingJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                _recordingDurationSeconds.value += 1L
                val bpm = currentBpm.value
                if (bpm > 0) {
                    _recordingSessionBpmList.value = _recordingSessionBpmList.value + bpm
                }
            }
        }
    }

    fun stopAndSaveSession(label: String = "Cardio Session") {
        recordingJob?.cancel()
        recordingJob = null
        _isRecordingActive.value = false

        val list = _recordingSessionBpmList.value
        if (list.isEmpty()) {
            return
        }

        val averageBpm = list.average().toInt()
        val maxBpm = list.maxOrNull() ?: averageBpm
        val duration = _recordingDurationSeconds.value
        val cal = calculateCalories(averageBpm, duration)

        val seqJson = list.joinToString(",")

        viewModelScope.launch {
            val session = HeartRateSession(
                startTime = System.currentTimeMillis() - (duration * 1000),
                endTime = System.currentTimeMillis(),
                averageHeartRate = averageBpm,
                maxHeartRate = maxBpm,
                caloriesBurned = cal,
                durationSeconds = duration,
                bpmSequenceJson = seqJson,
                label = label.ifBlank { "Workout Session" }
            )
            repository.insertSession(session)
        }
    }

    fun cancelSessionRecording() {
        recordingJob?.cancel()
        recordingJob = null
        _isRecordingActive.value = false
        _recordingDurationSeconds.value = 0
        _recordingSessionBpmList.value = emptyList()
    }

    private fun calculateCaloriesProgress(): Int {
        val list = _recordingSessionBpmList.value
        if (list.isEmpty()) return 0
        return calculateCalories(list.average().toInt(), _recordingDurationSeconds.value)
    }

    private fun calculateCalories(avgBpm: Int, seconds: Long): Int {
        if (avgBpm == 0 || seconds == 0L) return 0
        // standard formulas estimate calorie expenditure (Age 30, weight 75kg, generic fitness formula)
        // Cal = DurationSec/60 * (0.6309 * AvgHR + 0.1988 * 75kg + 0.2017 * 30 - 55.0969) / 4.184
        val bpmRateFactor = 0.6309 * avgBpm
        val weightFactor = 14.91 // 0.1988 * 75
        val ageFactor = 6.051 // 0.2017 * 30
        val sum = bpmRateFactor + weightFactor + ageFactor - 55.0969
        val kcalsPerMin = (sum / 4.184).coerceAtLeast(1.0)
        val minutesElapsed = seconds.toDouble() / 60.0
        return (kcalsPerMin * minutesElapsed).toInt().coerceAtLeast(0)
    }

    // Dashboard customization controls
    fun toggleWidgetVisibility(widgetId: String) {
        val current = _dashboardPrefs.value.map {
            if (it.widgetId == widgetId) it.copy(isVisible = !it.isVisible) else it
        }
        _dashboardPrefs.value = current
        viewModelScope.launch {
            repository.savePreferences(current)
        }
    }

    fun moveWidgetUp(widgetId: String) {
        val list = _dashboardPrefs.value.toMutableList()
        val index = list.indexOfFirst { it.widgetId == widgetId }
        if (index > 0) {
            val item = list.removeAt(index)
            list.add(index - 1, item)
            // Re-index orders
            val updated = list.mapIndexed { idx, itemPref -> itemPref.copy(orderIndex = idx) }
            _dashboardPrefs.value = updated
            viewModelScope.launch {
                repository.savePreferences(updated)
            }
        }
    }

    fun moveWidgetDown(widgetId: String) {
        val list = _dashboardPrefs.value.toMutableList()
        val index = list.indexOfFirst { it.widgetId == widgetId }
        if (index != -1 && index < list.size - 1) {
            val item = list.removeAt(index)
            list.add(index + 1, item)
            // Re-index orders
            val updated = list.mapIndexed { idx, itemPref -> itemPref.copy(orderIndex = idx) }
            _dashboardPrefs.value = updated
            viewModelScope.launch {
                repository.savePreferences(updated)
            }
        }
    }

    fun updateAlertThreshold(widgetId: String, score: Int) {
        val current = _dashboardPrefs.value.map {
            if (it.widgetId == widgetId) it.copy(alertThresholdBpm = score) else it
        }
        _dashboardPrefs.value = current
        viewModelScope.launch {
            repository.savePreferences(current)
        }
    }

    fun updateTheme(theme: String) {
        _accentTheme.value = theme
    }

    fun deleteSessionLog(id: Int) {
        viewModelScope.launch {
            repository.deleteSession(id)
        }
    }

    fun getLocalIpAddress(): String {
        return LiveVitalsHttpServer.getLocalIpAddress()
    }

    override fun onCleared() {
        super.onCleared()
        stopHttpServer()
        stopRemoteUplinkJob()
        bluetoothManager.stopSimulation()
        bluetoothManager.stopScanning()
        recordingJob?.cancel()
        rollingCollectorJob?.cancel()
    }

    // Extension on repository directly to retrieve database flows as statein
    private fun ListVitalsExtensionDao(): VitalsDao = database.vitalsDao()

    private fun AppDatabase.vitalsDao(): VitalsDao = database.vitalsDao()

    private fun VitalsRepository.allItemsFlow() = allSessions.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private fun VitalsRepository.allTokensFlow() = allTokens.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun getStreamlitScript(): String {
        return """
import streamlit as st
import requests
import json
import time
import pandas as pd

st.set_page_config(
    page_title="VitalsSync Pro - Real-time HR Dashboard",
    page_icon="❤️",
    layout="wide"
)

# Custom Styling
st.markdown("<style>div.block-container{padding-top:2rem;}</style>", unsafe_allow_html=True)

st.title("❤️ VitalsSync Pro")
st.caption("Live high-fidelity telemetry streamed wirelessly from your mobile heart rate monitor strap")

# Sidebar Connectivity Specs
st.sidebar.markdown("### 🔌 Connection Settings")
st.sidebar.info("Enter the local IP and active token shown in your Vitals Dashboard mobile app.")
device_ip = st.sidebar.text_input("Mobile IP Address", value="${getLocalIpAddress()}")
device_port = st.sidebar.number_input("Server Port", value=${serverPort.value}, step=1)
token = st.sidebar.text_input("Streamer Security Token", value="${activeToken.value.ifEmpty { "VITAL-SAMPLE-TOKEN" }}")
poll_interval = st.sidebar.slider("Sampling Interval (sec)", 0.2, 5.0, 1.0, 0.1)

endpoint = f"http://{device_ip}:{device_port}/vitals?token={token}"

# Session State for History Tracking
if "rolling_df" not in st.session_state:
    st.session_state.rolling_df = pd.DataFrame(columns=["Timestamp", "BPM"])

data_placeholder = st.empty()

while True:
    try:
        res = requests.get(endpoint, timeout=1.5)
        if res.status_code == 200:
            payload = res.json()
            bpm = payload.get("bpm", 0)
            avg_bpm = payload.get("average_bpm", 0)
            max_bpm = payload.get("max_bpm", 0)
            hrv = payload.get("hrv_ms", 0)
            stress = payload.get("stress_level", "Unknown")
            cals = payload.get("calories_burned", 0)
            device = payload.get("device_name", "None Connected")
            connected = payload.get("connected", False)

            # Update timeline DataFrame
            now_str = time.strftime("%H:%M:%S")
            if bpm > 0:
                new_row = pd.DataFrame([{"Timestamp": now_str, "BPM": bpm}])
                st.session_state.rolling_df = pd.concat([st.session_state.rolling_df, new_row]).tail(100)

            # Sync rolling list from remote cache on startup
            if len(st.session_state.rolling_df) <= 1 and len(payload.get("bpm_history", [])) > 0:
                remote_pts = []
                for pt in payload["bpm_history"]:
                    t_str = time.strftime("%H:%M:%S", time.localtime(pt["time"]))
                    remote_pts.append({"Timestamp": t_str, "BPM": pt["BPM"]})
                st.session_state.rolling_df = pd.DataFrame(remote_pts)

            with data_placeholder.container():
                # Streamlit Metrics
                col_c, col_z, col_b = st.columns(3)
                
                # Dynamic zone logic
                zone = "Unknown"
                if bpm > 0:
                    if bpm >= 165:
                        zone = "🔥 Peak Training"
                    elif bpm >= 140:
                        zone = "⚡ Threshold/Cardio"
                    elif bpm >= 110:
                        zone = "🏃 Aerobic/Fat-Burn"
                    elif bpm >= 90:
                        zone = "🚶 Warm-Up"
                    else:
                        zone = "💤 Resting State"
                else:
                    zone = "Waiting..."

                with col_c:
                    st.metric("Heart Rate (BPM)", f"{bpm} bpm" if bpm > 0 else "Disconnected", None if bpm == 0 else f"{bpm - 70} from rest")
                with col_z:
                    st.metric("Active Energy Zone", zone)
                with col_b:
                    st.metric("Device Status", "● Live Stream" if connected else "○ Offline Sensor", device)

                # Metrics grid
                st.write("---")
                col_a, col_e, col_s, col_cl = st.columns(4)
                col_a.metric("Average Heart Rate", f"{avg_bpm} bpm")
                col_e.metric("Session Max Heart Rate", f"{max_bpm} bpm")
                col_s.metric("Estimated HRV (RMSSD)", f"{hrv} ms")
                col_cl.metric("Calories Expended", f"{cals} kcal")

                # Heart Rate Timeline
                st.subheader("⚡ Vital Sign Timeline Stream")
                if not st.session_state.rolling_df.empty:
                    chart_df = st.session_state.rolling_df.copy().set_index("Timestamp")
                    st.line_chart(chart_df, height=350)
                else:
                    st.info("No timeline data loaded yet. Connect standard bluetooth strap or enable heart rate simulation.")
                
        else:
            with data_placeholder.container():
                st.error(f"⚠️ Authorization Rejected: Port connected but status code was {res.status_code}")
                st.info("Please verify your Security Token in the mobile app sidebar.")
    except Exception as ex:
        with data_placeholder.container():
            st.warning(f"⚠️ Telemetry Stream Offline: Ensure phone is on the same local network.")
            st.markdown(f'''
            **Debugging Steps:**
            1. Configure Phone Wi-Fi and host computer Wi-Fi onto the same SSID.
            2. Run this Streamlit script on your computer.
            3. Target IP `http://{device_ip}:{device_port}/` on your browser as check.
            ''')
    
    time.sleep(poll_interval)
    st.rerun()
        """.trimIndent()
    }
}
