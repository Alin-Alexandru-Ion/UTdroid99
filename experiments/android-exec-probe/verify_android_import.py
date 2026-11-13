import hashlib
import json
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2]
expected = json.loads((root / 'research/linux-runtime-test/source-hashes.json').read_text())
adb = ['adb', '-s', 'emulator-5562', 'exec-out', 'run-as', 'org.ut99android.host.linuxprobe']
files = subprocess.check_output(adb + ['find', 'files/patch-imports', '-type', 'f']).decode().splitlines()
files = [x for x in files if '/prepared/runtime/' in x]
bad = []
for name in files:
    data = subprocess.check_output(adb + ['cat', name])
    source = 'SystemARM64/' + name.split('/prepared/runtime/', 1)[1]
    if hashlib.sha256(data).hexdigest() != expected.get(source):
        bad.append(source)
if len(files) != 22 or bad:
    raise SystemExit(f'Android imported {len(files)} ARM64 files, mismatches: {bad}')
if not any(name.endswith('/runtime/ut-bin-arm64') for name in files):
    raise SystemExit('Original launcher missing')
print('Android imported 22 original ARM64 files with matching SHA-256 hashes')
