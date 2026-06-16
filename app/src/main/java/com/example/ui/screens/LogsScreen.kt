package com.example.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.HeartRateSession
import com.example.viewmodel.VitalsViewModel
import java.util.*

@Composable
fun LogsScreen(
    viewModel: VitalsViewModel,
    modifier: Modifier = Modifier
) {
    val logsList by viewModel.pastSessions.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Core header
        item {
            Column {
                Text(
                    text = "BIOMETRIC LOGS",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Historical Cardio Runs",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }

        if (logsList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "❤️",
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No saved cardiac sessions found.\nStart a recording on the Live Dashboard!",
                            fontSize = 13.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        } else {
            items(logsList, key = { it.id }) { log ->
                LogSessionRow(
                    log = log,
                    onDelete = { viewModel.deleteSessionLog(log.id) },
                    primaryColor = primaryColor
                )
            }
        }
    }
}

@Composable
fun LogSessionRow(
    log: HeartRateSession,
    onDelete: () -> Unit,
    primaryColor: Color
) {
    val dateString = remember(log.startTime) {
        val date = Date(log.startTime)
        DateFormat.format("MMM dd, yyyy - hh:mm a", date).toString()
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp))
            .testTag("log_row_${log.id}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = log.label,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dateString,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }

                // Delete Button
                IconButton(
                    onClick = { onDelete() },
                    modifier = Modifier.testTag("delete_log_button_${log.id}").size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete run",
                        tint = Color.Red.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Sub grid stats (3 columns)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Column 1 -> Duration
                Column {
                    Text(text = "DURATION", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = formatDuration(log.durationSeconds),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Column 2 -> AVG HR / Max HR
                Column {
                    Text(text = "AVG / MAX HR", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.averageHeartRate} / ${log.maxHeartRate} bpm",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = primaryColor,
                        fontFamily = FontFamily.Monospace
                    )
                }

                // Column 3 -> Calories
                Column {
                    Text(text = "ENERGY BURN", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${log.caloriesBurned} kcal",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFFF9500),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick Canvas preview of the logged BPM points if present (simple custom mini graph!)
            val bpmPoints = remember(log.bpmSequenceJson) {
                if (log.bpmSequenceJson.isBlank()) {
                    emptyList()
                } else {
                    log.bpmSequenceJson.split(",").mapNotNull { it.toIntOrNull() }
                }
            }

            if (bpmPoints.size > 2) {
                Spacer(modifier = Modifier.height(16.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(8.dp))
                        .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f), shape = RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        val min = bpmPoints.minOrNull() ?: 60
                        val max = bpmPoints.maxOrNull() ?: 120
                        val r = (max - min).toFloat().coerceAtLeast(1f)

                        val path = androidx.compose.ui.graphics.Path()
                        bpmPoints.forEachIndexed { index, pt ->
                            val x = (index.toFloat() / (bpmPoints.size - 1)) * w
                            val relativeY = (pt - min) / r
                            val y = h - (relativeY * h)
                            if (index == 0) {
                                path.moveTo(x, y)
                            } else {
                                path.lineTo(x, y)
                            }
                        }
                        drawPath(
                            path = path,
                            color = primaryColor.copy(alpha = 0.6f),
                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = 1.5.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
