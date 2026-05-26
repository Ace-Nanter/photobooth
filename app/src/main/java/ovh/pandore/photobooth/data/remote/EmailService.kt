package ovh.pandore.photobooth.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import ovh.pandore.photobooth.BuildConfig

/**
 * Service d'envoi d'email via le webhook n8n.
 *
 * L'URL et la clé API sont injectées depuis secrets.properties via BuildConfig
 * et ne sont jamais committées dans le VCS.
 *
 * POST EMAIL_WEBHOOK_URL
 * Header : x-api-key: EMAIL_WEBHOOK_API_KEY
 * Body   : { "email": "<adresse>" }
 */
object EmailService {

    private val webhookUrl get() = BuildConfig.EMAIL_WEBHOOK_URL
    private val apiKey     get() = BuildConfig.EMAIL_WEBHOOK_API_KEY

    /**
     * Envoie l'adresse email au webhook pour recevoir le lien album.
     * @return Result.success(Unit) si la requête aboutit (HTTP 2xx),
     *         Result.failure(Exception) sinon.
     */
    suspend fun sendEmailLink(email: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val json = """{"email":"$email"}"""
            val body = json.toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(webhookUrl)
                .header("x-api-key", apiKey)
                .post(body)
                .build()

            NetworkClient.photoClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.failure(Exception("Erreur serveur : ${response.code}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

