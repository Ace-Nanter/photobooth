package ovh.pandore.photobooth.ui.settings
import android.app.Activity
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import ovh.pandore.photobooth.domain.model.ImmichAlbum
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    // État local d'affichage de la clé API
    var apiKeyVisible by remember { mutableStateOf(false) }
    LaunchedEffect(uiState.saveSuccess) {
        if (uiState.saveSuccess) {
            snackbarHostState.showSnackbar("Réglages sauvegardés")
            viewModel.dismissSaveSuccess()
        }
    }
    LaunchedEffect(uiState.saveError) {
        uiState.saveError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissSaveError()
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Réglages") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            (context as? Activity)?.stopLockTask()
                            (context as? Activity)?.finishAndRemoveTask()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = "Quitter l'application",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            )
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState) { data -> Snackbar(snackbarData = data) }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            SectionTitle("Serveur Immich")
            OutlinedTextField(
                value = uiState.immichBaseUrl,
                onValueChange = viewModel::onImmichBaseUrlChange,
                label = { Text("URL du serveur Immich") },
                placeholder = { Text("https://photos.example.com") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    autoCorrectEnabled = false
                )
            )
            OutlinedTextField(
                value = uiState.immichApiKey,
                onValueChange = viewModel::onImmichApiKeyChange,
                label = { Text("Clé API Immich") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (apiKeyVisible) VisualTransformation.None
                                       else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    autoCorrectEnabled = false
                ),
                trailingIcon = {
                    IconButton(onClick = { apiKeyVisible = !apiKeyVisible }) {
                        Icon(
                            imageVector = if (apiKeyVisible) Icons.Filled.VisibilityOff
                                          else Icons.Filled.Visibility,
                            contentDescription = if (apiKeyVisible) "Masquer la clé API"
                                                 else "Afficher la clé API"
                        )
                    }
                }
            )
            Button(onClick = viewModel::saveImmichConfig, modifier = Modifier.fillMaxWidth()) {
                Text("Sauvegarder la configuration Immich")
            }
            HorizontalDivider()
            SectionTitle("Album Immich")
            if (uiState.immichAlbumId.isNotBlank()) {
                Text(
                    text = "Album sélectionné : ${uiState.selectedAlbumName.ifBlank { uiState.immichAlbumId }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            OutlinedButton(onClick = viewModel::fetchAlbums, modifier = Modifier.fillMaxWidth()) {
                if (uiState.isLoadingAlbums) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text("Charger la liste des albums")
            }
            uiState.albumsError?.let {
                Text(text = it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
            if (uiState.albums.isNotEmpty()) {
                AlbumList(
                    albums = uiState.albums,
                    selectedAlbumId = uiState.immichAlbumId,
                    onAlbumSelected = viewModel::selectAlbum
                )
            }
            HorizontalDivider()
            SectionTitle("Affichage photo")
            PhotoPreviewDurationSlider(
                durationSeconds = uiState.photoPreviewDuration,
                onDurationChange = viewModel::onPhotoPreviewDurationChange
            )
            HorizontalDivider()
            SectionTitle("Code PIN")
            Text(
                text = "Laissez vide pour conserver le code actuel.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = uiState.newPinCode,
                onValueChange = viewModel::onNewPinChange,
                label = { Text("Nouveau code PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    autoCorrectEnabled = false
                )
            )
            OutlinedTextField(
                value = uiState.confirmPinCode,
                onValueChange = viewModel::onConfirmPinChange,
                label = { Text("Confirmer le code PIN") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.NumberPassword,
                    autoCorrectEnabled = false
                )
            )
            Button(
                onClick = viewModel::savePinCode,
                enabled = uiState.newPinCode.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Changer le code PIN")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
@Composable
private fun PhotoPreviewDurationSlider(
    durationSeconds: Int,
    onDurationChange: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Durée d'affichage de la vignette",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "${durationSeconds}s",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            value = durationSeconds.toFloat(),
            onValueChange = { onDurationChange(it.toInt()) },
            valueRange = 2f..15f,
            steps = 12,   // 2,3,4,...,15 → 13 valeurs → 12 pas
            modifier = Modifier.fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("2s", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("15s", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary
    )
}
@Composable
private fun AlbumList(
    albums: List<ImmichAlbum>,
    selectedAlbumId: String,
    onAlbumSelected: (ImmichAlbum) -> Unit
) {
    Card {
        Column(modifier = Modifier.fillMaxWidth()) {
            albums.forEachIndexed { index, album ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAlbumSelected(album) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = album.id == selectedAlbumId,
                        onClick = { onAlbumSelected(album) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = album.albumName, style = MaterialTheme.typography.bodyMedium)
                }
                if (index < albums.lastIndex) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
