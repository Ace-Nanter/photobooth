package ovh.pandore.photobooth.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import ovh.pandore.photobooth.ui.launch.LaunchScreen
import ovh.pandore.photobooth.ui.main.MainScreen
import ovh.pandore.photobooth.ui.settings.SettingsScreen

private const val ROUTE_LAUNCH = "launch"
private const val ROUTE_MAIN = "main"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun AppNavigation(
    navigateToLaunch: MutableState<Boolean>,
    onResetHandled: () -> Unit,
    onEnterKiosk: () -> Unit,
    onExitKiosk: () -> Unit
) {
    val navController = rememberNavController()

    // Retour à la page de lancement à chaque reprise de l'activité
    LaunchedEffect(navigateToLaunch.value) {
        if (navigateToLaunch.value) {
            navController.navigate(ROUTE_LAUNCH) {
                popUpTo(0) { inclusive = true }
            }
            onResetHandled()
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_LAUNCH
    ) {
        composable(ROUTE_LAUNCH) {
            // Page de lancement : navigation système libre
            LaunchedEffect(Unit) { onExitKiosk() }
            LaunchScreen(
                onLaunchSuccess = {
                    navController.navigate(ROUTE_MAIN) {
                        popUpTo(ROUTE_LAUNCH) { inclusive = true }
                    }
                }
            )
        }

        composable(ROUTE_MAIN) {
            // Page principale : activation du mode kiosque
            LaunchedEffect(Unit) { onEnterKiosk() }
            MainScreen(
                onNavigateToSettings = {
                    navController.navigate(ROUTE_SETTINGS)
                }
            )
        }

        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
