package ovh.pandore.photobooth.data.remote

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import ovh.pandore.photobooth.domain.model.ImmichAlbum
import java.io.File
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
     * Upload un fichier photo vers Immich.
     * @return l'ID de l'asset créé, ou null en cas d'échec.
     */
    suspend fun uploadAsset(file: File): String? = withContext(Dispatchers.IO) {
        try {
            val now = isoFormat.format(Date(file.lastModified()))
            val fileBody = file.asRequestBody("image/jpeg".toMediaType())

            val multipartBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
                .addFormDataPart("assetData", file.name, fileBody)
                .addFormDataPart("deviceAssetId", file.name)
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

