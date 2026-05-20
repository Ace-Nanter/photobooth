package ovh.pandore.photobooth.ui.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.IpWebCamService
import ovh.pandore.photobooth.worker.PhotoUploadWorker
import java.io.File
import java.util.concurrent.TimeUnit

data class MainUiState(
    val streamUrl: String = "",
    val albumLink: String = "",
    val isCapturing: Boolean = false,
    val captureSuccess: Boolean = false,
    val showPinDialog: Boolean = false,
    val pinError: Boolean = false,
    val error: String? = null,
    /** true = flux vidéo actif, false = flux perdu (affiche le warning). */
    val streamConnected: Boolean = false,
    /** Incrémenter déclenche une reconnexion dans MjpegStreamView. */
    val reconnectKey: Int = 0
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
            _uiState.update {
                it.copy(
                    streamUrl = IpWebCamService(webcamBase).getVideoStreamUrl(),
                    albumLink = albumLink
                )
            }
        }
    }

    /** Recharge le lien d'album (après modification dans les réglages). */
    fun refreshAlbumLink() {
        viewModelScope.launch {
            val albumLink = prefs.getImmichAlbumLink()
            _uiState.update { it.copy(albumLink = albumLink) }
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
                val file = savePhotoLocally(bytes)
                enqueueUpload(file)
                _uiState.update { it.copy(isCapturing = false, captureSuccess = true) }
            } else {
                _uiState.update {
                    it.copy(isCapturing = false, error = "Échec de la capture photo")
                }
            }
        }
    }

    fun dismissCaptureSuccess() {
        _uiState.update { it.copy(captureSuccess = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
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

    private fun savePhotoLocally(bytes: ByteArray): File {
        val dir = File(getApplication<Application>().filesDir, "photos").apply { mkdirs() }
        return File(dir, "photo_${System.currentTimeMillis()}.jpg").also { it.writeBytes(bytes) }
    }

    private fun enqueueUpload(file: File) {
        val work = OneTimeWorkRequestBuilder<PhotoUploadWorker>()
            .setInputData(workDataOf(PhotoUploadWorker.KEY_FILE_PATH to file.absolutePath))
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

