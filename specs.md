# Projet Photobooth

# Spécifications principales

L'application à développer est une application qui tournera sur une tablette Samsung SM590 avec 3go de RAM et un processeur **Qualcomm Snapdragon 450**.
L'application consiste à mettre à disposition un "photobooth", c'est-à-dire laisser à des personnes durant un évènement la possibilité de se prendre en photo avec leurs proches.
Pour cela le setup sera basé sur une caméra IP (téléphone portable avec l'application IP WebCam installée et lancée, qui expose un flux MJPEG), qui donne accès à un flux vidéo ainsi qu'à une API pour prendre des photos.
Les photos prises ne seront stockées que temporairement sur l'appareil. Un process background devra les envoyer vers une instance Immich, dans un album dédiée, à l'aide d'une clé API.

## Page de lancement

* La page de lancement s'affiche **à chaque démarrage ou reprise de l'application** (retour depuis le gestionnaire de tâches, déverrouillage, etc.).
* Il faut mettre l'adresse IP du téléphone pour avoir un BASE\_URL valable à travers le runtime de l'application.
* Le champ est pré-rempli avec la dernière adresse utilisée pour éviter de la ressaisir.
* Un bouton launch valide le formulaire qui récupère l'IP du téléphone et si l'URL peut être jointe et qu'un flux vidéo peut être récupéré, alors il y a navigation vers la page principale.

## Page principale

* Affichage en plein écran du flux vidéo exposé par l'application IP WebCam. Utilisation du décodage matériel obligatoire pour économiser les ressources.
* Verrouillage des fonctions de navigation système **actif uniquement sur cette page et les pages suivantes (réglages)** :
  * Mode immersif permanent (barre de navigation et barre de statut masquées).
  * Bouton retour physique désactivé.
  * **Screen Pinning Android** activé automatiquement à l'arrivée sur cette page (l'utilisateur confirme une seule fois ; home, recents et back sont ensuite tous bloqués).
  * Le mode kiosque est **désactivé** dès que l'application revient à la page de lancement (reprise, icônisation, etc.) : la navigation système redevient normale sur la page de lancement.
* Affichage d'un QR code en permanence avec le lien de l'album Immich configuré.
* Bouton pour prendre une photo => Appel API en background, récupération de la photo sur le stockage interne de la tablette.
* Bouton "roue crantée" **en haut à gauche** pour accéder aux réglages. L'appui sur ce bouton demande un code PIN.
* Si le code est correct alors la navigation se fait vers la page des réglages.
* **Surveillance du flux vidéo** : si le flux est perdu, un indicateur d'avertissement (⚠️ orange) apparaît en haut à droite. Un appui sur cet indicateur déclenche une tentative de reconnexion immédiate.

## Processus en background
* Surveille l'arrivée de nouvelles photos récupérées par l'application.
* Les envoie sur une instance Immich dans l'album configuré dans l'écran de réglages.
* Si l'upload s'est correctement déroulé alors la photo est supprimée du stockage local.

## Page de réglages

* Code PIN obligatoire pour y accéder (configurable dans les réglages eux-mêmes, défaut : 1234).
* Champ input pour l'URL du serveur Immich et la clé API.
* Champ input pour modifier le code PIN (confirmation requise).
* Choix de l'album Immich : récupération de la liste via appel API, puis sauvegarde de l'ID choisi.
* Une fois l'album choisi, le lien de l'album est construit (`{immichUrl}/albums/{albumId}`) et stocké pour être affiché via le QR code.
* Bouton pour quitter l'application (désactive le screen pinning puis ferme l'app).
