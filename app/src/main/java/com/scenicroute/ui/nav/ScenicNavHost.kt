package com.scenicroute.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.scenicroute.auth.AuthState
import com.scenicroute.auth.AuthViewModel
import com.scenicroute.ui.explore.ExploreScreen
import com.scenicroute.ui.home.HomeScreen
import com.scenicroute.ui.settings.SettingsScreen
import com.scenicroute.ui.signin.SignInScreen

@Composable
fun ScenicNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    val start = when (authState) {
        is AuthState.SignedIn -> Destinations.HOME
        else -> Destinations.EXPLORE
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Destinations.EXPLORE) {
            ExploreScreen(
                isSignedIn = authState is AuthState.SignedIn,
                onSignInClick = { navController.navigate(Destinations.SIGN_IN) },
                onDriveClick = { id -> navController.navigate(Destinations.driveDetail(id)) },
            )
        }
        composable(Destinations.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.EXPLORE) { inclusive = false }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.HOME) {
            HomeScreen(
                onRecord = { navController.navigate(Destinations.RECORDING) },
                onRecordFromEarlier = { navController.navigate(Destinations.RECORD_FROM_EARLIER) },
                onExplore = { navController.navigate(Destinations.EXPLORE) },
                onSettings = { navController.navigate(Destinations.SETTINGS) },
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Destinations.RECORDING) {
            Placeholder("Recording (M1)")
        }
        composable(Destinations.RECORD_FROM_EARLIER) {
            Placeholder("Record from earlier (M2)")
        }
        composable(Destinations.DRIVE_DETAIL) {
            Placeholder("Drive detail (M1)")
        }
    }
}

@Composable
private fun Placeholder(label: String) {
    androidx.compose.material3.Text(label)
}
