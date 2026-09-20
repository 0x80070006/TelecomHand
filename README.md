# TelecomHand

**TelecomHand** permet de contrôler à distance un ordinateur **Windows** ou **Linux / Raspberry Pi** depuis une application Android.

Le projet utilise notamment **Tailscale** afin de permettre une connexion distante sécurisée entre les appareils sans avoir à ouvrir directement de ports sur Internet.

---

## 📦 Téléchargements

<p align="center">

[![Android](https://img.shields.io/badge/Android-Télécharger_APK-3DDC84?style=for-the-badge\&logo=android\&logoColor=white)](https://github.com/0x80070006/TelecomHand/releases/download/TelecomHandv1.3/TelecomHand-Android-v1.3.apk)

[![Windows](https://img.shields.io/badge/Windows-Télécharger-0078D6?style=for-the-badge\&logo=windows\&logoColor=white)](https://github.com/0x80070006/TelecomHand/releases/download/TelecomHandv1.3/TelecomHand-Windows-x64.zip)

[![Raspberry Pi](https://img.shields.io/badge/Raspberry_Pi-Télécharger-C51A4A?style=for-the-badge\&logo=raspberrypi\&logoColor=white)](https://github.com/0x80070006/TelecomHand/releases/download/TelecomHandv1.3/TelecomHand-RaspberryPi.tar.gz)

[![Release v1.3](https://img.shields.io/badge/GitHub-Release_TelecomHand_v1.3-181717?style=for-the-badge\&logo=github)](https://github.com/0x80070006/TelecomHand/releases/tag/TelecomHandv1.3)

</p>

---

## 📱 Application Android

L'application Android constitue l'interface principale de TelecomHand.

Elle permet d'ajouter plusieurs machines distantes et de définir leur système d'exploitation :

* **Windows**
* **Linux / Raspberry Pi**

Le type de machine sélectionné permet à l'application d'afficher automatiquement les commandes adaptées.

---

## 🖥️ Contrôle à distance

TelecomHand permet notamment :

* déplacement de la souris ;
* clics à distance ;
* saisie clavier ;
* envoi d'un vrai `Backspace` à la machine distante ;
* aperçu de l'écran distant ;
* contrôle du volume ;
* raccourcis système ;
* connexion via l'adresse IP Tailscale de la machine.

Le bouton **⌫** fonctionne même lorsque le champ de saisie du téléphone est vide.

Il est donc possible d'effacer du texte qui était déjà présent sur la machine distante avant la connexion.

---

## 🪟 Fonctions Windows

Lorsqu'un appareil est configuré comme **Windows**, un panneau supplémentaire est disponible dans l'application.

Il permet notamment d'envoyer des raccourcis Windows tels que :

| Commande  | Fonction                |
| --------- | ----------------------- |
| `Windows` | Ouvrir le menu Démarrer |
| `Ctrl`    | Touche Ctrl             |
| `Alt`     | Touche Alt              |
| `Win + L` | Verrouiller la session  |
| Volume +  | Augmenter le volume     |
| Volume -  | Diminuer le volume      |

Le panneau Windows est uniquement affiché pour les appareils déclarés comme étant sous Windows.

---

## 🐧 Fonctions Linux / Raspberry Pi

Les appareils Linux disposent également des commandes standards de TelecomHand :

* souris ;
* clavier ;
* Backspace distant ;
* aperçu d'écran ;
* augmentation du volume ;
* diminution du volume.

Sur Linux, TelecomHand permet également de sélectionner le **périphérique de sortie audio** utilisé par la machine distante.

Cela permet par exemple de basculer entre :

* HDMI ;
* haut-parleurs ;
* casque ;
* carte son USB ;
* autres périphériques audio disponibles sur le système.

---

## 🔐 Connexion avec Tailscale

L'utilisation de **Tailscale** est recommandée.

Tailscale crée un réseau privé entre vos appareils et attribue à chaque machine une adresse du type :

```text
100.x.x.x
```

Il n'est donc normalement pas nécessaire d'exposer directement TelecomHand sur Internet.

### Exemple de configuration

Dans l'application Android :

```text
Nom : Raspberry Pi
Système : Linux
Adresse IP : <IP_TAILSCALE>
Port : 45870
Code : <VOTRE_CODE>
```

Pour Windows :

```text
Nom : Mon PC
Système : Windows
Adresse IP : <IP_TAILSCALE_WINDOWS>
Port : 45870
Code : <VOTRE_CODE>
```

> ⚠️ Ne publiez jamais votre véritable code TelecomHand dans un dépôt GitHub public.

---

## 🪟 Installation Windows

Téléchargez :

**`TelecomHand-Windows-x64.zip`**

Depuis la page des releases :

https://github.com/0x80070006/TelecomHand/releases/tag/TelecomHandv1.3

Décompressez l'archive puis utilisez l'installateur fourni.

Le paquet contient notamment les scripts nécessaires à l'installation de TelecomHand comme **service Windows**.

Une fois installé :

* TelecomHand fonctionne comme un service Windows ;
* le service démarre automatiquement avec Windows ;
* l'agent peut être relancé automatiquement en cas d'arrêt ou de plantage ;
* aucune console n'a besoin de rester ouverte ;
* TelecomHand peut fonctionner en arrière-plan ;
* Tailscale peut également fonctionner automatiquement au démarrage.

### Service TelecomHand

Le service apparaît sous le nom :

```text
TelecomHand Remote Control
```

---

## 🥧 Installation Raspberry Pi

Téléchargez :

**`TelecomHand-RaspberryPi.tar.gz`**

Puis extrayez l'archive :

```bash
tar -xzf TelecomHand-RaspberryPi.tar.gz
cd TelecomHand-RaspberryPi
```

Lancez ensuite l'installation :

```bash
chmod +x install.sh
sudo ./install.sh
```

---

## 🔎 Vérifier le service sur Raspberry Pi

Pour vérifier l'état du service :

```bash
sudo systemctl status telecomhand
```

Pour consulter les derniers logs :

```bash
sudo journalctl -u telecomhand -n 15 --no-pager
```

Pour redémarrer TelecomHand :

```bash
sudo systemctl restart telecomhand
```

---

## 🌐 Vérifier Tailscale

Afficher l'état de Tailscale :

```bash
tailscale status
```

Afficher l'adresse IP Tailscale de la machine :

```bash
tailscale ip -4
```

Le résultat devrait être une adresse similaire à :

```text
100.x.x.x
```

C'est cette adresse qu'il faut saisir dans l'application TelecomHand.

---

## 📲 Installation Android

Téléchargez :

**`TelecomHand-Android-v1.3.apk`**

Puis installez l'APK sur votre téléphone Android.

Android peut demander l'autorisation d'installer des applications provenant de sources externes.

Une fois l'application installée :

1. ouvrez TelecomHand ;
2. ajoutez un appareil ;
3. donnez-lui un nom ;
4. choisissez **Windows** ou **Linux** ;
5. renseignez son adresse IP Tailscale ;
6. renseignez le port ;
7. saisissez votre code TelecomHand ;
8. enregistrez l'appareil ;
9. connectez-vous.

---

## 🔌 Ports

TelecomHand utilise notamment :

```text
45870
45871
```

L'utilisation de Tailscale permet de faire transiter les connexions à travers le réseau privé Tailscale.

---

## ✅ Configuration validée

TelecomHand a notamment été utilisé avec :

### Raspberry Pi

* Tailscale actif ;
* client TelecomHand actif ;
* connexion via IP Tailscale ;
* authentification ;
* contrôle de la souris ;
* aperçu distant jusqu'à `1280×720` ;
* commandes audio.

### Windows

* TelecomHand installé comme service Windows ;
* démarrage automatique ;
* relance automatique de l'agent ;
* Tailscale configuré pour fonctionner automatiquement ;
* contrôle distant via Tailscale ;
* raccourcis clavier Windows ;
* contrôle du volume.

---

## 📁 Fichiers de la release v1.3

```text
TelecomHand-Android-v1.3.apk
TelecomHand-Windows-x64.zip
TelecomHand-RaspberryPi.tar.gz
```

### Release GitHub

👉 **https://github.com/0x80070006/TelecomHand/releases/tag/TelecomHandv1.3**

---

## 🔒 Sécurité

TelecomHand donne accès au clavier, à la souris et à certaines fonctions système de la machine distante.

Quelques recommandations :

* utilisez un code TelecomHand suffisamment robuste ;
* ne publiez jamais votre code dans le dépôt ;
* utilisez de préférence Tailscale ;
* n'exposez pas directement les ports TelecomHand sur Internet sans protection appropriée ;
* limitez l'accès à votre réseau Tailscale aux appareils autorisés ;
* gardez Windows, Linux, TelecomHand et Tailscale à jour.

---

## 🚀 TelecomHand v1.3

La version **v1.3** ajoute notamment :

* prise en charge du type d'appareil Windows/Linux ;
* panneau de commandes spécifique à Windows ;
* touche Windows ;
* raccourcis `Ctrl` et `Alt` ;
* verrouillage Windows avec `Win + L` ;
* contrôle du volume ;
* sélection de la sortie audio sous Linux ;
* vrai Backspace distant ;
* nouvelles tailles d'icône Android ;
* amélioration du service Windows ;
* meilleure intégration avec Tailscale.

---

<p align="center">
  <b>TelecomHand — Remote control through your private network.</b>
</p>
