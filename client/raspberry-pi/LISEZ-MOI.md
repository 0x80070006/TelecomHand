# TelecomHand pour Raspberry Pi

Ce client prend en charge Raspberry Pi OS sous **Wayland** et X11.

## Installation

Dans un terminal ouvert dans ce dossier :

```bash
chmod +x install.sh
./install.sh
```

L’installateur affiche l’adresse IP à saisir dans l’application mobile. Gardez le port `45870`. Le code d'accès est créé au premier démarrage dans
`~/.local/share/telecomhand/config.json` : saisissez-le dans l'application.

Si Tailscale est installé, le client affiche également son adresse `100.x`, à
utiliser de préférence depuis le téléphone.

Le service système démarre ensuite automatiquement avec le Raspberry Pi.

```bash
sudo systemctl status telecomhand
sudo journalctl -u telecomhand -f
```

Sous Wayland, TelecomHand utilise un périphérique souris virtuel `uinput`,
`wtype` pour le clavier et `grim` pour l’aperçu. L’installateur configure les
droits nécessaires automatiquement.

N’exposez pas les ports `45870` et `45871` sur Internet.

## Manette DualShock 4 comme télécommande

`install.sh` installe aussi le service `telecomhand-gamepad`. Il reste à associer
la manette une seule fois. Maintenez **Share + PS** jusqu’à ce que la barre
lumineuse clignote, puis lancez `bluetoothctl` et saisissez :

```text
power on
agent on
default-agent
scan on
pair XX:XX:XX:XX:XX:XX
trust XX:XX:XX:XX:XX:XX
connect XX:XX:XX:XX:XX:XX
scan off
exit
```

Remplacez `XX:XX:XX:XX:XX:XX` par l’adresse affichée pour `Wireless Controller`.
Ensuite, la touche **PS** rallume la manette et la reconnecte, à condition que le
Raspberry Pi soit déjà allumé.

| Manette | Action |
|---|---|
| Croix directionnelle | Passer de tuile en tuile ; dans une vidéo, voir ci-dessous |
| Croix (✕) | Valider ; clic gauche au curseur si le joystick vient de servir |
| Rond (○) | Page précédente |
| Carré (□) | Lecture/pause |
| Triangle (△) | Commandes du lecteur pendant une vidéo, sinon recherche du site |
| PS, appui bref | Accueil RaspyTV ; un second appui revient à la page quittée |
| PS, appui long (1 s) | Panneau : lecture en cours, son, sortie audio, batterie, extinction |
| Options | Paramètres ; un second appui revient à la page quittée |
| Share | Déploie la barre latérale, pilotable à la croix directionnelle |
| Joystick gauche / droit | Souris / Défilement |
| L1 / R1 | Volume − / + : un cran par appui, en continu si maintenu |
| L2 / R2 | Titre précédent / suivant |
| L3 / R3 (clic des joysticks) | Clic gauche / droit |

**Dans un lecteur vidéo :** gauche et droite reculent ou avancent de 10 secondes à
chaque appui, puis de 10 secondes toutes les demi-secondes si le bouton reste
maintenu. Haut et bas n’ont pas d’effet.

**Barre latérale :** elle se replie seule après 4 secondes sans action, sauf si le
pointeur la survole. Share ou ○ la referme aussitôt.

**Panneau (PS maintenu) :** à gauche, la lecture en cours (NouTube Music en
priorité, même depuis l'accueil), avec jaquette, barre de lecture, précédent,
lecture/pause et suivant ; puis le volume (‹ › pour régler) et la sortie audio,
casques Bluetooth jumelés compris (« Se connecter »). À droite, la manette :
batterie et extinction. ↑ ↓ pour naviguer, ✕ pour valider, ○ ou PS pour fermer.
Le panneau est dessiné par RaspyTV ; sans RaspyTV, l'ancien panneau (batterie,
extinction) s'ouvre à la place.

### Pendant un jeu

Dès que `moonlight-qt` tourne, le service libère la manette, qui redevient une
vraie manette pour le jeu. Il la reprend à la fermeture de Moonlight.

Un appui bref sur PS reste transmis au jeu. **Maintenir PS 1 seconde** ouvre le
panneau de la manette et suspend le jeu côté manette : il propose d’éteindre la
manette ou de quitter le jeu. La manette revient au jeu dès la fermeture du
panneau, qui se ferme aussi seul après 20 secondes sans action.

### Personnaliser

Modifiez `~/.local/share/telecomhand/gamepad.json`, puis lancez
`sudo systemctl restart telecomhand-gamepad`. Une mise à jour n’écrase pas ce
fichier. Chaque bouton accepte une action parmi :

- `{"key": "KEY_ENTER"}` ou `{"key": ["KEY_LEFTALT", "KEY_LEFT"]}` : touche ou combinaison ;
- `{"mouse": "left"}` : bouton de souris (`left`, `right` ou `middle`) ;
- `{"confirm": {"key": "KEY_ENTER", "mouse": "left"}}` : clic si le joystick vient de servir, touche sinon ;
- `{"raspytv": {"action": "launch", "id": "youtube"}}` : commande RaspyTV ;
- `{"command": ["playerctl", "play-pause"]}` : programme à lancer.

Ajoutez `"repeat": true` pour répéter l’action tant que le bouton est maintenu,
sauf pour un bouton de souris. Pour réserver la manette à un autre jeu, ajoutez
son nom de processus à `game_processes`.

### Diagnostic

```bash
sudo systemctl status telecomhand-gamepad
sudo journalctl -u telecomhand-gamepad -f
```

Si la manette cesse d’émettre alors que le Pi la croit connectée (liaison figée),
le service suspend les commandes au bout d’une seconde (le curseur ne dérive plus),
puis coupe la liaison après 8 secondes : un appui sur PS la rétablit.

Si la manette se coupe environ une seconde après chaque connexion Bluetooth,
vérifiez d’abord sa batterie : à 5 %, le Bluetooth ne tient pas, alors qu’elle
s’allume encore et fonctionne par câble.

```bash
cat /sys/class/power_supply/ps-controller-battery-*/{capacity,status}
```

Si le service est arrêté, RaspyTV retrouve sa gestion native de la manette
(boutons ✕ et ○ et croix directionnelle, dans son interface uniquement).
