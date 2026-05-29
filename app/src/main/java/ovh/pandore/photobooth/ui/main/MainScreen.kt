package ovh.pandore.photobooth.ui.main

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ovh.pandore.photobooth.MainActivity
import ovh.pandore.photobooth.R
import ovh.pandore.photobooth.ui.components.MjpegStreamView

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToGallery: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val activity = context as? MainActivity

    BackHandler(enabled = true) {
        viewModel.showExitPinDialog()
    }

    val soundPool = remember {
        SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
    }
    val shutterSoundId = remember(soundPool) { soundPool.load(context, R.raw.photo, 1) }
    DisposableEffect(soundPool) { onDispose { soundPool.release() } }

    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAlbumLink()
        viewModel.loadRecentPhotos()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenAspect = maxWidth.value / maxHeight.value
        val videoAspect  = 16f / 9f

        val letterboxTopDp: Dp = if (screenAspect < videoAspect) {
            ((maxHeight - maxWidth / videoAspect) / 2).coerceAtLeast(0.dp)
        } else 0.dp

        val settingsBtnTopPadding: Dp = ((letterboxTopDp - 48.dp) / 2).coerceAtLeast(4.dp)

        if (uiState.streamUrl.isNotBlank()) {
            MjpegStreamView(
                streamUrl = uiState.streamUrl,
                modifier = Modifier.fillMaxSize(),
                reconnectKey = uiState.reconnectKey,
                onStreamStatusChange = viewModel::onStreamStatusChange
            )
        }

        // Diaporama de photos en haut à droite (remplace le QR code)
        if (uiState.recentPhotoUris.isNotEmpty()) {
            PhotoSlideshow(
                photoUris = uiState.recentPhotoUris,
                onNavigateToGallery = onNavigateToGallery,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
                size = 140.dp
            )
        }

        val buttonsDisabled = uiState.capturedPhotoBytes != null
                || uiState.showPinDialog
                || uiState.showExitPinDialog
                || uiState.isCountingDown
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilledIconButton(
                onClick = { viewModel.toggleFlash() },
                enabled = !buttonsDisabled,
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (uiState.flashEnabled)
                        Color(0xFFFFC107).copy(alpha = 0.9f)
                    else Color.White.copy(alpha = 0.55f),
                    contentColor = Color.Black
                )
            ) {
                Icon(
                    imageVector = if (uiState.flashEnabled) Icons.Filled.FlashOn else Icons.Filled.FlashOff,
                    contentDescription = if (uiState.flashEnabled) "Désactiver le flash" else "Activer le flash",
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            FilledIconButton(
                onClick = {
                    if (!uiState.isCapturing && !uiState.isCountingDown) {
                        viewModel.startCountdown()
                    }
                },
                enabled = !buttonsDisabled,
                modifier = Modifier.size(80.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = Color.White.copy(alpha = 0.85f),
                    contentColor = Color.Black
                )
            ) {
                if (uiState.isCapturing) {
                    CircularProgressIndicator(modifier = Modifier.size(36.dp), strokeWidth = 3.dp)
                } else {
                    Icon(
                        imageVector = Icons.Filled.PhotoCamera,
                        contentDescription = "Prendre une photo",
                        modifier = Modifier.size(40.dp)
                    )
                }
            }
        }

        IconButton(
            onClick = viewModel::showPinDialog,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 8.dp, top = settingsBtnTopPadding)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Réglages",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        if (!uiState.streamConnected) {
            IconButton(
                onClick = viewModel::retryStream,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 168.dp, top = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Flux vidéo perdu — appuyer pour reconnecter",
                    tint = Color(0xFFFFC107),
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        CountdownOverlay(
            isVisible = uiState.isCountingDown,
            durationSeconds = uiState.countdownDurationSeconds,
            onCaptureTrigger = {
                soundPool.play(shutterSoundId, 1f, 1f, 0, 0, 1f)
                viewModel.capturePhoto()
            },
            onDone = viewModel::stopCountdown
        )

        CapturedPhotoOverlay(
            photoBytes = uiState.capturedPhotoBytes,
            durationMs = uiState.photoPreviewDurationMs,
            onDismiss = viewModel::dismissCaptureSuccess,
            onNavigateToGallery = {
                viewModel.dismissCaptureSuccess()
                onNavigateToGallery()
            }
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        ) { data -> Snackbar(snackbarData = data) }
    }

    if (uiState.showPinDialog) {
        PinDialog(
            hasError = uiState.pinError,
            onConfirm = { pin -> viewModel.checkPin(pin, onCorrect = onNavigateToSettings) },
            onDismiss = viewModel::dismissPinDialog
        )
    }

    if (uiState.showExitPinDialog) {
        ExitPinDialog(
            hasError = uiState.exitPinError,
            onConfirm = { pin ->
                viewModel.checkExitPin(pin) {
                    activity?.exitKioskMode()
                    activity?.finishAndRemoveTask()
                }
            },
            onDismiss = viewModel::dismissExitPinDialog
        )
    }
}

// ── Diaporama de photos ────────────────────────────────────────────────────────

/**
 * Affiche un diaporama animé des photos récentes dans un cadre de [size].
 * Change de photo toutes les 3 secondes avec un fondu enchaîné.
 * Un clic navigue vers la Galerie.
 */
@Composable
private fun PhotoSlideshow(
    photoUris: List<Uri>,
    onNavigateToGallery: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 140.dp
) {
    var currentIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(photoUris.size) {
        if (photoUris.size > 1) {
            while (true) {
                delay(3_000L)
                currentIndex = (currentIndex + 1) % photoUris.size
            }
        }
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(12.dp))
            .clickable(onClick = onNavigateToGallery)
    ) {
        AnimatedContent(
            targetState = currentIndex.coerceIn(photoUris.indices),
            transitionSpec = {
                fadeIn(tween(700)) togetherWith fadeOut(tween(700))
            },
            label = "photo_slideshow"
        ) { index ->
            val uri = photoUris.getOrNull(index)
            if (uri != null) {
                UriThumbnailImage(
                    uri = uri,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(Color.DarkGray))
            }
        }
    }
}

// ── Chargement asynchrone de vignettes ────────────────────────────────────────

/**
 * Charge et affiche une vignette depuis un content URI de façon asynchrone.
 */
@Composable
internal fun UriThumbnailImage(
    uri: Uri,
    modifier: Modifier = Modifier,
    targetSize: Int = 400,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            loadThumbnailBitmap(context.contentResolver, uri, targetSize)
        }
    }

    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale
        )
    } else {
        Box(modifier = modifier.background(Color.DarkGray.copy(alpha = 0.5f)))
    }
}

/**
 * Décode une vignette depuis un content URI avec downsampling intelligent.
 */
internal fun loadThumbnailBitmap(
    resolver: android.content.ContentResolver,
    uri: Uri,
    targetSize: Int
): Bitmap? {
    return try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }

        var sampleSize = 1
        while (opts.outWidth / sampleSize > targetSize || opts.outHeight / sampleSize > targetSize) {
            sampleSize *= 2
        }

        resolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(
                stream, null,
                BitmapFactory.Options().apply { inSampleSize = sampleSize }
            )
        }
    } catch (_: Exception) {
        null
    }
}

// ── Overlay compte à rebours ────────────────────────────────────────────────────

/**
 * Décalage en ms appliqué avant la fin du compte à rebours pour compenser
 * la latence de l'appel HTTP de capture.
 */
private const val CAPTURE_LATENCY_OFFSET_MS = 400L

/**
 * Overlay plein-écran affichant un compte à rebours animé avant la prise de vue.
 * [onCaptureTrigger] est appelé [CAPTURE_LATENCY_OFFSET_MS] ms avant la fin
 * pour compenser la latence réseau. [onDone] est appelé à la fin du décompte.
 */
@Composable
private fun CountdownOverlay(
    isVisible: Boolean,
    durationSeconds: Int,
    onCaptureTrigger: () -> Unit,
    onDone: () -> Unit
) {
    // Chiffre affiché en cours (0 = décompte terminé, on affiche rien)
    var currentSecond by remember(isVisible, durationSeconds) { mutableIntStateOf(durationSeconds) }

    // Animation de pulsation sur chaque changement de chiffre
    val pulseScale = remember { Animatable(1f) }

    // Timer principal — annulé automatiquement si isVisible passe à false
    LaunchedEffect(isVisible, durationSeconds) {
        if (!isVisible) return@LaunchedEffect
        val totalMs = durationSeconds * 1_000L
        val captureMs = (totalMs - CAPTURE_LATENCY_OFFSET_MS).coerceAtLeast(0L)
        var elapsed = 0L
        var captured = false
        val tickMs = 50L

        while (elapsed < totalMs) {
            delay(tickMs)
            elapsed += tickMs
            val remaining = (totalMs - elapsed).coerceAtLeast(0L)
            val newSecond = kotlin.math.ceil(remaining / 1_000.0).toInt().coerceAtLeast(1)
            if (newSecond != currentSecond) {
                currentSecond = newSecond
                // Pulsation à chaque changement de chiffre
                pulseScale.snapTo(1.4f)
                pulseScale.animateTo(1f, animationSpec = tween(350))
            }
            if (!captured && elapsed >= captureMs) {
                onCaptureTrigger()
                captured = true
            }
        }
        onDone()
    }

    // Fond légèrement assombri pour lisibilité sans masquer la vidéo
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(200)),
        exit  = fadeOut(tween(300))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.30f))
        )
    }

    // Chiffre central animé
    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(tween(150)),
        exit  = fadeOut(tween(250)),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = currentSecond,
                transitionSpec = {
                    (slideInVertically(tween(300)) { -it / 2 } +
                     scaleIn(initialScale = 1.8f, animationSpec = tween(300)) +
                     fadeIn(tween(200))) togetherWith
                    (slideOutVertically(tween(250)) { it / 2 } +
                     scaleOut(targetScale = 0.4f, animationSpec = tween(250)) +
                     fadeOut(tween(200)))
                },
                label = "countdown_number"
            ) { second ->
                Text(
                    text = second.toString(),
                    fontSize = 280.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.scale(pulseScale.value),
                    style = androidx.compose.ui.text.TextStyle(
                        shadow = androidx.compose.ui.graphics.Shadow(
                            color = Color.Black.copy(alpha = 0.8f),
                            blurRadius = 32f
                        )
                    )
                )
            }
        }
    }
}

// ── Overlay photo prise ────────────────────────────────────────────────────────

/**
 * Overlay plein écran après la prise de photo.
 * Affiche la photo avec un bouton "Récupérer les photos" pour accéder à la Galerie.
 */
@Composable
private fun CapturedPhotoOverlay(
    photoBytes: ByteArray?,
    durationMs: Long,
    onDismiss: () -> Unit,
    onNavigateToGallery: () -> Unit
) {
    val imageBitmap = remember(photoBytes) {
        photoBytes?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
        }
    }

    LaunchedEffect(photoBytes) {
        if (photoBytes != null) {
            delay(durationMs)
            onDismiss()
        }
    }

    AnimatedVisibility(
        visible = photoBytes != null,
        enter = fadeIn(animationSpec = tween(250)),
        exit  = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
        )
    }

    AnimatedVisibility(
        visible = photoBytes != null && imageBitmap != null,
        enter = fadeIn(tween(200)) + scaleIn(
            initialScale = 0.04f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        ),
        exit = fadeOut(tween(380)) + scaleOut(
            targetScale   = 0.04f,
            animationSpec = tween(380)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (imageBitmap != null) {
                    Image(
                        bitmap = imageBitmap,
                        contentDescription = "Photo prise",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxWidth(0.70f)
                            .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height.toFloat())
                            .border(width = 6.dp, color = Color.White, shape = RoundedCornerShape(12.dp))
                            .clip(RoundedCornerShape(12.dp))
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onNavigateToGallery,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color.White.copy(alpha = 0.92f),
                        contentColor   = Color.Black
                    )
                ) {
                    Text("Récupérer les photos")
                }
            }
        }
    }
}

// ── Dialogs PIN ────────────────────────────────────────────────────────────────

@Composable
private fun PinDialog(
    hasError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Accès réglages") },
        text = {
            Column {
                Text("Entrez le code PIN pour accéder aux réglages.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { pinValue = it },
                    label = { Text("Code PIN") },
                    singleLine = true,
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text("Code incorrect", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(pinValue) })
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pinValue) }) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}

@Composable
private fun ExitPinDialog(
    hasError: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var pinValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Quitter l'application") },
        text = {
            Column {
                Text("Entrez le code PIN pour déverrouiller et quitter l'application.")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = pinValue,
                    onValueChange = { pinValue = it },
                    label = { Text("Code PIN") },
                    singleLine = true,
                    isError = hasError,
                    supportingText = if (hasError) {
                        { Text("Code incorrect", color = MaterialTheme.colorScheme.error) }
                    } else null,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(onDone = { onConfirm(pinValue) })
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(pinValue) }) { Text("Valider") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    )
}
