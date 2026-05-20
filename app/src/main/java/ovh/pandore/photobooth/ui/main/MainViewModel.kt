package ovh.pandore.photobooth.ui.main

import android.app.Application
import android.content.ContentValues
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.IpWebCamService
import ovh.pandore.photobooth.worker.PhotoUploadWorker
import java.util.concurrent.TimeUnit

@Suppress("ArrayInDataClass")
data class MainUiState(
    val streamUrl: String = "",
    val albumLink: String = "",
    val isCapturing: Boolean = false,
    /**
     * Bytes JPEG de la dernière photo prise — non null déclenche l'affichage de la vignette.
     * Redevient null après fermeture de la vignette.
     */
    val capturedPhotoBytes: ByteArray? = null,
    val showPinDialog: Boolean = false,
    val pinError: Boolean = false,
    val error: String? = null,
    /** true = flux vidéo actif, false = flux perdu (affiche le warning). */
    val streamConnected: Boolean = false,
    /** Incrémenter déclenche une reconnexion dans MjpegStreamView. */
    val reconnectKey: Int = 0,
    /** true = flash (torche) activé */
    val flashEnabled: Boolean = false,
    /** Durée d'affichage de la vignette en millisecondes (depuis les préférences). */
    val photoPreviewDurationMs: Long = 5_000L
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val webcamBase = prefs.getWebcamBaseUrl()
            val albumLink = prefs.getImmichAlbumLink()
            val previewDurationMs = prefs.getPhotoPreviewDuration() * 1_000L
            _uiState.update {
                it.copy(
                    streamUrl = IpWebCamService(webcamBase).getVideoStreamUrl(),
                    albumLink = albumLink,
                    photoPreviewDurationMs = previewDurationMs
                )
            }
        }
    }

    /** Recharge le lien d'album (après modification dans les réglages). */
    fun refreshAlbumLink() {
        viewModelScope.launch {
            val albumLink = prefs.getImmichAlbumLink()
            val previewDurationMs = prefs.getPhotoPreviewDuration() * 1_000L
            _uiState.update { it.copy(albumLink = albumLink, photoPreviewDurationMs = previewDurationMs) }
        }
    }

    // --- Capture photo ---

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            val webcamBase = prefs.getWebcamBaseUrl()
            val service = IpWebCamService(webcamBase)
            val bytes = service.capturePhoto()

            if (bytes != null) {
                val fileRef = savePhotoLocally(bytes)
                enqueueUpload(fileRef)
                _uiState.update { it.copy(isCapturing = false, capturedPhotoBytes = bytes) }
            } else {
                _uiState.update {
                    it.copy(isCapturing = false, error = "Échec de la capture photo")
                }
            }
        }
    }

    fun dismissCaptureSuccess() {
        _uiState.update { it.copy(capturedPhotoBytes = null) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // --- Flash ---

    /** Active ou désactive le flash (torche) de la caméra IP. */
    fun toggleFlash() {
        viewModelScope.launch {
            val webcamBase = prefs.getWebcamBaseUrl()
            val service = IpWebCamService(webcamBase)
            val currentlyEnabled = _uiState.value.flashEnabled
            val success = if (currentlyEnabled) service.disableTorch() else service.enableTorch()
            if (success) {
                _uiState.update { it.copy(flashEnabled = !currentlyEnabled) }
            }
        }
    }

    // --- Flux vidéo ---

    /** Appelé par MjpegStreamView à chaque changement d'état du flux. */
    fun onStreamStatusChange(isConnected: Boolean) {
        _uiState.update { it.copy(streamConnected = isConnected) }
    }

    /** Déclenche une tentative de reconnexion au flux MJPEG. */
    fun retryStream() {
        _uiState.update { it.copy(reconnectKey = it.reconnectKey + 1, streamConnected = false) }
    }

    // --- PIN ---

    fun showPinDialog() {
        _uiState.update { it.copy(showPinDialog = true, pinError = false) }
    }

    fun dismissPinDialog() {
        _uiState.update { it.copy(showPinDialog = false, pinError = false) }
    }

    fun checkPin(enteredPin: String, onCorrect: () -> Unit) {
        viewModelScope.launch {
            val savedPin = prefs.getPinCode()
            if (savedPin == enteredPin) {
                _uiState.update { it.copy(showPinDialog = false, pinError = false) }
                onCorrect()
            } else {
                _uiState.update { it.copy(pinError = true) }
            }
        }
    }

    // --- Helpers ---

    /**
     * Sauvegarde la photo dans Pictures/Photobooth via MediaStore (API 29+ garanti par minSdk).
     * Retourne la content URI string pour le suivi et l'upload.
     */
    private suspend fun savePhotoLocally(bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val fileName = "photo_${System.currentTimeMillis()}.jpg"
        val resolver = getApplication<Application>().contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Photobooth")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            ?: throw IllegalStateException("Impossible de créer l'entrée MediaStore")
        resolver.openOutputStream(uri)?.use { it.write(bytes) }
        resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
        uri.toString()
    }

    private fun enqueueUpload(fileRef: String) {
        val work = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .addTag(PhotoUploadWorker.TAG_PENDING_UPLOAD)
            .setInputData(workDataOf(PhotoUploadWorker.KEY_FILE_REF to fileRef))
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(getApplication()).enqueue(work)
    }
}

