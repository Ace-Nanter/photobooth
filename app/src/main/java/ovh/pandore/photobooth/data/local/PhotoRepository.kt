package ovh.pandore.photobooth.data.local

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Depot persistant pour les enregistrements de photos.
 * Serialise en JSON dans filesDir/photo_records.json. Thread-safe via Mutex.
 * Singleton partage entre Worker et ViewModels.
 */
class PhotoRepository private constructor(context: Context) {

    companion object {
        private const val FILE_NAME = "photo_records.json"

        @Volatile
        private var INSTANCE: PhotoRepository? = null

        fun getInstance(context: Context): PhotoRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: PhotoRepository(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val gson = Gson()
    private val file = File(context.filesDir, FILE_NAME)
    private val mutex = Mutex()

    suspend fun getAllRecords(): List<PhotoRecord> = withContext(Dispatchers.IO) {
        mutex.withLock { readFromFile().sortedByDescending { it.capturedAt } }
    }

    suspend fun addRecord(record: PhotoRecord) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readFromFile().toMutableList()
            if (records.none { it.localUri == record.localUri }) {
                records.add(record)
                writeToFile(records)
            }
        }
    }

    suspend fun updateImmichId(localUri: String, immichId: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readFromFile().toMutableList()
            val index = records.indexOfFirst { it.localUri == localUri }
            if (index >= 0) {
                records[index] = records[index].copy(immichId = immichId)
                writeToFile(records)
            }
        }
    }

    /** Retourne le record associé à l'URI locale, ou null si non trouvé. */
    suspend fun getRecordByUri(localUri: String): PhotoRecord? = withContext(Dispatchers.IO) {
        mutex.withLock { readFromFile().firstOrNull { it.localUri == localUri } }
    }

    /** Supprime le record associé à l'URI locale. */
    suspend fun removeRecord(localUri: String) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val records = readFromFile().toMutableList()
            records.removeIf { it.localUri == localUri }
            writeToFile(records)
        }
    }

    private fun readFromFile(): List<PhotoRecord> {
        if (!file.exists()) return emptyList()
        return try {
            val type = object : TypeToken<List<PhotoRecord>>() {}.type
            gson.fromJson<List<PhotoRecord>>(file.readText(), type) ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeToFile(records: List<PhotoRecord>) {
        try {
            file.writeText(gson.toJson(records))
        } catch (_: Exception) { }
    }
}

