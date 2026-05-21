# Projet Photobooth

# Spécifications principales

L'application à développer est une application qui tournera sur une tablette Samsung SM590 avec 3go de RAM et un processeur **Qualcomm Snapdragon 450**.
L'application s'affiche sous le nom **"Photobooth"** dans le tiroir d'applications Android (`app_name` = `Photobooth` dans `strings.xml`).
L'application consiste à mettre à disposition un "photobooth", c'est-à-dire laisser à des personnes durant un évènement la possibilité de se prendre en photo avec leurs proches.
Pour cela le setup sera basé sur une caméra IP (téléphone portable avec l'application IP WebCam installée et lancée, qui expose un flux MJPEG), qui donne accès à un flux vidéo ainsi qu'à une API pour prendre des photos.
Les photos prises sont sauvegardées dans le dossier public **`Pictures/Photobooth`** de l'appareil. L'application maintient une liste interne des photos en attente d'upload via la file WorkManager (tag `pending_photo_upload`).
Un process background les envoie vers une instance Immich, dans un album dédié, à l'aide d'une clé API. Après upload réussi la photo est supprimée du stockage local.

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
* Verrouillage des fonctions de navigation système **actif uniquement sur cette page et les pages suivantes (réglages)** :
  * Mode immersif permanent (barre de navigation et barre de statut masquées).
  * **Bouton retour physique protégé par PIN** : appuyer sur retour depuis l'écran principal affiche une dialog « Quitter l'application » demandant le code PIN. Si le code est correct : le mode kiosque est désactivé (`stopLockTask`) et l'application se ferme (`finishAndRemoveTask`). Si le code est incorrect, un message d'erreur s'affiche dans la dialog.
  * **Screen Pinning Android** activé automatiquement à l'arrivée sur cette page (l'utilisateur confirme une seule fois ; home et recents sont ensuite bloqués).
  * Le mode kiosque est **désactivé** dès que l'application revient à la page de lancement (reprise, icônisation, etc.) : la navigation système redevient normale sur la page de lancement.
* Affichage d'un QR code en permanence avec le lien de l'album Immich configuré.
* **Bouton de prise de vue** (centré en bas) → au clic, un **son de déclencheur** (`res/raw/photo.mp3`) est joué instantanément via `SoundPool`, puis l'appel `GET /photoaf.jpg` est exécuté en background ; la photo est sauvegardée dans `Pictures/Photobooth` puis mise en file d'upload. Après la capture, une **vignette animée** de la photo s'affiche :
  * Apparition depuis le centre en zoom-in avec animation spring (rebond léger, `DampingRatioMediumBouncy`), accompagnée d'un fondu du fond assombri.
  * La photo occupe environ **70 % de la largeur de l'écran**, avec un ratio d'aspect réel, une **bordure blanche de 6 dp** et des coins arrondis.
  * La vignette reste affichée pendant la durée configurée dans les réglages (défaut 5 s), puis disparaît en zoom-out + fondu.
  * Aucune popup / snackbar n'est affichée en plus de cette vignette.
* **Bouton Flash** (à gauche du bouton de prise de vue) → bascule sur/hors du flash (torche) via `POST /enabletorch` / `POST /disabletorch`. L'icône indique l'état actuel (éclair jaune = actif, grisé = inactif).
* Bouton "roue crantée" **en haut à gauche** pour accéder aux réglages. L'appui sur ce bouton demande un code PIN.
* Si le code est correct alors la navigation se fait vers la page des réglages.
* **Positionnement du bouton réglages** : utilise `BoxWithConstraints` pour calculer dynamiquement la bande letterbox entre la vidéo 720p (16:9) et les bords de l'écran. Sur la tablette cible SM-T590 (résolution 1920×1200, ~213 DPI, soit ~1443×902 dp en paysage), la vidéo 16:9 laisse une bande letterbox d'environ 45 dp en haut et en bas. Le bouton réglages est centré verticalement dans cette bande, hors de la zone vidéo.
* **Surveillance du flux vidéo** : si le flux est perdu, un indicateur d'avertissement (⚠️ orange) apparaît en haut à droite. Un appui sur cet indicateur déclenche une tentative de reconnexion immédiate.

## Processus en background
* Surveille l'arrivée de nouvelles photos récupérées par l'application (file WorkManager taguée `pending_photo_upload`).
* Les envoie sur une instance Immich dans l'album configuré dans l'écran de réglages.
* Si l'upload s'est correctement déroulé alors la photo est supprimée du stockage local (`Pictures/Photobooth`).
* Sur Android 10+ (API 29+) : les photos sont gérées via MediaStore (content URI `content://media/external/images/media/…`). Sur Android ≤ 9 : stockage direct dans `Pictures/Photobooth` via le système de fichiers.

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
* Une fois l'album choisi, le lien de l'album est construit (`{immichUrl}/albums/{albumId}`) et stocké pour être affiché via le QR code.
* Bouton pour quitter l'application : icône `ExitToApp` (rouge) placée dans la `TopAppBar` en haut à droite, accessible immédiatement sans scroller. Au clic : désactive le screen pinning (`stopLockTask`) puis ferme l'app (`finishAndRemoveTask`).
