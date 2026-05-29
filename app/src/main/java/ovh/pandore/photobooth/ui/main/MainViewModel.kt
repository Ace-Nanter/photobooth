package ovh.pandore.photobooth.ui.main

import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
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
import ovh.pandore.photobooth.data.local.PhotoRecord
import ovh.pandore.photobooth.data.local.PhotoRepository
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.IpWebCamService
import ovh.pandore.photobooth.worker.PhotoUploadWorker
import java.util.concurrent.TimeUnit

@Suppress("ArrayInDataClass")
data class MainUiState(
    val streamUrl: String = "",
    val albumLink: String = "",
    val isCapturing: Boolean = false,
    val capturedPhotoBytes: ByteArray? = null,
    val showPinDialog: Boolean = false,
    val pinError: Boolean = false,
    val showExitPinDialog: Boolean = false,
    val exitPinError: Boolean = false,
    val error: String? = null,
    val streamConnected: Boolean = false,
    val reconnectKey: Int = 0,
    val flashEnabled: Boolean = false,
    val photoPreviewDurationMs: Long = 5_000L,
    /** Durée du minuteur avant capture, en secondes. */
    val countdownDurationSeconds: Int = 5,
    /** Indique si le compte à rebours est en cours. */
    val isCountingDown: Boolean = false,
    /** Liste des URIs de photos recentes pour le diaporama (haut-droite). */
    val recentPhotoUris: List<Uri> = emptyList()
)

class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = PreferencesManager(application)
    private val photoRepository = PhotoRepository.getInstance(application)

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    init {
        loadConfig()
        observePreviewDuration()
        observeCountdownDuration()
        loadRecentPhotos()
    }

    private fun loadConfig() {
        viewModelScope.launch {
            val webcamBase = prefs.getWebcamBaseUrl()
            val albumLink  = prefs.getImmichAlbumLink()
            _uiState.update {
                it.copy(
                    streamUrl = IpWebCamService(webcamBase).getVideoStreamUrl(),
                    albumLink = albumLink
                )
            }
        }
    }

    private fun observePreviewDuration() {
        viewModelScope.launch {
            prefs.photoPreviewDurationFlow.collect { seconds ->
                _uiState.update { it.copy(photoPreviewDurationMs = seconds * 1_000L) }
            }
        }
    }

    private fun observeCountdownDuration() {
        viewModelScope.launch {
            prefs.countdownDurationFlow.collect { seconds ->
                _uiState.update { it.copy(countdownDurationSeconds = seconds) }
            }
        }
    }

    fun refreshAlbumLink() {
        viewModelScope.launch {
            val albumLink = prefs.getImmichAlbumLink()
            _uiState.update { it.copy(albumLink = albumLink) }
        }
    }

    /** Charge (ou recharge) les URIs de photos pour le diaporama. */
    fun loadRecentPhotos() {
        viewModelScope.launch(Dispatchers.IO) {
            val uris = queryPhotosFromMediaStore()
            _uiState.update { it.copy(recentPhotoUris = uris) }
        }
    }

    // --- Countdown ---

    /** Démarre le compte à rebours (avant la capture). */
    fun startCountdown() {
        _uiState.update { it.copy(isCountingDown = true) }
    }

    /** Arrête le compte à rebours (annulation ou fin naturelle). */
    fun stopCountdown() {
        _uiState.update { it.copy(isCountingDown = false) }
    }

    // --- Capture photo ---

    fun capturePhoto() {
        viewModelScope.launch {
            _uiState.update { it.copy(isCapturing = true, error = null) }

            val webcamBase = prefs.getWebcamBaseUrl()
            val service    = IpWebCamService(webcamBase)
            val bytes      = service.capturePhoto()

            if (bytes != null) {
                val fileRef = savePhotoLocally(bytes)
                // Enregistre la photo dans le depot local avant l'upload
                photoRepository.addRecord(PhotoRecord(localUri = fileRef))
                enqueueUpload(fileRef)
                // Rafraichit le diaporama avec la nouvelle photo
                loadRecentPhotos()
                _uiState.update { it.copy(isCapturing = false, capturedPhotoBytes = bytes) }
            } else {
                _uiState.update { it.copy(isCapturing = false, error = "Echec de la capture photo") }
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

    fun toggleFlash() {
        viewModelScope.launch {
            val webcamBase      = prefs.getWebcamBaseUrl()
            val service         = IpWebCamService(webcamBase)
            val currentlyEnabled = _uiState.value.flashEnabled
            val success = if (currentlyEnabled) service.disableTorch() else service.enableTorch()
            if (success) {
                _uiState.update { it.copy(flashEnabled = !currentlyEnabled) }
            }
        }
    }

    // --- Flux video ---

    fun onStreamStatusChange(isConnected: Boolean) {
        _uiState.update { it.copy(streamConnected = isConnected) }
    }

    fun retryStream() {
        _uiState.update { it.copy(reconnectKey = it.reconnectKey + 1, streamConnected = false) }
    }

    // --- PIN ---

    fun showPinDialog() {
        _uiState.update { it.copy(showPinDialog = true, pinError = false, isCountingDown = false) }
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

    fun showExitPinDialog() {
        _uiState.update { it.copy(showExitPinDialog = true, exitPinError = false, isCountingDown = false) }
    }

    fun dismissExitPinDialog() {
        _uiState.update { it.copy(showExitPinDialog = false, exitPinError = false) }
    }

    fun checkExitPin(enteredPin: String, onCorrect: () -> Unit) {
        viewModelScope.launch {
            val savedPin = prefs.getPinCode()
            if (savedPin == enteredPin) {
                _uiState.update { it.copy(showExitPinDialog = false, exitPinError = false) }
                onCorrect()
            } else {
                _uiState.update { it.copy(exitPinError = true) }
            }
        }
    }

    // --- Helpers ---

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
            ?: throw IllegalStateException("Impossible de creer l'entree MediaStore")
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

    /** Requete MediaStore pour les photos dans Pictures/Photobooth, triees par date DESC. */
    private fun queryPhotosFromMediaStore(): List<Uri> {
        val context  = getApplication<Application>()
        val resolver = context.contentResolver
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
        return list.take(30)
    }
}
