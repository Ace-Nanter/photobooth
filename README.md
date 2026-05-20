# 📸 Photobooth

Application Android de photobooth pour événements, conçue pour tourner en mode kiosque sur une tablette **Samsung SM-T590** (Android 10, Snapdragon 450).

## Fonctionnement

Le setup repose sur :
- **IP WebCam** (application Android) installée sur un téléphone, qui expose un flux MJPEG et une API de capture photo.
- **Immich** comme serveur de stockage de photos, accessible via une clé API.

L'application affiche le flux vidéo en plein écran, permet de prendre des photos en un bouton, et upload automatiquement chaque photo vers un album Immich dédié en arrière-plan.

## Architecture

```
app/src/main/java/ovh/pandore/photobooth/
├── data/
│   ├── local/          → PreferencesManager (DataStore)
│   └── remote/         → IpWebCamService, ImmichService, NetworkClient
├── domain/model/       → ImmichAlbum
├── navigation/         → AppNavigation (NavHost)
├── ui/
│   ├── components/     → MjpegStreamView, QrCodeImage
│   ├── launch/         → LaunchScreen + ViewModel
│   ├── main/           → MainScreen + ViewModel
│   ├── settings/       → SettingsScreen + ViewModel
│   └── theme/
├── worker/             → PhotoUploadWorker (WorkManager)
├── MainActivity.kt     → Mode kiosque, immersif
└── PhotoboothApplication.kt
```

## Pages

### Page de lancement
Saisie de l'URL de la caméra IP WebCam (format `http://192.168.x.x:8080`).
Validation de la connexion avant d'accéder à la page principale.

### Page principale
- Flux vidéo MJPEG plein écran (décodage hardware)
- QR code de l'album Immich (bas gauche)
- Bouton de capture (bas centre)
- Bouton réglages (haut gauche, protégé par PIN)
- Indicateur ⚠️ de perte de flux (haut droite, cliquable pour reconnecter)

### Page de réglages
- Configuration de l'URL et clé API Immich
- Sélection de l'album cible
- Modification du code PIN
- Bouton pour quitter l'application

## Prérequis

- Android **10** minimum (API 29)
- Tablette en réseau Wi-Fi avec le téléphone IP WebCam
- Instance Immich accessible depuis la tablette

## Configuration initiale

1. Lancer **IP WebCam** sur le téléphone (noter l'adresse IP affichée)
2. Lancer l'application Photobooth sur la tablette
3. Entrer l'URL au format `http://192.168.x.x:8080`
4. Dans les réglages (PIN: `1234` par défaut — **à changer**) :
   - Saisir l'URL de l'instance Immich
   - Saisir la clé API Immich
   - Sélectionner l'album cible

## Dépendances principales

| Bibliothèque | Usage |
|---|---|
| Jetpack Compose | Interface utilisateur |
| Navigation Compose | Navigation entre pages |
| OkHttp | Flux MJPEG + appels API |
| WorkManager | Upload photos en arrière-plan |
| DataStore Preferences | Persistance des paramètres |
| ZXing | Génération du QR code |

## Notes techniques

- **Mode kiosque** : `startLockTask()` actif uniquement sur la page principale (pas sur la page de lancement). Nécessite une confirmation système au premier lancement.
- **HTTP en clair** : `android:usesCleartextTraffic="true"` requis car IP WebCam ne supporte pas HTTPS (réseau local uniquement).
- **Décodage hardware** : `Bitmap.Config.HARDWARE` force la décompression JPEG sur le GPU/DSP du Snapdragon 450.

