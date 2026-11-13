"""Copy only our isolated directprobe app's test state between two explicit emulators."""
import subprocess
import sys
from pathlib import Path

ADB = r"C:\ADB\adb.exe"
PACKAGE = "org.ut99android.host.directprobe"
ROOT = "/data/user/0/" + PACKAGE
if len(sys.argv) != 3 or any(not x.startswith("emulator-") for x in sys.argv[1:]) or sys.argv[1] == sys.argv[2]:
    raise SystemExit("usage: copy_direct_test_state.py emulator-SOURCE emulator-TARGET")
source, target = sys.argv[1:]
read = [ADB, "-s", source, "exec-out", "run-as", PACKAGE, "tar", "-C", ROOT, "-cf", "-", "files", "shared_prefs"]
archive = Path(__file__).resolve().parents[2] / "research/android-exec-probe/directprobe-transfer.tar"
with archive.open("wb") as out:
    subprocess.run(read, stdout=out, check=True)
remote = "/data/local/tmp/ut99-directprobe-transfer.tar"
subprocess.run([ADB, "-s", target, "push", str(archive), remote], check=True)
subprocess.run([ADB, "-s", target, "shell", "run-as", PACKAGE, "tar", "-C", ROOT, "-xf", remote], check=True)
print("Copied only the directprobe test app's private state.")
