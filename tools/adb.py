"""
Finding adb, and choosing which device to talk to.

⚠ **This exists because every one of these tools started with a hard-coded path to one
laptop's SDK.** That is the same fault that kept the icon generator out of the repo until it
was needed on a day it could not be found: a tool that only runs on the machine that wrote it
is a note, not a tool.

⚠ **Two devices is the normal case here, not the exception.** Sika is developed against a real
Pixel *and* an emulator, and the emulator is the only place a clock can be moved. So nothing
here guesses: with more than one device attached and no `--device` given, it says so and stops
rather than picking one and writing to the wrong ledger.
"""
import os
import shutil
import subprocess
import sys

_CANDIDATES = [
    os.path.join(os.environ.get("ANDROID_HOME", ""), "platform-tools", "adb.exe"),
    os.path.join(os.environ.get("ANDROID_SDK_ROOT", ""), "platform-tools", "adb.exe"),
    os.path.expanduser("~/AppData/Local/Android/Sdk/platform-tools/adb.exe"),
    os.path.expanduser("~/Library/Android/sdk/platform-tools/adb"),
    os.path.expanduser("~/Android/Sdk/platform-tools/adb"),
]

PKG = "gh.mutalib.sika"


def find_adb():
    found = shutil.which("adb")
    if found:
        return found
    for c in _CANDIDATES:
        if c and os.path.exists(c):
            return c
    sys.exit("adb not found. Put it on PATH, or set ANDROID_HOME.")


ADB = find_adb()


def devices():
    out = subprocess.run([ADB, "devices"], capture_output=True, text=True).stdout
    return [ln.split("\t")[0] for ln in out.splitlines()[1:]
            if ln.strip() and ln.endswith("device")]


def pick(serial=None):
    """The device to act on. Refuses to guess when several are attached."""
    attached = devices()
    if serial:
        if serial not in attached:
            sys.exit(f"{serial} is not attached. Attached: {attached or 'nothing'}")
        return serial
    if not attached:
        sys.exit("No device attached.")
    if len(attached) > 1:
        sys.exit("More than one device attached — name one with --device:\n  "
                 + "\n  ".join(attached))
    return attached[0]


class Device:
    """One device, and the handful of adb calls these tools actually make."""

    def __init__(self, serial=None):
        self.serial = pick(serial)

    @property
    def is_emulator(self):
        return self.serial.startswith("emulator-")

    def run(self, *args, binary=False):
        r = subprocess.run([ADB, "-s", self.serial, *args], capture_output=True)
        return r.stdout if binary else r.stdout.decode("utf-8", "replace")

    def shell(self, cmd):
        return self.run("shell", cmd)

    def broadcast(self, extras):
        """
        Sends the debug injector one `--es`/`--el` set.

        ⚠ **The whole `am` line is one argument on purpose.** Passing `--es body "..."` with
        only local quoting lets the words split on the device's shell, and the broadcast
        silently takes the second word as the package — `pkg=for` — instead of failing.
        """
        return self.shell(
            f"am broadcast -a {PKG}.DEBUG_INJECT_SMS -n {PKG}/.sms.DebugSmsReceiver {extras}")

    def pull_db(self, out_dir):
        """
        Copies the live database out, WAL included.

        ⚠ **The `-wal` file is not optional.** Room runs in write-ahead-logging mode, so most
        of a recent ledger can still be in `sika.db-wal` while `sika.db` itself is nearly
        empty — 4 KB against 400 KB, measured. Copy only the first and you will read a
        database that is missing everything recent, and conclude the app lost it.

        ⚠ **`exec-out`, never `shell`.** On Windows, `adb shell cat` translates line endings
        and corrupts the binary — 106,698 bytes arriving for a 106,496-byte file.
        """
        os.makedirs(out_dir, exist_ok=True)
        for name in ("sika.db", "sika.db-wal", "sika.db-shm"):
            data = self.run("exec-out", "run-as", PKG, "cat", f"databases/{name}", binary=True)
            if data:
                with open(os.path.join(out_dir, name), "wb") as fh:
                    fh.write(data)
        main = os.path.join(out_dir, "sika.db")
        if not os.path.exists(main) or open(main, "rb").read(15) != b"SQLite format 3":
            sys.exit("Could not read the database. Is this a debug build?")
        return main


def add_device_arg(parser):
    parser.add_argument("--device", help="adb serial, when more than one is attached")
    return parser
