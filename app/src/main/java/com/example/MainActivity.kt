package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.ConnectionScreen
import com.example.ui.screens.MainDashboardScreen
import com.example.ui.screens.LogsScreen
import com.example.ui.screens.StreamingScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.VitalsViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Enable true Edge-to-Edge rendering
        enableEdgeToEdge()
        setContent {
            val viewModel: VitalsViewModel = viewModel()
            val accentTheme by viewModel.accentTheme.collectAsState()

            MyApplicationTheme(themeName = accentTheme) {
                var selectedTab by remember { mutableIntStateOf(0) }

                val items = listOf(
                    NavigationTabItem("Dashboard", Icons.Default.Favorite, "dashboard_tab"),
                    NavigationTabItem("Streaming", Icons.Default.Share, "streaming_tab"),
                    NavigationTabItem("Sensors", Icons.Default.Refresh, "sensors_tab"),
                    NavigationTabItem("Logs", Icons.Default.List, "logs_tab")
                )

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    bottomBar = {
                        NavigationBar(
                            containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surface,
                            tonalElevation = NavigationBarDefaults.TonalElevation
                        ) {
                            items.forEachIndexed { index, tab ->
                                val active = selectedTab == index
                                NavigationBarItem(
                                    selected = active,
                                    onClick = { selectedTab = index },
                                    icon = {
                                        Icon(
                                            imageVector = tab.icon,
                                            contentDescription = tab.title,
                                            tint = if (active) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.title,
                                            fontSize = 11.sp,
                                            fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
                                            color = if (active) androidx.compose.material3.MaterialTheme.colorScheme.primary else Color.Gray
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        indicatorColor = androidx.compose.material3.MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    val screenModifier = Modifier.padding(innerPadding)
                    when (selectedTab) {
                        0 -> MainDashboardScreen(viewModel = viewModel, modifier = screenModifier)
                        1 -> StreamingScreen(viewModel = viewModel, modifier = screenModifier)
                        2 -> ConnectionScreen(viewModel = viewModel, modifier = screenModifier)
                        3 -> LogsScreen(viewModel = viewModel, modifier = screenModifier)
                    }
                }
            }
        }
    }
}

data class NavigationTabItem(
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

object NavigationBarDefaults {
    val TonalElevation = 8.dp
}
