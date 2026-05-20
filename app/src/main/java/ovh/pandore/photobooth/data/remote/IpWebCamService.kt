package ovh.pandore.photobooth.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Service pour interagir avec l'application IP WebCam.
 * L'API IP WebCam expose :
 *  - GET /photoaf.jpg                              → capture une photo JPEG (avec autofocus)
 *  - GET /video                                    → flux MJPEG continu
 *  - GET /settings/focusmode?set=continuous-picture → configure l'autofocus continu
 *  - POST /focus                                   → déclenche la mise au point
 *  - POST /enabletorch                             → active le flash/torche
 *  - POST /disabletorch                            → désactive le flash/torche
 */
class IpWebCamService(private val baseUrl: String) {

    private val normalizedBase get() = baseUrl.trimEnd('/')

    /** URL complète du flux vidéo MJPEG. */
    fun getVideoStreamUrl(): String = "$normalizedBase/video"

    /**
     * Configure la caméra avant démarrage du flux :
     *  1. Passe l'autofocus en mode continu-photo.
     *  2. Déclenche une mise au point immédiate.
     * Les erreurs sont ignorées silencieusement (la caméra peut quand même fonctionner).
     */
    suspend fun setupCamera() = withContext(Dispatchers.IO) {
        try {
            // 1. Réglage du mode de mise au point
            val focusRequest = Request.Builder()
                .url("$normalizedBase/settings/focusmode?set=continuous-picture")
                .get()
                .build()
            NetworkClient.photoClient.newCall(focusRequest).execute().use { /* ignore */ }
        } catch (_: Exception) { }

        try {
            // 2. Déclenchement de la mise au point
            val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val triggerRequest = Request.Builder()
                .url("$normalizedBase/focus")
                .post(emptyBody)
                .build()
            NetworkClient.photoClient.newCall(triggerRequest).execute().use { /* ignore */ }
        } catch (_: Exception) { }
    }

    /**
     * Capture une photo avec autofocus et retourne les bytes JPEG, ou null en cas d'erreur.
     */
    suspend fun capturePhoto(): ByteArray? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBase/photoaf.jpg")
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.bytes() else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Active le flash / la torche de la caméra.
     * Retourne true si la commande a été acceptée.
     */
    suspend fun enableTorch(): Boolean = withContext(Dispatchers.IO) {
        try {
            val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url("$normalizedBase/enabletorch")
                .post(emptyBody)
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Désactive le flash / la torche de la caméra.
     * Retourne true si la commande a été acceptée.
     */
    suspend fun disableTorch(): Boolean = withContext(Dispatchers.IO) {
        try {
            val emptyBody = "".toRequestBody("application/x-www-form-urlencoded".toMediaType())
            val request = Request.Builder()
                .url("$normalizedBase/disabletorch")
                .post(emptyBody)
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Teste si la connexion à l'appareil est possible.
     * On accepte tout code 2xx — le Content-Type peut varier selon la version d'IP WebCam.
     */
    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$normalizedBase/photoaf.jpg")
                .build()
            NetworkClient.photoClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }
}

