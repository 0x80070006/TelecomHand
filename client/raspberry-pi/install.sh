#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
INSTALL_DIR="$HOME/.local/share/telecomhand"
SERVICE_FILE="/etc/systemd/system/telecomhand.service"
CURRENT_USER="$(id -un)"
CURRENT_UID="$(id -u)"

echo "Installation de TelecomHand pour Raspberry Pi…"
sudo apt-get update
sudo apt-get install -y python3-venv python3-tk scrot python3-evdev grim wtype pulseaudio-utils playerctl
mkdir -p "$INSTALL_DIR"
cp "$SCRIPT_DIR/../telecomhand_client.py" "$INSTALL_DIR/telecomhand_client.py"
python3 -m venv --system-site-packages "$INSTALL_DIR/.venv"
"$INSTALL_DIR/.venv/bin/pip" install --upgrade pip
"$INSTALL_DIR/.venv/bin/pip" install PyAutoGUI Pillow

sudo groupadd -f input
sudo usermod -aG input "$CURRENT_USER"
echo 'KERNEL=="uinput", GROUP="input", MODE="0660"' | sudo tee /etc/udev/rules.d/90-telecomhand-uinput.rules >/dev/null
sudo udevadm control --reload-rules
sudo udevadm trigger --name-match=uinput || true
sudo chgrp input /dev/uinput
sudo chmod 0660 /dev/uinput

sed -e "s|__HOME__|$HOME|g" -e "s|__USER__|$CURRENT_USER|g" -e "s|__UID__|$CURRENT_UID|g" \
    "$SCRIPT_DIR/telecomhand.service" | sudo tee "$SERVICE_FILE" >/dev/null
systemctl --user disable --now telecomhand.service 2>/dev/null || true
sudo systemctl daemon-reload
sudo systemctl enable --now telecomhand.service

IP_ADDRESS="$(hostname -I | awk '{print $1}')"
echo
echo "=========================================================="
echo " TELECOMHAND EST INSTALLE"
echo " Dans l'app, ajoutez l'adresse IP : $IP_ADDRESS"
echo " Port : 45870   Code : voir $INSTALL_DIR/config.json"
echo "=========================================================="
echo "Diagnostic : systemctl status telecomhand"
