package ovh.pandore.photobooth.ui.main

import android.app.Activity
import android.graphics.BitmapFactory
import android.media.AudioAttributes
import android.media.SoundPool
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.pandore.photobooth.R
import kotlinx.coroutines.delay
import ovh.pandore.photobooth.ui.components.MjpegStreamView
import ovh.pandore.photobooth.ui.components.QrCodeImage

@Composable
fun MainScreen(
    onNavigateToSettings: () -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // SoundPool pour le son de déclencheur
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
    val shutterSoundId = remember(soundPool) {
        soundPool.load(context, R.raw.photo, 1)
    }
    DisposableEffect(soundPool) {
        onDispose { soundPool.release() }
    }

    // État d'expansion du QR code
    var qrExpanded by remember { mutableStateOf(false) }

    // Animation de pulse périodique sur le QR code (toutes les 10 s)
    val qrPulseScale = remember { Animatable(1f) }
    LaunchedEffect(uiState.albumLink) {
        if (uiState.albumLink.isNotBlank()) {
            while (true) {
                delay(10_000L)
                qrPulseScale.animateTo(1.13f, animationSpec = tween(380))
                qrPulseScale.animateTo(1f,    animationSpec = tween(380))
            }
        }
    }

    // Garde l'écran allumé en permanence
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Rafraîchit le lien d'album et la durée de vignette (utile au retour des réglages)
    LaunchedEffect(Unit) {
        viewModel.refreshAlbumLink()
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissError()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Flux vidéo MJPEG plein écran
        if (uiState.streamUrl.isNotBlank()) {
            MjpegStreamView(
                streamUrl = uiState.streamUrl,
                modifier = Modifier.fillMaxSize(),
                reconnectKey = uiState.reconnectKey,
                onStreamStatusChange = viewModel::onStreamStatusChange
            )
        }

        // QR code en haut à droite — pulse périodique + clic pour agrandir
        if (uiState.albumLink.isNotBlank()) {
            QrCodeImage(
                content = uiState.albumLink,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .scale(qrPulseScale.value)
                    .clickable { qrExpanded = true },
                size = 140.dp
            )
        }

        // Bouton de capture centré en bas + bouton Flash à sa droite
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bouton Flash
            FilledIconButton(
                onClick = { viewModel.toggleFlash() },
                modifier = Modifier.size(56.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (uiState.flashEnabled)
                        Color(0xFFFFC107).copy(alpha = 0.9f)
                    else
                        Color.White.copy(alpha = 0.55f),
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

            // Bouton de capture
            FilledIconButton(
                onClick = {
                    if (!uiState.isCapturing) {
                        soundPool.play(shutterSoundId, 1f, 1f, 0, 0, 1f)
                        viewModel.capturePhoto()
                    }
                },
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

        // Bouton réglages en haut à gauche
        IconButton(
            onClick = viewModel::showPinDialog,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Settings,
                contentDescription = "Réglages",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        // Indicateur de perte de flux — décalé à gauche pour ne pas chevaucher le QR code
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

        // Overlay QR code agrandi
        QrExpandedOverlay(
            visible = qrExpanded && uiState.albumLink.isNotBlank(),
            content = uiState.albumLink,
            onDismiss = { qrExpanded = false }
        )

        // Vignette animée de la photo prise
        CapturedPhotoOverlay(
            photoBytes = uiState.capturedPhotoBytes,
            durationMs = uiState.photoPreviewDurationMs,
            onDismiss = viewModel::dismissCaptureSuccess
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.TopCenter)
        ) { data -> Snackbar(snackbarData = data) }
    }

    // Dialog de saisie du code PIN
    if (uiState.showPinDialog) {
        PinDialog(
            hasError = uiState.pinError,
            onConfirm = { pin -> viewModel.checkPin(pin, onCorrect = onNavigateToSettings) },
            onDismiss = viewModel::dismissPinDialog
        )
    }
}

/**
 * Overlay plein écran pour afficher le QR code agrandi.
 * Un appui sur le fond sombre ou sur la croix referme la vue.
 */
@Composable
private fun QrExpandedOverlay(
    visible: Boolean,
    content: String,
    onDismiss: () -> Unit
) {
    // Fond assombri — clic n'importe où pour fermer
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(220)),
        exit  = fadeOut(tween(350))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .clickable(onClick = onDismiss)
        )
    }

    // QR code centré avec zoom-in spring + bouton fermer
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(180)) + scaleIn(
            initialScale = 0.20f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness    = Spring.StiffnessMediumLow
            )
        ),
        exit  = fadeOut(tween(300)) + scaleOut(
            targetScale   = 0.20f,
            animationSpec = tween(300)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            QrCodeImage(content = content, size = 310.dp)

            // Croix de fermeture en haut à droite de l'overlay
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}

/**
 * Overlay plein écran affiché après la prise de photo.
 * La vignette apparaît depuis le centre en zoom-in spring, reste [durationMs] ms,
 * puis disparaît en zoom-out fluide.
 */
@Composable
private fun CapturedPhotoOverlay(
    photoBytes: ByteArray?,
    durationMs: Long,
    onDismiss: () -> Unit
) {
    // Convertit les bytes en ImageBitmap une seule fois par nouvelle photo
    val imageBitmap = remember(photoBytes) {
        photoBytes?.let {
            BitmapFactory.decodeByteArray(it, 0, it.size)?.asImageBitmap()
        }
    }

    // Timer : auto-fermeture après durationMs, repart si une nouvelle photo arrive
    LaunchedEffect(photoBytes) {
        if (photoBytes != null) {
            delay(durationMs)
            onDismiss()
        }
    }

    // Fond assombri
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

    // Vignette
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
            targetScale  = 0.04f,
            animationSpec = tween(380)
        ),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (imageBitmap != null) {
                Image(
                    bitmap = imageBitmap,
                    contentDescription = "Photo prise",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth(0.70f)
                        .aspectRatio(imageBitmap.width.toFloat() / imageBitmap.height.toFloat())
                        .border(
                            width = 6.dp,
                            color = Color.White,
                            shape = RoundedCornerShape(12.dp)
                        )
                        .clip(RoundedCornerShape(12.dp))
                )
            }
        }
    }
}

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword)
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
