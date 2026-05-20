package ovh.pandore.photobooth.ui.main

import android.app.Activity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    // Garde l'écran allumé en permanence
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Rafraîchit le lien d'album (utile au retour des réglages)
    LaunchedEffect(Unit) {
        viewModel.refreshAlbumLink()
    }

    LaunchedEffect(uiState.captureSuccess) {
        if (uiState.captureSuccess) {
            snackbarHostState.showSnackbar("Photo prise ! Upload en cours…")
            viewModel.dismissCaptureSuccess()
        }
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

        // QR code en bas à gauche
        if (uiState.albumLink.isNotBlank()) {
            QrCodeImage(
                content = uiState.albumLink,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(16.dp),
                size = 180.dp
            )
        }

        // Bouton de capture centré en bas
        FilledIconButton(
            onClick = { if (!uiState.isCapturing) viewModel.capturePhoto() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 24.dp)
                .size(80.dp),
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

        // Indicateur de perte de flux en haut à droite (cliquable pour reconnecter)
        if (!uiState.streamConnected) {
            IconButton(
                onClick = viewModel::retryStream,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Flux vidéo perdu — appuyer pour reconnecter",
                    tint = Color(0xFFFFC107), // Amber
                    modifier = Modifier.size(32.dp)
                )
            }
        }

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


