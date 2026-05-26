package ovh.pandore.photobooth.ui.gallery

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowBackIos
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.pandore.photobooth.ui.components.QrCodeImage
import ovh.pandore.photobooth.ui.main.UriThumbnailImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = true) {
        if (viewerIndex != null) viewerIndex = null else onNavigateBack()
    }

    // Chargement des photos à l'entrée
    LaunchedEffect(Unit) { viewModel.loadPhotos() }

    // Toast one-shot (succès ou erreur API)
    LaunchedEffect(Unit) {
        viewModel.toastEvent.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    // Dialog de confirmation envoi email
    if (uiState.showConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConfirmDialog,
            title = { Text("Confirmation") },
            text = {
                Text("Le lien sera envoyé à ${uiState.email.trim()}.\nÊtes-vous sûr ?")
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmAndSubmitEmail) {
                    Text("Oui")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConfirmDialog) {
                    Text("Non")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galerie") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // ── Section supérieure 50 / 50 ───────────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                ) {
                    // ── Moitié gauche : section email ─────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Recevoir un email avec le lien de toutes les photos !",
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Start
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Champ email + icône Send sur la même ligne
                        OutlinedTextField(
                            value = uiState.email,
                            onValueChange = viewModel::onEmailChange,
                            label = { Text("Adresse email") },
                            placeholder = { Text("exemple@email.com") },
                            singleLine = true,
                            isError = uiState.submitError != null,
                            supportingText = uiState.submitError?.let { err ->
                                { Text(err, color = MaterialTheme.colorScheme.error) }
                            },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Send
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { viewModel.requestSubmitEmail() }
                            ),
                            trailingIcon = {
                                if (uiState.isSubmitting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    IconButton(
                                        onClick = viewModel::requestSubmitEmail,
                                        enabled = !uiState.isSubmitting
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Send,
                                            contentDescription = "Envoyer"
                                        )
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    VerticalDivider()

                    // ── Moitié droite : QR code ───────────────────────────────────
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Scannez ce QR Code pour accéder aux photos !",
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        AlbumQrCodeSection(state = uiState.albumLinkState)
                    }
                }

                HorizontalDivider()

                // ── Grille des photos ─────────────────────────────────────────────
                if (uiState.photoUris.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Aucune photo prise pour l'instant.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement   = Arrangement.spacedBy(4.dp)
                    ) {
                        items(uiState.photoUris, key = { it.toString() }) { uri ->
                            val index = uiState.photoUris.indexOf(uri)
                            GalleryPhotoCell(uri = uri, onClick = { viewerIndex = index })
                        }
                    }
                }
            }

            // ── Visionneuse plein écran ───────────────────────────────────────────
            val idx = viewerIndex
            if (idx != null && uiState.photoUris.isNotEmpty()) {
                PhotoViewer(
                    photoUris    = uiState.photoUris,
                    initialIndex = idx,
                    onClose      = { viewerIndex = null }
                )
            }
        }
    }
}

// ── Section QR Code adaptée à l'état du lien album ────────────────────────────

@Composable
private fun AlbumQrCodeSection(state: AlbumLinkState) {
    val boxSize = 140.dp
    when (state) {
        is AlbumLinkState.Loading -> {
            Box(modifier = Modifier.size(boxSize), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
        is AlbumLinkState.NoAlbumSelected -> {
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Aucun album\nsélectionné",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        is AlbumLinkState.Ready -> {
            QrCodeImage(content = state.url, size = boxSize)
        }
        is AlbumLinkState.Error -> {
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = state.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

// ── Cellule de photo dans la grille ───────────────────────────────────────────

@Composable
private fun GalleryPhotoCell(uri: Uri, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(6.dp))
            .background(Color.DarkGray.copy(alpha = 0.3f))
            .pointerInput(Unit) {
                detectHorizontalDragGestures { _, _ -> }
            }
    ) {
        UriThumbnailImage(
            uri = uri,
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onClick() })
                },
            targetSize = 300,
            contentScale = ContentScale.Crop
        )
    }
}

// ── Visionneuse plein écran avec navigation ────────────────────────────────────

@Composable
private fun PhotoViewer(
    photoUris: List<Uri>,
    initialIndex: Int,
    onClose: () -> Unit
) {
    var currentIndex by remember(initialIndex) { mutableStateOf(initialIndex) }
    val density = LocalDensity.current
    var dragAccum by remember { mutableFloatStateOf(0f) }
    val swipeThresholdPx = with(density) { 80.dp.toPx() }

    val goNext = { if (currentIndex < photoUris.lastIndex) currentIndex++ }
    val goPrev = { if (currentIndex > 0) currentIndex-- }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.95f))
            .pointerInput(currentIndex) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        when {
                            dragAccum >  swipeThresholdPx -> goPrev()
                            dragAccum < -swipeThresholdPx -> goNext()
                        }
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f }
                ) { _, delta -> dragAccum += delta }
            }
    ) {
        val uri = photoUris.getOrNull(currentIndex)
        if (uri != null) {
            UriThumbnailImage(
                uri = uri,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 72.dp, vertical = 56.dp),
                targetSize = 1200,
                contentScale = ContentScale.Fit
            )
        }

        IconButton(
            onClick = onClose,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = Color.White,
                modifier = Modifier.size(32.dp))
        }

        if (currentIndex > 0) {
            IconButton(
                onClick = { goPrev() },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBackIos, contentDescription = "Photo précédente",
                    tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        if (currentIndex < photoUris.lastIndex) {
            IconButton(
                onClick = { goNext() },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = "Photo suivante",
                    tint = Color.White, modifier = Modifier.size(36.dp))
            }
        }

        Text(
            text = "${currentIndex + 1} / ${photoUris.size}",
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
