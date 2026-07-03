"""
End-to-end test for file/image sharing: pairs a real Android phone with the PC's virtual
client via a fresh invite deep-link (simulating a tapped share-invite link), then sends a real
file from the PC to the phone over the encrypted P2P socket and waits for confirmation.

Usage:
    python tools/test_file_share.py --device-serial 254bee32 --file path\\to\\image.png [--adb-path ...]

After the PC->phone send is confirmed, the script stays alive (use --interactive) so a
phone->PC send can be triggered by hand and observed via the same pc-client process.
"""

from __future__ import annotations

import argparse
import asyncio
import base64
import json
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
    """Drives the tools/pc-client subprocess over stdin/stdout."""

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

    def close(self) -> None:
        try:
            self.send("QUIT")
        except Exception:
            pass
        self.proc.terminate()


def build_invite_link(payload_json: str) -> str:
    encoded = base64.urlsafe_b64encode(payload_json.encode("utf-8")).decode("ascii").rstrip("=")
    return f"p2pmessenger://pair?d={encoded}"


async def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--device-serial", required=True)
    parser.add_argument("--adb-path", default=None)
    parser.add_argument("--file", required=True, help="Local file on the PC to send to the phone")
    parser.add_argument("--interactive", action="store_true")
    args = parser.parse_args()

    adb_path = args.adb_path or find_adb()
    adb = Adb(adb_path, args.device_serial)
    print(f"[adb] Using device serial: {args.device_serial}")

    file_path = Path(args.file)
    if not file_path.is_file():
        print(f"[error] file not found: {file_path}")
        return 1

    print("[pc-client] Starting...")
    client = PcClient()
    try:
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
        print("[result] Phone connected. Sending test file to phone...")

        client.send(f"SEND_FILE {file_path}")
        sent_line = await client.wait_for("FILE_SENT", timeout=30)
        print(f"[result] {sent_line}")
        print("[result] Check the phone's chat screen and MediaStore for the received file.")

        if args.interactive:
            print("[result] Interactive mode: type SEND_FILE <path> to send more files, or QUIT to exit.")
            loop = asyncio.get_event_loop()
            while True:
                line = await loop.run_in_executor(None, sys.stdin.readline)
                line = line.strip()
                if not line:
                    continue
                if line == "QUIT":
                    break
                client.send(line)
        else:
            print("[result] Staying alive for 30s to observe any phone->PC send (watch for RECV_FILE)...")
            try:
                await client.wait_for("RECV_FILE", timeout=30)
            except TimeoutError:
                print("[result] No RECV_FILE observed in the 30s window.")

        return 0
    finally:
        client.close()


if __name__ == "__main__":
    raise SystemExit(asyncio.run(main()))
