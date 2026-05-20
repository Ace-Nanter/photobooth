package ovh.pandore.photobooth.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

/**
 * Service pour interagir avec l'application IP WebCam.
 * L'API IP WebCam expose :
 *  - GET /photo.jpg  → capture une photo JPEG
 *  - GET /video      → flux MJPEG continu
 */
class IpWebCamService(private val baseUrl: String) {

    private val normalizedBase get() = baseUrl.trimEnd('/')

    /** URL complète du flux vidéo MJPEG. */
    fun getVideoStreamUrl(): String = "$normalizedBase/video"

    /**
     * Capture une photo et retourne les bytes JPEG, ou null en cas d'erreur.
     */
    suspend fun capturePhoto(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBase/photo.jpg")
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Teste si la connexion à l'appareil est possible.
     * Effectue un HEAD sur /photo.jpg puis un GET si nécessaire.
     * On accepte tout code 2xx — le Content-Type peut varier selon la version d'IP WebCam.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBase/photo.jpg")
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}

