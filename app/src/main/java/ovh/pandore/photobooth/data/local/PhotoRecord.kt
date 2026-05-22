package ovh.pandore.photobooth.data.local

/**
 * Enregistrement d'une photo prise par le photobooth.
 * Associe la référence locale (content URI) à l'ID Immich après upload.
 */
data class PhotoRecord(
    /** Content URI string (ex: content://media/external/images/media/123) */
    val localUri: String,
    /** ID de l'asset Immich après upload réussi ; null si pas encore uploadé. */
    val immichId: String? = null,
    /** Timestamp de la capture (ms depuis l'epoch). */
    val capturedAt: Long = System.currentTimeMillis()
)

