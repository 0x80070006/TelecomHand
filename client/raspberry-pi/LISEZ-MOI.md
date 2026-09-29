# TelecomHand pour Raspberry Pi

Ce client prend en charge Raspberry Pi OS sous **Wayland** et X11.

## Installation

Dans un terminal ouvert dans ce dossier :

```bash
chmod +x install.sh
./install.sh
```

L’installateur affiche l’adresse IP à saisir dans l’application mobile. Gardez
les valeurs par défaut : port `45870` et code `telecomhand`.

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
