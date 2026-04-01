package com.example.mratsmastudenttracker

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Class
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.example.mratsmastudenttracker.ui.*
import com.example.mratsmastudenttracker.ui.theme.MrAtsmaStudentTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MrAtsmaStudentTrackerTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    val viewModel: StudentViewModel = viewModel()
    val collaborationViewModel: CollaborationViewModel = viewModel()

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentDestination = navBackStackEntry?.destination
            
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Class, contentDescription = null) },
                    label = { Text("Classes") },
                    selected = currentDestination?.route?.startsWith("classes") == true,
                    onClick = {
                        navController.navigate("classes") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Event, contentDescription = null) },
                    label = { Text("Sessions") },
                    selected = currentDestination?.route?.startsWith("sessions") == true,
                    onClick = {
                        navController.navigate("sessions") {
                            popUpTo(navController.graph.startDestinationId) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "classes",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("classes") {
                ClassManagementScreen(
                    viewModel = viewModel,
                    onNavigateToStudents = { classId ->
                        navController.navigate("classes/$classId")
                    }
                )
            }
            composable(
                route = "classes/{classId}",
                arguments = listOf(navArgument("classId") { type = NavType.LongType })
            ) { backStackEntry ->
                val classId = backStackEntry.arguments?.getLong("classId") ?: return@composable
                StudentManagementScreen(
                    viewModel = viewModel,
                    classId = classId
                )
            }
            composable("sessions") {
                SessionListScreen(
                    viewModel = viewModel,
                    collaborationViewModel = collaborationViewModel,
                    onNavigateToAddSession = { navController.navigate("sessions/add_session") },
                    onNavigateToEditSession = { sessionId ->
                        navController.navigate("sessions/$sessionId/edit")
                    },
                    onNavigateToSessionDetail = { sessionId ->
                        navController.navigate("sessions/$sessionId/details")
                    },
                    onNavigateToAssistantScanner = {
                        navController.navigate("sessions/assistant_scanner")
                    }
                )
            }
            composable("sessions/add_session") {
                AddSessionScreen(
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "sessions/{sessionId}/edit",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                EditSessionScreen(
                    sessionId = sessionId,
                    viewModel = viewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "sessions/{sessionId}/details",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                SessionDetailScreen(
                    sessionId = sessionId,
                    viewModel = viewModel,
                    collaborationViewModel = collaborationViewModel,
                    onNavigateToScanner = { sid ->
                        navController.navigate("sessions/$sid/scanner")
                    }
                )
            }
            composable(
                route = "sessions/{sessionId}/scanner",
                arguments = listOf(navArgument("sessionId") { type = NavType.LongType })
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getLong("sessionId") ?: return@composable
                ScannerScreen(
                    sessionId = sessionId,
                    viewModel = viewModel,
                    collaborationViewModel = collaborationViewModel,
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable("sessions/assistant_scanner") {
                AssistantScannerScreen(
                    collaborationViewModel = collaborationViewModel,
                    onNavigateBack = { navController.popBackStack() },
                    onConnectionSuccess = { sessionId ->
                        navController.navigate("sessions/$sessionId/scanner") {
                            popUpTo("sessions")
                        }
                    }
                )
            }
        }
    }
}
