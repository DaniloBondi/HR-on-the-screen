package com.example.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluetooth.ConnectionState
import com.example.data.DashboardPreference
import com.example.viewmodel.VitalsViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun MainDashboardScreen(
    viewModel: VitalsViewModel,
    modifier: Modifier = Modifier
) {
    val currentBpm by viewModel.currentBpm.collectAsState()
    val isRecording by viewModel.isRecordingActive.collectAsState()
    val durationSeconds by viewModel.recordingDurationSeconds.collectAsState()
    val prefs by viewModel.dashboardPrefs.collectAsState()
    val accentTheme by viewModel.accentTheme.collectAsState()
    val rollingHistory by viewModel.rollingBpmHistory.collectAsState()
    val connectionState by viewModel.connectionState.collectAsState()
    val sensorBattery by viewModel.sensorBattery.collectAsState()
    val rrIntervals by viewModel.rrIntervals.collectAsState()
    val isSensorRrCapable by viewModel.isSensorRrCapable.collectAsState()
    val synthesizeRrIfMissing by viewModel.synthesizeRrIfMissing.collectAsState()

    var isCustomizerMode by remember { mutableStateOf(false) }
    var sessionLabelInput by remember { mutableStateOf("") }
    var showSaveDialog by remember { mutableStateOf(false) }

    // Primary Theme color extraction
    val themePrimary = MaterialTheme.colorScheme.primary
    val themeSurface = MaterialTheme.colorScheme.surface
    val themeBackground = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(themeBackground)
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header Stats bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "BIOMETRIC CONTROL",
                        style = MaterialTheme.typography.labelSmall,
                        color = themePrimary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp
                    )
                    Text(
                        text = "Vitals Telemetry",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Record Toggle button
                    if (!isRecording) {
                        Button(
                            onClick = { viewModel.startSessionRecording() },
                            colors = ButtonDefaults.buttonColors(containerColor = themePrimary),
                            modifier = Modifier
                                .testTag("start_workout_button")
                                .height(38.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PlayArrow,
                                contentDescription = "Record",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                            modifier = Modifier
                                .testTag("stop_workout_button")
                                .height(38.dp)
                        ) {
                            val pulseAlpha by rememberInfiniteTransition(label = "").animateFloat(
                                initialValue = 0.5f,
                                targetValue = 1.0f,
                                animationSpec = infiniteRepeatable(
                                    animation = tween(600, easing = EaseInOutSine),
                                    repeatMode = RepeatMode.Reverse
                                ),
                                label = ""
                            )
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(Color.White.copy(alpha = pulseAlpha), shape = RoundedCornerShape(2.dp))
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                "STOP (${formatDuration(durationSeconds)})",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                        }
                    }

                    // Customize layout switch
                    IconButton(
                        onClick = { isCustomizerMode = !isCustomizerMode },
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = if (isCustomizerMode) themePrimary else themeSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (isCustomizerMode) Icons.Default.Close else Icons.Default.Build,
                            contentDescription = "Customize Layout",
                            tint = if (isCustomizerMode) Color.White else themePrimary
                        )
                    }
                }
            }

            // Dashboard items lists
            if (prefs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = themePrimary)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Inject layout customization configuration box at top if active
                    if (isCustomizerMode) {
                        item {
                            CustomizerControlPanel(
                                prefs = prefs,
                                accentTheme = accentTheme,
                                onToggle = { id -> viewModel.toggleWidgetVisibility(id) },
                                onMoveUp = { id -> viewModel.moveWidgetUp(id) },
                                onMoveDown = { id -> viewModel.moveWidgetDown(id) },
                                onThemeChange = { theme -> viewModel.updateTheme(theme) },
                                primaryColor = themePrimary
                            )
                        }
                    }

                    // Filter visible widgets and sort them
                    val sortedWidgets = prefs.filter { it.isVisible }.sortedBy { it.orderIndex }
                    
                    if (sortedWidgets.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                colors = CardDefaults.cardColors(containerColor = themeSurface)
                            ) {
                                Text(
                                    text = "All widgets are currently hidden. Enter Layout Customizer to re-enable them.",
                                    modifier = Modifier.padding(24.dp),
                                    textAlign = TextAlign.Center,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    items(sortedWidgets, key = { it.widgetId }) { pref ->
                        when (pref.widgetId) {
                            "vitals_dial" -> {
                                VitalsDialWidget(
                                    bpm = currentBpm,
                                    connectionState = connectionState,
                                    battery = sensorBattery,
                                    deviceName = viewModel.deviceName.collectAsState().value,
                                    primaryColor = themePrimary,
                                    isSensorRrCapable = isSensorRrCapable,
                                    synthesizeRrIfMissing = synthesizeRrIfMissing
                                )
                            }
                            "beats_chart" -> {
                                BeatsChartWidget(
                                    history = rollingHistory,
                                    primaryColor = themePrimary
                                )
                            }
                            "zone_distribution" -> {
                                ZoneDistributionWidget(
                                    history = rollingHistory,
                                    primaryColor = themePrimary
                                )
                            }
                            "advanced_metrics" -> {
                                AdvancedMetricsWidget(
                                    bpm = currentBpm,
                                    rrList = rrIntervals,
                                    primaryColor = themePrimary
                                )
                            }
                            "streamlit_status" -> {
                                StreamlitStatusWidget(
                                    isServerRunning = viewModel.isServerRunning.collectAsState().value,
                                    port = viewModel.serverPort.collectAsState().value,
                                    ip = viewModel.getLocalIpAddress(),
                                    token = viewModel.activeToken.collectAsState().value,
                                    primaryColor = themePrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Recording Save session prompt Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                title = { Text("Save Recording Run") },
                text = {
                    Column {
                        Text("Add a name tag for this workout log:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = sessionLabelInput,
                            onValueChange = { sessionLabelInput = it },
                            placeholder = { Text("e.g. AM Fat Burn, Evening Sprint") },
                            modifier = Modifier.fillMaxWidth().testTag("workout_label_input"),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.stopAndSaveSession(sessionLabelInput)
                            sessionLabelInput = ""
                            showSaveDialog = false
                        }
                    ) {
                        Text("Save workout", fontWeight = FontWeight.Bold, color = themePrimary)
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            viewModel.cancelSessionRecording()
                            sessionLabelInput = ""
                            showSaveDialog = false
                        }
                    ) {
                        Text("Discard run", color = Color.Gray)
                    }
                },
                modifier = Modifier.testTag("save_workout_dialog")
            )
        }
    }
}

// 1. WIDGET: Vitals Dial (Pulsing heart, vital zone ring)
@Composable
fun VitalsDialWidget(
    bpm: Int,
    connectionState: ConnectionState,
    battery: Int,
    deviceName: String,
    primaryColor: Color,
    isSensorRrCapable: Boolean?,
    synthesizeRrIfMissing: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Pulse",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "REAL-TIME CARDIO DIAL",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                // Battery status / sensor connection status
                if (connectionState == ConnectionState.CONNECTED) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Battery",
                            tint = primaryColor,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "$battery%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                // Background Arc representing 40 - 220 Max boundary progress
                val sweepAngle = 240f
                val startAngle = 150f

                val zoneColor = when {
                    bpm == 0 -> Color.Gray
                    bpm < 90 -> Color.Cyan
                    bpm < 110 -> Color(0xFF00E676) // Warmup/Fat burn green
                    bpm < 140 -> Color(0xFFFFCC00) // Cardio cardio orange
                    else -> Color(0xFFFF3366) // peak red
                }

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Backring
                    drawArc(
                        color = Color.DarkGray.copy(alpha = 0.3f),
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = false,
                        style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                    )

                    // Forward progress of HR
                    if (bpm > 0) {
                        val percentage = ((bpm - 40).toFloat() / 180f).coerceIn(0f, 1f)
                        drawArc(
                            color = zoneColor,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle * percentage,
                            useCenter = false,
                            style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                }

                // Internal numeric stats + animated pulsing heart
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val heartScale by rememberInfiniteTransition(label = "").animateFloat(
                        initialValue = 0.9f,
                        targetValue = 1.15f,
                        animationSpec = infiniteRepeatable(
                            // speed depends on current heart rate
                            animation = tween(
                                durationMillis = if (bpm > 0) (60000 / bpm).coerceIn(250, 1000) else 800,
                                easing = FastOutSlowInEasing
                            ),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = ""
                    )

                    Icon(
                        imageVector = Icons.Default.Favorite,
                        contentDescription = "Core Pulse Icon",
                        tint = if (bpm > 0) zoneColor else Color.DarkGray,
                        modifier = Modifier
                            .size(36.dp)
                            .scale(if (bpm > 0) heartScale else 1.0f)
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = if (bpm > 0) bpm.toString() else "--",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        ),
                        color = if (bpm > 0) MaterialTheme.colorScheme.onSurface else Color.Gray,
                        modifier = Modifier.testTag("live_bpm_text")
                    )

                    Text(
                        text = "BPM",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        letterSpacing = 1.sp
                    )
                }
            }

            // Connection descriptors underneath
            Text(
                text = when (connectionState) {
                    ConnectionState.CONNECTED -> "Streaming from: $deviceName"
                    ConnectionState.SCANNING -> "Discovering wireless straps..."
                    ConnectionState.CONNECTING -> "Interfacing BioStrap..."
                    ConnectionState.ERROR -> "Bluetooth interface error"
                    else -> "Sensor offline. Scan & connect strap or toggle simulation."
                },
                fontSize = 12.sp,
                color = when (connectionState) {
                    ConnectionState.CONNECTED -> primaryColor
                    ConnectionState.ERROR -> Color.Red
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                },
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            if (connectionState == ConnectionState.CONNECTED) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Battery Level
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.FavoriteBorder, // Standard battery icon placeholder or star or customized icon
                            contentDescription = "Battery Status",
                            tint = if (battery > 25) Color(0xFF00E676) else Color.Red,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Battery: $battery%",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }

                    // RR support status
                    val rrTextSelected = when (isSensorRrCapable) {
                        true -> "RR: Native"
                        false -> if (synthesizeRrIfMissing) "RR: Reconstructed" else "RR: None"
                        null -> "RR: Checking..."
                    }
                    val rrColorSelected = when (isSensorRrCapable) {
                        true -> Color(0xFF00E676) // green
                        false -> if (synthesizeRrIfMissing) Color(0xFFFF9800) else Color.Red // orange/red
                        null -> Color.Gray
                    }
                    val rrIconSelected = when (isSensorRrCapable) {
                        true -> Icons.Default.CheckCircle
                        false -> if (synthesizeRrIfMissing) Icons.Default.Refresh else Icons.Default.Warning
                        null -> Icons.Default.Info
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = rrIconSelected,
                            contentDescription = "RR Status",
                            tint = rrColorSelected,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = rrTextSelected,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        )
                    }
                }
            }
        }
    }
}

// 2. WIDGET: Beats scrolling line chart
@Composable
fun BeatsChartWidget(
    history: List<Pair<Long, Int>>,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Share, // Wave indicator representation
                        contentDescription = "Wave",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "BPM TIMELINE GRAPH",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                if (history.isNotEmpty()) {
                    val maxVal = history.maxOf { it.second }
                    val minVal = history.minOf { it.second }
                    Text(
                        text = "MIN: $minVal | MAX: $maxVal",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            HeartRateLineChart(history = history, primaryColor = primaryColor, modifier = Modifier.fillMaxSize())
        }
    }
}

// Custom heart rate canvas spline line chart
@Composable
fun HeartRateLineChart(
    history: List<Pair<Long, Int>>,
    primaryColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        if (history.size < 2) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Awaiting cardiac telemetry stream...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                val bpmValues = history.map { it.second }
                val minBpm = (bpmValues.minOrNull() ?: 60).coerceAtLeast(40) - 5
                val maxBpm = (bpmValues.maxOrNull() ?: 120).coerceAtMost(220) + 5
                val range = (maxBpm - minBpm).toFloat()

                val points = history.mapIndexed { idx, pair ->
                    val x = (idx.toFloat() / (history.size - 1)) * width
                    val relativeY = (pair.second - minBpm) / range
                    val y = height - (relativeY * height)
                    Offset(x, y)
                }

                // Smooth Path using bezier curves
                val linePath = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) {
                            val pPrev = points[i - 1]
                            val pCurr = points[i]
                            val controlX = (pPrev.x + pCurr.x) / 2
                            cubicTo(controlX, pPrev.y, controlX, pCurr.y, pCurr.x, pCurr.y)
                        }
                    }
                }

                // Fill Path under line
                val fillPath = Path().apply {
                    addPath(linePath)
                    lineTo(points.last().x, height)
                    lineTo(points.first().x, height)
                    close()
                }

                // Draw gradient under line
                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(primaryColor.copy(alpha = 0.25f), Color.Transparent),
                        startY = 0f,
                        endY = height
                    )
                )

                // Draw the main line
                drawPath(
                    path = linePath,
                    color = primaryColor,
                    style = Stroke(width = 3.dp.toPx(), join = StrokeJoin.Round)
                )

                // Highlight latest point
                if (points.isNotEmpty()) {
                    drawCircle(
                        color = primaryColor,
                        radius = 5.dp.toPx(),
                        center = points.last()
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 2.dp.toPx(),
                        center = points.last()
                    )
                }
            }
        }
    }
}

// 3. WIDGET: Training zone distributions
@Composable
fun ZoneDistributionWidget(
    history: List<Pair<Long, Int>>,
    primaryColor: Color
) {
    val bpmList = history.map { it.second }
    val totalBeats = bpmList.size.coerceAtLeast(1)

    // Zone limits mapping
    val peakCount = bpmList.count { it >= 165 }
    val cardioCount = bpmList.count { it in 140..164 }
    val fatBurnCount = bpmList.count { it in 110..139 }
    val warmupCount = bpmList.count { it in 90..109 }
    val restCount = bpmList.count { it < 90 }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.List,
                    contentDescription = "Distro",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ENERGY ZONE TARGETS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            ZoneRow(name = "Peak Anaerobic (165-220 bpm)", percentage = peakCount.toFloat() / totalBeats, color = Color(0xFFFF3366))
            ZoneRow(name = "Cardio Training (140-164 bpm)", percentage = cardioCount.toFloat() / totalBeats, color = Color(0xFFFF9500))
            ZoneRow(name = "Aerobic Fat-Burn (110-139 bpm)", percentage = fatBurnCount.toFloat() / totalBeats, color = Color(0xFF00E676))
            ZoneRow(name = "Active Warmup (90-109 bpm)", percentage = warmupCount.toFloat() / totalBeats, color = Color(0xFF00E5FF))
            ZoneRow(name = "Recovery State (<90 bpm)", percentage = restCount.toFloat() / totalBeats, color = Color.Gray)
        }
    }
}

@Composable
fun ZoneRow(name: String, percentage: Float, color: Color) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f))
            Text(
                text = "${(percentage * 100).roundToInt()}%",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = color,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape),
            color = color,
            trackColor = Color.DarkGray.copy(alpha = 0.3f)
        )
    }
}

// 4. WIDGET: Advanced metabolic bio analytics
@Composable
fun AdvancedMetricsWidget(
    bpm: Int,
    rrList: List<Int>,
    primaryColor: Color
) {
    // Estimations of HRV and respiration based on current BPM context
    val currentRR = rrList.firstOrNull() ?: (if (bpm > 0) (60000 / bpm) else 800)
    val stressIndex = when {
        bpm == 0 -> "--"
        bpm < 65 -> "Low"
        bpm < 82 -> "Balanced"
        bpm < 110 -> "Moderated"
        else -> "High Exertion"
    }

    val hrvValue = if (bpm > 0) {
        (currentRR * 0.08 + 24).toInt().coerceIn(35, 125)
    } else {
        0
    }

    // Respiration rate estimated: ~12 to 24 cycles per minute depending on cardio load
    val respRate = if (bpm > 0) {
        (12 + (bpm - 60) * 0.12).roundToInt().coerceIn(12, 28)
    } else {
        0
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Metrics",
                    tint = primaryColor,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "METABOLIC BIOMETRICS",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(modifier = Modifier.weight(1f)) {
                    MetricBox(name = "HRV (RMSSD)", value = if (hrvValue > 0) "$hrvValue ms" else "--", color = primaryColor)
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricBox(name = "Stress Response", value = stressIndex, color = Color(0xFFFF9500))
                }
                Column(modifier = Modifier.weight(1f)) {
                    MetricBox(name = "Est. Respiration", value = if (respRate > 0) "$respRate bpm" else "--", color = Color(0xFF00E5FF))
                    Spacer(modifier = Modifier.height(10.dp))
                    MetricBox(name = "Cardiac HRV Drift", value = if (bpm > 0) "${(currentRR * 0.15).roundToInt()} ms" else "--", color = Color(0xFF00E676))
                }
            }
        }
    }
}

@Composable
fun MetricBox(name: String, value: String, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
            .padding(10.dp)
    ) {
        Column {
            Text(text = name, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Black, color = color)
        }
    }
}

// 5. WIDGET: Streamlit syncing endpoint widget
@Composable
fun StreamlitStatusWidget(
    isServerRunning: Boolean,
    port: Int,
    ip: String,
    token: String,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Sync",
                        tint = primaryColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "STREAMER TELEMETRY SYNC",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .background(if (isServerRunning) Color.Green else Color.Red, shape = CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isServerRunning) "STREAMING" else "IDLE",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        color = if (isServerRunning) Color.Green else Color.Red,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                    .padding(10.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Endpoint IP Address", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Text("http://$ip:$port/", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Active Secure Token", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Text(token, fontSize = 11.sp, color = primaryColor, fontWeight = FontWeight.Black, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// LAYOUT CUSTOMIZER DRAWER CONTROL PANEL
@Composable
fun CustomizerControlPanel(
    prefs: List<DashboardPreference>,
    accentTheme: String,
    onToggle: (String) -> Unit,
    onMoveUp: (String) -> Unit,
    onMoveDown: (String) -> Unit,
    onThemeChange: (String) -> Unit,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp)),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "DASHBOARD CUSTOMIZER ENGINE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = primaryColor,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Theme selector chips
            Text(text = "App Color Accent Preset", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val themes = listOf("Geometric Balance", "Crimson Cardio", "Cyberpunk Neon", "Electric Violet", "Emerald Athlete")
                themes.forEach { t ->
                    val isSelected = accentTheme == t
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(16.dp)
                            )
                            .border(0.5.dp, if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(16.dp))
                            .clickable { onThemeChange(t) }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = t.substringBefore(" "),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(text = "Re-arrange and enable widgets", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            Spacer(modifier = Modifier.height(8.dp))

            prefs.forEach { pref ->
                val label = when (pref.widgetId) {
                    "vitals_dial" -> "Heart Rate Dial & Zones"
                    "beats_chart" -> "Telemetry Canvas Timeline"
                    "zone_distribution" -> "Heart zone distribution bars"
                    "advanced_metrics" -> "Heart HRV & Metabolic Estimations"
                    "streamlit_status" -> "IP Streamer Connection Card"
                    else -> pref.widgetId
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Checkbox(
                            checked = pref.isVisible,
                            onCheckedChange = { onToggle(pref.widgetId) },
                            colors = CheckboxDefaults.colors(checkedColor = primaryColor),
                            modifier = Modifier.size(32.dp).testTag("checkbox_${pref.widgetId}")
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = label,
                            fontSize = 12.sp,
                            color = if (pref.isVisible) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                            fontWeight = FontWeight.Medium
                        )
                    }

                    // Move layout order icons
                    if (pref.isVisible) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(onClick = { onMoveUp(pref.widgetId) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowUp,
                                    contentDescription = "Up",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { onMoveDown(pref.widgetId) }, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Down",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// Utility formater duration helper
fun formatDuration(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return if (hrs > 0) {
        String.format("%02d:%02d:%02d", hrs, mins, secs)
    } else {
        String.format("%02d:%02d", mins, secs)
    }
}
