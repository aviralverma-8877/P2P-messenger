"""
Pairs the PC's virtual client with the phone via a fresh invite deep-link, then just waits to
observe a file sent FROM the phone (via its chat UI's attach button), confirming pc-client
receives and reassembles it correctly.

Note: only phone->PC is testable via the one-way "share invite" deep link, since that flow only
gives the phone a session to encrypt *to* pc-client's identity -- pc-client never receives the
phone's own key bundle back, so it has no outbound session to send with.

Usage:
    python tools/test_file_receive.py --device-serial 254bee32 [--adb-path ...] [--timeout 90]
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import subprocess
import sys
import threading
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PC_CLIENT_BAT = ROOT / "tools" / "pc-client" / "build" / "install" / "pc-client" / "bin" / "pc-client.bat"


def find_adb() -> str:
    import shutil

    candidate = Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe"
    if candidate.exists():
        return str(candidate)
    found = shutil.which("adb")
    if found:
        return found
    raise RuntimeError("Could not find adb.exe -- pass --adb-path explicitly")


class Adb:
    def __init__(self, adb_path: str, serial: str):
        self.adb_path = adb_path
        self.serial = serial

    def run(self, *args: str, timeout: float = 30) -> subprocess.CompletedProcess:
        return subprocess.run(
            [self.adb_path, "-s", self.serial, *args],
            capture_output=True,
            text=True,
            timeout=timeout,
        )


class PcClient:
    def __init__(self):
        if not PC_CLIENT_BAT.exists():
            raise FileNotFoundError(f"{PC_CLIENT_BAT} not found -- run 'gradlew :tools:pc-client:installDist' first")
        self.proc = subprocess.Popen(
            [str(PC_CLIENT_BAT)],
            stdin=subprocess.PIPE,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
        self._queue: asyncio.Queue[str] = asyncio.Queue()
        self._loop = asyncio.get_event_loop()
        self._thread = threading.Thread(target=self._pump_stdout, daemon=True)
        self._thread.start()

    def _pump_stdout(self) -> None:
        assert self.proc.stdout is not None
        for line in self.proc.stdout:
            line = line.rstrip("\n")
            print(f"[pc-client] {line}", flush=True)
            self._loop.call_soon_threadsafe(self._queue.put_nowait, line)

    def send(self, command: str) -> None:
        assert self.proc.stdin is not None
        self.proc.stdin.write(command + "\n")
        self.proc.stdin.flush()

    async def wait_for(self, prefix: str, timeout: float = 20) -> str:
        deadline = time.time() + timeout
        while True:
            remaining = deadline - time.time()
            if remaining <= 0:
                raise TimeoutError(f"Timed out waiting for a line starting with {prefix!r}")
            line = await asyncio.wait_for(self._queue.get(), timeout=remaining)
            if line.startswith(prefix):
                return line


def build_invite_link(payload_json: str) -> str:
    encoded = base64.urlsafe_b64encode(payload_json.encode("utf-8")).decode("ascii").rstrip("=")
    return f"p2pmessenger://pair?d={encoded}"


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-serial", required=True)
    parser.add_argument("--adb-path", default=None)
    parser.add_argument("--timeout", type=float, default=90)
    args = parser.parse_args()

    adb_path = args.adb_path or find_adb()
    adb = Adb(adb_path, args.device_serial)
    print(f"[adb] Using device serial: {args.device_serial}")

    print("[pc-client] Starting...")
    client = PcClient()
    await client.wait_for("READY", timeout=20)
    client.send("GET_BUNDLE")
    bundle_line = await client.wait_for("BUNDLE", timeout=20)
    payload_json = bundle_line[len("BUNDLE "):]

    link = build_invite_link(payload_json)
    print(f"[link] {len(link)} chars")

    print("[phone] Force-stopping app and firing invite link intent...")
    adb.run("shell", "am", "force-stop", "com.p2pmessenger")
    time.sleep(1)
    result = adb.run(
        "shell", "am", "start", "-a", "android.intent.action.VIEW",
        "-d", link, "com.p2pmessenger",
    )
    if result.returncode != 0:
        print(f"[error] am start failed: {result.stderr}")
        return 1

    print("[phone] Waiting for the phone to connect back to pc-client...")
    await client.wait_for("LOG inbound connection from", timeout=20)
    print("[result] Phone connected and paired. Now send a file from the phone's chat UI.")
    print(f"[result] Waiting up to {args.timeout}s for RECV_FILE...")

    try:
        recv_line = await client.wait_for("RECV_FILE", timeout=args.timeout)
        print(f"[result] SUCCESS: {recv_line}")
        return 0
    except TimeoutError:
        print("[result] FAILED: no RECV_FILE observed within the timeout window.")
        return 1


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
