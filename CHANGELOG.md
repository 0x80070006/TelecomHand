# Changelog

Toutes les évolutions notables de TelecomHand sont consignées dans ce fichier.

## v1.10.2 — 12 novembre 2026

### Corrigé

- Bouton OK de la croix directionnelle à la bonne taille : il ne masque plus les flèches et correspond à sa zone de toucher.
- Un défilement de la page qui commence sur la jauge de volume ne coupe plus le son ; sur la barre de lecture, il ne déplace plus la lecture.
- Depuis l'aperçu d'écran ouvert en mode RaspyTV, Retour ramène à la télécommande.
- La feuille « Sortie audio » s'ouvre en entier et ne passe plus sous la barre de navigation.
- Onglet « Télécommande » lisible en entier.

### Modifié

- RaspyTV est détecté dès le lancement, sans passer par l'écran classique ; nouvel essai rapide si la première requête échoue.
- Flèches du clavier en icônes.
- README refait, avec des captures de chaque écran.

## v1.10.1 — 12 novembre 2026

### Modifié

- Croix directionnelle du mode RaspyTV épurée : disque plat, bouton OK sobre, flèches discrètes.

## v1.10 — 11 novembre 2026

### Ajouté

- Mode RaspyTV, activé tout seul quand l'app se connecte à un Raspberry Pi qui fait tourner RaspyTV :
  - croix directionnelle avec bouton OK, répétition quand une direction reste appuyée, retour haptique ;
  - Retour, Accueil, barre latérale, Options (△), Panneau (PS) et Réglages de RaspyTV ;
  - lecture en cours avec jaquette, barre de lecture déplaçable, précédent, lecture/pause et suivant ;
  - volume réglé par RaspyTV (l'indicateur s'affiche sur la TV), jauge à glisser, toucher pour couper le son ;
  - choix de la sortie audio, dont la connexion d'un casque Bluetooth jumelé ;
  - applications RaspyTV à leurs couleurs et jeux Moonlight avec leur jaquette ;
  - batterie de la manette DualShock 4 ;
  - redémarrage et extinction du Pi, avec confirmation en deux temps ;
  - onglets Télécommande, Souris et Clavier.
- Agent Raspberry Pi en protocole 5 : commandes `tv`, `tv_media`, `tv_volume`, `system_power` et pages `/tv-state.json`, `/tv-art`, `/app-icon`.
- Service `telecomhand-gamepad` : la manette DualShock 4 comme télécommande (voir LISEZ-MOI du Raspberry Pi).
- Confirmation avant de supprimer un appareil ; code d'accès masquable.

### Modifié

- Interface plus chaleureuse : fond encre, cartes arrondies, icônes Material arrondies, logos en couleur.
- Pavé tactile dans une carte, avec un rappel des gestes.
- En-tête avec le logo du système et un point d'état coloré.

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
