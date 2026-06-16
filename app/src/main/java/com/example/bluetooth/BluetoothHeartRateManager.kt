package com.example.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ERROR
}

data class BLEDeviceItem(
    val name: String,
    val address: String
)

class BluetoothHeartRateManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var scanCallback: ScanCallback? = null
    private val handler = Handler(Looper.getMainLooper())

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BLEDeviceItem>>(emptyList())
    val discoveredDevices: StateFlow<List<BLEDeviceItem>> = _discoveredDevices.asStateFlow()

    private val _currentBpm = MutableStateFlow(0)
    val currentBpm: StateFlow<Int> = _currentBpm.asStateFlow()

    private val _rrIntervals = MutableStateFlow<List<Int>>(emptyList())
    val rrIntervals: StateFlow<List<Int>> = _rrIntervals.asStateFlow()

    private val _sensorBattery = MutableStateFlow(100)
    val sensorBattery: StateFlow<Int> = _sensorBattery.asStateFlow()

    private val _deviceName = MutableStateFlow("")
    val deviceName: StateFlow<String> = _deviceName.asStateFlow()

    // Simulation fields
    private var simulationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val _isSimulated = MutableStateFlow(false)
    val isSimulated: StateFlow<Boolean> = _isSimulated.asStateFlow()

    private var simulationHeartRateBase = 72
    private var simulationPhase = 0.0

    // Standard UUIDs
    private val HEART_RATE_SERVICE_UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
    private val HEART_RATE_CHAR_UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
    private val CLIENT_CHARACTERISTIC_CONFIG = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (_isSimulated.value) return
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            _connectionState.value = ConnectionState.ERROR
            Log.e("BLE_HRM", "Bluetooth is disabled or not supported")
            return
        }

        _connectionState.value = ConnectionState.SCANNING
        _discoveredDevices.value = emptyList()

        val scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            _connectionState.value = ConnectionState.ERROR
            return
        }

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult?) {
                result?.device?.let { device ->
                    val name = device.name ?: "Unknown HRM"
                    val item = BLEDeviceItem(name, device.address)
                    val currentList = _discoveredDevices.value
                    if (currentList.none { it.address == item.address }) {
                        _discoveredDevices.value = currentList + item
                    }
                }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e("BLE_HRM", "Scan failed: $errorCode")
                _connectionState.value = ConnectionState.ERROR
            }
        }

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(HEART_RATE_SERVICE_UUID))
                .build()
        )
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(filters, settings, scanCallback)
            // Stop scanning automatically after 15 seconds
            handler.postDelayed({
                if (_connectionState.value == ConnectionState.SCANNING) {
                    stopScanning()
                }
            }, 15000)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.ERROR
            Log.e("BLE_HRM", "Permissions missing for scanning", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        val scanner = bluetoothAdapter?.bluetoothLeScanner
        if (scanner != null && scanCallback != null) {
            try {
                scanner.stopScan(scanCallback)
            } catch (e: SecurityException) {
                Log.e("BLE_HRM", "Error stopping scan", e)
            }
        }
        scanCallback = null
        if (_connectionState.value == ConnectionState.SCANNING) {
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    @SuppressLint("MissingPermission")
    fun connectDevice(address: String) {
        stopSimulation()
        stopScanning()

        val adapter = bluetoothAdapter ?: return
        val device = adapter.getRemoteDevice(address) ?: return

        _connectionState.value = ConnectionState.CONNECTING
        _deviceName.value = device.name ?: "BLE HRM Strap"

        _currentBpm.value = 0
        _rrIntervals.value = emptyList()

        try {
            bluetoothGatt = device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            _connectionState.value = ConnectionState.ERROR
            Log.e("BLE_HRM", "Failed permission on connect", e)
        }
    }

    @SuppressLint("MissingPermission")
    fun disconnectDevice() {
        stopSimulation()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        _connectionState.value = ConnectionState.DISCONNECTED
        _deviceName.value = ""
        _currentBpm.value = 0
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                Log.e("BLE_HRM", "Connection error status: $status")
                gatt.close()
                bluetoothGatt = null
                _connectionState.value = ConnectionState.ERROR
                return
            }

            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _connectionState.value = ConnectionState.CONNECTED
                Log.d("BLE_HRM", "Connected, discovering services...")
                try {
                    gatt.discoverServices()
                } catch (e: SecurityException) {
                    Log.e("BLE_HRM", "Discover permissions missing", e)
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _connectionState.value = ConnectionState.DISCONNECTED
                gatt.close()
                bluetoothGatt = null
                _deviceName.value = ""
                _currentBpm.value = 0
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val service = gatt.getService(HEART_RATE_SERVICE_UUID)
                if (service != null) {
                    val characteristic = service.getCharacteristic(HEART_RATE_CHAR_UUID)
                    if (characteristic != null) {
                        try {
                            gatt.setCharacteristicNotification(characteristic, true)
                            val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG)
                            if (descriptor != null) {
                                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptor)
                                Log.d("BLE_HRM", "Subscribed to HR Measurement notification successfully")
                            }
                        } catch (e: SecurityException) {
                            Log.e("BLE_HRM", "Descriptor write exception", e)
                        }
                    }
                }
            }
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            if (characteristic.uuid == HEART_RATE_CHAR_UUID) {
                parseHeartRateCharacteristic(characteristic)
            }
        }

        // Support modern callback as well
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            if (characteristic.uuid == HEART_RATE_CHAR_UUID) {
                parseHeartRateByteArray(value)
            }
        }
    }

    private fun parseHeartRateCharacteristic(characteristic: BluetoothGattCharacteristic) {
        val value = characteristic.value ?: return
        parseHeartRateByteArray(value)
    }

    private fun parseHeartRateByteArray(value: ByteArray) {
        if (value.isEmpty()) return
        val flags = value[0].toInt()
        var offset = 1

        val isFormat16Bit = (flags and 0x01) != 0
        val bpm = if (isFormat16Bit) {
            val high = value[offset + 1].toInt() and 0xFF
            val low = value[offset].toInt() and 0xFF
            offset += 2
            (high shl 8) or low
        } else {
            val hVal = value[offset].toInt() and 0xFF
            offset += 1
            hVal
        }

        val rrPresent = (flags and 0x10) != 0
        val rrList = mutableListOf<Int>()
        if (rrPresent && offset < value.size) {
            while (offset + 1 < value.size) {
                val low = value[offset].toInt() and 0xFF
                val high = value[offset + 1].toInt() and 0xFF
                offset += 2
                // RR Interval in units of 1/1024 second.
                val rr = (high shl 8) or low
                rrList.add(rr)
            }
        }

        _currentBpm.value = bpm
        if (rrList.isNotEmpty()) {
            _rrIntervals.value = rrList
        }
    }

    // SIMULATION ENGINE
    fun startSimulation(targetIntensity: Int = 1) {
        disconnectDevice()
        stopScanning()

        _isSimulated.value = true
        _connectionState.value = ConnectionState.CONNECTED
        _deviceName.value = "Virtual BioStrap (Simulated)"
        _sensorBattery.value = 87

        simulationJob?.cancel()
        simulationPhase = 0.0

        simulationJob = scope.launch {
            while (isActive) {
                // Heart rate baseline based on intensity slider/target
                // 1 -> Rest (60-72), 2 -> Warmup (100-115), 3 -> Threshold/Cardio (130-155), 4 -> High Peak (165-185)
                val targetBase = when (targetIntensity) {
                    2 -> 110
                    3 -> 142
                    4 -> 172
                    else -> 68
                }

                // Smooth migration towards targetIntensity
                if (simulationHeartRateBase < targetBase) {
                    simulationHeartRateBase += (1..3).random()
                } else if (simulationHeartRateBase > targetBase) {
                    simulationHeartRateBase -= (1..3).random()
                }

                // Add sinus drift (respiratory sinus arrhythmia mock)
                simulationPhase += 0.25
                val drift = (Math.sin(simulationPhase) * 4).toInt()
                val finalBpm = (simulationHeartRateBase + drift + (-1..1).random()).coerceIn(40, 220)

                _currentBpm.value = finalBpm

                // Simulate realistic RR intervals: ~60000ms / BPM with variable millisecond offsets
                val beatIntervalMs = (60000 / finalBpm)
                val variability = (Math.cos(simulationPhase * 1.5) * 45).toInt()
                val simulatedRR = beatIntervalMs + variability + (-10..10).random()
                _rrIntervals.value = listOf(simulatedRR)

                // Battery slowly decreases over minutes
                if (Math.random() < 0.02) {
                    _sensorBattery.value = (_sensorBattery.value - 1).coerceIn(1, 100)
                }

                delay(1000)
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulated.value = false
        if (_connectionState.value == ConnectionState.CONNECTED && _deviceName.value.contains("Virtual")) {
            _connectionState.value = ConnectionState.DISCONNECTED
            _deviceName.value = ""
            _currentBpm.value = 0
        }
    }
}
