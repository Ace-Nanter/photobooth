package ovh.pandore.photobooth.worker

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import ovh.pandore.photobooth.data.local.PhotoRepository
import ovh.pandore.photobooth.data.local.PreferencesManager
import ovh.pandore.photobooth.data.remote.ImmichService

/**
 * Worker qui upload une photo vers Immich et sauvegarde l'ID Immich en local.
 * Les fichiers locaux ne sont PAS supprimés après upload.
 *
 * Parametre d'entree :
 *  - [KEY_FILE_REF] : référence du fichier (content URI string ou chemin absolu).
 *
 * Toutes les taches en attente sont taguees [TAG_PENDING_UPLOAD].
 */
class PhotoUploadWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    companion object {
        const val KEY_FILE_REF = "file_ref"
        const val TAG_PENDING_UPLOAD = "pending_photo_upload"

        @Deprecated("Utiliser KEY_FILE_REF", ReplaceWith("KEY_FILE_REF"))
        const val KEY_FILE_PATH = "file_path"
    }

    override suspend fun doWork(): Result {
        @Suppress("DEPRECATION")
        val fileRef = inputData.getString(KEY_FILE_REF)
            ?: inputData.getString(KEY_FILE_PATH)
            ?: return Result.failure()

        val prefs = PreferencesManager(applicationContext)
        val immichBaseUrl = prefs.getImmichBaseUrl()
        val immichApiKey  = prefs.getImmichApiKey()
        val immichAlbumId = prefs.getImmichAlbumId()

        if (immichBaseUrl.isBlank() || immichApiKey.isBlank() || immichAlbumId.isBlank()) {
            return Result.retry()
        }

        val service = ImmichService(immichBaseUrl, immichApiKey)
        val bytes   = resolveBytes(fileRef) ?: return Result.failure()

        // Récupère le DISPLAY_NAME MediaStore pour que le fichier ait un nom lisible avec timestamp
        // sur Immich (ex: "photobooth_20240101_120000.jpg") plutôt que le seul ID numérique.
        val displayName = resolveDisplayName(fileRef)

        val assetId = service.uploadAsset(fileRef, bytes, displayName) ?: return Result.retry()
        val added   = service.addAssetToAlbum(assetId, immichAlbumId)

        return if (added) {
            // Sauvegarde l'ID Immich dans le dépôt local — le fichier est CONSERVE sur le disque.
            PhotoRepository.getInstance(applicationContext).updateImmichId(fileRef, assetId)
            Result.success()
        } else {
            Result.retry()
        }
    }

    private fun resolveBytes(fileRef: String): ByteArray? {
        return try {
            if (fileRef.startsWith("content://")) {
                val uri = Uri.parse(fileRef)
                applicationContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            } else {
                val f = java.io.File(fileRef)
                if (f.exists()) f.readBytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Résout le DISPLAY_NAME depuis MediaStore pour une content URI,
     * ou extrait le nom depuis le chemin absolu pour un fichier ordinaire.
     * Retourne null si la résolution échoue (le service utilisera son propre fallback).
     */
    private fun resolveDisplayName(fileRef: String): String? {
        return try {
            if (fileRef.startsWith("content://")) {
                val uri = Uri.parse(fileRef)
                applicationContext.contentResolver.query(
                    uri,
                    arrayOf(MediaStore.Images.Media.DISPLAY_NAME),
                    null, null, null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            } else {
                val name = java.io.File(fileRef).name
                name.ifBlank { null }
            }
        } catch (_: Exception) {
            null
        }
    }
}
