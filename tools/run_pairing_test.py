"""
End-to-end test: pair a real Android phone (running the P2P Messenger app) with a "virtual
client" running on this PC, over real Bluetooth LE, then exchange real Signal-Protocol-encrypted
messages over a real IPv6 socket -- no mocks, no stubs.

Requires:
  - The debug APK installed and running on the phone, connected via `adb` (USB is fine; the BLE
    and IPv6 traffic itself goes over real radios/network, only app control uses adb/USB).
  - The phone and this PC on the same IPv6-capable LAN (see README.md in this folder for how to
    check that).
  - Python 3.10+ with `bleak` installed (`pip install bleak`).
  - `tools/pc-client` built (`gradlew :tools:pc-client:installDist`).

The PC only ever acts as a BLE *central* (scanner + GATT client) here, connecting to the phone's
advertised peripheral/GATT server -- Windows' BLE *peripheral* role is comparatively unreliable,
and the app already fully implements the peripheral side, so there's no need for the PC to do it
too for this test.

Usage:
    python tools/run_pairing_test.py [--device-serial 254bee32] [--skip-navigate]
"""

from __future__ import annotations

import argparse
import asyncio
import json
import subprocess
import sys
import threading
import time
from pathlib import Path

from bleak import BleakClient, BleakScanner

SERVICE_UUID = "6e400001-b5a3-f393-e0a9-e50e24dcca9e"
CHAR_UUID = "6e400002-b5a3-f393-e0a9-e50e24dcca9e"
FRAME_DATA = 0x01
FRAME_END = 0x02

ROOT = Path(__file__).resolve().parent.parent
ADB = ROOT.parent / "AppData"  # placeholder, overridden by --adb-path / auto-detect below
PC_CLIENT_BAT = ROOT / "tools" / "pc-client" / "build" / "install" / "pc-client" / "bin" / "pc-client.bat"


def find_adb() -> str:
    import shutil

    candidates = [
        Path.home() / "AppData/Local/Android/Sdk/platform-tools/adb.exe",
    ]
    for c in candidates:
        if c.exists():
            return str(c)
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

    def tap(self, x: int, y: int) -> None:
        self.run("shell", "input", "tap", str(x), str(y))

    def text(self, s: str) -> None:
        escaped = s.replace(" ", "%s")
        self.run("shell", "input", "text", escaped)

    def dump_ui(self) -> str:
        self.run("shell", "uiautomator", "dump", "/sdcard/ui_test.xml")
        result = self.run("shell", "cat", "/sdcard/ui_test.xml")
        return result.stdout

    def find_bounds_by_text(self, text: str) -> tuple[int, int] | None:
        """Returns the (x, y) center point of the first node whose text or
        content-desc contains `text`, by scraping the uiautomator XML dump."""
        import re

        xml = self.dump_ui()
        for m in re.finditer(r'<node[^>]*?/>', xml):
            node = m.group(0)
            if f'text="{text}"' in node or f'content-desc="{text}"' in node:
                bounds_match = re.search(r'bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', node)
                if bounds_match:
                    x1, y1, x2, y2 = map(int, bounds_match.groups())
                    return (x1 + x2) // 2, (y1 + y2) // 2
        return None

    def tap_text(self, text: str, timeout: float = 20) -> None:
        deadline = time.time() + timeout
        while time.time() < deadline:
            bounds = self.find_bounds_by_text(text)
            if bounds:
                self.tap(*bounds)
                return
            time.sleep(0.5)
        raise TimeoutError(f"Could not find UI element with text/content-desc={text!r}")

    def has_text(self, substring: str) -> bool:
        xml = self.dump_ui()
        return substring in xml

    def tap_text_until(self, tap_target: str, verify_text: str, attempts: int = 5) -> None:
        """Taps on `tap_target` repeatedly (re-locating it fresh each time) until `verify_text`
        shows up on screen -- works around this device's occasionally-dropped synthetic taps on
        our app's Compose UI (plain adb key/tap events to the OS itself are reliable; taps
        specifically on our app's buttons are intermittently swallowed for reasons that weren't
        worth chasing further -- see the conversation this was built in)."""
        for attempt in range(1, attempts + 1):
            if self.has_text(verify_text):
                return
            self.tap_text(tap_target, timeout=10)
            for _ in range(6):
                time.sleep(0.5)
                if self.has_text(verify_text):
                    return
            print(f"[phone] Tap on {tap_target!r} attempt {attempt}/{attempts} didn't land; retrying...")
        raise TimeoutError(f"Tapping {tap_target!r} never produced {verify_text!r} after {attempts} attempts")


def ensure_unlocked(adb: Adb) -> None:
    dump = adb.run("shell", "dumpsys", "window").stdout
    if "isKeyguardShowing=true" in dump:
        raise RuntimeError(
            "The phone's screen is locked. adb cannot bypass real device lock security "
            "(PIN/pattern/fingerprint) -- please unlock the phone by hand and re-run this "
            "script. Consider enabling Developer Options > 'Stay awake while charging' so it "
            "doesn't re-lock mid-test.",
        )


def navigate_phone_to_ble_pairing_screen(adb: Adb) -> None:
    adb.run("shell", "input", "keyevent", "KEYCODE_WAKEUP")
    time.sleep(0.5)
    ensure_unlocked(adb)
    print("[phone] Launching app and navigating to Add Contact > Bluetooth...")
    adb.run("shell", "am", "force-stop", "com.p2pmessenger")
    time.sleep(1)
    adb.run("shell", "am", "start", "-n", "com.p2pmessenger/.MainActivity")
    time.sleep(3)
    adb.tap_text_until("Add contact", verify_text="SMS (far away)")
    adb.tap_text_until("Bluetooth (nearby)", verify_text="Broadcasting and scanning")
    time.sleep(1)
    print("[phone] Should now be advertising + scanning + hosting its BLE GATT server.")


class PcClient:
    """Drives the tools/pc-client subprocess over stdin/stdout."""

    def __init__(self):
        if not PC_CLIENT_BAT.exists():
            raise FileNotFoundError(
                f"{PC_CLIENT_BAT} not found -- run "
                "'gradlew :tools:pc-client:installDist' first",
            )
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
            print(f"[pc-client] {line}")
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


class FrameAssembler:
    def __init__(self):
        self.buffer = bytearray()
        self.done = asyncio.Event()
        self.frame_count = 0

    def handle(self, _characteristic, data: bytearray) -> None:
        if not data:
            print("[debug] empty notification received")
            return
        self.frame_count += 1
        kind = data[0]
        if kind == FRAME_DATA:
            print(f"[debug] frame #{self.frame_count}: DATA, {len(data) - 1} bytes, running total={len(self.buffer) + len(data) - 1}")
            self.buffer.extend(data[1:])
        elif kind == FRAME_END:
            print(f"[debug] frame #{self.frame_count}: END, final total={len(self.buffer)}")
            self.done.set()
        else:
            print(f"[debug] frame #{self.frame_count}: UNKNOWN kind={kind}, len={len(data)}")


def build_frames(payload: bytes, chunk_size: int) -> list[bytes]:
    frames = []
    for i in range(0, len(payload), chunk_size):
        frames.append(bytes([FRAME_DATA]) + payload[i : i + chunk_size])
    frames.append(bytes([FRAME_END]))
    return frames


async def exchange_pairing_payload_over_ble(our_payload_json: str) -> str:
    print("[ble] Scanning for the phone's P2P Messenger BLE service...")

    def matches(_device, adv_data) -> bool:
        uuids = [u.lower() for u in (adv_data.service_uuids or [])]
        return SERVICE_UUID.lower() in uuids

    device = await BleakScanner.find_device_by_filter(matches, timeout=25.0)
    if device is None:
        raise RuntimeError(
            "Did not see the phone advertising the P2P Messenger BLE service within 25s. "
            "Make sure the phone app is on Add Contact > Bluetooth (nearby), Bluetooth is on, "
            "and BLE permissions were granted.",
        )
    print(f"[ble] Found device: {device.address} ({device.name})")

    assembler = FrameAssembler()
    async with BleakClient(device) as client:
        mtu = getattr(client, "mtu_size", 23) or 23
        # The MTU bleak reports is Windows' own request, not necessarily what the real link
        # with an Android peripheral actually settled on -- writing at that size can fail with
        # a generic "Unreachable" error. Stay well under it regardless of what was negotiated.
        chunk_size = min(max(mtu - 3, 20), 150)
        print(f"[ble] Connected. Negotiated MTU={mtu}, using conservative chunk_size={chunk_size}")

        await client.start_notify(CHAR_UUID, assembler.handle)
        await asyncio.sleep(0.3)  # let the notification subscription settle before writing

        payload_bytes = our_payload_json.encode("utf-8")
        frames = build_frames(payload_bytes, chunk_size)
        for i, frame in enumerate(frames):
            for attempt in range(3):
                try:
                    await client.write_gatt_char(CHAR_UUID, frame, response=True)
                    break
                except Exception as e:
                    if attempt == 2:
                        raise
                    print(f"[ble] write {i + 1}/{len(frames)} failed ({e}); retrying...")
                    await asyncio.sleep(0.5)
            await asyncio.sleep(0.02)
        print(f"[ble] Sent our pairing payload ({len(payload_bytes)} bytes) in {len(frames)} frames.")

        await asyncio.wait_for(assembler.done.wait(), timeout=20.0)
        await client.stop_notify(CHAR_UUID)

    phone_payload_json = assembler.buffer.decode("utf-8")
    print(f"[ble] Received phone's pairing payload ({len(phone_payload_json)} bytes).")
    try:
        parsed = json.loads(phone_payload_json)
        import base64

        kyber_b64 = parsed.get("kyberPreKeyPublic", "")
        kyber_raw = base64.b64decode(kyber_b64)
        print(
            f"[debug] kyberPreKeyPublic base64 len={len(kyber_b64)} "
            f"decoded len={len(kyber_raw)} (expected 1569 -- libsignal prefixes a type byte)",
        )
    except Exception as e:
        print(f"[debug] Could not parse/inspect phone payload JSON: {e}")
    return phone_payload_json


async def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--device-serial", default=None, help="adb device serial (auto-detects the only one if omitted)")
    parser.add_argument("--adb-path", default=None, help="path to adb.exe (auto-detected if omitted)")
    parser.add_argument("--skip-navigate", action="store_true", help="assume the phone is already on the Bluetooth pairing screen")
    parser.add_argument("--interactive", action="store_true", help="keep the session open to type messages after the automated test")
    args = parser.parse_args()

    adb_path = args.adb_path or find_adb()
    serial = args.device_serial
    if serial is None:
        result = subprocess.run([adb_path, "devices"], capture_output=True, text=True)
        lines = [l for l in result.stdout.splitlines()[1:] if l.strip() and "device" in l]
        if len(lines) != 1:
            print("Could not auto-detect a single adb device. Pass --device-serial explicitly.")
            print(result.stdout)
            return 1
        serial = lines[0].split()[0]
    print(f"[adb] Using device serial: {serial}")
    adb = Adb(adb_path, serial)

    print("[pc-client] Starting...")
    pc = PcClient()
    try:
        await pc.wait_for("READY", timeout=15)
        pc.send("GET_BUNDLE")
        bundle_line = await pc.wait_for("BUNDLE ", timeout=10)
        our_payload_json = bundle_line[len("BUNDLE "):]

        if not args.skip_navigate:
            navigate_phone_to_ble_pairing_screen(adb)
        else:
            print("[phone] --skip-navigate set; assuming phone is already on the BLE pairing screen.")

        phone_payload_json = await exchange_pairing_payload_over_ble(our_payload_json)

        pc.send(f"PAIR {phone_payload_json}")
        paired_line = await pc.wait_for("PAIRED", timeout=15)
        print(f"[result] {paired_line}")

        await asyncio.sleep(2)
        test_message = "Hello from the PC virtual client!"
        pc.send(f"SEND {test_message}")
        await pc.wait_for("SENT", timeout=10)
        print(f"[result] Sent test message to phone: {test_message!r}")
        print(
            "[result] Check the phone's chat screen now for this message. "
            "To test phone -> PC, send a message from the phone's chat UI within the next "
            "30s and watch for a 'RECV ...' line below.",
        )

        if args.interactive:
            print("\nType a message and press Enter to send PC -> phone, or Ctrl+C to quit.")
            while True:
                line = await asyncio.get_event_loop().run_in_executor(None, sys.stdin.readline)
                if not line:
                    break
                text = line.strip()
                if not text:
                    continue
                pc.send(f"SEND {text}")
                await pc.wait_for("SENT", timeout=10)
        else:
            # Give the phone a window to send a reply (if the user does it manually) so
            # any "RECV ..." line has a chance to show up in this run's output, then exit
            # cleanly instead of blocking forever on stdin.
            await asyncio.sleep(30)
            print("[result] Done. Re-run with --interactive to keep the session open for chatting.")
    finally:
        pc.close()

    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(asyncio.run(main()))
    except KeyboardInterrupt:
        pass
