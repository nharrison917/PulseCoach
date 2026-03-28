package com.pulsecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.ui.EvaluationScreen
import com.pulsecoach.ui.LiveSessionScreen
import com.pulsecoach.ui.ProfileSetupScreen
import com.pulsecoach.ui.SessionHistoryScreen
import com.pulsecoach.ui.SettingsScreen

/**
 * The single Activity for PulseCoach.
 * Navigation between screens is handled by the Compose Navigation library via NavHost —
 * no Fragments, no back-stack management in Java/Kotlin code.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // SystemBarStyle.light forces dark (black) status bar icons.
        // The default SystemBarStyle.auto() picks icon color from the *system* dark-mode
        // setting, not the app's background — causing white-on-white on some devices.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT
            )
        )

        // Check first-launch flag before rendering anything.
        // SharedPreferences.getBoolean is synchronous and safe on the main thread
        // for a single boolean read — it does not do disk I/O on every call.
        val repository = UserProfileRepository(this)
        val startDestination = if (repository.isProfileComplete()) "live_session" else "profile_setup"

        setContent {
            MaterialTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = startDestination) {

                    // First-launch onboarding: no back button, replaces itself with live_session
                    composable("profile_setup") {
                        ProfileSetupScreen(
                            onProfileSaved = {
                                // popUpTo removes profile_setup from the back stack so the user
                                // can never navigate back to onboarding with the back button.
                                navController.navigate("live_session") {
                                    popUpTo("profile_setup") { inclusive = true }
                                }
                            },
                            onNavigateBack = null
                        )
                    }

                    // Edit mode reached from Settings: shows back arrow, pops back on save
                    composable("profile_edit") {
                        ProfileSetupScreen(
                            onProfileSaved = { navController.popBackStack() },
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("live_session") {
                        LiveSessionScreen(
                            onNavigateToSettings = { navController.navigate("settings") },
                            onNavigateToHistory  = { navController.navigate("session_history") }
                        )
                    }

                    composable("session_history") {
                        SessionHistoryScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToEvaluation = { navController.navigate("evaluation") }
                        )
                    }

                    composable("evaluation") {
                        EvaluationScreen(
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            onNavigateBack = { navController.popBackStack() },
                            onNavigateToProfile = { navController.navigate("profile_edit") }
                        )
                    }
                }
            }
        }
    }
}
