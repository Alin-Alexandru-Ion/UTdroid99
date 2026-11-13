#!/usr/bin/env python3
"""Build the private debug APK from pinned inputs in a fresh, ignored workspace.

No game content is downloaded or bundled. This does not grant distribution rights.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tarfile

ROOT = Path(__file__).resolve().parents[1]
ARCHIVE = 'OldUnreal-UTPatch469e-Linux-arm64.tar.bz2'
ARCHIVE_SHA = '4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b'
SOURCES = {
    'SDL2': ('https://github.com/libsdl-org/SDL.git', '5d249570393f7a37e037abf22cd6012a4cc56a71', 'patches/sdl2/ut99-android.patch'),
    'openal-soft': ('https://github.com/kcat/openal-soft.git', 'd3875f333fb6abe2f39d82caca329414871ae53b', 'patches/audio/openal-soft.patch'),
    'libxmp-native': ('https://github.com/libxmp/libxmp.git', '828ef357943e1fbb13910e7a6fca21987c5c5827', 'patches/audio/libxmp.patch'),
    'libsndfile-native': ('https://github.com/libsndfile/libsndfile.git', '72f6af15e8f85157bd622ed45b979025828b7001', 'patches/audio/libsndfile.patch'),
    'mpg123-native': ('https://github.com/libsdl-org/mpg123.git', 'b6881556a0ee8f417ccc14ded553893b533ffe0c', None),
}

def run(args, **kwargs):
    print('+', subprocess.list2cmdline([str(a) for a in args]), flush=True)
    subprocess.run([str(a) for a in args], check=True, **kwargs)

def sha(path):
    h = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''): h.update(block)
    return h.hexdigest()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--patch-archive', type=Path, help='User-supplied official 469e Linux ARM64 archive for a local-build APK')
    parser.add_argument('--support-only', action='store_true', help='Build the engine-free import APK without any patch or game files')
    parser.add_argument('--work-dir', type=Path, default=ROOT / 'research/bootstrap', help='New, empty workspace')
    parser.add_argument('--local-sources', type=Path, help='Optional folder with local Git mirrors, for offline development')
    parser.add_argument('--no-apk', action='store_true', help='Stop after preparing the runtime bundle')
    parser.add_argument('--openal-only', action='store_true', help='Compatibility option; FMOD and Cluster are always excluded')
    args = parser.parse_args()
    if args.support_only and args.patch_archive:
        parser.error('--support-only does not accept or use a patch archive')
    if not args.support_only and args.patch_archive is None:
        parser.error('--patch-archive is required unless --support-only is selected')
    if os.name != 'nt': parser.error('This build recipe currently supports Windows only')
    sdk = Path(os.environ.get('ANDROID_HOME', ''))
    ndk = sdk / 'ndk/23.2.8568313'
    cmake = sdk / 'cmake/3.22.1/bin/cmake.exe'
    ninja = sdk / 'cmake/3.22.1/bin/ninja.exe'
    newer_cmake = shutil.which('cmake')
    if not all(p.is_file() for p in (cmake, ninja, ndk / 'build/cmake/android.toolchain.cmake')):
        parser.error('Set ANDROID_HOME to an SDK with CMake 3.22.1 and NDK 23.2.8568313')
    if not newer_cmake: parser.error('Install CMake 3.27 or newer and put it on PATH for mpg123')
    version = subprocess.check_output([newer_cmake, '--version'], text=True).splitlines()[0].split()[-1].split('.')
    if tuple(int(n) for n in version[:2]) < (3, 27): parser.error('CMake on PATH must be version 3.27 or newer for mpg123')
    if not args.support_only and (not args.patch_archive.is_file() or sha(args.patch_archive) != ARCHIVE_SHA):
        parser.error('The supplied 469e ARM64 archive is missing or has an unexpected SHA-256')
    work = args.work_dir.resolve()
    if work.exists() and any(work.iterdir()): parser.error('Work directory must be new or empty; existing files are never overwritten')
    work.mkdir(parents=True, exist_ok=True)
    if not args.support_only:
        shutil.copyfile(args.patch_archive, work / ARCHIVE)
        with tarfile.open(work / ARCHIVE, 'r:bz2') as tar:
            # Extract only regular files directly under this exact directory.
            dest = work / 'SystemARM64'; dest.mkdir()
            seen = set()
            for member in tar:
                parts = member.name.split('/')
                if len(parts) != 2 or parts[0] != 'SystemARM64' or not member.isfile(): continue
                name = parts[1]
                if not name or name in ('.', '..') or '\\' in name or ':' in name or name.lower() in seen:
                    raise ValueError('Unsafe or duplicate archive member: ' + member.name)
                seen.add(name.lower())
                if member.size > 100_000_000: raise ValueError('Unexpectedly large archive member: ' + member.name)
                with tar.extractfile(member) as src, (dest / name).open('xb') as out: shutil.copyfileobj(src, out)
    for name, (url, commit, patch) in SOURCES.items():
        source = args.local_sources / name if args.local_sources else url
        run(['git', 'clone', '--no-checkout', str(source), work / name])
        run(['git', '-C', work / name, 'checkout', '--detach', commit])
        if patch:
            run(['git', '-C', work / name, 'apply', '--check', ROOT / patch])
            run(['git', '-C', work / name, 'apply', ROOT / patch])
    env = os.environ.copy(); env['UT99_RESEARCH_DIR'] = str(work)
    if not args.support_only:
        for script in ('extract_core_probe.py', 'prepare_launcher_probe.py', 'prepare_launcher_tls_probe.py', 'prepare_render_probe.py'):
            run([sys.executable, ROOT / 'scripts' / script], env=env)
    common = ['-G', 'Ninja', '-DCMAKE_MAKE_PROGRAM=' + str(ninja), '-DCMAKE_TOOLCHAIN_FILE=' + str(ndk / 'build/cmake/android.toolchain.cmake'), '-DANDROID_ABI=arm64-v8a', '-DANDROID_PLATFORM=android-31', '-DCMAKE_BUILD_TYPE=Release', '-DPython3_EXECUTABLE=' + sys.executable, '-DPYTHON_EXECUTABLE=' + sys.executable]
    page = '-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384 -Wl,-z,common-page-size=16384'
    def build(source, dest, opts=(), targets=(), tool=cmake):
        run([tool, '-S', source, '-B', work / dest, *common, *opts])
        run([tool, '--build', work / dest, '--parallel', '8', *(['--target', *targets] if targets else [])])
    for name in ('libgcc-bridge', 'libdl-bridge', 'libm-bridge', 'libc-probe'):
        build(ROOT / 'experiments' / name, name + '-build')
    build(ROOT / 'experiments/cxx-bridge', 'cxx-bridge-build', ['-DGCC_BRIDGE=' + str(work / 'libgcc-bridge-build/libgcc_s.so.1')])
    build(ROOT / 'experiments/sdl-soname', 'sdl-soname-build', ['-DSDL_SOURCE_DIR=' + str(work / 'SDL2')])
    build(work / 'openal-soft', 'openal-android-build', [page, '-DANDROID_STL=c++_static', '-DALSOFT_EXAMPLES=OFF', '-DALSOFT_UTILS=OFF', '-DALSOFT_TESTS=OFF', '-DALSOFT_REQUIRE_OPENSL=ON'])
    build(work / 'libxmp-native', 'libxmp-android-build', [page, '-DBUILD_SHARED=ON', '-DBUILD_STATIC=OFF', '-DWITH_UNIT_TESTS=OFF'])
    build(work / 'libsndfile-native', 'libsndfile-android-322', [page, '-DBUILD_SHARED_LIBS=ON', '-DBUILD_EXAMPLES=OFF', '-DBUILD_PROGRAMS=OFF', '-DBUILD_TESTING=OFF', '-DBUILD_REGTEST=OFF', '-DENABLE_EXTERNAL_LIBS=OFF', '-DENABLE_MPEG=OFF'])
    build(work / 'mpg123-native/ports/cmake', 'mpg123-android-build', [page, '-DBUILD_SHARED_LIBS=ON', '-DBUILD_LIBOUT123=OFF'], ['libmpg123'], newer_cmake)
    if args.support_only:
        support = work / 'ut99-support-only.zip'
        run([sys.executable, ROOT / 'scripts/prepare_support_bundle.py', '--source-root', work, '--output', support])
        if not args.no_apk:
            run([ROOT / 'gradlew.bat', '--no-daemon', 'assembleDebug', '-Put99DirectRehost', '-Put99SdlSource=' + str(work / 'SDL2'), '-Put99RuntimeBundle=' + str(support)], cwd=ROOT, env=env)
        print('Prepared engine-free support bundle in', work)
        if not args.no_apk: print('Engine-free debug APK:', ROOT / 'app/build/outputs/apk/debug/app-debug.apk')
        return
    env['UT99_SDL_SHA256'] = sha(work / 'sdl-soname-build/sdl/libSDL2-2.0.so.0')
    print('Locally rebuilt SDL SHA-256:', env['UT99_SDL_SHA256'], flush=True)
    catalog = ROOT / 'app/src/debug/java/org/ut99android/host/RuntimeCatalog.java'
    previous = catalog.read_bytes() if catalog.exists() else None
    try:
        run([sys.executable, ROOT / 'scripts/prepare_shared_runtime.py'], env=env)
        if not args.no_apk:
            run([ROOT / 'gradlew.bat', '--no-daemon', 'assembleDebug', '-Put99SdlSource=' + str(work / 'SDL2'), '-Put99RuntimeBundle=' + str(work / 'ut99-android-runtime-system.zip')], cwd=ROOT, env=env)
    finally:
        if previous is None:
            if catalog.exists(): catalog.unlink()
        else: catalog.write_bytes(previous)
    print('Prepared private runtime in', work)
    if not args.no_apk: print('Debug APK:', ROOT / 'app/build/outputs/apk/debug/app-debug.apk')

if __name__ == '__main__':
    try: main()
    except (subprocess.CalledProcessError, OSError, ValueError) as exc:
        print('Build stopped:', exc, file=sys.stderr); sys.exit(1)
