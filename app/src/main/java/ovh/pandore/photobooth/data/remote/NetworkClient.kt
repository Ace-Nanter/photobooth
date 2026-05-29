package ovh.pandore.photobooth.data.remote

import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Fournit les instances OkHttpClient configurées pour chaque usage.
 */
object NetworkClient {

    /** Client pour le flux MJPEG continu : pas de timeout de lecture. */
    val streamingClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // Pas de timeout pour le streaming
        .build()

    /** Client pour les appels API courts (test connexion, capture photo). */
    val photoClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    /** Client pour les appels Immich (upload peut être long). */
    val immichClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    /**
     * Client dédié au scan réseau : timeouts très courts pour ne pas bloquer le balayage
     * des 254 adresses du sous-réseau local.
     */
    val scanClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(500, TimeUnit.MILLISECONDS)
        .readTimeout(500, TimeUnit.MILLISECONDS)
        .build()
}

