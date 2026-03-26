package com.pulsecoach

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pulsecoach.ui.LiveSessionScreen
import com.pulsecoach.ui.SettingsScreen

/**
 * The single Activity for PulseCoach.
 * Navigation between screens is handled by the Compose Navigation library via NavHost —
 * no Fragments, no back-stack management in Java/Kotlin code.
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                // NavController tracks which screen is currently shown and handles
                // the back stack. rememberNavController() ties its lifecycle to this composable.
                val navController = rememberNavController()

                // NavHost declares all routes. startDestination is what opens first.
                NavHost(navController = navController, startDestination = "live_session") {

                    composable("live_session") {
                        LiveSessionScreen(
                            onNavigateToSettings = { navController.navigate("settings") }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            // popBackStack() returns to live_session without recreating it
                            onNavigateBack = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
