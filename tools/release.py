"""
Build a signed release APK and publish it as a GitHub Release.

    python tools/release.py v1.1.0
    python tools/release.py v1.1.0 --notes "What changed"
    python tools/release.py v1.1.0 --dry-run

⚠ **The check that matters is the signer check, not the build.** A release build with no
`keystore.properties` still succeeds — it quietly falls back to the debug key, because a clone
on another machine has to be able to prove the code compiles. A debug-signed APK handed to a
tester is a trap that springs months later: Android refuses an update signed by a different
key, so moving to the real key would force every tester to uninstall, and uninstalling Sika
destroys the ledger. This script refuses to publish anything the release key did not sign.

⚠ **It also refuses on a dirty tree.** A release built from uncommitted work cannot be rebuilt
from the tag it is attached to, which makes the tag a lie the first time someone tries.
"""
import argparse
import os
import re
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
APK = os.path.join(ROOT, "app", "build", "outputs", "apk", "release", "app-release.apk")
JBR = r"C:\Program Files\Android\Android Studio\jbr"

# The certificate the release key produces. Anything else — most likely "CN=Android Debug" —
# means the build fell back and must not be shipped.
EXPECTED_DN = "CN=Sika"
DEBUG_DN = "CN=Android Debug"


def run(cmd, **kw):
    env = dict(os.environ, JAVA_HOME=JBR)
    return subprocess.run(cmd, cwd=ROOT, env=env, capture_output=True, text=True, **kw)


def die(msg):
    sys.exit(f"\n  STOP: {msg}\n")


def apksigner():
    base = os.path.expandvars(r"%LOCALAPPDATA%\Android\Sdk\build-tools")
    versions = sorted(os.listdir(base)) if os.path.isdir(base) else []
    for v in reversed(versions):
        p = os.path.join(base, v, "apksigner.bat")
        if os.path.exists(p):
            return p
    die("apksigner not found. Is the Android SDK installed?")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("tag", help="the git tag to publish, e.g. v1.1.0")
    ap.add_argument("--notes", default=None, help="release notes; defaults to the tag message")
    ap.add_argument("--dry-run", action="store_true", help="build and verify, publish nothing")
    args = ap.parse_args()

    if not os.path.exists(os.path.join(ROOT, "keystore.properties")):
        die("keystore.properties is missing, so this would build a DEBUG-signed APK.\n"
            "  It is gitignored on purpose — restore it from your backup.")

    dirty = run(["git", "status", "--porcelain"]).stdout.strip()
    if dirty:
        die("the working tree is dirty. A release must be rebuildable from its tag:\n  "
            + dirty.replace("\n", "\n  "))

    if run(["git", "rev-parse", args.tag]).returncode != 0:
        die(f"tag {args.tag} does not exist. Create it first with `git tag -a {args.tag}`.")

    head = run(["git", "rev-parse", "HEAD"]).stdout.strip()
    tagged = run(["git", "rev-list", "-n1", args.tag]).stdout.strip()
    if head != tagged:
        die(f"HEAD is not {args.tag}. Check out the tag, or move it, before releasing.")

    print(f"  building a signed release for {args.tag} …")
    build = run([os.path.join(ROOT, "gradlew.bat"), "assembleRelease", "-q"])
    if build.returncode != 0:
        die("the release build failed:\n" + (build.stderr or build.stdout)[-2000:])
    if not os.path.exists(APK):
        die(f"no APK at {APK}")

    out = run([apksigner(), "verify", "--print-certs", APK]).stdout
    dn = next((l for l in out.splitlines() if "certificate DN" in l), "")
    sha = next((l for l in out.splitlines() if "SHA-256 digest" in l), "")
    if DEBUG_DN in dn or EXPECTED_DN not in dn:
        die(f"this APK is not signed by the release key:\n  {dn.strip() or out[:300]}")
    print(f"  signed by: {dn.split(':', 1)[1].strip()}")
    print(f"  {sha.strip()}")
    print(f"  size: {os.path.getsize(APK) / 1048576:.1f} MB")

    fingerprint = sha.split(":", 1)[1].strip() if ":" in sha else "unknown"
    notes = args.notes or run(["git", "tag", "-l", "--format=%(contents)", args.tag]).stdout.strip()
    notes = (notes + "\n\n---\n\n"
             "**Installing.** Download the APK below and open it. Android will ask you to allow "
             "installs from this source, and Play Protect will warn you that the app is not from "
             "the Play Store — that is expected for a sideloaded app.\n\n"
             "Sika reads your MTN MoMo text messages to build the ledger. It has no `INTERNET` "
             "permission, so nothing it reads can leave the phone.\n\n"
             f"Signing certificate SHA-256: `{fingerprint}`\n\n"
             "⚠ Every release is signed with the same key. If Android ever refuses to install an "
             "update, the APK is not from this repo — do not force it.")

    if args.dry_run:
        print("\n  --dry-run: nothing published. Notes would have been:\n")
        print("  " + notes.replace("\n", "\n  "))
        return

    existing = run(["gh", "release", "view", args.tag]).returncode == 0
    if existing:
        print(f"  {args.tag} already exists — replacing its APK")
        run(["gh", "release", "delete-asset", args.tag, "app-release.apk", "-y"])
        r = run(["gh", "release", "upload", args.tag, APK])
    else:
        r = run(["gh", "release", "create", args.tag, APK,
                 "--title", f"Sika {args.tag}", "--notes", notes])
    if r.returncode != 0:
        die("gh failed:\n" + (r.stderr or r.stdout))
    url = run(["gh", "release", "view", args.tag, "--json", "url", "-q", ".url"]).stdout.strip()
    print(f"\n  published: {url}\n")


if __name__ == "__main__":
    main()
