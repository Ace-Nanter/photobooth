package ovh.pandore.photobooth.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ovh.pandore.photobooth.domain.model.ImmichAlbum
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Service pour interagir avec l'API Immich.
 */
class ImmichService(
    private val baseUrl: String,
    private val apiKey: String
) {
    private val gson = Gson()
    private val normalizedBase get() = baseUrl.trimEnd('/')
    private val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Récupère la liste des albums Immich.
     */
    suspend fun getAlbums(): List<ImmichAlbum> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBase/api/albums")
                .header("x-api-key", apiKey)
                .get()
                .build()
            NetworkClient.immichClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val json = response.body?.string() ?: return@withContext emptyList()
                val type = object : TypeToken<List<ImmichAlbum>>() {}.type
                gson.fromJson(json, type) ?: emptyList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Upload les bytes d'une photo vers Immich.
     * @param fileRef  chemin ou content URI utilisé pour dériver le nom de fichier.
     * @param bytes    contenu JPEG brut de la photo.
     * @return l'ID de l'asset créé, ou null en cas d'échec.
     */
    suspend fun uploadAsset(fileRef: String, bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        try {
            val now = isoFormat.format(Date())
            // Dérive un nom de fichier lisible depuis la référence
            val fileName = fileRef.substringAfterLast('/').substringAfterLast(':')
                .ifBlank { "photo_${System.currentTimeMillis()}.jpg" }
            val fileBody = bytes.toRequestBody("image/jpeg".toMediaType())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("assetData", fileName, fileBody)
                .addFormDataPart("deviceAssetId", fileName)
                .addFormDataPart("deviceId", "photobooth-android")
                .addFormDataPart("fileCreatedAt", now)
                .addFormDataPart("fileModifiedAt", now)
                .addFormDataPart("isFavorite", "false")
                .build()

            val request = Request.Builder()
                .url("$normalizedBase/api/assets")
                .header("x-api-key", apiKey)
                .post(multipartBody)
                .build()

            NetworkClient.immichClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val json = response.body?.string() ?: return@withContext null
                JsonParser.parseString(json).asJsonObject?.get("id")?.asString
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Récupère tous les liens de partage de type ALBUM pour l'album donné.
     * GET /api/shared-links
     */
    suspend fun getAlbumSharedLinks(albumId: String): List<ImmichSharedLink> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$normalizedBase/api/shared-links")
                    .header("x-api-key", apiKey)
                    .get()
                    .build()
                NetworkClient.immichClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val json = response.body?.string() ?: return@withContext emptyList()
                    val type = object : TypeToken<List<ImmichSharedLink>>() {}.type
                    val all: List<ImmichSharedLink> = gson.fromJson(json, type) ?: emptyList()
                    // Filtre uniquement les liens appartenant à l'album souhaité
                    all.filter { link ->
                        link.type == "ALBUM" &&
                                (link.albumId == albumId || link.album?.id == albumId)
                    }
                }
            } catch (e: Exception) {
                emptyList()
            }
        }

    /**
     * Crée un lien de partage public pour l'album (sans mot de passe, avec téléchargement).
     * POST /api/shared-links
     * @return le lien créé, ou null en cas d'échec.
     */
    suspend fun createAlbumShareLink(albumId: String): ImmichSharedLink? =
        withContext(Dispatchers.IO) {
            try {
                val bodyMap = mapOf(
                    "type"           to "ALBUM",
                    "albumId"        to albumId,
                    "allowDownload"  to true,
                    "allowUpload"    to false,
                    "showExif"       to true
                )
                val body = gson.toJson(bodyMap).toRequestBody("application/json".toMediaType())
                val request = Request.Builder()
                    .url("$normalizedBase/api/shared-links")
                    .header("x-api-key", apiKey)
                    .post(body)
                    .build()
                NetworkClient.immichClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext null
                    val json = response.body?.string() ?: return@withContext null
                    gson.fromJson(json, ImmichSharedLink::class.java)
                }
            } catch (e: Exception) {
                null
            }
        }

    /**
     * Construit l'URL publique d'un lien de partage à partir de sa clé.
     */
    fun buildShareUrl(linkKey: String): String = "$normalizedBase/share/$linkKey"

    /**
     * Ajoute un asset à un album.
     * @return true si l'ajout a réussi.
     */
    suspend fun addAssetToAlbum(assetId: String, albumId: String): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(mapOf("ids" to listOf(assetId)))
                    .toRequestBody("application/json".toMediaType())

                val request = Request.Builder()
                    .url("$normalizedBase/api/albums/$albumId/assets")
                    .header("x-api-key", apiKey)
                    .put(body)
                    .build()

                NetworkClient.immichClient.newCall(request).execute().use { response ->
                    response.isSuccessful
                }
            } catch (e: Exception) {
                false
            }
        }
}

