package ovh.pandore.photobooth.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.ImmichService
import ovh.pandore.photobooth.domain.model.ImmichAlbum

data class SettingsUiState(
    val immichBaseUrl: String = "",
    val immichApiKey: String = "",
    val immichAlbumId: String = "",
    val selectedAlbumName: String = "",
    val newPinCode: String = "",
    val confirmPinCode: String = "",
    val albums: List<ImmichAlbum> = emptyList(),
    val isLoadingAlbums: Boolean = false,
    val albumsError: String? = null,
    val saveSuccess: Boolean = false,
    val saveError: String? = null,
    /** Durée d'affichage de la vignette photo après capture, en secondes (2-15). */
    val photoPreviewDuration: Int = PreferencesManager.DEFAULT_PREVIEW_DURATION_SECONDS,
    /** Durée du minuteur avant la prise de photo, en secondes (2-20). */
    val countdownDuration: Int = PreferencesManager.DEFAULT_COUNTDOWN_DURATION_SECONDS
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadCurrentSettings()
    }

    private fun loadCurrentSettings() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    immichBaseUrl = prefs.getImmichBaseUrl(),
                    immichApiKey = prefs.getImmichApiKey(),
                    immichAlbumId = prefs.getImmichAlbumId(),
                    photoPreviewDuration = prefs.getPhotoPreviewDuration(),
                    countdownDuration = prefs.getCountdownDuration()
                )
            }
        }
    }

    fun onImmichBaseUrlChange(value: String) =
        _uiState.update { it.copy(immichBaseUrl = value, albumsError = null) }

    fun onImmichApiKeyChange(value: String) =
        _uiState.update { it.copy(immichApiKey = value, albumsError = null) }

    fun onNewPinChange(value: String) =
        _uiState.update { it.copy(newPinCode = value, saveError = null) }

    fun onConfirmPinChange(value: String) =
        _uiState.update { it.copy(confirmPinCode = value, saveError = null) }

    /** Sauvegarde l'URL et la clé API Immich. */
    fun saveImmichConfig() {
        viewModelScope.launch {
            prefs.saveImmichBaseUrl(_uiState.value.immichBaseUrl.trim())
            prefs.saveImmichApiKey(_uiState.value.immichApiKey.trim())
            _uiState.update { it.copy(saveSuccess = true) }
        }
    }

    /** Charge la liste des albums depuis l'API Immich. */
    fun fetchAlbums() {
        val state = _uiState.value
        if (state.immichBaseUrl.isBlank() || state.immichApiKey.isBlank()) {
            _uiState.update { it.copy(albumsError = "Veuillez d'abord saisir l'URL et la clé API Immich") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingAlbums = true, albumsError = null) }
            val service = ImmichService(state.immichBaseUrl.trim(), state.immichApiKey.trim())
            val albums = service.getAlbums()
            if (albums.isNotEmpty()) {
                _uiState.update { it.copy(albums = albums, isLoadingAlbums = false) }
            } else {
                _uiState.update {
                    it.copy(
                        isLoadingAlbums = false,
                        albumsError = "Impossible de charger les albums. Vérifiez l'URL et la clé API."
                    )
                }
            }
        }
    }

    /** Sélectionne un album et sauvegarde son ID + lien construit. */
    fun selectAlbum(album: ImmichAlbum) {
        val albumLink = "${_uiState.value.immichBaseUrl.trim().trimEnd('/')}/albums/${album.id}"
        viewModelScope.launch {
            prefs.saveImmichAlbumId(album.id)
            prefs.saveImmichAlbumLink(albumLink)
            _uiState.update {
                it.copy(
                    immichAlbumId = album.id,
                    selectedAlbumName = album.albumName
                )
            }
        }
    }

    /** Enregistre le nouveau code PIN si les deux champs correspondent. */
    fun savePinCode() {
        val state = _uiState.value
        if (state.newPinCode.length < 4) {
            _uiState.update { it.copy(saveError = "Le code PIN doit contenir au moins 4 caractères") }
            return
        }
        if (state.newPinCode != state.confirmPinCode) {
            _uiState.update { it.copy(saveError = "Les codes PIN ne correspondent pas") }
            return
        }
        viewModelScope.launch {
            prefs.savePinCode(state.newPinCode)
            _uiState.update { it.copy(newPinCode = "", confirmPinCode = "", saveSuccess = true) }
        }
    }

    fun dismissSaveSuccess() = _uiState.update { it.copy(saveSuccess = false) }
    fun dismissSaveError() = _uiState.update { it.copy(saveError = null) }

    /** Met à jour et sauvegarde immédiatement la durée de la vignette (slider). */
    fun onPhotoPreviewDurationChange(seconds: Int) {
        _uiState.update { it.copy(photoPreviewDuration = seconds) }
        viewModelScope.launch { prefs.savePhotoPreviewDuration(seconds) }
    }

    /** Met à jour et sauvegarde immédiatement la durée du minuteur (slider). */
    fun onCountdownDurationChange(seconds: Int) {
        _uiState.update { it.copy(countdownDuration = seconds) }
        viewModelScope.launch { prefs.saveCountdownDuration(seconds) }
    }
}

