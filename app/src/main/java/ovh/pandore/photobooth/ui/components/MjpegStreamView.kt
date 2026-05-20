package ovh.pandore.photobooth.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Buffer JPEG réutilisable sans copie.
 *
 * [ByteArrayOutputStream] déclare [buf] et [count] comme champs `protected`,
 * ce qui nous permet d'y accéder directement depuis cette sous-classe pour
 * appeler [BitmapFactory.decodeByteArray] sur le buffer interne **sans jamais
 * allouer un tableau intermédiaire** (contrairement à [ByteArrayOutputStream.toByteArray]).
 */
private class ReusableJpegBuffer(capacity: Int) : ByteArrayOutputStream(capacity) {
    /** Décode le contenu courant du buffer en [Bitmap] — zéro copie. */
    fun decodeAsBitmap(opts: BitmapFactory.Options): Bitmap? =
        BitmapFactory.decodeByteArray(buf, 0, count, opts)
}

/**
 * Composable affichant un flux vidéo MJPEG en plein écran.
 *
 * ## Architecture de décodage
 * Deux coroutines tournent en parallèle dans un même [CoroutineScope] :
 * 1. **Decode job** (`Dispatchers.IO`) — lit le flux réseau, détecte les marqueurs
 *    JPEG et décode chaque frame via [Bitmap.Config.HARDWARE] pour que les pixels
 *    restent en mémoire GPU. L'écriture dans le [MutableStateFlow] est **non-suspending**
 *    (`flow.value = bitmap`) : le thread réseau ne s'arrête jamais pour attendre le rendu.
 * 2. **UI job** (`Dispatchers.Main`) — collecte le [MutableStateFlow]. Comme un
 *    [MutableStateFlow] est naturellement conflated, les frames intermédiaires sont
 *    automatiquement ignorées si Compose est occupé : seule la dernière frame est rendue.
 *
 * ## Zéro copie
 * [ReusableJpegBuffer] expose le tableau interne de [ByteArrayOutputStream] directement
 * à [BitmapFactory], supprimant l'allocation de [ByteArrayOutputStream.toByteArray]
 * à chaque frame.
 *
 * @param reconnectKey  Incrémenter cette valeur déclenche une reconnexion.
 * @param onStreamStatusChange  Appelé avec `true` dès la première image reçue,
 *                              avec `false` en cas d'erreur ou de fin de flux.
 */
@Composable
fun MjpegStreamView(
    streamUrl: String,
    modifier: Modifier = Modifier,
    reconnectKey: Int = 0,
    onStreamStatusChange: (isConnected: Boolean) -> Unit = {}
) {
    var currentBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // La clé combine l'URL et reconnectKey : tout changement recrée le scope et redémarre.
    DisposableEffect(streamUrl, reconnectKey) {
        // StateFlow conflated : le decode job écrit sans jamais bloquer sur le Main thread.
        val frameFlow = MutableStateFlow<Bitmap?>(null)

        // SupervisorJob : l'échec d'un enfant ne propage pas aux autres.
        // Un seul scope.cancel() dans onDispose suffit pour tout arrêter proprement.
        val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

        // ── UI job ──────────────────────────────────────────────────────────────
        // Collecte sur le Main thread. StateFlow étant conflated, si Compose rend
        // lentement, les frames intermédiaires sont ignorées automatiquement.
        scope.launch(Dispatchers.Main) {
            var firstFrameReceived = false
            frameFlow.collect { bmp ->
                if (bmp != null) {
                    currentBitmap = bmp
                    if (!firstFrameReceived) {
                        firstFrameReceived = true
                        onStreamStatusChange(true)
                    }
                }
            }
        }

        // ── Decode job ──────────────────────────────────────────────────────────
        // Reste intégralement sur Dispatchers.IO. Ne se suspend jamais pour le
        // rendu UI : frameFlow.value est une affectation non-suspending.
        scope.launch {
            try {
                val client = OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(0, TimeUnit.MILLISECONDS)
                    .build()

                // HARDWARE : les pixels restent en mémoire GPU → Compose/Skia
                // les consomme sans copie CPU↔GPU lors du rendu.
                val hwOptions = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.HARDWARE
                }

                val request = Request.Builder().url(streamUrl).build()
                client.newCall(request).execute().use { response ->
                    val inputStream = response.body?.byteStream() ?: run {
                        withContext(Dispatchers.Main) { onStreamStatusChange(false) }
                        return@use
                    }

                    // Buffers généreux pour réduire les appels système et la pression GC.
                    // 128 Ko d'entrée réseau + 128 Ko pour le frame JPEG courant.
                    val buffered  = BufferedInputStream(inputStream, 131_072)
                    val jpegBuffer = ReusableJpegBuffer(131_072)
                    val readBuffer = ByteArray(32_768)
                    var prevByte = -1
                    var inJpeg   = false

                    while (isActive) {
                        val bytesRead = buffered.read(readBuffer)
                        if (bytesRead == -1) break

                        for (i in 0 until bytesRead) {
                            val b = readBuffer[i].toInt() and 0xFF

                            if (!inJpeg) {
                                if (prevByte == 0xFF && b == 0xD8) {
                                    inJpeg = true
                                    jpegBuffer.reset()
                                    jpegBuffer.write(0xFF)
                                    jpegBuffer.write(0xD8)
                                }
                            } else {
                                jpegBuffer.write(b)
                                if (prevByte == 0xFF && b == 0xD9) {
                                    // Décodage depuis le buffer interne : zéro copie.
                                    val bitmap = jpegBuffer.decodeAsBitmap(hwOptions)
                                    if (bitmap != null) {
                                        // Non-suspending : le decode job ne s'arrête
                                        // jamais pour attendre que l'UI ait rendu.
                                        frameFlow.value = bitmap
                                    }
                                    jpegBuffer.reset()
                                    inJpeg = false
                                }
                            }
                            prevByte = b
                        }
                    }
                }
                withContext(Dispatchers.Main) { onStreamStatusChange(false) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onStreamStatusChange(false) }
            }
        }

        // Annule les deux coroutines et libère le scope en un seul appel.
        onDispose { scope.cancel() }
    }

    Box(
        modifier = modifier.background(androidx.compose.ui.graphics.Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val bmp = currentBitmap
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    }
}
