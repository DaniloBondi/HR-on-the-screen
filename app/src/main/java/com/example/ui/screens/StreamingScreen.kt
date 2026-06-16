package com.example.ui.screens

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.ApiToken
import com.example.viewmodel.VitalsViewModel

@Composable
fun StreamingScreen(
    viewModel: VitalsViewModel,
    modifier: Modifier = Modifier
) {
    val isServerRunning by viewModel.isServerRunning.collectAsState()
    val serverPort by viewModel.serverPort.collectAsState()
    val activeToken by viewModel.activeToken.collectAsState()
    val tokensList by viewModel.apiTokens.collectAsState()
    val phoneIp = viewModel.getLocalIpAddress()

    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary
    val clipboardManager = LocalClipboardManager.current

    var portInput by remember { mutableStateOf(serverPort.toString()) }
    var tokenLabelInput by remember { mutableStateOf("") }
    var showCreateTokenDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Heading
        item {
            Column {
                Text(
                    text = "DESKTOP CONSOLE LINK",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "Streamlit Streamer Sync",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black
                )
            }
        }

        // Active service status & main server controls
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
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "LOCAL TELEMETRY NETWORK",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = primaryColor
                            )
                            Text(
                                text = if (isServerRunning) "Web engine is active on port $serverPort" else "Sync server is idle",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }

                        Switch(
                            checked = isServerRunning,
                            onCheckedChange = { start ->
                                if (start) {
                                    viewModel.startHttpServer()
                                } else {
                                    viewModel.stopHttpServer()
                                }
                            },
                            colors = SwitchDefaults.colors(checkedThumbColor = primaryColor),
                            modifier = Modifier.testTag("stream_server_toggle")
                        )
                    }

                    Divider(color = Color.DarkGray.copy(alpha = 0.5f), modifier = Modifier.padding(vertical = 8.dp))

                    // IP details helper
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Broadcast Endpoint Address", fontSize = 12.sp, color = Color.Gray)
                        Text(
                            text = "http://$phoneIp:$serverPort/vitals",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = primaryColor,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.testTag("server_address_text")
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Port update form
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it },
                            label = { Text("Local Server Port") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f).testTag("port_input"),
                            singleLine = true
                        )

                        Button(
                            onClick = {
                                val p = portInput.toIntOrNull() ?: 8080
                                viewModel.updateServerPort(p)
                                Toast.makeText(context, "Server bind port set to $p", Toast.LENGTH_SHORT).show()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                            modifier = Modifier.height(52.dp).testTag("save_port_button")
                        ) {
                            Text("Update", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // STREAMLIT PYTHON CODE COPY card
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
                        text = "READY-TO-RUN STREAMLIT RECIPE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = primaryColor
                    )
                    Text(
                        text = "Copy and execute this script on your computer for seamless, fluid real-time graphing.",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Button(
                        onClick = {
                            val script = viewModel.getStreamlitScript()
                            clipboardManager.setText(AnnotatedString(script))
                            Toast.makeText(context, "Streamlit Python code copied to clipboard!", Toast.LENGTH_LONG).show()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = primaryColor),
                        modifier = Modifier.fillMaxWidth().testTag("copy_script_button")
                    ) {
                        Text("📋 Copy Streamlit Python Code", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Small snippet code view
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                            .border(0.5.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), shape = RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = """
                            # To run this visualization:
                            $ pip install streamlit pandas requests
                            $ streamlit run vitals_stream.py
                            """.trimIndent(),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color.LightGray
                        )
                    }
                }
            }
        }

        // TOKEN CONFIGURATION TABLE card
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "SECURE API ACCESS TOKENS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryColor
                )

                TextButton(
                    onClick = { showCreateTokenDialog = true },
                    modifier = Modifier.testTag("create_token_button")
                ) {
                    Icon(imageVector = Icons.Default.Add, contentDescription = "Add", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Token", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = primaryColor)
                }
            }
        }

        if (tokensList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(28.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape = RoundedCornerShape(28.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No tokens registered. Create one to authenticate Streamlit clients.", fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            items(tokensList, key = { it.token }) { itemToken ->
                TokenRow(
                    item = itemToken,
                    isActiveToken = activeToken == itemToken.token,
                    onSelect = { viewModel.selectActiveToken(itemToken.token) },
                    onRevoke = { viewModel.revokeToken(itemToken.token) },
                    primaryColor = primaryColor
                )
            }
        }
    }

    if (showCreateTokenDialog) {
        AlertDialog(
            onDismissRequest = { showCreateTokenDialog = false },
            title = { Text("Generate Access Token") },
            text = {
                Column {
                    Text("Describe the client application for this security authorization:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = tokenLabelInput,
                        onValueChange = { tokenLabelInput = it },
                        placeholder = { Text("e.g. Macbook Streamlit, Workout Server") },
                        modifier = Modifier.fillMaxWidth().testTag("token_label_input"),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.generateNewToken(tokenLabelInput)
                        tokenLabelInput = ""
                        showCreateTokenDialog = false
                        Toast.makeText(context, "Secure API Token generated successfully!", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Generate", fontWeight = FontWeight.Bold, color = primaryColor)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateTokenDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            },
            modifier = Modifier.testTag("create_token_dialog")
        )
    }
}

@Composable
fun TokenRow(
    item: ApiToken,
    isActiveToken: Boolean,
    onSelect: () -> Unit,
    onRevoke: () -> Unit,
    primaryColor: Color
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (isActiveToken) primaryColor.copy(alpha = 0.8f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(28.dp)
            )
            .clickable { onSelect() }
            .testTag("token_row_${item.token}"),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isActiveToken) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.token,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        color = if (isActiveToken) primaryColor else MaterialTheme.colorScheme.onSurface
                    )
                    if (isActiveToken) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(primaryColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("ACTIVE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = primaryColor)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = item.label, fontSize = 11.sp, color = Color.Gray)
            }

            IconButton(
                onClick = { onRevoke() },
                modifier = Modifier.testTag("delete_token_button_${item.token}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = Color.Red.copy(alpha = 0.6f)
                )
            }
        }
    }
}
