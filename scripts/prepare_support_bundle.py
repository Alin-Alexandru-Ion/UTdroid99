#!/usr/bin/env python3
"""Package only locally built Android support libraries, never engine inputs."""
import argparse
from pathlib import Path
import zipfile

FILES = {
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
}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--source-root', type=Path, required=True, help='Existing local dependency build workspace')
    parser.add_argument('--output', type=Path, required=True, help='New support ZIP path')
    args = parser.parse_args()
    if args.output.exists(): parser.error('Output exists; choose a new path')
    source = args.source_root.resolve()
    entries = []
    for name, relative in FILES.items():
        path = (source / relative).resolve()
        if not path.is_relative_to(source) or not path.is_file():
            parser.error(f'Missing support library: {relative}')
        data = path.read_bytes()
        if not data.startswith(b'\x7fELF'):
            parser.error(f'Not an ELF library: {relative}')
        entries.append((name, data))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(args.output, 'x', compression=zipfile.ZIP_DEFLATED) as archive:
        for name, data in sorted(entries): archive.writestr(name, data)
    print(f'Created support-only ZIP with {len(entries)} libraries: {args.output}')


if __name__ == '__main__': main()
