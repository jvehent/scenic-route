package com.scenicroute.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.scenicroute.auth.AuthState
import com.scenicroute.auth.AuthViewModel
import com.scenicroute.ui.detail.DriveDetailScreen
import com.scenicroute.ui.explore.ExploreScreen
import com.scenicroute.ui.home.HomeScreen
import com.scenicroute.ui.mydrives.MyDrivesScreen
import com.scenicroute.ui.photo.PhotoViewerScreen
import com.scenicroute.ui.profile.MyProfileScreen
import com.scenicroute.ui.profile.OtherProfileScreen
import com.scenicroute.ui.public_.PublicDriveScreen
import com.scenicroute.ui.recording.RecordingScreen
import com.scenicroute.ui.review.DriveReviewScreen
import com.scenicroute.ui.rfe.RecordFromEarlierScreen
import com.scenicroute.ui.settings.SettingsScreen
import com.scenicroute.ui.signin.SignInScreen
import com.scenicroute.ui.trash.TrashScreen
import com.scenicroute.ui.welcome.WelcomeScreen

@Composable
fun ScenicNavHost(
    authViewModel: AuthViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    val authState by authViewModel.state.collectAsStateWithLifecycle()

    val start = when (authState) {
        is AuthState.SignedIn -> Destinations.HOME
        else -> Destinations.WELCOME
    }

    NavHost(navController = navController, startDestination = start) {
        composable(Destinations.WELCOME) {
            WelcomeScreen(
                onSignIn = { navController.navigate(Destinations.SIGN_IN) },
                onBrowseAsGuest = { navController.navigate(Destinations.EXPLORE) },
                onDriveClick = { id -> navController.navigate(Destinations.publicDrive(id)) },
            )
        }
        composable(Destinations.EXPLORE) {
            ExploreScreen(
                isSignedIn = authState is AuthState.SignedIn,
                onSignInClick = { navController.navigate(Destinations.SIGN_IN) },
                onDriveClick = { id -> navController.navigate(Destinations.publicDrive(id)) },
            )
        }
        composable(Destinations.SIGN_IN) {
            SignInScreen(
                onSignedIn = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.WELCOME) { inclusive = true }
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
                onDriveClick = { id -> navController.navigate(Destinations.driveDetail(id)) },
                onAllDrives = { navController.navigate(Destinations.MY_DRIVES) },
                onImported = { driveIds ->
                    when {
                        driveIds.size == 1 -> navController.navigate(Destinations.driveReview(driveIds.first()))
                        driveIds.size > 1 -> navController.navigate(Destinations.MY_DRIVES)
                    }
                },
            )
        }
        composable(Destinations.MY_DRIVES) {
            MyDrivesScreen(
                onBack = { navController.popBackStack() },
                onDriveClick = { id -> navController.navigate(Destinations.driveDetail(id)) },
                onOpenTrash = { navController.navigate(Destinations.TRASH) },
            )
        }
        composable(Destinations.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenProfile = { navController.navigate(Destinations.MY_PROFILE) },
            )
        }
        composable(Destinations.RECORD_FROM_EARLIER) {
            RecordFromEarlierScreen(
                onSaved = { driveId ->
                    navController.navigate(Destinations.driveReview(driveId)) {
                        popUpTo(Destinations.HOME)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Destinations.RECORDING) {
            RecordingScreen(
                onStopped = { driveId ->
                    if (driveId != null) {
                        navController.navigate(Destinations.driveReview(driveId)) {
                            popUpTo(Destinations.HOME)
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.DRIVE_REVIEW,
            arguments = listOf(navArgument(Destinations.ARG_DRIVE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Destinations.ARG_DRIVE_ID).orEmpty()
            DriveReviewScreen(
                onSaved = {
                    navController.navigate(Destinations.driveDetail(id)) {
                        popUpTo(Destinations.HOME)
                    }
                },
                onDiscarded = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Destinations.DRIVE_DETAIL,
            arguments = listOf(navArgument(Destinations.ARG_DRIVE_ID) { type = NavType.StringType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getString(Destinations.ARG_DRIVE_ID).orEmpty()
            DriveDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Destinations.driveReview(id)) },
                onDeleted = {
                    navController.navigate(Destinations.HOME) {
                        popUpTo(Destinations.HOME) { inclusive = true }
                    }
                },
                onAuthorClick = { navController.navigate(Destinations.MY_PROFILE) },
                onPhotoClick = { path ->
                    navController.navigate(Destinations.photoViewer(path))
                },
            )
        }
        composable(
            route = Destinations.PHOTO_VIEWER,
            arguments = listOf(navArgument(Destinations.ARG_PHOTO_PATH) { type = NavType.StringType }),
        ) { backStackEntry ->
            val path = backStackEntry.arguments?.getString(Destinations.ARG_PHOTO_PATH).orEmpty()
            PhotoViewerScreen(
                path = path,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = Destinations.PUBLIC_DRIVE,
            arguments = listOf(navArgument(Destinations.ARG_DRIVE_ID) { type = NavType.StringType }),
            deepLinks = listOf(
                navDeepLink { uriPattern = Destinations.DEEP_LINK_PATTERN_SCHEME },
                navDeepLink { uriPattern = Destinations.DEEP_LINK_PATTERN_HTTPS },
            ),
        ) {
            PublicDriveScreen(
                onBack = { navController.popBackStack() },
                onPhotoClick = { path -> navController.navigate(Destinations.photoViewer(path)) },
                onOwnerClick = { uid -> navController.navigate(Destinations.otherProfile(uid)) },
                onSignInClick = { navController.navigate(Destinations.SIGN_IN) },
            )
        }
        composable(Destinations.MY_PROFILE) {
            MyProfileScreen(
                onBack = { navController.popBackStack() },
                onOpenTrash = { navController.navigate(Destinations.TRASH) },
            )
        }
        composable(Destinations.TRASH) {
            TrashScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Destinations.OTHER_PROFILE,
            arguments = listOf(navArgument(Destinations.ARG_UID) { type = NavType.StringType }),
        ) {
            OtherProfileScreen(onBack = { navController.popBackStack() })
        }
    }
}
