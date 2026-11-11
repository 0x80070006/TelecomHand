#!/usr/bin/env python3
"""Fenêtre du panneau de la manette, affichée au-dessus de tout, y compris d'un jeu.

Elle ne prend ni le clavier ni aucune décision : le service lui envoie son état,
une ligne JSON par mise à jour, sur l'entrée standard. Elle se ferme à la fin de
cette entrée, donc dès que le service ferme le panneau ou s'arrête.
"""
import json
import os
import signal
import sys

import gi

gi.require_version("Gtk", "3.0")
gi.require_version("GtkLayerShell", "0.1")
from gi.repository import GLib, Gtk, GtkLayerShell  # noqa: E402

STATUS_LABELS = {
    "Charging": "En charge",
    "Discharging": "Sur batterie",
    "Full": "Chargée",
    "Not charging": "Branchée, sans charge",
}
CSS = b"""
window { background: transparent; }
.panel { min-width: 470px; padding: 26px 30px; border-radius: 22px;
         border: 1px solid rgba(255,255,255,.16); background: rgba(9,12,21,.97); }
.title { color: #fff; font-size: 21px; font-weight: 700; }
.connection { color: #919db4; font-size: 14px; }
.battery-value { color: #fff; font-size: 30px; font-weight: 700; }
.battery-status { color: #aeb9cd; font-size: 15px; }
progressbar trough { min-height: 14px; border-radius: 8px; background: rgba(255,255,255,.14); }
progressbar progress { min-height: 14px; border-radius: 8px; background: #2fd16b; }
.item { padding: 12px 16px; border-radius: 12px; color: #dfe6f5; font-size: 17px; }
.item.selected { background: #ffffff; color: #0a0d16; font-weight: 700; }
.hint { color: #7f8aa0; font-size: 13px; }
"""


class Panel:
    def __init__(self):
        self.buffer = b""
        self.window = Gtk.Window(type=Gtk.WindowType.TOPLEVEL)
        self.window.set_decorated(False)
        self.window.set_resizable(False)
        self.window.set_accept_focus(False)
        visual = self.window.get_screen().get_rgba_visual()
        if visual:
            self.window.set_visual(visual)
        self.window.set_app_paintable(True)
        GtkLayerShell.init_for_window(self.window)
        GtkLayerShell.set_layer(self.window, GtkLayerShell.Layer.OVERLAY)
        GtkLayerShell.set_exclusive_zone(self.window, 0)
        GtkLayerShell.set_keyboard_mode(self.window, GtkLayerShell.KeyboardMode.NONE)
        provider = Gtk.CssProvider()
        provider.load_from_data(CSS)
        Gtk.StyleContext.add_provider_for_screen(
            self.window.get_screen(), provider, Gtk.STYLE_PROVIDER_PRIORITY_APPLICATION)

        box = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=14)
        box.get_style_context().add_class("panel")
        self.window.add(box)

        header = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        header.pack_start(self._label("Manette DualShock 4", "title"), False, False, 0)
        self.connection = self._label("", "connection")
        header.pack_end(self.connection, False, False, 0)
        box.pack_start(header, False, False, 0)

        battery = Gtk.Box(orientation=Gtk.Orientation.HORIZONTAL, spacing=12)
        self.battery_value = self._label("—", "battery-value")
        battery.pack_start(self.battery_value, False, False, 0)
        self.battery_status = self._label("", "battery-status")
        battery.pack_end(self.battery_status, False, False, 0)
        box.pack_start(battery, False, False, 0)
        self.progress = Gtk.ProgressBar()
        box.pack_start(self.progress, False, False, 0)

        self.items = Gtk.Box(orientation=Gtk.Orientation.VERTICAL, spacing=4)
        box.pack_start(self.items, False, False, 6)
        box.pack_start(self._label("Croix directionnelle : choisir   ✕ : valider   ○ ou PS : fermer", "hint"), False, False, 0)

    @staticmethod
    def _label(text, css_class):
        label = Gtk.Label(label=text)
        label.set_xalign(0)
        label.get_style_context().add_class(css_class)
        return label

    def render(self, state):
        capacity = state.get("battery")
        known = isinstance(capacity, int)
        self.battery_value.set_text(f"{capacity} %" if known else "Batterie inconnue")
        self.progress.set_fraction(max(0.0, min(1.0, capacity / 100)) if known else 0.0)
        status = str(state.get("status") or "")
        self.battery_status.set_text(STATUS_LABELS.get(status, status))
        self.connection.set_text(str(state.get("connection") or ""))
        for child in self.items.get_children():
            self.items.remove(child)
        selected = state.get("selected")
        for index, text in enumerate(state.get("items") or []):
            item = self._label(str(text), "item")
            if index == selected:
                item.get_style_context().add_class("selected")
            self.items.pack_start(item, False, False, 0)
        self.window.show_all()

    def on_input(self, fd, _condition):
        try:
            data = os.read(fd, 65536)
        except OSError:
            data = b""
        if not data:
            Gtk.main_quit()
            return False
        self.buffer += data
        *lines, self.buffer = self.buffer.split(b"\n")
        for line in lines:
            try:
                state = json.loads(line)
            except ValueError:
                continue
            if isinstance(state, dict):
                self.render(state)
        return True


def main():
    panel = Panel()
    GLib.io_add_watch(sys.stdin.fileno(), GLib.PRIORITY_DEFAULT, GLib.IO_IN | GLib.IO_HUP | GLib.IO_ERR, panel.on_input)
    GLib.unix_signal_add(GLib.PRIORITY_DEFAULT, signal.SIGTERM, Gtk.main_quit)
    Gtk.main()


if __name__ == "__main__":
    main()
