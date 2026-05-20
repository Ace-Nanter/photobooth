package ovh.pandore.photobooth.domain.model

import com.google.gson.annotations.SerializedName

data class ImmichAlbum(
    @SerializedName("id") val id: String,
    @SerializedName("albumName") val albumName: String
)

