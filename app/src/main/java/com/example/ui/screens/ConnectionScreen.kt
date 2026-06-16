package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.bluetooth.BLEDeviceItem
import com.example.bluetooth.ConnectionState
import com.example.viewmodel.VitalsViewModel

@Composable
fun ConnectionScreen(
    viewModel: VitalsViewModel,
    modifier: Modifier = Modifier
) {
    val connectionState by viewModel.connectionState.collectAsState()
    val isSimulated by viewModel.isSimulated.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val connectedDeviceName by viewModel.deviceName.collectAsState()

    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    // Required Blue-Tooth Permissions checklist
    val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
    } else {
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { progress ->
        hasPermissions = progress.values.all { it }
    }

    // Intensity selector for Simulator: rest, warm, cardio, peak
    var simulatorIntensity by remember { mutableStateOf(1) } // 1: Rest, 2: Warmup, 3: Cardio, 4: Peak

    LaunchedEffect(simulatorIntensity, isSimulated) {
        if (isSimulated) {
            viewModel.bluetoothManager.startSimulation(simulatorIntensity)
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column {
                Text(
                    text = "HARDWARE INTERFACE",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Band & Sensor Pairer",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Active Connection Status overview card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "SENSOR COUPLING LOG",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = when (connectionState) {
                                    ConnectionState.CONNECTED -> "Coupled: $connectedDeviceName"
                                    ConnectionState.SCANNING -> "Scanning for signals..."
                                    ConnectionState.CONNECTING -> "Handshaking telemetry..."
                                    ConnectionState.ERROR -> "GATT Connection Error"
                                    else -> "Disconnected"
                                },
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = if (isSimulated) "Software simulation driver active" else "Hardware BLE driver active",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                        }

                        // Connected indicator bulb
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(
                                    when (connectionState) {
                                        ConnectionState.CONNECTED -> Color.Green
                                        ConnectionState.SCANNING, ConnectionState.CONNECTING -> Color.Cyan
                                        ConnectionState.ERROR -> Color.Red
                                        else -> Color.DarkGray
                                    }
                                )
                        )
                    }

                    if (connectionState == ConnectionState.CONNECTED) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.bluetoothManager.disconnectDevice() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                            modifier = Modifier.fillMaxWidth().testTag("disconnect_sensor_button")
                        ) {
                            Text("Disconnect Sensor", fontWeight = FontWeight.Black)
                        }
                    }
                }
            }
        }

        // VIRTUAL BIO-STREAM SIMULATOR (Perfect for AI Studio tests!)
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "VIRTUAL BIO-STRAP SIMULATOR",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                text = "Mocks realistic cardio vitals",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        // Simulation toggle switch
                        Switch(
                            checked = isSimulated,
                            onCheckedChange = { active ->
                                if (active) {
                                    viewModel.bluetoothManager.startSimulation(simulatorIntensity)
                                } else {
                                    viewModel.bluetoothManager.stopSimulation()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = primaryColor),
                            modifier = Modifier.testTag("simulation_toggle")
                        )
                    }

                    AnimatedVisibility(visible = isSimulated) {
                        Column(modifier = Modifier.padding(top = 16.dp)) {
                            Text(
                                text = "Target Cardiovascular Intensity Preset:",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            // Intensity buttons grid
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val intensities = listOf(
                                    Triple(1, "REST", Color.Cyan),
                                    Triple(2, "WARM", Color(0xFF00E676)),
                                    Triple(3, "CARDIO", Color(0xFFFF9500)),
                                    Triple(4, "PEAK", Color(0xFFFF3366))
                                )

                                intensities.forEach { (arg, lbl, color) ->
                                    val match = simulatorIntensity == arg
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .background(
                                                color = if (match) color else MaterialTheme.colorScheme.surfaceVariant,
                                                shape = RoundedCornerShape(8.dp)
                                            )
                                            .border(0.5.dp, if (match) color else MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(8.dp))
                                            .clickable { simulatorIntensity = arg }
                                            .padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = lbl,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            color = if (match) Color.Black else color
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // PHYSICAL BLE SCROLL AREA (Requires permission)
        item {
            Column {
                Text(
                    text = "PHYSIOLOGICAL SENSOR COUPLING",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (!hasPermissions) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
                        shape = RoundedCornerShape(28.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Alert",
                                tint = Color.Yellow,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Bluetooth Scan and Connect permissions are required to identify biometric strap hardware.",
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { launcher.launch(requiredPermissions.toTypedArray()) },
                                colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                                modifier = Modifier.testTag("request_permission_button")
                            ) {
                                Text("Approve BLE Access", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "BLE Heart Rate Monitors (Service 0x180D)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )

                        TextButton(
                            onClick = { viewModel.bluetoothManager.startScanning() },
                            modifier = Modifier.testTag("scan_hardware_button")
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = "Scan", modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Scan Straps", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                        }
                    }
                }
            }
        }

        // BLE Scanned results table / scan state
        if (hasPermissions && !isSimulated) {
            if (connectionState == ConnectionState.SCANNING) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = primaryColor, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Receiving wireless advertisements...", fontSize = 12.sp, color = Color.Gray)
                        }
                    }
                }
            }

            if (discoveredDevices.isEmpty() && connectionState != ConnectionState.SCANNING) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No broadcasting heart rate straps found.\nMake sure your strap is worn and active.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else {
                items(discoveredDevices, key = { it.address }) { dev ->
                    BLEDeviceRow(
                        device = dev,
                        onClick = { viewModel.bluetoothManager.connectDevice(dev.address) },
                        primaryColor = primaryColor
                    )
                }
            }
        }
    }
}

@Composable
fun BLEDeviceRow(
    device: BLEDeviceItem,
    onClick: () -> Unit,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp))
            .clickable { onClick() }
            .testTag("device_row_${device.address}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(text = device.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                Text(text = device.address, fontSize = 11.sp, color = Color.Gray, fontFamily = FontFamily.Monospace)
            }
            Text(
                text = "INTERCONNECT >",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor
            )
        }
    }
}
