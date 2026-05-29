package ovh.pandore.photobooth.ui.gallery

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.pandore.photobooth.data.local.PhotoRepository
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.EmailService
import ovh.pandore.photobooth.data.remote.ImmichService

// ── États possibles pour le lien de partage de l'album ──────────────────────

sealed class AlbumLinkState {
    object NoAlbumSelected : AlbumLinkState()
    object Loading : AlbumLinkState()
    data class Ready(val url: String) : AlbumLinkState()
    data class Error(val message: String) : AlbumLinkState()
}

data class GalleryUiState(
    val photoUris: List<Uri> = emptyList(),
    val albumLinkState: AlbumLinkState = AlbumLinkState.Loading,
    val email: String = "",
    val isSubmitting: Boolean = false,
    val showConfirmDialog: Boolean = false,
    val submitError: String? = null,
    val showDeleteConfirmDialog: Boolean = false,
    val deletingPhotoUri: Uri? = null,
    val isDeleting: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    /** Événements Toast one-shot (message à afficher). */
    private val _toastEvent = MutableSharedFlow<String>()
    val toastEvent: SharedFlow<String> = _toastEvent.asSharedFlow()

    init {
        resolveAlbumLink()
        loadPhotos()
    }

    // ── Résolution du lien Immich ────────────────────────────────────────────

    fun resolveAlbumLink() {
        viewModelScope.launch {
            _uiState.update { it.copy(albumLinkState = AlbumLinkState.Loading) }

            val albumId = prefs.getImmichAlbumId()
            val baseUrl = prefs.getImmichBaseUrl()
            val apiKey  = prefs.getImmichApiKey()

            if (albumId.isBlank()) {
                _uiState.update { it.copy(albumLinkState = AlbumLinkState.NoAlbumSelected) }
                return@launch
            }
            if (baseUrl.isBlank() || apiKey.isBlank()) {
                _uiState.update { it.copy(albumLinkState = AlbumLinkState.Error("Configuration Immich incomplète")) }
                return@launch
            }

            val service = ImmichService(baseUrl, apiKey)
            val existingLinks = service.getAlbumSharedLinks(albumId)
            val eligible = existingLinks.firstOrNull { it.allowDownload && it.password.isNullOrBlank() }

            val shareUrl = if (eligible != null) {
                service.buildShareUrl(eligible.key)
            } else {
                service.createAlbumShareLink(albumId)?.let { service.buildShareUrl(it.key) }
            }

            _uiState.update {
                it.copy(
                    albumLinkState = if (shareUrl != null)
                        AlbumLinkState.Ready(shareUrl)
                    else
                        AlbumLinkState.Error("Impossible de créer un lien de partage")
                )
            }
        }
    }

    // ── Photos ───────────────────────────────────────────────────────────────

    fun loadPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = queryPhotosFromMediaStore()
            _uiState.update { it.copy(photoUris = uris) }
        }
    }

    // ── Email ────────────────────────────────────────────────────────────────

    fun onEmailChange(email: String) {
        _uiState.update { it.copy(email = email, submitError = null) }
    }

    /**
     * Valide l'email et affiche la dialog de confirmation si valide.
     * Appelé par l'icône Send ou la touche IME.
     */
    fun requestSubmitEmail() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(submitError = "Veuillez entrer votre adresse email") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(submitError = "Adresse email invalide") }
            return
        }
        _uiState.update { it.copy(submitError = null, showConfirmDialog = true) }
    }

    /** Ferme la dialog sans envoyer (email conservé). */
    fun dismissConfirmDialog() {
        _uiState.update { it.copy(showConfirmDialog = false) }
    }

    /**
     * Appelé après confirmation "Oui".
     * TODO: Remplacer par l'appel API réel quand l'endpoint sera défini.
     */
    fun confirmAndSubmitEmail() {
        val email = _uiState.value.email.trim()
        _uiState.update { it.copy(showConfirmDialog = false, isSubmitting = true) }
        viewModelScope.launch {
            val result = EmailService.sendEmailLink(email)
            if (result.isSuccess) {
                _uiState.update { it.copy(isSubmitting = false, email = "") }
                _toastEvent.emit("Email sauvegardé")
            } else {
                _uiState.update { it.copy(isSubmitting = false) }
                _toastEvent.emit("Erreur lors de l'envoi : ${result.exceptionOrNull()?.message}")
            }
        }
    }

    /**
     * Demande la suppression d'une photo → affiche la dialog de confirmation.
     */
    fun requestDeletePhoto(uri: Uri) {
        _uiState.update { it.copy(showDeleteConfirmDialog = true, deletingPhotoUri = uri) }
    }

    /** Ferme la dialog de suppression sans rien faire. */
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteConfirmDialog = false, deletingPhotoUri = null) }
    }

    /**
     * Confirme la suppression : supprime la photo localement (MediaStore) et sur Immich.
     */
    fun confirmDeletePhoto() {
        val uri = _uiState.value.deletingPhotoUri ?: return
        _uiState.update { it.copy(showDeleteConfirmDialog = false, deletingPhotoUri = null, isDeleting = true) }

        viewModelScope.launch {
            val photoRepo = PhotoRepository.getInstance(getApplication())

            // 1. Récupérer l'ID Immich associé (peut être null si pas encore uploadé)
            val record = photoRepo.getRecordByUri(uri.toString())
            val immichId = record?.immichId

            // 2. Suppression locale via MediaStore
            val deletedLocally = withContext(Dispatchers.IO) {
                try {
                    getApplication<Application>().contentResolver.delete(uri, null, null) > 0
                } catch (_: Exception) { false }
            }

            // 3. Suppression sur Immich si un ID est connu
            if (immichId != null) {
                val baseUrl = prefs.getImmichBaseUrl()
                val apiKey  = prefs.getImmichApiKey()
                if (baseUrl.isNotBlank() && apiKey.isNotBlank()) {
                    ImmichService(baseUrl, apiKey).deleteAsset(immichId)
                }
            }

            // 4. Retrait de la base locale
            photoRepo.removeRecord(uri.toString())

            // 5. Rechargement de la liste
            loadPhotos()

            _uiState.update { it.copy(isDeleting = false) }
            _toastEvent.emit(if (deletedLocally) "Photo supprimée" else "Erreur lors de la suppression locale")
        }
    }

    // ── MediaStore ───────────────────────────────────────────────────────────

    private fun queryPhotosFromMediaStore(): List<Uri> {
        val resolver   = getApplication<Application>().contentResolver
        val projection = arrayOf(MediaStore.Images.Media._ID)
        val selection  = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
        val selArgs    = arrayOf("Pictures/Photobooth%")
        val sortOrder  = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        val list = mutableListOf<Uri>()
        resolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection, selection, selArgs, sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                list.add(ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id))
            }
        }
        return list
    }
}
