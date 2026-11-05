# Changelog

Toutes les évolutions notables de TelecomHand sont consignées dans ce fichier.

## v1.9 — 5 novembre 2026

### Ajouté

- Rail de défilement à côté du pavé tactile : lent près du centre, rapide en bout de course, retour automatique au relâchement.
- Traînée du doigt en ruban lissé, qui s'affine et s'estompe progressivement.
- Modification d'un appareil existant, dont son code d'accès.

### Modifié

- Interface noir et blanc épurée ; la page principale ne garde que le pavé, le rail et quatre boutons.
- Boutons d'action aux couleurs bleu, rouge, vert et rose.
- Volume, lecture, aperçu d'écran et raccourcis regroupés dans « Plus ».
- Icône de l'agent Windows (zone de notification et exécutables) et icône de l'app alignées sur le logo de l'APK.

### Retiré

- Thèmes de couleur et leurs images.

## v1.7 — 25 septembre 2026

### Ajouté

- Thèmes Noir et Blanc, en complément de Classique, CP2077, Tron, Matrix et des thèmes pastel.
- Logos transparents réels pour Windows, Linux et les applications du Stream Deck.
- Lanceur d’applications sur Windows et RaspyTV.
- Carte média : lecture/pause, précédent, suivant et métadonnées de lecture.
- Grille de jeux Moonlight sur RaspyTV avec jaquettes récupérées à la demande.
- Endpoint `/game-image` protégé par le code d’accès de l’agent.

### Amélioré

- Interface plate : bordures fines, fond uni et suppression des effets glassmorphism.
- Remplacement des emojis par des icônes cohérentes.
- Lisibilité du thème blanc grâce à des couleurs de contenu adaptées.
- Fiabilité du clavier distant et de la touche Effacer.
- Service Raspberry Pi démarré automatiquement, compatible Wayland.
- Raccourcis Windows, verrouillage de session et lancement automatique de l’agent Windows.

### Compatibilité

- Android : APK v1.7
- Windows : agent x64 v1.7
- Raspberry Pi / Linux : agent v1.7
- Protocole de commande : v4

## v1.6 et antérieures

Consultez les releases GitHub pour l’historique précédent.
