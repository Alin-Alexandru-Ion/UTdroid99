#!/usr/bin/env python3
"""Rebuild locally edited audio libraries and create a private test APK.

Run after bootstrap_windows.py in the same work directory. This does not grant
redistribution rights or establish LGPL compliance for any distributed APK.
"""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys

from bootstrap_windows import ROOT, sha

COMPONENTS = {
    'openal': ('openal-android-build', 'OpenAL', False),
    'sndfile': ('libsndfile-android-322', 'sndfile', False),
    'xmp': ('libxmp-android-build', 'xmp_shared', False),
    'mpg123': ('mpg123-android-build', 'libmpg123', True),
}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--work-dir', type=Path, default=ROOT / 'research/bootstrap')
    parser.add_argument('--component', choices=COMPONENTS, action='append', required=True,
                        help='Library to rebuild; repeat to select several')
    parser.add_argument('--engine-free', action='store_true', help='Rebuild the engine-free import APK')
    parser.add_argument('--support-output', type=Path, help='New ZIP path for the engine-free support bundle')
    args = parser.parse_args()
    if args.support_output and not args.engine_free: parser.error('--support-output requires --engine-free')
    if os.name != 'nt': parser.error('This recipe currently supports Windows only')
    work = args.work_dir.resolve()
    support = args.support_output or work / 'ut99-support-modified.zip'
    if args.engine_free and support.exists(): parser.error('Support output exists; choose a new --support-output path')
    sdk = Path(os.environ.get('ANDROID_HOME', ''))
    sdk_cmake = sdk / 'cmake/3.22.1/bin/cmake.exe'
    if not sdk_cmake.is_file(): parser.error('Set ANDROID_HOME to an SDK with CMake 3.22.1')
    newer_cmake = shutil.which('cmake')
    for name in dict.fromkeys(args.component):
        build_dir, target, use_newer = COMPONENTS[name]
        if not (work / build_dir / 'CMakeCache.txt').is_file():
            parser.error(f'No configured {name} build in {work}; run bootstrap first')
        tool = newer_cmake if use_newer else str(sdk_cmake)
        if not tool: parser.error('CMake 3.27 or newer must be on PATH for mpg123')
        subprocess.run([tool, '--build', str(work / build_dir), '--parallel', '8', '--target', target], check=True)
    env = os.environ.copy()
    env['UT99_RESEARCH_DIR'] = str(work)
    env['UT99_SDL_SHA256'] = sha(work / 'sdl-soname-build/sdl/libSDL2-2.0.so.0')
    if args.engine_free:
        subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_support_bundle.py'), '--source-root', str(work), '--output', str(support)], check=True)
        subprocess.run([str(ROOT / 'gradlew.bat'), '--no-daemon', 'assembleDebug', '-Put99DirectRehost',
                        '-Put99SdlSource=' + str(work / 'SDL2'), '-Put99RuntimeBundle=' + str(support.resolve())],
                       check=True, cwd=ROOT, env=env)
        print('Engine-free private test APK:', ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
        print('Matching support bundle:', support)
        return
    catalog = ROOT / 'app/src/debug/java/org/ut99android/host/RuntimeCatalog.java'
    old = catalog.read_bytes() if catalog.exists() else None
    try:
        subprocess.run([sys.executable, str(ROOT / 'scripts/prepare_shared_runtime.py')], check=True, env=env)
        subprocess.run([str(ROOT / 'gradlew.bat'), '--no-daemon', 'assembleDebug',
                        '-Put99SdlSource=' + str(work / 'SDL2'),
                        '-Put99RuntimeBundle=' + str(work / 'ut99-android-runtime-system.zip')], check=True, cwd=ROOT, env=env)
    finally:
        if old is None:
            if catalog.exists(): catalog.unlink()
        else: catalog.write_bytes(old)
    print('Private test APK:', ROOT / 'app/build/outputs/apk/debug/app-debug.apk')

if __name__ == '__main__':
    try: main()
    except (OSError, subprocess.CalledProcessError, ValueError) as exc:
        print('Rebuild stopped:', exc, file=sys.stderr); sys.exit(1)
