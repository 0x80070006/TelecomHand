"""Tests de la logique du service manette.

Sur le Pi, ils utilisent le vrai module evdev ; ailleurs, ils le remplacent par
les codes standards de Linux. Lancement : python test_telecomhand_gamepad.py
"""
import asyncio
import json
import os
import socket
import sys
import tempfile
import types
import unittest
from pathlib import Path
from unittest import mock

try:
    import evdev  # noqa: F401
except ImportError:
    codes = dict(
        EV_KEY=1, EV_REL=2, EV_ABS=3, REL_X=0, REL_Y=1, REL_WHEEL=8, BUS_USB=3, BUS_BLUETOOTH=5,
        ABS_X=0, ABS_Y=1, ABS_RY=4, ABS_HAT0X=16, ABS_HAT0Y=17,
        BTN_LEFT=272, BTN_RIGHT=273, BTN_MIDDLE=274,
        BTN_SOUTH=304, BTN_EAST=305, BTN_NORTH=307, BTN_WEST=308, BTN_TL=310, BTN_TR=311,
        BTN_TL2=312, BTN_TR2=313, BTN_SELECT=314, BTN_START=315, BTN_MODE=316, BTN_THUMBL=317, BTN_THUMBR=318,
        KEY_ESC=1, KEY_ENTER=28, KEY_LEFTALT=56, KEY_F4=62, KEY_UP=103, KEY_LEFT=105, KEY_RIGHT=106,
        KEY_DOWN=108, KEY_COMPOSE=127, KEY_MICMUTE=248,
    )
    sys.modules["evdev"] = types.SimpleNamespace(
        InputDevice=None, UInput=None, list_devices=None, ecodes=types.SimpleNamespace(**codes),
    )

import telecomhand_gamepad as gamepad
from telecomhand_gamepad import (
    ConfigError, ControllerPanel, GrabControl, Mapper, e, is_controller, is_game_running, parse_config,
    read_battery, run_external,
)

ADDRESS = "1c:a0:b8:a5:99:d2"
SHIPPED_CONFIG = Path(__file__).with_name("gamepad.json")


class FakeOutput:
    def __init__(self):
        self.calls = []

    def key(self, code, down):
        self.calls.append(("key", code, down))

    def mouse_button(self, code, down):
        self.calls.append(("button", code, down))

    def mouse_move(self, dx, dy):
        self.calls.append(("move", dx, dy))

    def wheel(self, amount):
        self.calls.append(("wheel", amount))


class FakeWindow:
    """Remplace le processus GTK du panneau."""

    def __init__(self, starts=True):
        self.starts = starts
        self.running = False
        self.states = []

    def start(self):
        self.running = self.starts
        return self.starts

    def send(self, state):
        self.states.append(state)
        return True

    def alive(self):
        return self.running

    def stop(self):
        self.running = False


def make_mapper(buttons=None, enabled=True, window=None, **settings):
    raw = {"buttons": buttons or {}, **settings}
    output, dispatched = FakeOutput(), []
    panel = ControllerPanel(window or FakeWindow(), dispatched.append, 20, lambda _address: (55, "Discharging"))
    panel.set_device(ADDRESS, True)
    mapper = Mapper(parse_config(raw), output, dispatched.append, panel)
    mapper.set_enabled(enabled)
    return mapper, output, dispatched


def press(mapper, button, down=True):
    mapper.handle_event(e.EV_KEY, gamepad.BUTTON_CODES[button], 1 if down else 0)


class ConfigTests(unittest.TestCase):
    def test_shipped_config_is_valid(self):
        config = parse_config(json.loads(SHIPPED_CONFIG.read_text(encoding="utf-8")))
        self.assertEqual(config.panel.button, "ps")
        self.assertEqual(config.actions["circle"].raspytv, {"action": "tv-back"})
        self.assertEqual(config.actions["cross"].confirm_key.keys, (e.KEY_ENTER,))
        self.assertTrue(config.actions["r1"].repeat)
        self.assertEqual(config.actions["r1"].raspytv, {"action": "volume", "delta": 4})
        self.assertEqual(config.actions["r2"].raspytv, {"action": "media", "op": "next"})

    def test_rejects_invalid_entries(self):
        for raw in (
            {},
            {"buttons": {"nope": {"key": "KEY_UP"}}},
            {"buttons": {"cross": {"key": "KEY_NOPE"}}},
            {"buttons": {"cross": {"key": "BTN_SOUTH"}}},
            {"buttons": {"cross": {"key": "KEY_UP", "mouse": "left"}}},
            {"buttons": {"cross": {"mouse": "left", "repeat": True}}},
            {"buttons": {"cross": {"raspytv": {"id": "x"}}}},
            {"buttons": {"cross": {"command": "reboot"}}},
            {"buttons": {}, "deadzone": 1},
            {"buttons": {}, "repeat_interval": 0},
            {"buttons": {"cross": {"confirm": {"key": "KEY_ENTER"}}}},
            {"buttons": {"cross": {"confirm": {"key": "KEY_ENTER", "mouse": "nope"}}}},
            {"buttons": {"cross": {"confirm": {"key": "KEY_ENTER", "mouse": "left"}, "repeat": True}}},
            {"buttons": {}, "panel": {"button": "dpad_up"}},
            {"buttons": {}, "panel": {"button": "ps", "hold_seconds": 0}},
            {"buttons": {"ps": {"mouse": "left"}}, "panel": {"button": "ps"}},
            {"buttons": {"ps": {"key": "KEY_UP", "repeat": True}}, "panel": {"button": "ps"}},
        ):
            with self.subTest(raw=raw), self.assertRaises(ConfigError):
                parse_config(raw)


class ButtonTests(unittest.TestCase):
    def test_key_follows_the_button(self):
        mapper, output, _ = make_mapper({"cross": {"key": "KEY_ENTER"}})
        press(mapper, "cross")
        press(mapper, "cross", False)
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True), ("key", e.KEY_ENTER, False)])

    def test_combo_releases_in_reverse_order(self):
        mapper, output, _ = make_mapper({"cross": {"key": ["KEY_LEFTALT", "KEY_F4"]}})
        press(mapper, "cross")
        press(mapper, "cross", False)
        self.assertEqual(output.calls, [
            ("key", e.KEY_LEFTALT, True), ("key", e.KEY_F4, True),
            ("key", e.KEY_F4, False), ("key", e.KEY_LEFTALT, False),
        ])

    def test_mouse_button(self):
        mapper, output, _ = make_mapper({"l3": {"mouse": "left"}})
        press(mapper, "l3")
        press(mapper, "l3", False)
        self.assertEqual(output.calls, [("button", e.BTN_LEFT, True), ("button", e.BTN_LEFT, False)])

    def test_external_action_fires_once_on_press(self):
        mapper, output, dispatched = make_mapper({"ps": {"raspytv": {"action": "home"}}})
        press(mapper, "ps")
        press(mapper, "ps", False)
        self.assertEqual([action.raspytv for action in dispatched], [{"action": "home"}])
        self.assertEqual(output.calls, [])

    def test_unmapped_button_does_nothing(self):
        mapper, output, dispatched = make_mapper({})
        press(mapper, "cross")
        press(mapper, "cross", False)
        self.assertEqual((output.calls, dispatched), ([], []))

    def test_autorepeat_event_is_ignored(self):
        mapper, output, _ = make_mapper({"cross": {"key": "KEY_ENTER"}})
        press(mapper, "cross")
        mapper.handle_event(e.EV_KEY, gamepad.BUTTON_CODES["cross"], 2)
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True)])

    def test_dpad_moves_between_directions(self):
        mapper, output, _ = make_mapper({"dpad_left": {"key": "KEY_LEFT"}, "dpad_right": {"key": "KEY_RIGHT"}})
        for value in (-1, 1, 0):
            mapper.handle_event(e.EV_ABS, e.ABS_HAT0X, value)
        self.assertEqual(output.calls, [
            ("key", e.KEY_LEFT, True), ("key", e.KEY_LEFT, False),
            ("key", e.KEY_RIGHT, True), ("key", e.KEY_RIGHT, False),
        ])


class RepeatTests(unittest.TestCase):
    BUTTONS = {"r2": {"raspytv": {"action": "volume", "delta": 5}, "repeat": True}}

    def test_repeats_after_the_delay_until_release(self):
        mapper, _, dispatched = make_mapper(self.BUTTONS, repeat_delay=0.4, repeat_interval=0.1)
        press(mapper, "r2")
        self.assertEqual(len(dispatched), 1)
        mapper.tick(0.3)
        self.assertEqual(len(dispatched), 1)
        mapper.tick(0.15)
        self.assertEqual(len(dispatched), 2)
        mapper.tick(0.1)
        self.assertEqual(len(dispatched), 3)
        press(mapper, "r2", False)
        mapper.tick(1)
        self.assertEqual(len(dispatched), 3)

    def test_repeated_key_is_pulsed(self):
        mapper, output, _ = make_mapper({"r2": {"key": "KEY_UP", "repeat": True}})
        press(mapper, "r2")
        self.assertEqual(output.calls, [("key", e.KEY_UP, True), ("key", e.KEY_UP, False)])
        press(mapper, "r2", False)
        self.assertEqual(len(output.calls), 2)


class StickTests(unittest.TestCase):
    def test_deadzone_blocks_movement(self):
        mapper, output, _ = make_mapper()
        mapper.handle_event(e.EV_ABS, e.ABS_X, 140)
        mapper.tick(0.1)
        self.assertEqual(output.calls, [])
        self.assertFalse(mapper.needs_fast_tick())

    def test_full_deflection_moves_at_mouse_speed(self):
        mapper, output, _ = make_mapper(mouse_speed=1000)
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.handle_event(e.EV_ABS, e.ABS_Y, 0)
        self.assertTrue(mapper.needs_fast_tick())
        mapper.tick(0.1)
        self.assertEqual(output.calls, [("move", 100, -100)])

    def test_slow_movement_accumulates(self):
        mapper, output, _ = make_mapper(mouse_speed=10)
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        for _ in range(4):
            mapper.tick(0.05)
        self.assertEqual(output.calls, [("move", 1, 0), ("move", 1, 0)])

    def test_stick_up_scrolls_up(self):
        mapper, output, _ = make_mapper(scroll_speed=10)
        mapper.handle_event(e.EV_ABS, e.ABS_RY, 0)
        mapper.tick(0.2)
        self.assertEqual(output.calls, [("wheel", 2)])

    def test_uses_the_range_reported_by_the_device(self):
        mapper, output, _ = make_mapper(mouse_speed=1000)
        mapper.set_axis(e.ABS_X, -100, 100, 100)
        mapper.tick(0.1)
        self.assertEqual(output.calls, [("move", 100, 0)])


class ConfirmTests(unittest.TestCase):
    BUTTONS = {"cross": {"confirm": {"key": "KEY_ENTER", "mouse": "left"}}, "dpad_up": {"key": "KEY_UP"}}

    def test_without_pointer_cross_presses_enter(self):
        mapper, output, _ = make_mapper(self.BUTTONS)
        press(mapper, "cross")
        press(mapper, "cross", False)
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True), ("key", e.KEY_ENTER, False)])

    def move_pointer(self, mapper, output):
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.tick(0.1)
        mapper.handle_event(e.EV_ABS, e.ABS_X, 127.5)
        output.calls.clear()

    def test_after_the_stick_cross_clicks_at_the_cursor(self):
        mapper, output, _ = make_mapper(self.BUTTONS)
        self.move_pointer(mapper, output)
        press(mapper, "cross")
        press(mapper, "cross", False)
        self.assertEqual(output.calls, [("button", e.BTN_LEFT, True), ("button", e.BTN_LEFT, False)])

    def test_clicks_can_follow_each_other_without_moving(self):
        mapper, output, _ = make_mapper(self.BUTTONS, pointer_timeout=1)
        self.move_pointer(mapper, output)
        for _ in range(3):
            mapper.tick(0.8)
            press(mapper, "cross")
            press(mapper, "cross", False)
        self.assertEqual([call[0] for call in output.calls], ["button"] * 6)

    def test_dpad_switches_back_to_enter(self):
        mapper, output, _ = make_mapper(self.BUTTONS)
        self.move_pointer(mapper, output)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, -1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        output.calls.clear()
        press(mapper, "cross")
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True)])

    def test_pointer_mode_expires(self):
        mapper, output, _ = make_mapper(self.BUTTONS, pointer_timeout=1)
        self.move_pointer(mapper, output)
        mapper.tick(1.1)
        press(mapper, "cross")
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True)])

    def test_release_matches_what_was_pressed(self):
        mapper, output, _ = make_mapper(self.BUTTONS)
        self.move_pointer(mapper, output)
        press(mapper, "cross")
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, -1)
        press(mapper, "cross", False)
        self.assertEqual(output.calls[-1], ("button", e.BTN_LEFT, False))


class PanelTests(unittest.TestCase):
    BUTTONS = {"ps": {"raspytv": {"action": "tv-home"}}, "cross": {"key": "KEY_ENTER"}}
    PANEL = {"button": "ps", "hold_seconds": 1.0, "idle_seconds": 20}

    def mapper(self, **options):
        return make_mapper(self.BUTTONS, panel=self.PANEL, **options)

    def test_short_press_fires_on_release_only(self):
        mapper, _, dispatched = self.mapper()
        press(mapper, "ps")
        mapper.tick(0.5)
        self.assertEqual(dispatched, [])
        press(mapper, "ps", False)
        mapper.tick(2)
        self.assertEqual([action.raspytv for action in dispatched], [{"action": "tv-home"}])
        self.assertFalse(mapper.panel_open)

    def test_long_press_opens_the_panel_without_the_short_action(self):
        window = FakeWindow()
        mapper, _, dispatched = self.mapper(window=window)
        press(mapper, "ps")
        mapper.tick(1.1)
        self.assertTrue(mapper.panel_open)
        press(mapper, "ps", False)
        self.assertTrue(mapper.panel_open, "le relâchement ne ferme pas le panneau")
        self.assertEqual(dispatched, [])
        self.assertEqual(window.states[-1]["battery"], 55)
        self.assertEqual(window.states[-1]["items"], ["Éteindre la manette", "Fermer"])

    def open_panel(self, mapper):
        press(mapper, "ps")
        mapper.tick(1.1)
        press(mapper, "ps", False)

    def test_turning_off_disconnects_this_controller(self):
        mapper, _, dispatched = self.mapper()
        self.open_panel(mapper)
        press(mapper, "cross")
        self.assertFalse(mapper.panel_open)
        self.assertEqual(dispatched[-1].command, ("bluetoothctl", "disconnect", ADDRESS))

    def test_dpad_wraps_and_close_does_nothing_else(self):
        window = FakeWindow()
        mapper, _, dispatched = self.mapper(window=window)
        self.open_panel(mapper)
        press(mapper, "cross", False)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        self.assertEqual(window.states[-1]["selected"], 1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        self.assertEqual(window.states[-1]["selected"], 0)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, -1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        press(mapper, "cross")
        self.assertFalse(mapper.panel_open)
        self.assertEqual(dispatched, [])

    def test_circle_and_ps_close_without_side_effect(self):
        for closing in ("circle", "ps"):
            with self.subTest(closing=closing):
                window = FakeWindow()
                mapper, _, dispatched = self.mapper(window=window)
                self.open_panel(mapper)
                press(mapper, closing)
                press(mapper, closing, False)
                self.assertFalse(mapper.panel_open)
                self.assertFalse(window.running)
                self.assertEqual(dispatched, [])

    def test_other_inputs_are_blocked_while_open(self):
        mapper, output, dispatched = self.mapper()
        self.open_panel(mapper)
        mapper.set_enabled(False)
        press(mapper, "triangle")
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.tick(0.1)
        self.assertEqual((output.calls, dispatched), ([], []))
        self.assertTrue(mapper.panel_open)

    def test_closes_after_being_left_alone(self):
        mapper, _, _ = self.mapper()
        self.open_panel(mapper)
        for _ in range(250):
            mapper.tick(0.1)
        self.assertFalse(mapper.panel_open)

    def test_dead_window_closes_the_panel(self):
        window = FakeWindow()
        mapper, _, _ = self.mapper(window=window)
        self.open_panel(mapper)
        window.running = False
        mapper.tick(0.05)
        self.assertFalse(mapper.panel_open)

    def test_window_that_cannot_start_leaves_the_controller_usable(self):
        mapper, output, _ = self.mapper(window=FakeWindow(starts=False))
        press(mapper, "ps")
        mapper.tick(1.1)
        press(mapper, "ps", False)
        self.assertFalse(mapper.panel_open)
        press(mapper, "cross")
        self.assertEqual(output.calls, [("key", e.KEY_ENTER, True)])

    def test_usb_controller_has_no_power_off(self):
        window = FakeWindow()
        mapper, _, _ = self.mapper(window=window)
        mapper.panel.set_device(ADDRESS, False)
        self.open_panel(mapper)
        self.assertEqual(window.states[-1]["items"], ["Fermer"])
        self.assertEqual(window.states[-1]["connection"], "USB")

    def test_unusual_address_never_reaches_a_command(self):
        window = FakeWindow()
        mapper, _, _ = self.mapper(window=window)
        mapper.panel.set_device("--help", True)
        self.open_panel(mapper)
        self.assertEqual(window.states[-1]["items"], ["Fermer"])

    def test_in_game_offers_to_leave_the_game(self):
        window = FakeWindow()
        mapper, _, dispatched = self.mapper(window=window, enabled=False)
        mapper.in_game = True
        self.open_panel(mapper)
        self.assertEqual(window.states[-1]["items"][1], "Quitter le jeu et revenir à l'accueil")
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        press(mapper, "cross")
        self.assertEqual(dispatched[-1].raspytv, {"action": "home"})

    def test_short_press_in_game_stays_in_the_game(self):
        mapper, _, dispatched = self.mapper(enabled=False)
        mapper.in_game = True
        press(mapper, "ps")
        mapper.tick(0.5)
        press(mapper, "ps", False)
        mapper.tick(2)
        self.assertEqual(dispatched, [])
        self.assertFalse(mapper.panel_open)


class GrabControlTests(unittest.TestCase):
    PANEL = {"button": "ps", "hold_seconds": 1.0}

    def setup_control(self, in_game):
        mapper, _, _ = make_mapper({"cross": {"key": "KEY_ENTER"}}, enabled=False, panel=self.PANEL)
        device = FakeDevice()
        control = GrabControl(device, mapper)
        mapper.on_mode_change = control.apply
        control.set_game(in_game)
        return mapper, device, control

    def test_remote_grabs_and_game_releases(self):
        mapper, device, control = self.setup_control(False)
        self.assertTrue(device.grabbed and mapper.enabled)
        control.set_game(True)
        self.assertFalse(device.grabbed or mapper.enabled)
        control.set_game(False)
        self.assertTrue(device.grabbed and mapper.enabled)

    def test_panel_in_game_grabs_only_after_the_button_is_released(self):
        mapper, device, _ = self.setup_control(True)
        press(mapper, "ps")
        mapper.tick(1.1)
        self.assertTrue(mapper.panel_open)
        self.assertFalse(device.grabbed, "le jeu doit voir le relâchement")
        press(mapper, "ps", False)
        self.assertTrue(device.grabbed)
        self.assertFalse(mapper.enabled)

    def test_closing_the_panel_in_game_gives_the_controller_back(self):
        mapper, device, _ = self.setup_control(True)
        press(mapper, "ps")
        mapper.tick(1.1)
        press(mapper, "ps", False)
        press(mapper, "circle")
        self.assertFalse(mapper.panel_open or device.grabbed or mapper.enabled)

    def test_panel_outside_a_game_keeps_the_grab_and_disables_outputs(self):
        mapper, device, _ = self.setup_control(False)
        press(mapper, "ps")
        mapper.tick(1.1)
        self.assertTrue(device.grabbed)
        self.assertFalse(mapper.enabled)
        press(mapper, "ps", False)
        press(mapper, "circle")
        self.assertTrue(device.grabbed and mapper.enabled)

    def test_game_closing_while_the_panel_is_open_keeps_it_working(self):
        mapper, device, control = self.setup_control(True)
        press(mapper, "ps")
        mapper.tick(1.1)
        press(mapper, "ps", False)
        control.set_game(False)
        self.assertTrue(mapper.panel_open and device.grabbed)
        press(mapper, "circle")
        self.assertTrue(device.grabbed and mapper.enabled)

    def test_grab_failure_retries_and_leaves_outputs_off(self):
        mapper, device, control = self.setup_control(True)
        device.grab = mock.Mock(side_effect=OSError(16, "Device or resource busy"))
        control.set_game(False)
        self.assertFalse(mapper.enabled)
        device.grab = lambda: None
        control.set_game(False)
        self.assertTrue(mapper.enabled)

    def test_reset_forgets_everything(self):
        mapper, _, _ = self.setup_control(False)
        press(mapper, "ps")
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0X, 1)
        mapper.on_mode_change = None
        mapper.reset()
        self.assertFalse(mapper.panel_open or mapper.enabled or mapper.panel_button_down)
        self.assertIsNone(mapper.hold_elapsed)
        self.assertEqual(mapper.dpad[e.ABS_HAT0X], 0)


class FakeTvPanel:
    def __init__(self, available=True):
        self.running = available
        self.opened = False

    def available(self):
        return self.running

    def is_open(self):
        return self.opened


class TvPanelTests(unittest.TestCase):
    BUTTONS = {"cross": {"confirm": {"key": "KEY_ENTER", "mouse": "left"}}, "circle": {"raspytv": {"action": "tv-back"}},
               "dpad_down": {"key": "KEY_DOWN"}, "triangle": {"raspytv": {"action": "tv-context"}},
               "ps": {"raspytv": {"action": "tv-home"}}, "r1": {"raspytv": {"action": "volume", "delta": 4}, "repeat": True}}

    def setup(self, available=True, in_game=False):
        mapper, output, dispatched = make_mapper(self.BUTTONS, enabled=False, panel={"button": "ps", "hold_seconds": 1.0})
        tv = FakeTvPanel(available)
        mapper.tv_panel = tv
        device = FakeDevice()
        control = GrabControl(device, mapper)
        mapper.on_mode_change = control.apply
        control.set_game(in_game)
        return mapper, output, dispatched, tv, device

    def open_with_ps(self, mapper, tv):
        press(mapper, "ps")
        mapper.tick(1.1)
        tv.opened = True  # RaspyTV a ouvert son panneau et créé son fichier témoin.
        press(mapper, "ps", False)
        mapper.tick(0.25)

    def test_long_press_asks_raspytv_for_its_panel(self):
        mapper, _, dispatched, tv, _ = self.setup()
        press(mapper, "ps")
        mapper.tick(1.1)
        self.assertEqual([action.raspytv for action in dispatched], [{"action": "tv-panel"}])
        self.assertFalse(mapper.panel.is_open, "le panneau du service reste fermé")

    def test_without_raspytv_the_service_panel_opens(self):
        mapper, _, dispatched, _, _ = self.setup(available=False)
        press(mapper, "ps")
        mapper.tick(1.1)
        self.assertTrue(mapper.panel.is_open)
        self.assertEqual(dispatched, [])

    def test_buttons_become_keys_for_the_panel(self):
        mapper, output, dispatched, tv, _ = self.setup()
        self.open_with_ps(mapper, tv)
        dispatched.clear()
        output.calls.clear()
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.tick(0.1)  # Joystick bougé : hors panneau, ✕ cliquerait au curseur.
        mapper.handle_event(e.EV_ABS, e.ABS_X, 127.5)
        output.calls.clear()
        for name in ("cross", "circle"):
            press(mapper, name)
            press(mapper, name, False)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 1)
        mapper.handle_event(e.EV_ABS, e.ABS_HAT0Y, 0)
        press(mapper, "triangle")
        press(mapper, "triangle", False)
        keys = [call for call in output.calls if call[0] == "key"]
        self.assertEqual(keys, [("key", e.KEY_ENTER, True), ("key", e.KEY_ENTER, False), ("key", e.KEY_ESC, True),
                                ("key", e.KEY_ESC, False), ("key", e.KEY_DOWN, True), ("key", e.KEY_DOWN, False)])
        self.assertEqual(dispatched, [], "△ et ○ ne déclenchent pas leurs actions habituelles")

    def test_volume_still_works_in_the_panel(self):
        mapper, _, dispatched, tv, _ = self.setup()
        self.open_with_ps(mapper, tv)
        dispatched.clear()
        press(mapper, "r1")
        self.assertEqual(dispatched[-1].raspytv, {"action": "volume", "delta": 4})

    def test_ps_closes_the_panel_with_escape(self):
        mapper, output, dispatched, tv, _ = self.setup()
        self.open_with_ps(mapper, tv)
        output.calls.clear()
        press(mapper, "ps")
        press(mapper, "ps", False)
        self.assertEqual(output.calls, [("key", e.KEY_ESC, True), ("key", e.KEY_ESC, False)])
        self.assertNotIn({"action": "tv-home"}, [action.raspytv for action in dispatched])

    def test_in_game_the_controller_is_taken_while_the_panel_is_open(self):
        mapper, _, _, tv, device = self.setup(in_game=True)
        self.assertFalse(device.grabbed)
        self.open_with_ps(mapper, tv)
        self.assertTrue(device.grabbed and mapper.enabled)
        tv.opened = False
        mapper.tick(0.25)
        self.assertFalse(device.grabbed or mapper.enabled, "la manette revient au jeu")


class LinkTests(unittest.TestCase):
    BUTTONS = {"cross": {"key": "KEY_ENTER"}}

    def watched(self, **options):
        mapper, output, dispatched = make_mapper(self.BUTTONS, mouse_speed=1000, **options)
        mapper.link_watch = True
        return mapper, output, dispatched

    def test_silence_stops_the_drifting_cursor_and_releases_keys(self):
        mapper, output, _ = self.watched()
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        press(mapper, "cross")
        mapper.tick(0.1)
        output.calls.clear()
        for _ in range(12):
            mapper.tick(0.1)
        self.assertTrue(mapper.link_frozen)
        self.assertIn(("key", e.KEY_ENTER, False), output.calls)
        moves = [call for call in output.calls if call[0] == "move"]
        self.assertLessEqual(len(moves), 9, "le curseur s'arrête dès que la liaison est jugée figée")
        output.calls.clear()
        mapper.tick(0.5)
        self.assertEqual(output.calls, [])

    def test_long_silence_disconnects_once(self):
        mapper, _, dispatched = self.watched()
        for _ in range(100):
            mapper.tick(0.1)
        disconnects = [action.command for action in dispatched if action.command]
        self.assertEqual(disconnects, [("bluetoothctl", "disconnect", ADDRESS)])

    def test_usb_controller_is_never_disconnected(self):
        mapper, _, dispatched = self.watched()
        mapper.panel.set_device(ADDRESS, False)
        for _ in range(100):
            mapper.tick(0.1)
        self.assertEqual(dispatched, [])

    def test_activity_resumes_control(self):
        mapper, output, _ = self.watched()
        for _ in range(12):
            mapper.tick(0.1)
        mapper.link_alive()
        self.assertFalse(mapper.link_frozen)
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.tick(0.1)
        self.assertEqual(output.calls[-1], ("move", 100, 0))

    def test_steady_sensor_stream_never_freezes(self):
        mapper, _, dispatched = self.watched()
        for _ in range(200):
            mapper.link_alive()
            mapper.tick(0.05)
        self.assertFalse(mapper.link_frozen)
        self.assertEqual(dispatched, [])

    def test_without_sensors_nothing_is_watched(self):
        mapper, _, dispatched = make_mapper(self.BUTTONS)
        for _ in range(200):
            mapper.tick(0.1)
        self.assertFalse(mapper.link_frozen)
        self.assertEqual(dispatched, [])


@unittest.skipIf(sys.platform == "win32", "Windows refuse les « : » des adresses dans un nom de dossier")
class BatteryTests(unittest.TestCase):
    def supply(self, root, name, capacity="55", status="Discharging"):
        folder = Path(root, name)
        folder.mkdir()
        (folder / "capacity").write_text(capacity + "\n")
        (folder / "status").write_text(status + "\n")

    def test_reads_the_battery_of_this_controller(self):
        with tempfile.TemporaryDirectory() as root:
            self.supply(root, "BAT0", "1")
            self.supply(root, f"ps-controller-battery-{ADDRESS}", "85", "Charging")
            self.supply(root, "ps-controller-battery-aa:aa:aa:aa:aa:aa", "10")
            self.assertEqual(read_battery(ADDRESS.upper(), root), (85, "Charging"))

    def test_without_address_a_single_controller_is_unambiguous(self):
        with tempfile.TemporaryDirectory() as root:
            self.supply(root, f"sony_controller_battery_{ADDRESS}", "45")
            self.assertEqual(read_battery(None, root), (45, "Discharging"))

    def test_unreadable_battery_is_unknown_rather_than_an_error(self):
        with tempfile.TemporaryDirectory() as root:
            self.assertEqual(read_battery(ADDRESS, root), (None, ""))
            self.supply(root, f"ps-controller-battery-{ADDRESS}", "garbage")
            self.assertEqual(read_battery(ADDRESS, root), (None, ""))
        self.assertEqual(read_battery(ADDRESS, "/nonexistent"), (None, ""))

    def test_capacity_is_bounded(self):
        with tempfile.TemporaryDirectory() as root:
            self.supply(root, f"ps-controller-battery-{ADDRESS}", "130")
            self.assertEqual(read_battery(ADDRESS, root)[0], 100)


class GameModeTests(unittest.TestCase):
    def test_entering_game_releases_held_inputs(self):
        mapper, output, _ = make_mapper({"cross": {"key": "KEY_ENTER"}, "l3": {"mouse": "left"}})
        press(mapper, "cross")
        press(mapper, "l3")
        output.calls.clear()
        mapper.set_enabled(False)
        self.assertCountEqual(output.calls, [("key", e.KEY_ENTER, False), ("button", e.BTN_LEFT, False)])

    def test_nothing_is_sent_during_a_game(self):
        mapper, output, dispatched = make_mapper(
            {"cross": {"key": "KEY_ENTER"}, "ps": {"raspytv": {"action": "home"}}}, enabled=False)
        press(mapper, "cross")
        press(mapper, "ps")
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.tick(0.1)
        self.assertEqual((output.calls, dispatched), ([], []))

    def test_button_held_across_the_switch_sends_no_stray_release(self):
        mapper, output, _ = make_mapper({"cross": {"key": "KEY_ENTER"}}, enabled=False)
        press(mapper, "cross")
        mapper.set_enabled(True)
        press(mapper, "cross", False)
        self.assertEqual(output.calls, [])

    def test_stick_position_is_kept_for_the_return_to_remote_mode(self):
        mapper, output, _ = make_mapper(enabled=False, mouse_speed=1000)
        mapper.handle_event(e.EV_ABS, e.ABS_X, 255)
        mapper.set_enabled(True)
        mapper.tick(0.1)
        self.assertEqual(output.calls, [("move", 100, 0)])


class GameDetectionTests(unittest.TestCase):
    def detect(self, processes):
        with tempfile.TemporaryDirectory() as root:
            for pid, (comm, cmdline) in enumerate(processes, start=100):
                folder = Path(root, str(pid))
                folder.mkdir()
                (folder / "comm").write_text(comm + "\n")
                (folder / "cmdline").write_bytes(b"\0".join(arg.encode() for arg in cmdline) + (b"\0" if cmdline else b""))
            Path(root, "self").mkdir()
            return is_game_running({"moonlight-qt"}, root)

    def test_stream_and_interface_count_as_game(self):
        self.assertTrue(self.detect([("moonlight-qt", ["/usr/bin/moonlight-qt", "stream", "host", "Game"])]))
        self.assertTrue(self.detect([("moonlight-qt", ["/usr/bin/moonlight-qt"])]))

    def test_catalog_listing_is_not_a_game(self):
        self.assertFalse(self.detect([("moonlight-qt", ["/usr/bin/moonlight-qt", "list", "--csv", "host"])]))

    def test_zombie_and_other_processes_are_ignored(self):
        self.assertFalse(self.detect([("moonlight-qt", []), ("electron", ["electron", "stream"])]))

    def test_missing_proc_is_not_a_game(self):
        self.assertFalse(is_game_running({"moonlight-qt"}, "/nonexistent-proc"))


class ControllerDetectionTests(unittest.TestCase):
    @staticmethod
    def device(name, vendor, keys, axes):
        return types.SimpleNamespace(
            name=name, info=types.SimpleNamespace(vendor=vendor),
            capabilities=lambda absinfo=False: {e.EV_KEY: keys, e.EV_ABS: axes},
        )

    def test_recognises_the_gamepad_node_only(self):
        pad = [e.BTN_SOUTH], [e.ABS_X]
        self.assertTrue(is_controller(self.device("Wireless Controller", 0x054C, *pad)))
        self.assertTrue(is_controller(self.device("Sony Interactive Entertainment Wireless Controller", 0x054C, *pad)))
        self.assertTrue(is_controller(self.device("Wireless Controller", 0x1234, *pad)))
        self.assertFalse(is_controller(self.device("Wireless Controller Touchpad", 0x054C, [e.BTN_LEFT], [e.ABS_X])))
        self.assertFalse(is_controller(self.device("Wireless Controller Motion Sensors", 0x054C, [], [e.ABS_X])))
        self.assertFalse(is_controller(self.device("Other Gamepad", 0x1234, *pad)))


class FakeDevice:
    name = "Wireless Controller"
    uniq = ADDRESS
    info = types.SimpleNamespace(bustype=e.BUS_BLUETOOTH, vendor=0x054C)

    def __init__(self):
        self.queue = asyncio.Queue()
        self.grabbed = False
        self.closed = False

    def absinfo(self, _axis):
        return types.SimpleNamespace(min=0, max=255, value=128)

    def grab(self):
        self.grabbed = True

    def ungrab(self):
        self.grabbed = False

    def close(self):
        self.closed = True

    async def async_read_loop(self):
        while True:
            item = await self.queue.get()
            if isinstance(item, Exception):
                raise item
            yield item


class SessionTests(unittest.IsolatedAsyncioTestCase):
    @staticmethod
    async def until(condition):
        async with asyncio.timeout(2):
            while not condition():
                await asyncio.sleep(0.005)

    async def test_grab_game_mode_and_disconnection(self):
        in_game = [False]
        mapper, output, _ = make_mapper({"cross": {"key": "KEY_ENTER"}}, enabled=False)
        device = FakeDevice()
        cross = lambda value: types.SimpleNamespace(type=e.EV_KEY, code=gamepad.BUTTON_CODES["cross"], value=value)
        with mock.patch.object(gamepad, "GAME_POLL_SECONDS", 0.005), \
                mock.patch.object(gamepad, "is_game_running", lambda _names: in_game[0]), \
                mock.patch.object(gamepad, "find_motion_sensors", lambda _device: None):
            session = asyncio.create_task(gamepad._run_session(device, mapper))

            await self.until(lambda: device.grabbed and mapper.enabled)
            device.queue.put_nowait(cross(1))
            await self.until(lambda: output.calls == [("key", e.KEY_ENTER, True)])

            in_game[0] = True
            await self.until(lambda: not device.grabbed and not mapper.enabled)
            self.assertEqual(output.calls[-1], ("key", e.KEY_ENTER, False), "touche relâchée en entrant en jeu")
            count = len(output.calls)
            device.queue.put_nowait(cross(0))
            device.queue.put_nowait(cross(1))
            await asyncio.sleep(0.02)
            self.assertEqual(len(output.calls), count, "rien n'est envoyé pendant le jeu")

            in_game[0] = False
            await self.until(lambda: device.grabbed and mapper.enabled)

            device.queue.put_nowait(OSError(19, "No such device"))
            await asyncio.wait_for(session, 2)
        self.assertTrue(device.closed)
        self.assertFalse(mapper.enabled)

    async def test_disconnection_closes_an_open_panel(self):
        window = FakeWindow()
        mapper, _, _ = make_mapper(window=window, enabled=False, panel={"button": "ps"})
        device = FakeDevice()
        ps = lambda value: types.SimpleNamespace(type=e.EV_KEY, code=gamepad.BUTTON_CODES["ps"], value=value)
        with mock.patch.object(gamepad, "GAME_POLL_SECONDS", 0.005), \
                mock.patch.object(gamepad, "is_game_running", lambda _names: False), \
                mock.patch.object(gamepad, "find_motion_sensors", lambda _device: None):
            session = asyncio.create_task(gamepad._run_session(device, mapper))
            await self.until(lambda: device.grabbed)
            device.queue.put_nowait(ps(1))
            await asyncio.sleep(0.01)
            mapper.tick(1.1)
            self.assertTrue(mapper.panel_open)
            device.queue.put_nowait(OSError(19, "No such device"))
            await asyncio.wait_for(session, 2)
        self.assertFalse(mapper.panel_open or window.running)


class ExternalActionTests(unittest.TestCase):
    @unittest.skipUnless(hasattr(socket, "AF_UNIX") and sys.platform != "win32", "socket Unix requis")
    def test_raspytv_command_reaches_the_socket(self):
        with tempfile.TemporaryDirectory() as runtime, socket.socket(socket.AF_UNIX) as server:
            server.bind(str(Path(runtime, "raspytv-nav.sock")))
            server.listen()
            with mock.patch.dict(os.environ, {"XDG_RUNTIME_DIR": runtime}):
                run_external(gamepad.Action(raspytv={"action": "home"}))
            client, _ = server.accept()
            with client:
                self.assertEqual(json.loads(client.recv(4096)), {"action": "home"})

    @unittest.skipUnless(hasattr(socket, "AF_UNIX") and sys.platform != "win32", "socket Unix requis")
    def test_missing_raspytv_does_not_stop_the_service(self):
        with mock.patch.dict(os.environ, {"XDG_RUNTIME_DIR": "/nonexistent"}), self.assertLogs(gamepad.LOG, "WARNING"):
            run_external(gamepad.Action(raspytv={"action": "home"}))

    def test_failed_command_does_not_stop_the_service(self):
        with self.assertLogs(gamepad.LOG, "WARNING"):
            run_external(gamepad.Action(command=("/nonexistent/program",)))


if __name__ == "__main__":
    unittest.main()
