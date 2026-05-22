package ovh.pandore.photobooth.domain.model

import com.google.gson.annotations.SerializedName

/**
 * Représente un lien de partage Immich (GET /api/shared-links).
 */
data class ImmichSharedLink(
    @SerializedName("id")              val id: String,
    @SerializedName("key")             val key: String,
    @SerializedName("type")            val type: String,          // "ALBUM" | "INDIVIDUAL"
    @SerializedName("albumId")         val albumId: String?,      // présent si type == "ALBUM"
    @SerializedName("album")           val album: ImmichAlbumRef?,
    @SerializedName("allowDownload")   val allowDownload: Boolean,
    @SerializedName("allowUpload")     val allowUpload: Boolean,
    @SerializedName("showExif")        val showExif: Boolean,
    @SerializedName("password")        val password: String?,     // null = pas de mot de passe
    @SerializedName("expiresAt")       val expiresAt: String?     // null = pas d'expiration
)

/** Référence légère à l'album embarquée dans un SharedLink. */
data class ImmichAlbumRef(
    @SerializedName("id")        val id: String,
    @SerializedName("albumName") val albumName: String
)

