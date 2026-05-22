# Projet Photobooth

# Spécifications principales

L'application à développer est une application qui tournera sur une tablette Samsung SM590 avec 3go de RAM et un processeur **Qualcomm Snapdragon 450**.
L'application s'affiche sous le nom **"Photobooth"** dans le tiroir d'applications Android (`app_name` = `Photobooth` dans `strings.xml`).
L'application consiste à mettre à disposition un "photobooth", c'est-à-dire laisser à des personnes durant un évènement la possibilité de se prendre en photo avec leurs proches.
Pour cela le setup sera basé sur une caméra IP (téléphone portable avec l'application IP WebCam installée et lancée, qui expose un flux MJPEG), qui donne accès à un flux vidéo ainsi qu'à une API pour prendre des photos.
Les photos prises sont sauvegardées dans le dossier public **`Pictures/Photobooth`** de l'appareil. L'application maintient une liste interne des photos en attente d'upload via la file WorkManager (tag `pending_photo_upload`).
Un process background les envoie vers une instance Immich, dans un album dédié, à l'aide d'une clé API. **Les photos ne sont plus supprimées après upload** : elles sont conservées sur le disque. Une base de données interne (fichier JSON `photo_records.json` dans `filesDir`) associe le chemin/content URI local à l'ID Immich de l'asset après upload réussi.

## Page de lancement

* La page de lancement s'affiche **à chaque démarrage ou reprise de l'application** (retour depuis le gestionnaire de tâches, déverrouillage, etc.).
* Il faut mettre l'adresse IP du téléphone pour avoir un BASE\_URL valable à travers le runtime de l'application.
* Le champ est pré-rempli avec la dernière adresse utilisée pour éviter de la ressaisir.
* Un bouton launch valide le formulaire qui récupère l'IP du téléphone et si l'URL peut être jointe et qu'un flux vidéo peut être récupéré, alors :
  1. La connexion est validée via `GET /photoaf.jpg`.
  2. Les réglages caméra sont configurés :
     - `GET /settings/focusmode?set=continuous-picture` — autofocus continu activé.
     - `POST /focus` — mise au point immédiate déclenchée.
  3. Navigation vers la page principale.

## Page principale

* Affichage en plein écran du flux vidéo exposé par l'application IP WebCam. Utilisation du décodage matériel obligatoire pour économiser les ressources.
* **Architecture de décodage optimisée** (`MjpegStreamView`) :
  * **Zéro copie JPEG** : sous-classe `ReusableJpegBuffer` de `ByteArrayOutputStream` qui expose le tableau interne `buf/count` (champs `protected`) directement à `BitmapFactory.decodeByteArray`, supprimant l'allocation de `.toByteArray()` par frame.
  * **`Bitmap.Config.HARDWARE`** : les pixels restent en mémoire GPU, consommés par Compose/Skia sans transfert CPU↔GPU.
  * **`MutableStateFlow` conflated** : le decode job écrit via `flow.value = bitmap` (non-suspending — le thread réseau ne s'arrête jamais pour attendre le rendu). Le collecteur UI sur `Dispatchers.Main` saute automatiquement les frames intermédiaires si Compose est occupé.
  * **Buffers agrandis** : `BufferedInputStream` 128 Ko, `readBuffer` 32 Ko — moins d'appels système et moins de pression GC.
  * **`CoroutineScope(Dispatchers.IO + SupervisorJob())`** : un seul `scope.cancel()` en `onDispose` arrête proprement les deux coroutines (decode + UI collector).
* Verrouillage des fonctions de navigation système **actif uniquement sur cette page et les pages suivantes (réglages, galerie)** :
  * Mode immersif permanent (barre de navigation et barre de statut masquées).
  * **Bouton retour physique protégé par PIN** : appuyer sur retour depuis l'écran principal affiche une dialog « Quitter l'application » demandant le code PIN. Si le code est correct : le mode kiosque est désactivé (`stopLockTask`) et l'application se ferme (`finishAndRemoveTask`). Si le code est incorrect, un message d'erreur s'affiche dans la dialog.
  * **Screen Pinning Android** activé automatiquement à l'arrivée sur cette page (l'utilisateur confirme une seule fois ; home et recents sont ensuite bloqués).
  * Le mode kiosque est **désactivé** dès que l'application revient à la page de lancement (reprise, icônisation, etc.) : la navigation système redevient normale sur la page de lancement.
* **Diaporama de photos** en haut à droite (~140 dp) : remplace l'ancien QR code. Affiche les photos prises dans `Pictures/Photobooth`, extraites via MediaStore. Les photos défilent avec un fondu enchaîné automatique toutes les 3 secondes. Un clic sur le diaporama navigue directement vers la **vue Galerie** (sans PIN). Les photos sont mises à jour à chaque capture et au démarrage.
* **Bouton de prise de vue** (centré en bas) → au clic, un **son de déclencheur** (`res/raw/photo.mp3`) est joué instantanément via `SoundPool`, puis l'appel `GET /photoaf.jpg` est exécuté en background ; la photo est sauvegardée dans `Pictures/Photobooth` puis mise en file d'upload. Après la capture, une **vignette animée** de la photo s'affiche :
  * Apparition depuis le centre en zoom-in avec animation spring (rebond léger, `DampingRatioMediumBouncy`), accompagnée d'un fondu du fond assombri.
  * La photo occupe environ **70 % de la largeur de l'écran**, avec un ratio d'aspect réel, une **bordure blanche de 6 dp** et des coins arrondis.
  * La vignette reste affichée pendant la durée configurée dans les réglages (défaut 5 s), puis disparaît en zoom-out + fondu. La durée est observée en temps réel depuis le DataStore via un Flow : toute modification dans les réglages est immédiatement prise en compte sans nécessiter un redémarrage ou un retour de navigation.
  * **En dessous de la photo**, un bouton **"Récupérer les photos"** permet de naviguer directement vers la vue Galerie (dismiss overlay + navigation). La vignette se ferme automatiquement si l'utilisateur n'agit pas.
  * Aucune popup / snackbar n'est affichée en plus de cette vignette.
  * **Pendant l'affichage de la vignette**, les boutons de prise de vue et de flash sont désactivés.
* **Bouton Flash** (à gauche du bouton de prise de vue) → bascule sur/hors du flash (torche) via `POST /enabletorch` / `POST /disabletorch`. L'icône indique l'état actuel (éclair jaune = actif, grisé = inactif).
* **Désactivation des boutons d'action** : les boutons de prise de vue et de flash sont désactivés (`enabled = false`) dans les cas suivants : vignette d'aperçu visible, dialog PIN réglages affichée, dialog PIN de sortie affichée.
* Bouton "roue crantée" **en haut à gauche** pour accéder aux réglages. L'appui sur ce bouton demande un code PIN.
* Si le code est correct alors la navigation se fait vers la page des réglages.
* **Positionnement du bouton réglages** : utilise `BoxWithConstraints` pour calculer dynamiquement la bande letterbox entre la vidéo 720p (16:9) et les bords de l'écran. Sur la tablette cible SM-T590 (résolution 1920×1200, ~213 DPI, soit ~1443×902 dp en paysage), la vidéo 16:9 laisse une bande letterbox d'environ 45 dp en haut et en bas. Le bouton réglages est centré verticalement dans cette bande, hors de la zone vidéo.
* **Surveillance du flux vidéo** : si le flux est perdu, un indicateur d'avertissement (⚠️ orange) apparaît en haut à droite. Un appui sur cet indicateur déclenche une tentative de reconnexion immédiate.

## Page Galerie

* Accessible **sans code PIN** depuis l'écran principal (clic sur le diaporama ou bouton "Récupérer les photos").
* **Mode kiosque actif** : le retour système intercepté par `BackHandler` renvoie vers l'écran principal (pas de sortie vers le launcher). Pas de possibilité d'accéder à l'OS via cette vue.
* **Bouton "Retour"** dans la `TopAppBar` pour revenir à l'écran principal (equivalent au bouton retour système).
* **Section supérieure en deux moitiés égales (50/50)** :
  * **Moitié gauche** : titre "Recevoir un email avec le lien de toutes les photos !" (`headlineSmall`) + champ email + bouton "Soumettre" (largeur naturelle — pas pleine largeur). Validation email basique (format). En cas de succès, un Snackbar de confirmation s'affiche. L'envoi email est un appel API POST vers un webservice à définir ultérieurement (TODO).
  * **Moitié droite** : texte explicatif "Scannez ce QR Code pour accéder aux photos !" + QR code de partage Immich (140 dp). Un `VerticalDivider` sépare les deux moitiés.
  * **Résolution dynamique du lien de partage QR** (sealed class `AlbumLinkState`) :
    * `NoAlbumSelected` — aucun album configuré dans les réglages → placeholder "Aucun album sélectionné".
    * `Loading` — appels API en cours → `CircularProgressIndicator`.
    * `Ready(url)` — lien disponible → QR code généré.
    * `Error(message)` — erreur API → message d'erreur dans le placeholder.
  * **Algorithme de résolution** (`GalleryViewModel.resolveAlbumLink`) :
    1. Lecture de `albumId`, `baseUrl`, `apiKey` depuis les préférences.
    2. Si `albumId` vide → état `NoAlbumSelected`.
    3. `GET /api/shared-links` → filtrage sur `type == "ALBUM"` et `album.id == albumId`.
    4. Sélection du premier lien avec `allowDownload == true` et `password == null`.
    5. Si aucun lien éligible → `POST /api/shared-links` pour créer un lien public sans mot de passe avec téléchargement.
    6. URL construite : `{baseUrl}/share/{key}`.
* **Visionneuse plein-écran** : clic sur une vignette → overlay fond noir avec :
  * Croix de fermeture (haut droite), boutons `<` / `>` sur les bords, compteur `n / total` (bas centre).
  * Swipe horizontal (seuil 80 dp) pour naviguer entre photos. `BackHandler` ferme la visionneuse avant de quitter la galerie.
* **Grille de photos** : `LazyVerticalGrid` (colonne adaptive, min 150 dp), photos de `Pictures/Photobooth` via MediaStore, triées par date décroissante. Rechargée à chaque entrée dans la vue.

## Processus en background
* Surveille l'arrivée de nouvelles photos récupérées par l'application (file WorkManager taguée `pending_photo_upload`).
* Les envoie sur une instance Immich dans l'album configuré dans l'écran de réglages.
* **Si l'upload s'est correctement déroulé, la photo est CONSERVÉE sur le disque local.** L'ID Immich retourné est sauvegardé dans `PhotoRepository` (fichier `photo_records.json` en JSON) en association avec la content URI locale.
* Sur Android 10+ (API 29+) : les photos sont gérées via MediaStore (content URI `content://media/external/images/media/…`).

## Base de données locale des photos
* Classe `PhotoRecord` : `localUri` (String), `immichId` (String?), `capturedAt` (Long).
* Classe `PhotoRepository` : singleton, accès thread-safe via `Mutex`, sérialisation JSON via Gson dans `filesDir/photo_records.json`.
* Méthodes : `getAllRecords()`, `addRecord(record)`, `updateImmichId(localUri, immichId)`.
* `MainViewModel` enregistre chaque nouvelle photo via `addRecord()` avant l'upload.
* `PhotoUploadWorker` appelle `updateImmichId()` après upload réussi au lieu de supprimer le fichier.

## Thème et apparence

* Le thème suit automatiquement les préférences système (clair/sombre via `isSystemInDarkTheme()`).
* Sur Android 12+ : **Material You** (couleurs dynamiques issues du fond d'écran de l'utilisateur).
* Sur Android 10-11 : palette statique "Studio Photo" (bleu ardoise — `#1B5299` clair / `#ADC8FF` sombre) avec surfaces adaptées (`#F6F8FF` / `#191C20`).
* Toutes les pages sont enveloppées dans une `Surface` Material3 pour garantir la bonne couleur de fond en toutes circonstances.

## Page de réglages

* Code PIN obligatoire pour y accéder (configurable dans les réglages eux-mêmes, défaut : 1234).
* Champ URL Immich : `KeyboardType.Uri`, autocomplétion désactivée.
* Champ Clé API Immich : masqué par défaut (style password), bouton icône œil pour afficher/masquer la clé, autocomplétion désactivée.
* Champs PIN : masqués, `KeyboardType.NumberPassword`, autocomplétion désactivée.
* Champ input pour l'URL du serveur Immich et la clé API.
* Champ input pour modifier le code PIN (confirmation requise).
* **Slider "Durée d'affichage de la vignette"** : plage 2-15 secondes (défaut 5 s). La valeur est sauvegardée immédiatement au relâchement du slider.
* Choix de l'album Immich : récupération de la liste via appel API, puis sauvegarde de l'ID choisi.
* Une fois l'album sélectionné dans les réglages, la vue Galerie résout dynamiquement le lien de partage via l'API Immich (GET + POST `/api/shared-links`) — plus de lien statique stocké.
* Bouton pour quitter l'application : icône `ExitToApp` (rouge) placée dans la `TopAppBar` en haut à droite, accessible immédiatement sans scroller. Au clic : désactive le screen pinning (`stopLockTask`) puis ferme l'app (`finishAndRemoveTask`).
