package ovh.pandore.photobooth.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.ImmichService
import java.io.File

/**
 * Worker qui upload une photo vers Immich puis supprime le fichier local.
 *
 * Paramètre d'entrée :
 *  - [KEY_FILE_PATH] : chemin absolu du fichier JPEG à uploader.
 */
class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_PATH = "file_path"
    }

    override suspend fun doWork(): Result {
        val filePath = inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure()

        val file = File(filePath)
        if (!file.exists()) return Result.failure()

        val prefs = PreferencesManager(applicationContext)
        val immichBaseUrl = prefs.getImmichBaseUrl()
        val immichApiKey = prefs.getImmichApiKey()
        val immichAlbumId = prefs.getImmichAlbumId()

        // Si Immich n'est pas configuré, on réessaie plus tard
        if (immichBaseUrl.isBlank() || immichApiKey.isBlank() || immichAlbumId.isBlank()) {
            return Result.retry()
        }

        val service = ImmichService(immichBaseUrl, immichApiKey)

        val assetId = service.uploadAsset(file) ?: return Result.retry()
        val added = service.addAssetToAlbum(assetId, immichAlbumId)

        return if (added) {
            file.delete()
            Result.success()
        } else {
            Result.retry()
        }
    }
}

