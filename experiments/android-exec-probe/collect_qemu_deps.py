"""Copy Termux test-only host dependencies from the isolated emulator."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[2] / 'research/android-exec-probe'
dest = root / 'qemu-deps'
dest.mkdir(exist_ok=True)
readelf = str(Path(os.environ['ANDROID_HOME']) / 'ndk/23.2.8568313/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-readelf.exe')
system = {'libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libandroid.so', 'libz.so', 'libEGL.so', 'libGLESv2.so'}
todo = [root / 'qemu-aarch64']
seen = set(system)
while todo:
    item = todo.pop()
    text = subprocess.check_output([readelf, '-d', str(item)], text=True)
    for name in re.findall(r'Shared library: \[(.*?)\]', text):
        if name in seen:
            continue
        seen.add(name)
        path = dest / name
        source = '/data/data/com.termux/files/usr/lib/' + name
        raw = subprocess.check_output(['adb','-s','emulator-5562','exec-out','run-as','com.termux','cat',source])
        path.write_bytes(raw)
        todo.append(path)
        print(name, len(raw))
print('Collected', len(seen - system), 'non-system dependencies')
