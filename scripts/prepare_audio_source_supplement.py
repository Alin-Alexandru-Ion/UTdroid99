#!/usr/bin/env python3
"""Create a local candidate source supplement from the exact pinned audio/SDL Git trees.

This is a review aid, not a legal-compliance or redistribution determination.
No OldUnreal archive, engine binaries, game content, APK or user files are added.
"""
import argparse
import io
import json
from pathlib import Path
import subprocess
import tarfile
import hashlib
import zipfile

from bootstrap_windows import ROOT, SOURCES

def git(repo, *args):
    return subprocess.check_output(['git', '-C', str(repo), *args])

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, default=ROOT / 'research')
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--apk', type=Path, help='Bind the supplement to an APK built from this source workspace')
    parser.add_argument('--runtime', type=Path, help='Runtime ZIP used in the APK (for example the engine-free support bundle)')
    args = parser.parse_args()
    if args.runtime and not args.apk: parser.error('--runtime requires --apk')
    trees = []
    for name, (_, commit, patch_name) in SOURCES.items():
        repo = args.source_root / name
        if git(repo, 'rev-parse', 'HEAD').decode().strip() != commit:
            raise SystemExit(f'{name}: wrong Git revision')
        diff = git(repo, 'diff', '--binary', 'HEAD', '--')
        expected = (ROOT / patch_name).read_bytes() if patch_name else b''
        if diff != expected:
            raise SystemExit(f'{name}: tracked changes do not exactly match the recorded patch')
        changed = set(git(repo, 'diff', '--name-only', 'HEAD', '--').decode().splitlines())
        archive = git(repo, 'archive', '--format=tar', commit)
        trees.append((name, repo, changed, archive))

    manifest = {'schema': 1, 'components': {}, 'notes': [
        'The recorded inputs and binary hashes are observations, not proof of a clean build or legal clearance.',
        'The OldUnreal runtime and game content are not included in this supplement.']}
    for name, (_, commit, patch_name) in SOURCES.items():
        manifest['components'][name] = {'commit': commit, 'patch': patch_name,
            'patch_sha256': hashlib.sha256((ROOT / patch_name).read_bytes()).hexdigest() if patch_name else None}
    if args.apk:
        outputs = {'SDL2': 'sdl-soname-build/sdl/libSDL2-2.0.so.0',
                   'openal-soft': 'openal-android-build/libopenal.so.1',
                   'libxmp-native': 'libxmp-android-build/libxmp.so.4',
                   'libsndfile-native': 'libsndfile-android-322/libsndfile.so.1',
                   'mpg123-native': 'mpg123-android-build/src/libmpg123/libmpg123.so'}
        builds = {'SDL2': 'sdl-soname-build', 'openal-soft': 'openal-android-build',
                  'libxmp-native': 'libxmp-android-build', 'libsndfile-native': 'libsndfile-android-322',
                  'mpg123-native': 'mpg123-android-build'}
        manifest['apk_sha256'] = hashlib.sha256(args.apk.read_bytes()).hexdigest()
        with zipfile.ZipFile(args.apk) as apk:
            runtime = apk.read('assets/ut99-runtime.zip')
        manifest['runtime_zip_sha256'] = hashlib.sha256(runtime).hexdigest()
        local_runtime = args.runtime or args.source_root / 'ut99-android-runtime-system.zip'
        if not local_runtime.is_file() or hashlib.sha256(local_runtime.read_bytes()).hexdigest() != manifest['runtime_zip_sha256']:
            raise SystemExit('APK runtime differs from the supplied build workspace')
        with zipfile.ZipFile(io.BytesIO(runtime)) as bundled:
            for name, rel in outputs.items():
                path = args.source_root / rel
                digest = hashlib.sha256(path.read_bytes()).hexdigest()
                if hashlib.sha256(bundled.read(path.name)).hexdigest() != digest:
                    raise SystemExit(f'{name}: APK binary differs from the supplied build workspace')
                manifest['components'][name]['apk_library_sha256'] = digest
                cache_path = args.source_root / builds[name] / 'CMakeCache.txt'
                if not cache_path.is_file(): raise SystemExit(f'{name}: build configuration is missing')
                cache = {}
                for line in cache_path.read_text(encoding='utf-8').splitlines():
                    if '=' not in line or ':' not in line or line.startswith(('#', '//')): continue
                    key, value = line.split('=', 1); key = key.split(':', 1)[0]
                    if key in ('ANDROID_ABI', 'ANDROID_PLATFORM', 'ANDROID_STL', 'CMAKE_BUILD_TYPE', 'CMAKE_SHARED_LINKER_FLAGS',
                               'ALSOFT_BACKEND_OPENSL', 'ALSOFT_REQUIRE_OPENSL', 'ALSOFT_EXAMPLES', 'ALSOFT_UTILS',
                               'ENABLE_EXTERNAL_LIBS', 'ENABLE_MPEG', 'BUILD_TESTING', 'BUILD_PROGRAMS', 'BUILD_REGTEST',
                               'BUILD_SHARED_LIBS', 'BUILD_SHARED', 'BUILD_STATIC', 'BUILD_LITE', 'WITH_UNIT_TESTS',
                               'BUILD_LIBOUT123', 'SDL_HIDAPI', 'SDL_AUDIO', 'SDL_VIDEO'):
                        cache[key] = value
                    elif key == 'CMAKE_TOOLCHAIN_FILE':
                        # Store the NDK version, not a builder's private absolute path.
                        normalized = value.replace('\\', '/')
                        parts = normalized.split('/')
                        if 'ndk' in parts and parts.index('ndk') + 1 < len(parts):
                            cache['ANDROID_NDK_VERSION'] = parts[parts.index('ndk') + 1]
                manifest['components'][name]['build_configuration'] = cache

    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists(): raise SystemExit('Output already exists; choose a new path')
    readme = ("Candidate source supplement for UTdroid99's pinned SDL and audio components.\n"
              "Each component tree contains the recorded source modifications. The patches,\n"
              "build script, and third-party notices are included separately for review.\n"
              "This is not a complete release compliance determination. Build instructions\n"
              "and the remaining Android host code are in the UTdroid99 source project.\n"
              "Game-format files and executable/binary test fixtures are omitted for review;\n"
              "see OMITTED_FILES.txt and assess whether they are required for completeness.\n"
              "No OldUnreal engine or game data is included.\n").encode()
    omitted = []
    with tarfile.open(output, 'x:gz') as out:
        info = tarfile.TarInfo('README.txt'); info.size = len(readme); out.addfile(info, io.BytesIO(readme))
        for name, repo, changed, archive in trees:
            with tarfile.open(fileobj=io.BytesIO(archive), mode='r:') as source:
                for member in source:
                    original_name = member.name
                    lower = original_name.lower()
                    if member.isfile() and (lower.endswith(('.exe', '.dll', '.so', '.apk', '.unr', '.utx', '.uax', '.umx', '.u', '.int')) or '.umx.' in lower):
                        omitted.append(f'components/{name}/{original_name}')
                        continue
                    member.name = f'components/{name}/{original_name}'
                    if original_name in changed and member.isfile():
                        data = (repo / original_name).read_bytes()
                        member.size = len(data); out.addfile(member, io.BytesIO(data))
                    else:
                        out.addfile(member, source.extractfile(member) if member.isfile() else None)
        for rel in ['scripts/bootstrap_windows.py', 'scripts/prepare_shared_runtime.py', 'scripts/prepare_support_bundle.py', 'scripts/rebuild_audio_windows.py', 'scripts/prepare_audio_source_supplement.py', 'docs/BUILDING.md', 'docs/USER_SUPPLIED_PATCH_OPTIONS.md', 'docs/AUDIO_SOURCE_AND_REBUILD.md', 'docs/COMPONENT_LICENSE_MATRIX.md', 'docs/RELEASE_PREPARATION.md', 'docs/LGPL_REVIEW.md', 'docs/RIGHTS_REQUEST_DRAFT.md',
                    'THIRD_PARTY_NOTICES.md', 'LICENSE', *[p.relative_to(ROOT).as_posix() for p in (ROOT / 'patches/audio').glob('*.patch')],
                    'patches/sdl2/ut99-android.patch', 'patches/sdl2/LICENSE.txt',
                    *[p.relative_to(ROOT).as_posix() for p in (ROOT / 'app/src/debug/assets/licenses').glob('*.txt')],
                    *[p.relative_to(ROOT).as_posix() for p in (ROOT / 'app/src/enginefree/assets/licenses').glob('*.txt')]]:
            out.add(ROOT / rel, arcname='utdroid99/' + rel, recursive=False)
        data = ('\n'.join(omitted) + '\n').encode()
        info = tarfile.TarInfo('OMITTED_FILES.txt'); info.size = len(data); out.addfile(info, io.BytesIO(data))
        data = (json.dumps(manifest, indent=2, sort_keys=True) + '\n').encode()
        info = tarfile.TarInfo('BUILD_EVIDENCE.json'); info.size = len(data); out.addfile(info, io.BytesIO(data))
    print('Candidate source supplement:', output)
    print('Verify source completeness, notices, relinking and permission before distribution.')

if __name__ == '__main__': main()
