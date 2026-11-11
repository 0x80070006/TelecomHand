#!/usr/bin/env python3
"""Transforme une manette DualShock 4 en télécommande pour RaspyTV.

Hors jeu, le service prend la manette en exclusivité et la traduit en touches,
en mouvements de souris et en commandes RaspyTV. Dès qu'un jeu listé dans
`game_processes` tourne, il la libère pour qu'elle redevienne une vraie manette.
Un appui long sur le bouton du panneau ouvre le panneau de la manette, y compris
pendant un jeu : le service reprend alors la manette tant qu'il reste ouvert.

Il n'y a pas de nettoyage à l'arrêt : le noyau libère la manette et détruit les
périphériques virtuels quand le processus se termine.
"""
import argparse
import asyncio
import json
import logging
import os
import re
import socket
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path

from evdev import InputDevice, UInput, ecodes as e, list_devices

LOG = logging.getLogger("telecomhand-gamepad")

EXIT_BAD_CONFIG = 2  # systemd ne relance pas le service : voir RestartPreventExitStatus.
SONY_VENDOR_ID = 0x054C
CONTROLLER_NAME = "Wireless Controller"
GAME_POLL_SECONDS = 0.5
DEVICE_POLL_SECONDS = 2.0
ACTIVE_TICK_SECONDS = 1 / 60
IDLE_TICK_SECONDS = 1 / 20
MAX_TICK_SECONDS = 0.1
# La DS4 envoie ses capteurs de mouvement en continu tant que la liaison vit. Un silence
# signifie une liaison figée : les dernières positions des joysticks ne sont plus valables.
LINK_FREEZE_SECONDS = 1.0
LINK_DEAD_SECONDS = 8.0
PANEL_SCRIPT = Path(__file__).resolve().with_name("telecomhand_gamepad_panel.py")
PANEL_REFRESH_SECONDS = 3.0
POWER_SUPPLY_ROOT = "/sys/class/power_supply"
BATTERY_PREFIXES = ("ps-controller-battery-", "sony_controller_battery_")
MAC_ADDRESS = re.compile(r"^[0-9a-f]{2}(:[0-9a-f]{2}){5}$")

BUTTON_CODES = {
    "cross": e.BTN_SOUTH, "circle": e.BTN_EAST, "triangle": e.BTN_NORTH, "square": e.BTN_WEST,
    "l1": e.BTN_TL, "r1": e.BTN_TR, "l2": e.BTN_TL2, "r2": e.BTN_TR2,
    "share": e.BTN_SELECT, "options": e.BTN_START, "ps": e.BTN_MODE,
    "l3": e.BTN_THUMBL, "r3": e.BTN_THUMBR,
}
BUTTON_NAMES = {code: name for name, code in BUTTON_CODES.items()}
DPAD_BUTTONS = {
    e.ABS_HAT0X: {-1: "dpad_left", 1: "dpad_right"},
    e.ABS_HAT0Y: {-1: "dpad_up", 1: "dpad_down"},
}
STICK_AXES = (e.ABS_X, e.ABS_Y, e.ABS_RY)
MOUSE_BUTTONS = {"left": e.BTN_LEFT, "right": e.BTN_RIGHT, "middle": e.BTN_MIDDLE}
VALID_BUTTONS = set(BUTTON_CODES) | {name for names in DPAD_BUTTONS.values() for name in names.values()}

# Moonlight et TelecomHand lancent `moonlight-qt list` pour lire le catalogue de
# jeux : ces commandes brèves ne doivent pas faire basculer la manette en mode jeu.
GAME_UTILITY_ARGUMENTS = {"list", "pair", "quit"}

DEFAULT_SETTINGS = {
    "deadzone": 0.15,
    "mouse_speed": 900.0,
    "scroll_speed": 12.0,
    "repeat_delay": 0.4,
    "repeat_interval": 0.15,
    "pointer_timeout": 8.0,
}
DEFAULT_PANEL_HOLD_SECONDS = 1.0
DEFAULT_PANEL_IDLE_SECONDS = 20.0


class ConfigError(ValueError):
    pass


@dataclass(frozen=True)
class Action:
    keys: tuple[int, ...] = ()
    mouse: int | None = None
    raspytv: dict | None = None
    command: tuple[str, ...] = ()
    repeat: bool = False
    # Validation adaptative : clic au curseur si le joystick vient de servir, touche sinon.
    confirm_pointer: "Action | None" = None
    confirm_key: "Action | None" = None

    @property
    def external(self) -> bool:
        return self.raspytv is not None or bool(self.command)


@dataclass(frozen=True)
class PanelConfig:
    button: str
    hold_seconds: float
    idle_seconds: float


@dataclass(frozen=True)
class Config:
    actions: dict[str, Action]
    game_processes: frozenset[str]
    panel: PanelConfig | None
    deadzone: float
    mouse_speed: float
    scroll_speed: float
    repeat_delay: float
    repeat_interval: float
    pointer_timeout: float


def _key_code(name) -> int:
    code = getattr(e, name, None) if isinstance(name, str) and name.startswith("KEY_") else None
    if code is None:
        raise ConfigError(f"touche inconnue : {name!r}")
    return code


def _compile_key(names) -> Action:
    names = names if isinstance(names, list) else [names]
    if not names:
        raise ConfigError("aucune touche indiquée")
    return Action(keys=tuple(_key_code(name) for name in names))


def _compile_mouse(name) -> Action:
    if name not in MOUSE_BUTTONS:
        raise ConfigError(f"bouton de souris inconnu : {name!r}")
    return Action(mouse=MOUSE_BUTTONS[name])


def compile_action(spec) -> Action:
    if not isinstance(spec, dict):
        raise ConfigError(f"action invalide : {spec!r}")
    kinds = [kind for kind in ("key", "mouse", "raspytv", "command", "confirm") if kind in spec]
    if len(kinds) != 1:
        raise ConfigError(f"une action doit avoir exactement un type parmi key, mouse, raspytv, command, confirm : {spec!r}")
    repeat = spec.get("repeat", False)
    if not isinstance(repeat, bool):
        raise ConfigError(f"repeat doit valoir true ou false : {spec!r}")
    kind = kinds[0]
    if kind == "key":
        return Action(keys=_compile_key(spec["key"]).keys, repeat=repeat)
    if kind == "mouse" or kind == "confirm":
        if repeat:
            raise ConfigError(f"repeat n'est pas disponible pour cette action : {spec!r}")
    if kind == "mouse":
        return _compile_mouse(spec["mouse"])
    if kind == "confirm":
        confirm = spec["confirm"]
        if not isinstance(confirm, dict) or "key" not in confirm or "mouse" not in confirm:
            raise ConfigError(f"confirm doit contenir key et mouse : {spec!r}")
        return Action(confirm_pointer=_compile_mouse(confirm["mouse"]), confirm_key=_compile_key(confirm["key"]))
    if kind == "raspytv":
        if not isinstance(spec["raspytv"], dict) or "action" not in spec["raspytv"]:
            raise ConfigError(f"raspytv doit contenir un champ action : {spec!r}")
        return Action(raspytv=spec["raspytv"], repeat=repeat)
    argv = spec["command"]
    if not isinstance(argv, list) or not argv or not all(isinstance(arg, str) for arg in argv):
        raise ConfigError(f"command doit être une liste de textes : {spec!r}")
    return Action(command=tuple(argv), repeat=repeat)


def _number(raw: dict, name: str, default: float, label: str) -> float:
    value = raw.get(name, default)
    if isinstance(value, bool) or not isinstance(value, (int, float)) or value < 0:
        raise ConfigError(f"{label} doit être un nombre positif")
    return float(value)


def _setting(raw: dict, name: str) -> float:
    return _number(raw, name, DEFAULT_SETTINGS[name], name)


def _parse_panel(raw) -> PanelConfig | None:
    if raw is None:
        return None
    if not isinstance(raw, dict) or raw.get("button") not in BUTTON_CODES:
        raise ConfigError("panel doit indiquer un bouton valide (hors croix directionnelle)")
    hold = _number(raw, "hold_seconds", DEFAULT_PANEL_HOLD_SECONDS, "panel.hold_seconds")
    idle = _number(raw, "idle_seconds", DEFAULT_PANEL_IDLE_SECONDS, "panel.idle_seconds")
    if hold <= 0 or idle <= 0:
        raise ConfigError("panel.hold_seconds et panel.idle_seconds doivent être supérieurs à 0")
    return PanelConfig(raw["button"], hold, idle)


def parse_config(raw) -> Config:
    if not isinstance(raw, dict) or not isinstance(raw.get("buttons"), dict):
        raise ConfigError("la configuration doit contenir un objet buttons")
    unknown = set(raw["buttons"]) - VALID_BUTTONS
    if unknown:
        raise ConfigError(f"boutons inconnus : {', '.join(sorted(unknown))}")
    actions = {name: compile_action(spec) for name, spec in raw["buttons"].items()}

    processes = raw.get("game_processes", ["moonlight-qt"])
    if not isinstance(processes, list) or not all(isinstance(name, str) for name in processes):
        raise ConfigError("game_processes doit être une liste de noms de processus")

    panel = _parse_panel(raw.get("panel"))
    if panel and panel.button in actions:
        tap = actions[panel.button]
        if tap.repeat or tap.mouse is not None or tap.confirm_key is not None:
            raise ConfigError("le bouton du panneau n'accepte qu'une action simple : key, raspytv ou command")

    deadzone = _setting(raw, "deadzone")
    if deadzone >= 1:
        raise ConfigError("deadzone doit être inférieur à 1")
    repeat_interval = _setting(raw, "repeat_interval")
    if repeat_interval <= 0:
        raise ConfigError("repeat_interval doit être supérieur à 0")
    return Config(
        actions=actions,
        game_processes=frozenset(processes),
        panel=panel,
        deadzone=deadzone,
        mouse_speed=_setting(raw, "mouse_speed"),
        scroll_speed=_setting(raw, "scroll_speed"),
        repeat_delay=_setting(raw, "repeat_delay"),
        repeat_interval=repeat_interval,
        pointer_timeout=_setting(raw, "pointer_timeout"),
    )


def read_battery(address: str | None, root: str = POWER_SUPPLY_ROOT) -> tuple[int | None, str]:
    """Renvoie (pourcentage, état) ; (None, "") si le pilote ne donne rien d'exploitable."""
    try:
        names = [entry.name for entry in os.scandir(root) if entry.name.startswith(BATTERY_PREFIXES)]
    except OSError:
        return None, ""
    matching = [name for name in names if address and name.lower().endswith(address.lower())]
    if not matching and len(names) == 1 and not address:
        matching = names
    if not matching:
        return None, ""
    folder = Path(root, matching[0])
    try:
        capacity = int(folder.joinpath("capacity").read_text().strip())
        status = folder.joinpath("status").read_text().strip()
    except (OSError, ValueError):
        return None, ""
    return max(0, min(100, capacity)), status


def session_environment() -> dict:
    """Environnement Wayland : le service système démarre hors de la session graphique."""
    environment = os.environ.copy()
    runtime = environment.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    environment["XDG_RUNTIME_DIR"] = runtime
    if not environment.get("WAYLAND_DISPLAY"):
        sockets = sorted(path.name for path in Path(runtime).glob("wayland-*") if path.suffix != ".lock")
        if sockets:
            environment["WAYLAND_DISPLAY"] = sockets[0]
    environment["GDK_BACKEND"] = "wayland"
    return environment


class PanelProcess:
    """Fenêtre GTK du panneau : un processus par ouverture, piloté par son entrée standard.

    La fenêtre se ferme d'elle-même dès que l'entrée se ferme, donc aussi si le
    service plante : elle ne peut pas rester affichée sans personne pour la piloter.
    """

    def __init__(self):
        self.process = None

    def start(self) -> bool:
        try:
            self.process = subprocess.Popen(
                [sys.executable, str(PANEL_SCRIPT)], stdin=subprocess.PIPE, text=True,
                encoding="utf-8", env=session_environment(),
            )
        except OSError as exc:
            LOG.warning("Panneau impossible à lancer : %s", exc)
            self.process = None
            return False
        return True

    def send(self, state: dict) -> bool:
        if not self.alive():
            return False
        try:
            self.process.stdin.write(json.dumps(state) + "\n")
            self.process.stdin.flush()
        except (OSError, ValueError):
            return False
        return True

    def alive(self) -> bool:
        return self.process is not None and self.process.poll() is None

    def stop(self) -> None:
        process, self.process = self.process, None
        if process is None:
            return
        try:
            process.stdin.close()
        except (OSError, ValueError):
            pass
        try:
            process.terminate()
        except OSError:
            pass


class ControllerPanel:
    """État du panneau de la manette ; la fenêtre ne fait qu'afficher ce qu'il envoie."""

    def __init__(self, ui, dispatch, idle_seconds: float = DEFAULT_PANEL_IDLE_SECONDS, battery_reader=read_battery):
        self.ui = ui
        self.dispatch = dispatch
        self.idle_seconds = idle_seconds
        self.read_battery = battery_reader
        self.on_change = None
        self.address = None
        self.bluetooth = False
        self.is_open = False
        self.items: list[tuple[str, str]] = []
        self.selected = 0
        self.idle = 0.0
        self.since_refresh = 0.0
        self.last_state = None

    def set_device(self, uniq, bluetooth: bool) -> None:
        address = (uniq or "").strip().lower()
        self.address = address if MAC_ADDRESS.match(address) else None
        self.bluetooth = bluetooth

    def _build_items(self, in_game: bool) -> list[tuple[str, str]]:
        items = []
        if self.bluetooth and self.address:
            items.append(("off", "Éteindre la manette"))
        if in_game:
            items.append(("home", "Quitter le jeu et revenir à l'accueil"))
        items.append(("close", "Fermer"))
        return items

    def open(self, in_game: bool) -> bool:
        if self.is_open:
            return True
        self.items = self._build_items(in_game)
        self.selected = 0
        self.idle = self.since_refresh = 0.0
        self.last_state = None
        if not self.ui.start():
            return False
        self.is_open = True
        self._publish()
        self._notify()
        return True

    def close(self) -> None:
        if not self.is_open:
            return
        self.is_open = False
        self.ui.stop()
        self._notify()

    def move(self, delta: int) -> None:
        self.idle = 0.0
        self.selected = (self.selected + delta) % len(self.items)
        self._publish()

    def activate(self) -> None:
        kind = self.items[self.selected][0]
        self.close()
        if kind == "off":
            self.dispatch(Action(command=("bluetoothctl", "disconnect", self.address)))
        elif kind == "home":
            self.dispatch(Action(raspytv={"action": "home"}))

    def tick(self, elapsed: float) -> None:
        if not self.is_open:
            return
        if not self.ui.alive():
            LOG.warning("La fenêtre du panneau s'est fermée de façon inattendue")
            self.close()
            return
        self.idle += elapsed
        if self.idle >= self.idle_seconds:
            self.close()
            return
        self.since_refresh += elapsed
        if self.since_refresh >= PANEL_REFRESH_SECONDS:
            self.since_refresh = 0.0
            self._publish()

    def _publish(self) -> None:
        capacity, status = self.read_battery(self.address)
        state = {
            "battery": capacity,
            "status": status,
            "connection": "Bluetooth" if self.bluetooth else "USB",
            "items": [label for _kind, label in self.items],
            "selected": self.selected,
        }
        if state != self.last_state:
            self.last_state = state
            self.ui.send(state)

    def _notify(self) -> None:
        if self.on_change:
            self.on_change()


class TvPanel:
    """Panneau de RaspyTV : présent si RaspyTV tourne, ouvert tant que son fichier témoin existe."""

    def __init__(self, runtime: str):
        self.socket = Path(runtime, "raspytv-nav.sock")
        self.flag = Path(runtime, "raspytv-panel.open")

    def available(self) -> bool:
        return self.socket.exists()

    def is_open(self) -> bool:
        return self.flag.exists()


# Dans le panneau de RaspyTV, la manette pilote une interface au clavier : ✕ valide, ○ ferme.
TV_PANEL_KEYS = {"cross": Action(keys=(e.KEY_ENTER,)), "circle": Action(keys=(e.KEY_ESC,))}
TV_PANEL_PASSTHROUGH = {"dpad_up", "dpad_down", "dpad_left", "dpad_right", "l1", "r1"}
TV_PANEL_CHECK_SECONDS = 0.2


class Mapper:
    """Traduit les événements de la manette ; désactivé tant que le jeu la possède."""

    def __init__(self, config: Config, output, dispatch, panel: ControllerPanel, tv_panel=None):
        self.config = config
        self.output = output
        self.dispatch = dispatch
        self.panel = panel
        self.panel.on_change = self._notify_mode
        self.on_mode_change = None
        self.enabled = False
        self.in_game = False
        self.axis_range = {axis: (0, 255) for axis in STICK_AXES}
        self.axis_value = {axis: 127.5 for axis in STICK_AXES}
        self.dpad = {axis: 0 for axis in DPAD_BUTTONS}
        self.active: dict[str, Action] = {}
        self.repeat_timers: dict[str, float] = {}
        self.panel_button_down = False
        self.hold_elapsed: float | None = None
        self.pointer_idle = float("inf")
        self.remainder = {"x": 0.0, "y": 0.0, "wheel": 0.0}
        self.link_watch = False
        self.link_silence = 0.0
        self.link_frozen = False
        self.link_reported = False
        self.tv_panel = tv_panel
        self.tv_panel_open = False
        self.tv_panel_check = 0.0

    @property
    def panel_open(self) -> bool:
        """Un panneau, celui du service ou celui de RaspyTV, a besoin de la manette."""
        return self.panel.is_open or self.tv_panel_open

    def _notify_mode(self) -> None:
        if self.on_mode_change:
            self.on_mode_change()

    def set_axis(self, axis: int, minimum: int, maximum: int, value: int) -> None:
        if axis in self.axis_range and maximum > minimum:
            self.axis_range[axis] = (minimum, maximum)
            self.axis_value[axis] = value

    def set_enabled(self, enabled: bool) -> None:
        if enabled == self.enabled:
            return
        if not enabled:
            for name in list(self.active):
                self._release(name)
        self.enabled = enabled
        self.remainder = dict.fromkeys(self.remainder, 0.0)

    def reset(self) -> None:
        """Oublie les appuis en cours : la manette vient d'être déconnectée."""
        self.panel.close()
        self.set_enabled(False)
        self.panel_button_down = False
        self.hold_elapsed = None
        self.dpad = dict.fromkeys(self.dpad, 0)
        self.link_watch = False
        self.link_silence = 0.0
        self.link_frozen = self.link_reported = False
        self.tv_panel_open = False

    def link_alive(self) -> None:
        self.link_silence = 0.0
        self.link_reported = False
        if self.link_frozen:
            self.link_frozen = False
            LOG.info("Liaison de la manette rétablie")

    def _freeze_link(self) -> None:
        """Liaison figée : on recentre les joysticks et on relâche tout, sinon le curseur dériverait sans fin."""
        self.link_frozen = True
        LOG.warning("La manette n'envoie plus rien : commandes suspendues")
        for axis, (minimum, maximum) in self.axis_range.items():
            self.axis_value[axis] = (minimum + maximum) / 2
        for name in list(self.active):
            self._release(name)
        self.hold_elapsed = None
        self.dpad = dict.fromkeys(self.dpad, 0)

    def _tick_link(self, elapsed: float) -> None:
        if not self.link_watch:
            return
        self.link_silence += elapsed
        if self.link_silence >= LINK_FREEZE_SECONDS and not self.link_frozen:
            self._freeze_link()
        if self.link_silence >= LINK_DEAD_SECONDS and not self.link_reported:
            self.link_reported = True
            address = self.panel.address
            if self.panel.bluetooth and address:
                LOG.warning("Liaison figée depuis %.0f s : déconnexion pour permettre la reconnexion", self.link_silence)
                self.dispatch(Action(command=("bluetoothctl", "disconnect", address)))

    def handle_event(self, event_type: int, code: int, value: int) -> None:
        self.link_alive()
        if event_type == e.EV_KEY and code in BUTTON_NAMES and value != 2:
            self._button(BUTTON_NAMES[code], value == 1)
        elif event_type == e.EV_ABS and code in DPAD_BUTTONS:
            names = DPAD_BUTTONS[code]
            previous, self.dpad[code] = self.dpad[code], value
            if previous in names and previous != value:
                self._button(names[previous], False)
            if value in names and previous != value:
                self._button(names[value], True)
        elif event_type == e.EV_ABS and code in self.axis_value:
            self.axis_value[code] = value

    def _button(self, name: str, down: bool) -> None:
        panel_config = self.config.panel
        if panel_config and name == panel_config.button:
            self._panel_button(down)
        elif self.panel.is_open:
            if down:
                self._panel_input(name)
        elif self.tv_panel_open:
            if not self.enabled:
                return
            if not down:
                self._release(name)
            elif name not in self.active:
                action = TV_PANEL_KEYS.get(name) or (self.config.actions.get(name) if name in TV_PANEL_PASSTHROUGH else None)
                if action:
                    self._press(name, action)
        elif self.enabled:
            if not down:
                self._release(name)
            elif name not in self.active and name in self.config.actions:
                self._press(name, self.config.actions[name])

    def _panel_button(self, down: bool) -> None:
        self.panel_button_down = down
        if self.tv_panel_open:
            # PS ferme le panneau de RaspyTV, comme ○.
            if down and self.enabled:
                self._fire(TV_PANEL_KEYS["circle"])
            self.hold_elapsed = None
            if not down:
                self._notify_mode()
            return
        if self.panel.is_open:
            if down:
                self.panel.close()  # Le relâchement qui suit n'est pas un appui bref.
        elif down:
            self.hold_elapsed = 0.0
        else:
            was_short = self.hold_elapsed is not None
            self.hold_elapsed = None
            action = self.config.actions.get(self.config.panel.button)
            if was_short and self.enabled and action:
                self._fire(action)
        if not down:
            self._notify_mode()  # Un jeu peut attendre ce relâchement pour céder la manette.

    def _panel_input(self, name: str) -> None:
        if name == "dpad_up":
            self.panel.move(-1)
        elif name == "dpad_down":
            self.panel.move(1)
        elif name == "cross":
            self.panel.activate()
        elif name == "circle":
            self.panel.close()

    def _pointer_recent(self) -> bool:
        return self.pointer_idle < self.config.pointer_timeout

    def _press(self, name: str, action: Action) -> None:
        if name.startswith("dpad_"):
            self.pointer_idle = float("inf")
        if action.confirm_key is not None:
            pointer = self._pointer_recent()
            action = action.confirm_pointer if pointer else action.confirm_key
            if pointer:
                self.pointer_idle = 0.0  # Enchaîner les clics reste possible sans bouger le curseur.
        self.active[name] = action
        if action.repeat:
            self.repeat_timers[name] = self.config.repeat_delay
            self._fire(action)
        elif action.keys:
            for code in action.keys:
                self.output.key(code, True)
        elif action.mouse is not None:
            self.output.mouse_button(action.mouse, True)
        else:
            self.dispatch(action)

    def _release(self, name: str) -> None:
        action = self.active.pop(name, None)
        self.repeat_timers.pop(name, None)
        if action is None or action.repeat:
            return
        if action.keys:
            for code in reversed(action.keys):
                self.output.key(code, False)
        elif action.mouse is not None:
            self.output.mouse_button(action.mouse, False)

    def _fire(self, action: Action) -> None:
        """Déclenche une fois une action : touche pressée et relâchée, ou action externe."""
        if action.external:
            self.dispatch(action)
            return
        for code in action.keys:
            self.output.key(code, True)
        for code in reversed(action.keys):
            self.output.key(code, False)

    def _axis(self, axis: int) -> float:
        minimum, maximum = self.axis_range[axis]
        centered = (self.axis_value[axis] - minimum) / (maximum - minimum) * 2 - 1
        magnitude = abs(centered)
        deadzone = self.config.deadzone
        if magnitude <= deadzone:
            return 0.0
        # La courbe quadratique garde une visée précise près du centre.
        scaled = min(1.0, (magnitude - deadzone) / (1 - deadzone)) ** 2
        return scaled if centered > 0 else -scaled

    def _take(self, name: str, delta: float) -> int:
        self.remainder[name] += delta
        whole = int(self.remainder[name])
        self.remainder[name] -= whole
        return whole

    def needs_fast_tick(self) -> bool:
        return self.enabled and any(self._axis(axis) for axis in STICK_AXES)

    def _tick_tv_panel(self, elapsed: float) -> None:
        if self.tv_panel is None:
            return
        self.tv_panel_check += elapsed
        if self.tv_panel_check < TV_PANEL_CHECK_SECONDS:
            return
        self.tv_panel_check = 0.0
        is_open = self.tv_panel.is_open()
        if is_open != self.tv_panel_open:
            # Ce qui était maintenu avant l'ouverture ou la fermeture ne doit pas rester enfoncé.
            for name in list(self.active):
                self._release(name)
            self.tv_panel_open = is_open
            self._notify_mode()

    def tick(self, elapsed: float) -> None:
        self._tick_link(elapsed)
        self._tick_tv_panel(elapsed)
        self.panel.tick(elapsed)
        self._tick_panel_hold(elapsed)
        if not self.enabled or self.link_frozen:
            return
        for name in list(self.repeat_timers):
            self.repeat_timers[name] -= elapsed
            while self.repeat_timers[name] <= 0:
                self.repeat_timers[name] += self.config.repeat_interval
                self._fire(self.active[name])
        speed = self.config.mouse_speed * elapsed
        dx = self._take("x", self._axis(e.ABS_X) * speed)
        dy = self._take("y", self._axis(e.ABS_Y) * speed)
        if dx or dy:
            self.pointer_idle = 0.0
            self.output.mouse_move(dx, dy)
        else:
            self.pointer_idle += elapsed
        # Pousser le joystick vers le haut (valeur négative) fait défiler vers le haut.
        wheel = self._take("wheel", -self._axis(e.ABS_RY) * self.config.scroll_speed * elapsed)
        if wheel:
            self.output.wheel(wheel)

    def _tick_panel_hold(self, elapsed: float) -> None:
        if self.hold_elapsed is None or self.config.panel is None:
            return
        self.hold_elapsed += elapsed
        if self.hold_elapsed >= self.config.panel.hold_seconds:
            self.hold_elapsed = None
            # Le panneau de RaspyTV (musique, son, manette) passe avant celui du service, gardé en secours.
            if self.tv_panel is not None and self.tv_panel.available():
                self.dispatch(Action(raspytv={"action": "tv-panel"}))
            else:
                self.panel.open(self.in_game)


class GrabControl:
    """Décide qui possède la manette : le service ou le jeu."""

    def __init__(self, device, mapper: Mapper):
        self.device = device
        self.mapper = mapper
        self.grabbed = False

    def set_game(self, in_game: bool) -> None:
        if in_game != self.mapper.in_game:
            LOG.info("Mode jeu : manette libérée" if in_game else "Mode télécommande")
        self.mapper.in_game = in_game
        self.apply()

    def apply(self) -> None:
        mapper = self.mapper
        # En jeu, la prise attend le relâchement du bouton : sinon le jeu ne verrait
        # jamais ce relâchement et croirait le bouton maintenu indéfiniment.
        want = not mapper.in_game or (mapper.panel_open and (self.grabbed or not mapper.panel_button_down))
        if want and not self.grabbed:
            try:
                self.device.grab()
            except OSError as exc:
                LOG.warning("Manette occupée par un autre programme : %s", exc)
                mapper.set_enabled(False)
                if mapper.panel_open and mapper.in_game and not mapper.panel_button_down:
                    mapper.panel.close()
                return
            self.grabbed = True
        elif not want and self.grabbed:
            mapper.set_enabled(False)
            try:
                self.device.ungrab()
            except OSError as exc:
                LOG.info("Manette non libérée : %s", exc)  # Elle vient d'être déconnectée.
            self.grabbed = False
        # Le panneau du service lit la manette lui-même ; celui de RaspyTV reçoit des touches.
        mapper.set_enabled(self.grabbed and not mapper.panel.is_open)


class ExternalActions:
    def __init__(self, loop):
        self.loop = loop

    def __call__(self, action: Action) -> None:
        self.loop.run_in_executor(None, run_external, action)


class UinputOutput:
    """Clavier et souris virtuels, séparés pour que le compositeur les reconnaisse bien."""

    def __init__(self):
        self.keyboard = UInput({e.EV_KEY: list(range(e.KEY_ESC, e.KEY_MICMUTE + 1))}, name="TelecomHand gamepad keyboard")
        self.mouse = UInput({
            e.EV_KEY: list(MOUSE_BUTTONS.values()),
            e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL],
        }, name="TelecomHand gamepad mouse")

    def key(self, code: int, down: bool) -> None:
        self.keyboard.write(e.EV_KEY, code, 1 if down else 0)
        self.keyboard.syn()

    def mouse_button(self, code: int, down: bool) -> None:
        self.mouse.write(e.EV_KEY, code, 1 if down else 0)
        self.mouse.syn()

    def mouse_move(self, dx: int, dy: int) -> None:
        if dx:
            self.mouse.write(e.EV_REL, e.REL_X, dx)
        if dy:
            self.mouse.write(e.EV_REL, e.REL_Y, dy)
        self.mouse.syn()

    def wheel(self, amount: int) -> None:
        self.mouse.write(e.EV_REL, e.REL_WHEEL, amount)
        self.mouse.syn()


def run_external(action: Action) -> None:
    """Exécute une action externe ; une erreur est journalisée sans arrêter le service."""
    try:
        if action.raspytv is not None:
            runtime = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
            with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
                client.settimeout(2)
                client.connect(str(Path(runtime) / "raspytv-nav.sock"))
                client.sendall(json.dumps(action.raspytv).encode("utf-8"))
        else:
            subprocess.run(action.command, check=True, timeout=10, capture_output=True)
    except (OSError, subprocess.SubprocessError) as exc:
        LOG.warning("Action impossible (%s) : %s", action.raspytv or action.command, exc)


def is_game_running(names, proc_root: str = "/proc") -> bool:
    try:
        entries = list(os.scandir(proc_root))
    except OSError:
        return False
    for entry in entries:
        if not entry.name.isdigit():
            continue
        try:
            if Path(entry.path, "comm").read_text().strip() not in names:
                continue
            arguments = Path(entry.path, "cmdline").read_bytes().split(b"\0")
        except OSError:
            continue  # Le processus vient de se terminer.
        if not arguments[0]:
            continue  # Processus zombie : sa ligne de commande est vide.
        if len(arguments) > 1 and arguments[1].decode(errors="replace") in GAME_UTILITY_ARGUMENTS:
            continue
        return True
    return False


def is_controller(device) -> bool:
    """Retient le nœud manette : la DS4 expose aussi un nœud capteurs et un nœud pavé tactile."""
    capabilities = device.capabilities(absinfo=False)
    if e.BTN_SOUTH not in capabilities.get(e.EV_KEY, []) or e.ABS_X not in capabilities.get(e.EV_ABS, []):
        return False
    return device.info.vendor == SONY_VENDOR_ID or CONTROLLER_NAME in device.name


def find_controller():
    found = None
    for path in list_devices():
        try:
            device = InputDevice(path)
        except OSError:
            continue
        if found is None and is_controller(device):
            found = device
        else:
            device.close()
    return found


async def _read_events(device, mapper: Mapper) -> None:
    async for event in device.async_read_loop():
        mapper.handle_event(event.type, event.code, event.value)


def find_motion_sensors(device):
    """Nœud capteurs de la même manette : il émet en continu, ce qui prouve que la liaison vit."""
    for path in list_devices():
        try:
            candidate = InputDevice(path)
        except OSError:
            continue
        if candidate.path != device.path and candidate.name == f"{device.name} Motion Sensors" and candidate.uniq == device.uniq:
            return candidate
        candidate.close()
    return None


async def _watch_link(sensors, mapper: Mapper) -> None:
    try:
        async for _event in sensors.async_read_loop():
            mapper.link_alive()
    except OSError:
        await asyncio.Event().wait()  # La déconnexion est déjà traitée par la lecture principale.


async def _tick(mapper: Mapper) -> None:
    last = time.monotonic()
    while True:
        await asyncio.sleep(ACTIVE_TICK_SECONDS if mapper.needs_fast_tick() else IDLE_TICK_SECONDS)
        now = time.monotonic()
        mapper.tick(min(now - last, MAX_TICK_SECONDS))
        last = now


async def _follow_game(control: GrabControl) -> None:
    while True:
        control.set_game(is_game_running(control.mapper.config.game_processes))
        await asyncio.sleep(GAME_POLL_SECONDS)


async def _run_session(device, mapper: Mapper) -> None:
    LOG.info("Manette connectée : %s", device.name)
    for axis in STICK_AXES:
        info = device.absinfo(axis)
        mapper.set_axis(axis, info.min, info.max, info.value)
    mapper.panel.set_device(device.uniq, device.info.bustype == e.BUS_BLUETOOTH)
    control = GrabControl(device, mapper)
    mapper.on_mode_change = control.apply
    mapper.in_game = is_game_running(mapper.config.game_processes)
    LOG.info("Mode jeu : manette libérée" if mapper.in_game else "Mode télécommande")
    jobs = [_read_events(device, mapper), _tick(mapper), _follow_game(control)]
    sensors = find_motion_sensors(device)
    if sensors:
        mapper.link_watch = True
        mapper.link_alive()
        jobs.append(_watch_link(sensors, mapper))
    else:
        LOG.info("Capteurs de mouvement introuvables : surveillance de la liaison désactivée")
    tasks = [asyncio.create_task(job) for job in jobs]
    try:
        done, _pending = await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        for task in done:
            try:
                task.result()
            except OSError as exc:
                LOG.info("Manette déconnectée : %s", exc)
    finally:
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        mapper.on_mode_change = None
        mapper.reset()
        for node in (device, sensors):
            try:
                if node:
                    node.close()
            except OSError:
                pass


async def _create_output() -> UinputOutput:
    """Au démarrage du Pi, /dev/uinput peut ne pas être encore prêt : on attend plutôt que de planter."""
    warned = False
    while True:
        try:
            return UinputOutput()
        except Exception as exc:  # evdev lève UInputError ou OSError selon la cause.
            if not warned:
                LOG.warning("Clavier virtuel indisponible pour l'instant (%s), nouvelle tentative…", exc)
                warned = True
            await asyncio.sleep(1)


async def _serve(config: Config) -> None:
    dispatch = ExternalActions(asyncio.get_running_loop())
    idle = config.panel.idle_seconds if config.panel else DEFAULT_PANEL_IDLE_SECONDS
    panel = ControllerPanel(PanelProcess(), dispatch, idle)
    runtime = os.environ.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
    mapper = Mapper(config, await _create_output(), dispatch, panel, TvPanel(runtime))
    LOG.info("En attente de la manette…")
    while True:
        device = find_controller()
        if device is None:
            await asyncio.sleep(DEVICE_POLL_SECONDS)
            continue
        await _run_session(device, mapper)
        LOG.info("En attente de la manette…")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--config", type=Path, default=Path(__file__).resolve().with_name("gamepad.json"))
    arguments = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s %(message)s")
    try:
        config = parse_config(json.loads(arguments.config.read_text(encoding="utf-8")))
    except (OSError, ValueError) as exc:
        LOG.error("Configuration %s invalide : %s", arguments.config, exc)
        sys.exit(EXIT_BAD_CONFIG)
    try:
        asyncio.run(_serve(config))
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    main()
