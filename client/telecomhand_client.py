#!/usr/bin/env python3
"""TelecomHand desktop agent for Windows, Raspberry Pi X11 and Wayland."""

import argparse
import asyncio
import csv
import hmac
import io
import json
import logging
import mimetypes
import os
import shutil
import secrets
import socket
import socketserver
import subprocess
import sys
import threading
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, unquote, urlparse
from urllib.request import urlopen

try:
    from winrt.windows.media.control import GlobalSystemMediaTransportControlsSessionManager as MediaManager
except ImportError:
    MediaManager = None

LOG = logging.getLogger("telecomhand")
KEYS = {"enter", "escape", "backspace", "tab", "space", "delete", "up", "down", "left", "right", "home", "end", "pageup", "pagedown", "capslock", *(f"f{i}" for i in range(1, 13))}


class PyAutoGuiBackend:
    def __init__(self):
        import pyautogui
        self.api = pyautogui
        self.api.PAUSE = 0
        self.api.FAILSAFE = False

    def move(self, dx: int, dy: int) -> None:
        self.api.moveRel(dx, dy, duration=0)

    def click(self, button: str) -> None:
        self.api.click(button=button)

    def button(self, button: str, down: bool) -> None:
        (self.api.mouseDown if down else self.api.mouseUp)(button=button)

    def scroll(self, dy: int) -> None:
        self.api.scroll(dy)

    def text(self, value: str) -> None:
        self.api.write(value, interval=0.002)

    def key(self, value: str) -> None:
        self.api.press(value)

    def key_state(self, value: str, down: bool) -> None:
        (self.api.keyDown if down else self.api.keyUp)(value)

    def hotkey(self, keys: list[str]) -> None:
        normalized = ["winleft" if key == "win" else key for key in keys]
        if len(normalized) == 1:
            self.api.press(normalized[0])
            return
        held = []
        try:
            for modifier in normalized[:-1]:
                self.api.keyDown(modifier)
                held.append(modifier)
            self.api.press(normalized[-1])
        finally:
            for modifier in reversed(held):
                self.api.keyUp(modifier)

    def lock_screen(self) -> None:
        if sys.platform != "win32":
            raise ValueError("screen lock is only available on Windows")
        import ctypes
        if not ctypes.windll.user32.LockWorkStation():
            raise OSError(ctypes.get_last_error(), "LockWorkStation failed")

    def media_state(self) -> dict:
        if sys.platform != "win32" or MediaManager is None:
            return {"available": False}

        async def read_state():
            manager = await MediaManager.request_async()
            session = manager.get_current_session()
            if session is None:
                return {"available": False}
            properties = await session.try_get_media_properties_async()
            timeline = session.get_timeline_properties()
            playback = session.get_playback_info()
            status = str(playback.playback_status).lower()
            return {
                "available": True,
                "title": str(properties.title or "Lecture en cours"),
                "artist": str(properties.artist or properties.album_artist or ""),
                "playing": status.endswith("playing"),
                "position": max(0, int(timeline.position.total_seconds())),
                "duration": max(0, int(timeline.end_time.total_seconds())),
            }

        try:
            return asyncio.run(read_state())
        except Exception as exc:
            LOG.debug("Windows media state unavailable: %s", exc)
            return {"available": False}

    def media_control(self, action: str) -> None:
        key = {"previous": "prevtrack", "play_pause": "playpause", "next": "nexttrack"}.get(action)
        if not key:
            raise ValueError("unsupported media action")
        self.api.press(key)

    def launch_app(self, app_id: str) -> None:
        web_apps = {
            "browser": "https://www.google.com/", "youtube": "https://www.youtube.com/",
            "spotify": "https://open.spotify.com/", "netflix": "https://www.netflix.com/",
            "mail": "https://outlook.live.com/mail/",
        }
        commands = {
            "terminal": ["wt.exe"], "explorer": ["explorer.exe"],
            "notes": ["notepad.exe"], "settings": ["cmd.exe", "/c", "start", "", "ms-settings:"],
        }
        if app_id in web_apps:
            webbrowser.open(web_apps[app_id])
        elif app_id in commands:
            subprocess.Popen(commands[app_id], close_fds=True)
        else:
            raise ValueError("unknown application")

    def launcher_state(self) -> dict:
        return {"raspyTv": False, "apps": [
            {"id": "browser", "name": "Navigateur"}, {"id": "terminal", "name": "Terminal"},
            {"id": "explorer", "name": "Explorateur"}, {"id": "mail", "name": "Mail"},
            {"id": "notes", "name": "Notes"}, {"id": "youtube", "name": "YouTube"},
            {"id": "spotify", "name": "Spotify"}, {"id": "netflix", "name": "Netflix"},
        ], "games": []}

    def game_image(self, game_id: str):
        return None

    def launch_game(self, game_name: str) -> None:
        raise ValueError("games are only available on RaspyTV")

    def volume(self, action: str) -> None:
        self.api.press({"up": "volumeup", "down": "volumedown", "mute": "volumemute"}[action])

    def audio_outputs(self) -> list[dict]:
        return []

    def set_audio_output(self, output_id: str) -> None:
        raise ValueError("audio output selection is only available on Linux")

    def screenshot(self):
        return self.api.screenshot()


class WaylandBackend:
    BUTTONS = {"left": "BTN_LEFT", "right": "BTN_RIGHT", "middle": "BTN_MIDDLE"}
    KEY_NAMES = {
        "enter": "Return", "escape": "Escape", "backspace": "BackSpace",
        "tab": "Tab", "space": "space", "delete": "Delete", "up": "Up",
        "down": "Down", "left": "Left", "right": "Right", "home": "Home",
        "end": "End", "pageup": "Page_Up", "pagedown": "Page_Down", "capslock": "Caps_Lock",
        **{f"f{i}": f"F{i}" for i in range(1, 13)},
    }

    def __init__(self):
        from evdev import UInput, ecodes as e
        self.e = e
        self.environment = os.environ.copy()
        runtime = self.environment.get("XDG_RUNTIME_DIR") or f"/run/user/{os.getuid()}"
        self.environment["XDG_RUNTIME_DIR"] = runtime
        self.environment.setdefault("DBUS_SESSION_BUS_ADDRESS", f"unix:path={runtime}/bus")
        if not self.environment.get("WAYLAND_DISPLAY"):
            sockets = sorted(Path(runtime).glob("wayland-*"))
            if sockets:
                self.environment["WAYLAND_DISPLAY"] = sockets[0].name
        self.mouse = UInput({
            e.EV_KEY: [e.BTN_LEFT, e.BTN_RIGHT, e.BTN_MIDDLE, e.KEY_VOLUMEUP, e.KEY_VOLUMEDOWN, e.KEY_MUTE],
            e.EV_REL: [e.REL_X, e.REL_Y, e.REL_WHEEL],
        }, name="TelecomHand virtual mouse")
        self.game_images: dict[str, str] = {}

    def _button_code(self, button: str):
        return getattr(self.e, self.BUTTONS.get(button, "BTN_LEFT"))

    def move(self, dx: int, dy: int) -> None:
        if dx:
            self.mouse.write(self.e.EV_REL, self.e.REL_X, dx)
        if dy:
            self.mouse.write(self.e.EV_REL, self.e.REL_Y, dy)
        self.mouse.syn()

    def button(self, button: str, down: bool) -> None:
        self.mouse.write(self.e.EV_KEY, self._button_code(button), 1 if down else 0)
        self.mouse.syn()

    def click(self, button: str) -> None:
        self.button(button, True)
        self.button(button, False)

    def scroll(self, dy: int) -> None:
        self.mouse.write(self.e.EV_REL, self.e.REL_WHEEL, dy)
        self.mouse.syn()

    def text(self, value: str) -> None:
        if value:
            subprocess.run(["wtype", "--", value], check=True, timeout=5, env=self.environment)

    def key(self, value: str) -> None:
        subprocess.run(["wtype", "-k", self.KEY_NAMES[value]], check=True, timeout=5, env=self.environment)

    def key_state(self, value: str, down: bool) -> None:
        modifier = {"ctrl": "ctrl", "alt": "alt"}.get(value)
        if not modifier:
            raise ValueError("unsupported modifier")
        subprocess.run(["wtype", "-M" if down else "-m", modifier], check=True, timeout=5, env=self.environment)

    def hotkey(self, keys: list[str]) -> None:
        modifiers = [key for key in keys[:-1] if key in {"ctrl", "alt", "shift", "logo"}]
        final_key = self.KEY_NAMES.get(keys[-1], keys[-1])
        args = ["wtype"]
        for modifier in modifiers:
            args += ["-M", modifier]
        args += ["-k", final_key]
        for modifier in reversed(modifiers):
            args += ["-m", modifier]
        subprocess.run(args, check=True, timeout=5, env=self.environment)

    def lock_screen(self) -> None:
        raise ValueError("screen lock is only available on Windows")

    def _raspytv_socket(self) -> Path | None:
        candidate = Path(self.environment["XDG_RUNTIME_DIR"]) / "raspytv-nav.sock"
        return candidate if candidate.exists() else None

    def _raspytv_command(self, payload: dict) -> None:
        target = self._raspytv_socket()
        if target is None:
            raise ValueError("RaspyTV is not running")
        with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
            client.settimeout(2)
            client.connect(str(target))
            client.sendall(json.dumps(payload).encode("utf-8"))

    def media_state(self) -> dict:
        try:
            metadata = subprocess.run(
                ["playerctl", "metadata", "--format", "{{title}}\t{{artist}}\t{{status}}\t{{mpris:length}}"],
                capture_output=True, text=True, check=True, timeout=3, env=self.environment,
            ).stdout.strip().split("\t")
            position = subprocess.run(["playerctl", "position"], capture_output=True, text=True, timeout=2, env=self.environment).stdout.strip()
            return {"available": True, "title": metadata[0] or "Lecture en cours", "artist": metadata[1] if len(metadata) > 1 else "",
                    "playing": len(metadata) > 2 and metadata[2].lower() == "playing", "position": int(float(position or 0)),
                    "duration": int(int(metadata[3]) / 1_000_000) if len(metadata) > 3 and metadata[3].isdigit() else 0}
        except Exception:
            return {"available": False}

    def media_control(self, action: str) -> None:
        command = {"previous": "previous", "play_pause": "play-pause", "next": "next"}.get(action)
        if not command:
            raise ValueError("unsupported media action")
        subprocess.run(["playerctl", command], check=True, timeout=3, env=self.environment)

    def launch_app(self, app_id: str) -> None:
        allowed = {"lumo", "jellyfin", "jellyfin-web", "youtube", "spotify", "twitch", "netflix", "homelab", "immich"}
        if app_id not in allowed:
            raise ValueError("unknown RaspyTV application")
        self._raspytv_command({"action": "launch", "id": app_id})

    def launch_game(self, game_name: str) -> None:
        if not game_name or len(game_name) > 200 or any(ord(char) < 32 for char in game_name):
            raise ValueError("invalid game")
        if game_name == "__moonlight__":
            self._raspytv_command({"action": "moonlight"})
        else:
            self._raspytv_command({"action": "moonlight-game", "name": game_name})

    def launcher_state(self) -> dict:
        is_raspytv = self._raspytv_socket() is not None
        apps = [
            {"id": "jellyfin", "name": "Lumo"}, {"id": "youtube", "name": "YouTube"},
            {"id": "spotify", "name": "Spotify"}, {"id": "twitch", "name": "Twitch"},
            {"id": "netflix", "name": "Netflix"}, {"id": "homelab", "name": "Dashboard"},
            {"id": "immich", "name": "Immich"},
        ] if is_raspytv else []
        games = []
        self.game_images = {}
        if is_raspytv:
            try:
                moonlight_host = os.environ.get("TELECOMHAND_MOONLIGHT_HOST", "").strip()
                if not moonlight_host:
                    raise ValueError("Moonlight host is not configured")
                result = subprocess.run(["/usr/bin/moonlight-qt", "list", "--csv", moonlight_host], capture_output=True, text=True, timeout=6, env={**self.environment, "QT_QPA_PLATFORM": "offscreen"})
                rows = list(csv.reader(result.stdout.splitlines()))[1:]
                for row in rows:
                    if len(row) <= 4 or not row[0] or row[4].lower() == "true":
                        continue
                    image = row[6].strip() if len(row) > 6 else ""
                    has_image = image.startswith(("file:", "http://", "https://"))
                    if has_image:
                        self.game_images[row[0]] = image
                    games.append({"id": row[0], "name": row[0], "hasImage": has_image})
            except Exception:
                games = []
            if not games:
                games = [{"id": "__moonlight__", "name": "Moonlight", "hasImage": False}]
        return {"raspyTv": is_raspytv, "apps": apps, "games": games}

    def game_image(self, game_id: str):
        source = self.game_images.get(game_id)
        if not source:
            return None
        try:
            if source.startswith("file:"):
                path = Path(unquote(urlparse(source).path))
                if not path.is_file() or path.stat().st_size > 5_000_000:
                    return None
                data = path.read_bytes()
                content_type = mimetypes.guess_type(path.name)[0] or "image/jpeg"
            else:
                with urlopen(source, timeout=5) as response:
                    content_type = response.headers.get_content_type()
                    if not content_type.startswith("image/"):
                        return None
                    data = response.read(5_000_001)
                if len(data) > 5_000_000:
                    return None
            return data, content_type
        except Exception as exc:
            LOG.debug("Game image unavailable for %s: %s", game_id, exc)
            return None

    def volume(self, action: str) -> None:
        code = {"up": self.e.KEY_VOLUMEUP, "down": self.e.KEY_VOLUMEDOWN, "mute": self.e.KEY_MUTE}[action]
        self.mouse.write(self.e.EV_KEY, code, 1)
        self.mouse.write(self.e.EV_KEY, code, 0)
        self.mouse.syn()

    def audio_outputs(self) -> list[dict]:
        result = subprocess.run(
            ["pactl", "--format=json", "list", "sinks"],
            capture_output=True, text=True, check=True, timeout=5,
        )
        default = subprocess.run(
            ["pactl", "get-default-sink"], capture_output=True, text=True,
            check=True, timeout=5,
        ).stdout.strip()
        outputs = []
        for sink in json.loads(result.stdout):
            output_id = str(sink.get("name", ""))
            if not output_id:
                continue
            properties = sink.get("properties") or {}
            label = sink.get("description") or properties.get("device.description") or output_id
            outputs.append({"id": output_id, "name": str(label), "active": output_id == default})
        return outputs

    def set_audio_output(self, output_id: str) -> None:
        known = {item["id"] for item in self.audio_outputs()}
        if output_id not in known:
            raise ValueError("unknown audio output")
        subprocess.run(["pactl", "set-default-sink", output_id], check=True, timeout=5)

    def screenshot(self):
        from PIL import Image
        result = subprocess.run(["grim", "-"], capture_output=True, check=True, timeout=5)
        return Image.open(io.BytesIO(result.stdout)).copy()


def create_backend():
    if sys.platform.startswith("linux") and (os.environ.get("WAYLAND_DISPLAY") or shutil.which("wtype")):
        LOG.info("Using Raspberry Pi Wayland backend")
        return WaylandBackend()
    LOG.info("Using PyAutoGUI desktop backend")
    return PyAutoGuiBackend()


class State:
    def __init__(self, token: str, backend):
        self.token = token
        self.backend = backend
        self.input_lock = threading.Lock()

    def authorized(self, supplied: str) -> bool:
        return hmac.compare_digest(self.token, supplied or "")

    def execute(self, command: dict) -> None:
        if not self.authorized(str(command.get("token", ""))):
            raise PermissionError("invalid token")
        kind = command.get("type")
        if kind not in {"move", "scroll", "ping"}:
            LOG.info("Command received: %s", kind)
        with self.input_lock:
            if kind == "move":
                self.backend.move(int(command.get("dx", 0)), int(command.get("dy", 0)))
            elif kind == "click":
                self.backend.click(command.get("button", "left"))
            elif kind == "button_down":
                self.backend.button(command.get("button", "left"), True)
            elif kind == "button_up":
                self.backend.button(command.get("button", "left"), False)
            elif kind == "scroll":
                self.backend.scroll(int(command.get("dy", 0)))
            elif kind == "text":
                self.backend.text(str(command.get("value", "")))
            elif kind == "key" and command.get("value") in KEYS:
                self.backend.key(command["value"])
            elif kind in {"key_down", "key_up"} and command.get("value") in {"ctrl", "alt"}:
                self.backend.key_state(command["value"], kind == "key_down")
            elif kind == "hotkey":
                keys = command.get("keys")
                if not isinstance(keys, list) or not keys or len(keys) > 4:
                    raise ValueError("invalid hotkey")
                allowed = {"win", "ctrl", "alt", "shift", "escape", "delete", "l", "tab", "d", "e", "s", "c", "v", "x", "z", "a"}
                normalized = [str(key).lower() for key in keys]
                if any(key not in allowed for key in normalized):
                    raise ValueError("unsupported hotkey")
                self.backend.hotkey(normalized)
            elif kind == "lock_screen":
                self.backend.lock_screen()
            elif kind == "volume" and command.get("action") in {"up", "down", "mute"}:
                self.backend.volume(str(command["action"]))
            elif kind == "audio_output":
                self.backend.set_audio_output(str(command.get("id", "")))
            elif kind == "media_control" and command.get("action") in {"previous", "play_pause", "next"}:
                self.backend.media_control(str(command["action"]))
            elif kind == "launch_app":
                self.backend.launch_app(str(command.get("id", "")))
            elif kind == "launch_game":
                self.backend.launch_game(str(command.get("id", "")))
            elif kind == "ping":
                pass
            elif kind != "hello":
                raise ValueError(f"unknown command: {kind}")


class ControlHandler(socketserver.StreamRequestHandler):
    def handle(self) -> None:
        LOG.info("Control connection from %s", self.client_address[0])
        self.request.setsockopt(socket.SOL_SOCKET, socket.SO_KEEPALIVE, 1)
        while True:
            try:
                line = self.rfile.readline(65536)
                if not line:
                    return
                command = json.loads(line.decode("utf-8"))
                self.server.state.execute(command)
                if command.get("type") == "hello":
                    self.wfile.write(b'{"status":"ok","version":4}\n')
                    self.wfile.flush()
            except (ConnectionError, TimeoutError, socket.timeout):
                return
            except PermissionError as exc:
                self.wfile.write(b'{"status":"error","reason":"authentication"}\n')
                self.wfile.flush()
                LOG.warning("Rejected connection from %s: %s", self.client_address[0], exc)
                return
            except Exception as exc:
                LOG.warning("Rejected command from %s: %s", self.client_address[0], exc)


class ControlServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True

    def __init__(self, address, state):
        self.state = state
        super().__init__(address, ControlHandler)


class PreviewHandler(BaseHTTPRequestHandler):
    def send_json(self, value: dict) -> None:
        body = json.dumps(value, ensure_ascii=False).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self) -> None:
        parsed = urlparse(self.path)
        token = parse_qs(parsed.query).get("token", [""])[0]
        if not self.server.state.authorized(token):
            self.send_error(403)
            return
        if parsed.path == "/audio-outputs.json":
            try:
                self.send_json({"outputs": self.server.state.backend.audio_outputs()})
            except Exception as exc:
                LOG.warning("Audio output listing failed: %s", exc)
                self.send_error(503)
            return
        if parsed.path == "/remote-state.json":
            try:
                launcher = self.server.state.backend.launcher_state()
                self.send_json({**launcher, "media": self.server.state.backend.media_state(), "protocol": 4})
            except Exception as exc:
                LOG.warning("Remote state failed: %s", exc)
                self.send_error(503)
            return
        if parsed.path == "/game-image":
            game_id = parse_qs(parsed.query).get("id", [""])[0]
            image = self.server.state.backend.game_image(game_id)
            if image is None:
                self.send_error(404)
                return
            body, content_type = image
            self.send_response(200)
            self.send_header("Content-Type", content_type)
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
            return
        if parsed.path != "/screen.jpg":
            self.send_error(404)
            return
        try:
            image = self.server.state.backend.screenshot()
            image.thumbnail((1280, 720))
            payload = io.BytesIO()
            image.convert("RGB").save(payload, "JPEG", quality=58, optimize=True)
            body = payload.getvalue()
            self.send_response(200)
            self.send_header("Content-Type", "image/jpeg")
            self.send_header("Content-Length", str(len(body)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(body)
        except Exception as exc:
            LOG.warning("Screenshot failed: %s", exc)
            self.send_error(503)

    def log_message(self, format, *args):
        return


class PreviewServer(ThreadingHTTPServer):
    daemon_threads = True

    def __init__(self, address, state):
        self.state = state
        super().__init__(address, PreviewHandler)


def local_ip() -> str:
    probe = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        probe.connect(("8.8.8.8", 80))
        return probe.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        probe.close()


def tailscale_ip() -> str | None:
    executable = shutil.which("tailscale")
    if not executable:
        return None
    try:
        result = subprocess.run(
            [executable, "ip", "-4"], capture_output=True, text=True,
            timeout=3, check=True
        )
        address = result.stdout.strip().splitlines()[0]
        return address if address.startswith("100.") else None
    except (OSError, subprocess.SubprocessError, IndexError):
        return None


def load_config(path: Path) -> dict:
    defaults = {"bind": "0.0.0.0", "port": 45870}
    if path.exists():
        defaults.update(json.loads(path.read_text(encoding="utf-8")))
    else:
        path.parent.mkdir(parents=True, exist_ok=True)
        defaults["token"] = secrets.token_urlsafe(32)
        path.write_text(json.dumps(defaults, indent=2), encoding="utf-8")
        if os.name != "nt":
            path.chmod(0o600)
    token = defaults.get("token")
    if not isinstance(token, str) or len(token) < 16 or token in {"telecomhand", "change-moi-avec-un-code-long"}:
        raise ValueError(f"Configure a unique token of at least 16 characters in {path}")
    return defaults


def application_directory() -> Path:
    """Use the executable folder when bundled by PyInstaller."""
    if getattr(sys, "frozen", False):
        return Path(sys.executable).resolve().parent
    return Path(__file__).resolve().parent


def start_windows_tray(control, preview):
    if sys.platform != "win32":
        return None
    import pystray
    from PIL import Image, ImageDraw

    image = Image.new("RGBA", (64, 64), (7, 12, 24, 255))
    draw = ImageDraw.Draw(image)
    blue = (47, 168, 255, 255)
    cyan = (30, 225, 236, 255)
    draw.rounded_rectangle((2, 2, 61, 61), radius=15, fill=(7, 12, 24, 255), outline=(64, 92, 150, 255), width=2)
    draw.arc((10, 8, 48, 46), 196, 355, fill=blue, width=5)
    draw.arc((17, 15, 42, 40), 196, 355, fill=cyan, width=5)
    draw.polygon(((27, 25), (51, 42), (38, 45), (45, 57), (37, 61), (30, 48), (21, 58)), fill=cyan)

    def restart(_icon, _item):
        LOG.info("Restart requested from tray")
        control.shutdown()
        preview.shutdown()
        _icon.stop()
        os._exit(0)

    menu = pystray.Menu(
        pystray.MenuItem("TelecomHand — actif", None, enabled=False),
        pystray.MenuItem("Ports 45870 / 45871", None, enabled=False),
        pystray.Menu.SEPARATOR,
        pystray.MenuItem("Redémarrer l’agent", restart),
    )
    icon = pystray.Icon("TelecomHand", image, "TelecomHand — télécommande active", menu)
    icon.run_detached()
    return icon


def main() -> None:
    parser = argparse.ArgumentParser(description="TelecomHand desktop agent")
    parser.add_argument("--config", default=str(application_directory() / "config.json"))
    args = parser.parse_args()
    handlers = None
    if sys.platform == "win32":
        handlers = [logging.FileHandler(application_directory() / "client.log", encoding="utf-8")]
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s", handlers=handlers)
    config = load_config(Path(args.config))
    state = State(str(config["token"]), create_backend())
    control = ControlServer((str(config["bind"]), int(config["port"])), state)
    preview = PreviewServer((str(config["bind"]), int(config["port"]) + 1), state)
    threading.Thread(target=preview.serve_forever, daemon=True).start()
    tray = start_windows_tray(control, preview)
    print("\n" + "=" * 58)
    print("  TELECOMHAND EST PRET")
    tailnet_address = tailscale_ip()
    if tailnet_address:
        print(f"  IP Tailscale recommandee : {tailnet_address}")
    print(f"  IP du reseau local       : {local_ip()}")
    print(f"  Port : {config['port']}   Code : voir {args.config}")
    print("  Gardez cette fenetre ouverte.")
    print("=" * 58 + "\n", flush=True)
    LOG.info("Control port %s — preview port %s", config["port"], int(config["port"]) + 1)
    try:
        control.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        control.shutdown(); preview.shutdown()
        if tray:
            tray.stop()


if __name__ == "__main__":
    main()
