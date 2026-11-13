"""Create the local-only runtime bundle and its pinned debug-app catalog.

Inputs are the user's previously prepared engine and support binaries. The debug APK includes the
resulting engine runtime, but does not include the user's game-content files.
"""
import hashlib
import argparse
import os
from pathlib import Path
import struct
import tarfile
import zipfile

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--openal-only', action='store_true', help='Compatibility option; FMOD and Cluster are always excluded')
parser.add_argument('--output', type=Path, help='Override the runtime bundle destination')
args = parser.parse_args()
research = Path(os.environ.get('UT99_RESEARCH_DIR', root / 'research'))
overrides = {
    'Core.so': 'Core.so',
    'Engine.so': 'SystemARM64/Engine.so',
    'Render.so': 'launcher-probe/Render.so',
    'IpDrv.so': 'launcher-probe/IpDrv.so',
    'ut-launch.so': 'launcher-probe/ut-launch-tls.so',
    'libSDL2-2.0.so.0': 'sdl-soname-build/sdl/libSDL2-2.0.so.0',
    'libc.so.6': 'libc-probe-build/load-only/libc.so.6',
    'ld-linux-aarch64.so.1': 'libc-probe-build/load-only/ld-linux-aarch64.so.1',
    'libpthread.so.0': 'libc-probe-build/load-only/libpthread.so.0',
    'libutlibcprobe.so': 'libc-probe-build/libutlibcprobe.so',
    'libdl.so.2': 'libdl-bridge-build/libdl.so.2',
    'libstdc++.so.6': 'cxx-bridge-build/libstdc++.so.6',
    'libm.so.6': 'libm-bridge-build/libm.so.6',
    'libgcc_s.so.1': 'libgcc-bridge-build/libgcc_s.so.1',
    'libopenal.so.1': 'openal-android-build/libopenal.so.1',
    'libxmp.so.4': 'libxmp-android-build/libxmp.so.4',
    'libsndfile.so.1': 'libsndfile-android-322/libsndfile.so.1',
    'libmpg123.so': 'mpg123-android-build/src/libmpg123/libmpg123.so',
    'UT99Android-Default.ini': 'SystemARM64/Default.ini',
    'UT99Android-DefUser.ini': 'SystemARM64/DefUser.ini',
}
patch = research / 'OldUnreal-UTPatch469e-Linux-arm64.tar.bz2'
with patch.open('rb') as stream:
    if hashlib.file_digest(stream, 'sha256').hexdigest() != '4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b':
        raise SystemExit('Patch archive hash mismatch')
files = {}
with tarfile.open(patch, 'r:bz2') as source:
    for member in source:
        parts = Path(member.name).parts
        if len(parts) == 2 and parts[0] == 'SystemARM64' and member.isfile():
            name = parts[1]
            if name.lower() == 'cluster.so' or 'fmod' in name.lower():
                continue
            if name.endswith('.so') or '.so.' in name:
                stream = source.extractfile(member)
                files[name] = stream.read()
for name, path in overrides.items():
    files[name] = (research / path).read_bytes()

# Android's loader does not support STB_GNU_UNIQUE. Make the one pinned
# ALAudio dynamic symbol global in a private runtime copy, as for Render.
audio = bytearray(files['ALAudio.so'])
if hashlib.sha256(audio).hexdigest() != 'c8285102dac000c10168d21572d39b8a9a025c325a0d4339abcb8dc92bedf3be':
    raise SystemExit('Unexpected original ALAudio.so hash')
section_offset = struct.unpack_from('<Q', audio, 40)[0]
section_size, section_count = struct.unpack_from('<HH', audio, 58)
changed = 0
for i in range(section_count):
    section = section_offset + i * section_size
    if struct.unpack_from('<I', audio, section + 4)[0] != 11: continue
    start, length = struct.unpack_from('<QQ', audio, section + 24)
    stride = struct.unpack_from('<Q', audio, section + 56)[0]
    if stride != 24: raise SystemExit('Unexpected ALAudio symbol size')
    for entry in range(start, start + length, stride):
        info = audio[entry + 4]
        if info >> 4 == 10:
            audio[entry + 4] = (1 << 4) | (info & 15)
            changed += 1
if changed != 1: raise SystemExit('Unexpected ALAudio unique-symbol count')
files['ALAudio.so'] = bytes(audio)

required_hashes = {
    'Core.so': 'f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d',
    'Engine.so': '7c08c8b166684f7a10b9b88e6bfe0aa965e982be5b9508bcf32061b1730f4853',
    'ut-launch.so': 'fafd3829192f7f829c9b40bf2d7ece98439dd5de2e5a4d27e8eab67e815af0a4',
    # Locally rebuilt SDL binaries may differ by toolchain/build path. The
    # bootstrap verifies its source revision and patch and supplies its hash.
    'libSDL2-2.0.so.0': os.environ.get('UT99_SDL_SHA256', 'ec3e2d484593d1829a2d907c97fa8334036f0396b5cf2a8a195ef11a34618588'),
}
for name, expected in required_hashes.items():
    if hashlib.sha256(files[name]).hexdigest() != expected:
        raise SystemExit(f'Unexpected hash for {name}')

catalog = Path(os.environ.get('UT99_RUNTIME_CATALOG', root / 'app/src/debug/java/org/ut99android/host/RuntimeCatalog.java'))
catalog.parent.mkdir(parents=True, exist_ok=True)
rows = []
for name, data in sorted(files.items()):
    target = {'UT99Android-Default.ini': 'Default.ini', 'UT99Android-DefUser.ini': 'DefUser.ini'}.get(name, name)
    rows.append(f'        {{"{name}", "{target}", "{hashlib.sha256(data).hexdigest()}"}},')
catalog.write_text('''package org.ut99android.host;

/** Generated by scripts/prepare_shared_runtime.py from local research inputs. */
final class RuntimeCatalog {
    private RuntimeCatalog() {}
    static final String[][] FILES = {
''' + '\n'.join(rows) + '''
    };
}
''', encoding='utf-8', newline='\n')
destination = args.output or research / 'ut99-android-runtime-system.zip'
destination.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(destination, 'w', compression=zipfile.ZIP_DEFLATED) as archive:
    for name, data in sorted(files.items()):
        archive.writestr(name, data)
print(f'Local runtime bundle: {destination} ({len(files)} files)')
print(f'Pinned catalog: {catalog}')
