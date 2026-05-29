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
import ovh.pandore.photobooth.data.remote.NetworkScanner

data class LaunchUiState(
    val webcamUrl: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    /** true pendant le scan réseau automatique */
    val isScanning: Boolean = false,
    /** Message de résultat du scan (succès ou échec) */
    val scanMessage: String? = null
)

class LaunchViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val networkScanner = NetworkScanner(application)

    private val _uiState = MutableStateFlow(LaunchUiState())
    val uiState: StateFlow<LaunchUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            // Pré-remplit avec la dernière adresse utilisée en attendant le scan
            val savedUrl = prefs.getWebcamBaseUrl()
            if (savedUrl.isNotBlank()) {
                _uiState.update { it.copy(webcamUrl = savedUrl) }
            }
            // Lance le scan réseau automatique dès l'arrivée sur l'écran
            startNetworkScan()
        }
    }

    /**
     * Scanne le sous-réseau local pour détecter automatiquement le serveur IP WebCam
     * (GET http://<ip>:8080/videostatus). Met à jour le champ URL si un serveur est trouvé.
     */
    fun startNetworkScan() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanMessage = null) }

            val foundUrl = networkScanner.findVideoServer()

            if (foundUrl != null) {
                _uiState.update {
                    it.copy(
                        webcamUrl = foundUrl,
                        isScanning = false,
                        scanMessage = "Serveur détecté automatiquement ✓"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isScanning = false,
                        scanMessage = "Aucun serveur détecté. Entrez l'adresse manuellement."
                    )
                }
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
                // Configure l'autofocus avant de démarrer le flux
                service.setupCamera()
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
