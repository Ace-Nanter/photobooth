package ovh.pandore.photobooth.ui.launch

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.IpWebCamService

data class LaunchUiState(
    val webcamUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class LaunchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(LaunchUiState())
    val uiState: StateFlow<LaunchUiState> = _uiState.asStateFlow()

    init {
        // Pré-remplit avec la dernière adresse utilisée
        viewModelScope.launch {
            val savedUrl = prefs.getWebcamBaseUrl()
            if (savedUrl.isNotBlank()) {
                _uiState.update { it.copy(webcamUrl = savedUrl) }
            }
        }
    }

    fun onUrlChange(url: String) {
        _uiState.update { it.copy(webcamUrl = url, error = null) }
    }

    /**
     * Valide l'URL, teste la connexion, sauvegarde et appelle [onSuccess] si tout va bien.
     */
    fun launch(onSuccess: () -> Unit) {
        val url = _uiState.value.webcamUrl.trim()

        if (url.isBlank()) {
            _uiState.update { it.copy(error = "Veuillez entrer l'adresse de la caméra") }
            return
        }

        if (!URL_REGEX.matches(url)) {
            _uiState.update {
                it.copy(error = "Format invalide. Exemple : http://192.168.1.117:8080")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }

            val service = IpWebCamService(url)
            val canConnect = service.testConnection()

            if (canConnect) {
                prefs.saveWebcamBaseUrl(url)
                onSuccess()
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = "Impossible de se connecter. Vérifiez l'adresse et que l'app IP WebCam est lancée."
                    )
                }
            }
        }
    }

    companion object {
        /**
         * Regex stricte : http://<IPv4>:<port>
         * Exemples valides : http://192.168.1.117:8080  http://10.0.0.5:4747
         * Pas de slash final, pas de https, pas de nom de domaine.
         */
        private val URL_REGEX =
            Regex("""^http://(\d{1,3}\.){3}\d{1,3}:\d{2,5}$""")
    }
}

