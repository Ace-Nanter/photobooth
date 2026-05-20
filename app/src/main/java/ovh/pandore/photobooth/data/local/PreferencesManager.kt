package ovh.pandore.photobooth.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "photobooth_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        private val WEBCAM_BASE_URL = stringPreferencesKey("webcam_base_url")
        private val IMMICH_BASE_URL = stringPreferencesKey("immich_base_url")
        private val IMMICH_API_KEY = stringPreferencesKey("immich_api_key")
        private val IMMICH_ALBUM_ID = stringPreferencesKey("immich_album_id")
        private val IMMICH_ALBUM_LINK = stringPreferencesKey("immich_album_link")
        private val PIN_CODE = stringPreferencesKey("pin_code")

        const val DEFAULT_PIN = "1234"
    }

    // --- Flows ---

    val webcamBaseUrlFlow: Flow<String> = context.dataStore.data
        .map { it[WEBCAM_BASE_URL] ?: "" }

    val immichBaseUrlFlow: Flow<String> = context.dataStore.data
        .map { it[IMMICH_BASE_URL] ?: "" }

    val immichApiKeyFlow: Flow<String> = context.dataStore.data
        .map { it[IMMICH_API_KEY] ?: "" }

    val immichAlbumIdFlow: Flow<String> = context.dataStore.data
        .map { it[IMMICH_ALBUM_ID] ?: "" }

    val immichAlbumLinkFlow: Flow<String> = context.dataStore.data
        .map { it[IMMICH_ALBUM_LINK] ?: "" }

    val pinCodeFlow: Flow<String> = context.dataStore.data
        .map { it[PIN_CODE] ?: DEFAULT_PIN }

    // --- Lectures suspendues (one-shot) ---

    suspend fun getWebcamBaseUrl(): String = webcamBaseUrlFlow.first()
    suspend fun getImmichBaseUrl(): String = immichBaseUrlFlow.first()
    suspend fun getImmichApiKey(): String = immichApiKeyFlow.first()
    suspend fun getImmichAlbumId(): String = immichAlbumIdFlow.first()
    suspend fun getImmichAlbumLink(): String = immichAlbumLinkFlow.first()
    suspend fun getPinCode(): String = pinCodeFlow.first()
    suspend fun hasValidWebcamConfig(): Boolean = getWebcamBaseUrl().isNotBlank()

    // --- Écritures ---

    suspend fun saveWebcamBaseUrl(url: String) =
        context.dataStore.edit { it[WEBCAM_BASE_URL] = url }

    suspend fun saveImmichBaseUrl(url: String) =
        context.dataStore.edit { it[IMMICH_BASE_URL] = url }

    suspend fun saveImmichApiKey(apiKey: String) =
        context.dataStore.edit { it[IMMICH_API_KEY] = apiKey }

    suspend fun saveImmichAlbumId(albumId: String) =
        context.dataStore.edit { it[IMMICH_ALBUM_ID] = albumId }

    suspend fun saveImmichAlbumLink(link: String) =
        context.dataStore.edit { it[IMMICH_ALBUM_LINK] = link }

    suspend fun savePinCode(pin: String) =
        context.dataStore.edit { it[PIN_CODE] = pin }
}

