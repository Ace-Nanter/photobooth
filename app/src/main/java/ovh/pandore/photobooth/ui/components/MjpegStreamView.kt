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
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * Composable affichant un flux vidéo MJPEG en plein écran.
 *
 * Le décodage JPEG utilise [Bitmap.Config.HARDWARE] pour
 * déléguer le décodage au hardware (codec matériel sur Snapdragon 450).
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

    // La clé combine l'URL et reconnectKey : tout changement redémarre le flux
    DisposableEffect(streamUrl, reconnectKey) {
        val client = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()

        val hwOptions = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.HARDWARE
        }

        val job: Job = CoroutineScope(Dispatchers.IO).launch {
            try {
                val request = Request.Builder().url(streamUrl).build()
                client.newCall(request).execute().use { response ->
                    val inputStream = response.body?.byteStream() ?: run {
                        withContext(Dispatchers.Main) { onStreamStatusChange(false) }
                        return@use
                    }
                    val buffered = BufferedInputStream(inputStream, 65536)
                    val jpegBuffer = ByteArrayOutputStream(65536)
                    val readBuffer = ByteArray(8192)
                    var prevByte = -1
                    var inJpeg = false
                    var firstFrameReceived = false

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
                                    val frameData = jpegBuffer.toByteArray()
                                    val bitmap = BitmapFactory.decodeByteArray(
                                        frameData, 0, frameData.size, hwOptions
                                    )
                                    if (bitmap != null) {
                                        withContext(Dispatchers.Main) {
                                            currentBitmap = bitmap
                                            if (!firstFrameReceived) {
                                                firstFrameReceived = true
                                                onStreamStatusChange(true)
                                            }
                                        }
                                    }
                                    jpegBuffer.reset()
                                    inJpeg = false
                                }
                            }
                            prevByte = b
                        }
                    }
                }
                // Fin normale du flux
                withContext(Dispatchers.Main) { onStreamStatusChange(false) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { onStreamStatusChange(false) }
            }
        }

        onDispose { job.cancel() }
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
