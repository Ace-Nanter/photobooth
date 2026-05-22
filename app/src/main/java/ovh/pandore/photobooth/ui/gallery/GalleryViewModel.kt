package ovh.pandore.photobooth.ui.gallery

import android.app.Application
import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.ImmichService

// ── États possibles pour le lien de partage de l'album ──────────────────────

sealed class AlbumLinkState {
    /** Aucun album n'est configuré dans les réglages. */
    object NoAlbumSelected : AlbumLinkState()
    /** Chargement en cours (appels API Immich). */
    object Loading : AlbumLinkState()
    /** Lien prêt à être affiché en QR Code. */
    data class Ready(val url: String) : AlbumLinkState()
    /** Erreur lors de la récupération/création du lien. */
    data class Error(val message: String) : AlbumLinkState()
}

data class GalleryUiState(
    val photoUris: List<Uri> = emptyList(),
    val albumLinkState: AlbumLinkState = AlbumLinkState.Loading,
    val email: String = "",
    val isSubmitting: Boolean = false,
    val submitMessage: String? = null,
    val submitError: String? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    init {
        resolveAlbumLink()
        loadPhotos()
    }

    // ── Résolution du lien Immich ────────────────────────────────────────────

    /**
     * 1. Vérifie si un album est sélectionné.
     * 2. Récupère les liens de partage existants.
     * 3. Sélectionne le premier lien public (sans mot de passe, avec téléchargement).
     * 4. Si aucun lien éligible, en crée un nouveau.
     */
    fun resolveAlbumLink() {
        viewModelScope.launch {
            _uiState.update { it.copy(albumLinkState = AlbumLinkState.Loading) }

            val albumId   = prefs.getImmichAlbumId()
            val baseUrl   = prefs.getImmichBaseUrl()
            val apiKey    = prefs.getImmichApiKey()

            if (albumId.isBlank()) {
                _uiState.update { it.copy(albumLinkState = AlbumLinkState.NoAlbumSelected) }
                return@launch
            }

            if (baseUrl.isBlank() || apiKey.isBlank()) {
                _uiState.update {
                    it.copy(albumLinkState = AlbumLinkState.Error("Configuration Immich incomplète"))
                }
                return@launch
            }

            val service = ImmichService(baseUrl, apiKey)

            // Étape 1 : liens existants
            val existingLinks = service.getAlbumSharedLinks(albumId)

            // Étape 2 : chercher un lien public sans mot de passe avec téléchargement
            val eligible = existingLinks.firstOrNull { link ->
                link.allowDownload && link.password.isNullOrBlank()
            }

            val shareUrl = if (eligible != null) {
                // Étape 2 : lien trouvé
                service.buildShareUrl(eligible.key)
            } else {
                // Étape 3 : créer un nouveau lien
                val created = service.createAlbumShareLink(albumId)
                if (created != null) {
                    service.buildShareUrl(created.key)
                } else {
                    null
                }
            }

            // Étape 4 : mettre à jour l'état
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
     * Envoie l'adresse email au webservice.
     * TODO: Implémenter l'appel API réel quand l'endpoint sera défini.
     */
    fun submitEmail() {
        val email = _uiState.value.email.trim()
        if (email.isBlank()) {
            _uiState.update { it.copy(submitError = "Veuillez entrer votre adresse email") }
            return
        }
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            _uiState.update { it.copy(submitError = "Adresse email invalide") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isSubmitting = true, submitError = null) }
            // TODO: Remplacer par l'appel API réel vers le webservice
            delay(500L)
            _uiState.update {
                it.copy(
                    isSubmitting = false,
                    submitMessage = "Email envoyé !",
                    email = ""
                )
            }
        }
    }

    fun dismissSubmitMessage() {
        _uiState.update { it.copy(submitMessage = null, submitError = null) }
    }

    // ── MediaStore ───────────────────────────────────────────────────────────

    private fun queryPhotosFromMediaStore(): List<Uri> {
        val resolver  = getApplication<Application>().contentResolver
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
                list.add(
                    ContentUris.withAppendedId(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id
                    )
                )
            }
        }
        return list
    }
}
