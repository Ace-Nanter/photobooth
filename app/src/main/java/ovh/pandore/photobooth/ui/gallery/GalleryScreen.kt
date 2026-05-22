package ovh.pandore.photobooth.ui.gallery

import android.net.Uri
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.pandore.photobooth.ui.components.QrCodeImage
import ovh.pandore.photobooth.ui.main.UriThumbnailImage

/**
 * Vue Galerie : affiche toutes les photos Photobooth, propose le partage par email
 * et affiche le QR code de l'album Immich.
 *
 * Navigation : accessible depuis l'ecran principal sans code PIN.
 * Le bouton retour systeme et le bouton "Retour" ramenent vers l'ecran principal.
 * Le mode kiosque reste actif (pas de sortie vers le systeme).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateBack: () -> Unit,
    viewModel: GalleryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    // Index de la photo actuellement affichée en plein écran (null = viewer fermé)
    var viewerIndex by remember { mutableStateOf<Int?>(null) }

    BackHandler(enabled = true) {
        if (viewerIndex != null) {
            viewerIndex = null
        } else {
            onNavigateBack()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.loadPhotos()
    }

    LaunchedEffect(uiState.submitMessage) {
        uiState.submitMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSubmitMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Galerie") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour"
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState) { data ->
                Snackbar(snackbarData = data)
            }
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
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(
                                onDone = { viewModel.submitEmail() }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = viewModel::submitEmail,
                            enabled = !uiState.isSubmitting
                            // Pas de fillMaxWidth — largeur naturelle du label
                        ) {
                            if (uiState.isSubmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary
                                )
                            } else {
                                Text("Soumettre")
                            }
                        }
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
                    photoUris   = uiState.photoUris,
                    initialIndex = idx,
                    onClose     = { viewerIndex = null }
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
            Box(
                modifier = Modifier.size(boxSize),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(modifier = Modifier.size(40.dp))
            }
        }
        is AlbumLinkState.NoAlbumSelected -> {
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp)
                    ),
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
                    .background(
                        MaterialTheme.colorScheme.errorContainer,
                        RoundedCornerShape(8.dp)
                    ),
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
                detectHorizontalDragGestures { _, _ -> } // absorb accidental swipes on cells
            }
    ) {
        UriThumbnailImage(
            uri = uri,
            modifier = Modifier
                .fillMaxSize()
                    .pointerInput(Unit) {
                    // Utilise detectTapGestures pour distinguer tap vs swipe
                    detectTapGestures(
                        onTap = { onClick() }
                    )
                },
            targetSize = 300,
            contentScale = ContentScale.Crop
        )
    }
}

// ── Visionneuse plein écran avec navigation ────────────────────────────────────

/**
 * Overlay plein écran pour visualiser une photo.
 * - Croix en haut à droite pour fermer.
 * - Boutons < et > pour naviguer entre les photos.
 * - Swipe gauche/droite pour naviguer.
 */
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
                ) { _, delta ->
                    dragAccum += delta
                }
            }
    ) {
        // Photo centrée
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

        // Bouton fermer (croix) — haut droite
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Fermer",
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        // Bouton précédent (<) — bord gauche
        if (currentIndex > 0) {
            IconButton(
                onClick = { goPrev() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBackIos,
                    contentDescription = "Photo précédente",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Bouton suivant (>) — bord droit
        if (currentIndex < photoUris.lastIndex) {
            IconButton(
                onClick = { goNext() },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                    contentDescription = "Photo suivante",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        // Compteur de photos — bas centre
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
