package ovh.pandore.photobooth

import android.app.ActivityManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import ovh.pandore.photobooth.navigation.AppNavigation
import ovh.pandore.photobooth.ui.theme.PhotoboothTheme

class MainActivity : ComponentActivity() {

    /** Déclenche la navigation vers la page de lancement à chaque reprise. */
    val navigateToLaunch = mutableStateOf(false)

    /** Callback de blocage du bouton retour — actif uniquement en mode kiosque. */
    private val kioskBackCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() { /* bloqué en mode kiosque */ }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Enregistrement du callback retour (désactivé par défaut, activé en mode kiosque)
        onBackPressedDispatcher.addCallback(this, kioskBackCallback)

        setContent {
            PhotoboothTheme {
                AppNavigation(
                    navigateToLaunch = navigateToLaunch,
                    onResetHandled = { navigateToLaunch.value = false },
                    onEnterKiosk = { enterKioskMode() },
                    onExitKiosk = { exitKioskMode() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemUI()
        // Retour systématique à la page de lancement + sortie du mode kiosque
        exitKioskMode()
        navigateToLaunch.value = true
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUI()
    }

    /**
     * Active le mode kiosque : bloque le bouton retour et épingle l'écran.
     * Appelé à l'arrivée sur la page principale.
     */
    fun enterKioskMode() {
        kioskBackCallback.isEnabled = true
        val am = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) {
            try { startLockTask() } catch (_: Exception) { }
        }
    }

    /**
     * Désactive le mode kiosque : réactive le bouton retour et dépingle l'écran.
     * Appelé à l'arrivée sur la page de lancement.
     */
    fun exitKioskMode() {
        kioskBackCallback.isEnabled = false
        try { stopLockTask() } catch (_: Exception) { }
    }

    /** Mode immersif via WindowInsetsControllerCompat (compatible API 20+). */
    private fun hideSystemUI() {
        WindowInsetsControllerCompat(window, window.decorView).let { ctrl ->
            ctrl.hide(WindowInsetsCompat.Type.systemBars())
            ctrl.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
