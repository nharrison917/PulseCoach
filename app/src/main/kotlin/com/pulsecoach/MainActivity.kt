package com.pulsecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsecoach.repository.ThemeRepository
import com.pulsecoach.repository.UserProfileRepository
import com.pulsecoach.ui.EvaluationScreen
import com.pulsecoach.ui.LiveSessionScreen
import com.pulsecoach.ui.ProfileSetupScreen
import com.pulsecoach.ui.SessionHistoryScreen
import com.pulsecoach.ui.SettingsScreen
import com.pulsecoach.ui.theme.AppTheme
import com.pulsecoach.ui.theme.colorScheme

/**
 * The single Activity for PulseCoach.
 * Navigation between screens is handled by the Compose Navigation library via NavHost —
 * no Fragments, no back-stack management in Java/Kotlin code.
 */
class MainActivity : ComponentActivity() {

    // Created once here so both setContent and the settings callback share the same instance.
    private lateinit var themeRepository: ThemeRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeRepository = ThemeRepository(this)
        // enableEdgeToEdge is called reactively in a SideEffect below,
        // keyed to the selected theme — so status bar icons stay correct
        // even when the user switches themes without restarting the app.
        // We still need one unconditional call here so the window is
        // edge-to-edge before the first frame is drawn.
        enableEdgeToEdge()

        // Check first-launch flag before rendering anything.
        // SharedPreferences.getBoolean is synchronous and safe on the main thread
        // for a single boolean read — it does not do disk I/O on every call.
        val repository = UserProfileRepository(this)
        val startDestination = if (repository.isProfileComplete()) "live_session" else "profile_setup"

        setContent {
            // selectedTheme drives the ColorScheme for the entire composition tree.
            // mutableStateOf triggers recomposition the moment the user picks a new theme
            // in Settings — no restart or navigation required.
            var selectedTheme by remember { mutableStateOf(themeRepository.getTheme()) }

            // Keep status bar icon color in sync with the active theme.
            // SideEffect runs after every recomposition, so switching themes
            // in Settings updates the icons immediately without an Activity restart.
            // light() = dark icons (good for light backgrounds)
            // dark() = light icons (good for dark backgrounds)
            SideEffect {
                enableEdgeToEdge(
                    statusBarStyle = if (selectedTheme == AppTheme.DEFAULT) {
                        SystemBarStyle.light(
                            scrim = android.graphics.Color.TRANSPARENT,
                            darkScrim = android.graphics.Color.TRANSPARENT
                        )
                    } else {
                        SystemBarStyle.dark(
                            scrim = android.graphics.Color.TRANSPARENT
                        )
                    }
                )
            }

            MaterialTheme(colorScheme = selectedTheme.colorScheme) {
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
                            onNavigateToProfile = { navController.navigate("profile_edit") },
                            currentTheme = selectedTheme,
                            onThemeChange = { theme ->
                                selectedTheme = theme
                                themeRepository.saveTheme(theme)
                            }
                        )
                    }
                }
            }
        }
    }
}
