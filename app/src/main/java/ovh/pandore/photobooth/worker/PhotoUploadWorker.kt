package ovh.pandore.photobooth.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.ImmichService
import java.io.File

/**
 * Worker qui upload une photo vers Immich puis supprime le fichier local.
 *
 * Paramètre d'entrée :
 *  - [KEY_FILE_REF] : référence du fichier à uploader.
 *    Peut être un chemin absolu (API < 29) ou une content URI string (API 29+).
 *
 * Toutes les tâches en attente sont taguées [TAG_PENDING_UPLOAD],
 * ce qui permet à l'application de lister les uploads en cours.
 */
class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_REF = "file_ref"
        /** Tag WorkManager commun pour lister les photos en attente d'upload. */
        const val TAG_PENDING_UPLOAD = "pending_photo_upload"

        // Rétrocompatibilité avec l'ancienne clé si nécessaire
        @Deprecated("Utiliser KEY_FILE_REF", ReplaceWith("KEY_FILE_REF"))
        const val KEY_FILE_PATH = "file_path"
    }

    override suspend fun doWork(): Result {
        // Supporte les deux clés pour la rétrocompatibilité
        @Suppress("DEPRECATION")
        val fileRef = inputData.getString(KEY_FILE_REF)
            ?: inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure()

        val prefs = PreferencesManager(applicationContext)
        val immichBaseUrl = prefs.getImmichBaseUrl()
        val immichApiKey = prefs.getImmichApiKey()
        val immichAlbumId = prefs.getImmichAlbumId()

        // Si Immich n'est pas configuré, on réessaie plus tard
        if (immichBaseUrl.isBlank() || immichApiKey.isBlank() || immichAlbumId.isBlank()) {
            return Result.retry()
        }

        val service = ImmichService(immichBaseUrl, immichApiKey)

        // Résout les bytes selon le type de référence (content URI ou chemin fichier)
        val bytes = resolveBytes(fileRef) ?: return Result.failure()

        val assetId = service.uploadAsset(fileRef, bytes) ?: return Result.retry()
        val added = service.addAssetToAlbum(assetId, immichAlbumId)

        return if (added) {
            deleteFile(fileRef)
            Result.success()
        } else {
            Result.retry()
        }
    }

    /** Lit les bytes depuis un content URI ou un chemin fichier absolu. */
    private fun resolveBytes(fileRef: String): ByteArray? {
        return try {
            if (fileRef.startsWith("content://")) {
                val uri = Uri.parse(fileRef)
                applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val file = File(fileRef)
                if (file.exists()) file.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Supprime le fichier ou l'entrée MediaStore après upload réussi. */
    private fun deleteFile(fileRef: String) {
        try {
            if (fileRef.startsWith("content://")) {
                val uri = Uri.parse(fileRef)
                applicationContext.contentResolver.delete(uri, null, null)
            } else {
                File(fileRef).delete()
            }
        } catch (_: Exception) { }
    }
}

