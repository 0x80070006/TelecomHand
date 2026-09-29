"""Windows service supervising the interactive TelecomHand desktop agent."""

import os
import subprocess
import sys
import time
from pathlib import Path

import servicemanager
import win32api
import win32con
import win32event
import win32process
import win32profile
import win32service
import win32serviceutil
import win32ts


SERVICE_ROOT = Path(os.environ.get("PROGRAMDATA", r"C:\ProgramData")) / "TelecomHand"
AGENT_ROOT = Path(os.environ.get("PUBLIC", r"C:\Users\Public")) / "TelecomHand"
AGENT_EXE = AGENT_ROOT / "TelecomHand-Windows.exe"
LOG_FILE = SERVICE_ROOT / "service.log"


def log(message: str) -> None:
    SERVICE_ROOT.mkdir(parents=True, exist_ok=True)
    stamp = time.strftime("%Y-%m-%d %H:%M:%S")
    with LOG_FILE.open("a", encoding="utf-8") as handle:
        handle.write(f"{stamp} {message}\n")


class TelecomHandService(win32serviceutil.ServiceFramework):
    _svc_name_ = "TelecomHandService"
    _svc_display_name_ = "TelecomHand Remote Control"
    _svc_description_ = "Supervise le client TelecomHand dans la session Windows active."

    def __init__(self, args):
        super().__init__(args)
        self.stop_event = win32event.CreateEvent(None, 0, 0, None)
        self.process_handle = None
        self.process_id = None
        self.session_id = None

    def SvcStop(self):
        self.ReportServiceStatus(win32service.SERVICE_STOP_PENDING)
        win32event.SetEvent(self.stop_event)
        self._stop_agent()

    def _stop_agent(self):
        if self.process_handle and self.process_id:
            try:
                subprocess.run(
                    ["taskkill.exe", "/PID", str(self.process_id), "/T", "/F"],
                    capture_output=True,
                    timeout=10,
                    check=False,
                )
                win32event.WaitForSingleObject(self.process_handle, 5000)
            except Exception as exc:
                log(f"Impossible d'arreter l'agent: {exc}")
            try:
                win32api.CloseHandle(self.process_handle)
            except Exception:
                pass
        self.process_handle = None
        self.process_id = None
        self.session_id = None

    def _agent_running(self) -> bool:
        if not self.process_handle:
            return False
        return win32event.WaitForSingleObject(self.process_handle, 0) == win32event.WAIT_TIMEOUT

    def _launch_agent(self, session_id: int) -> None:
        if not AGENT_EXE.exists():
            log(f"Agent introuvable: {AGENT_EXE}")
            return
        token = win32ts.WTSQueryUserToken(session_id)
        try:
            environment = win32profile.CreateEnvironmentBlock(token, False)
            startup = win32process.STARTUPINFO()
            startup.lpDesktop = r"winsta0\default"
            flags = win32con.CREATE_UNICODE_ENVIRONMENT | win32con.CREATE_NO_WINDOW
            process, thread, process_id, _ = win32process.CreateProcessAsUser(
                token,
                str(AGENT_EXE),
                f'"{AGENT_EXE}"',
                None,
                None,
                False,
                flags,
                environment,
                str(AGENT_ROOT),
                startup,
            )
            win32api.CloseHandle(thread)
            self.process_handle = process
            self.process_id = process_id
            self.session_id = session_id
            log(f"Agent lance dans la session {session_id}, PID {process_id}")
        finally:
            win32api.CloseHandle(token)

    def SvcDoRun(self):
        servicemanager.LogInfoMsg("TelecomHand service started")
        log("Service demarre")
        while win32event.WaitForSingleObject(self.stop_event, 2000) == win32event.WAIT_TIMEOUT:
            try:
                active_session = win32ts.WTSGetActiveConsoleSessionId()
                no_session = active_session in (0xFFFFFFFF, -1)
                if self._agent_running() and (no_session or active_session != self.session_id):
                    self._stop_agent()
                if not no_session and not self._agent_running():
                    self._stop_agent()
                    self._launch_agent(active_session)
            except Exception as exc:
                log(f"Erreur de supervision: {exc}")
        self._stop_agent()
        log("Service arrete")


if __name__ == "__main__":
    if len(sys.argv) == 1:
        servicemanager.Initialize()
        servicemanager.PrepareToHostSingle(TelecomHandService)
        servicemanager.StartServiceCtrlDispatcher()
    else:
        win32serviceutil.HandleCommandLine(TelecomHandService)
