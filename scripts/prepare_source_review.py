#!/usr/bin/env python3
"""Make a local, review-only source snapshot; this does not publish or clear rights."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
import sys
import zipfile

from audit_source_tree import ROOT, files

DIRECTORIES = {'app', 'native', 'experiments', 'scripts', 'tests', 'patches', 'docs', 'gradle'}
TOP_FILES = {'.gitignore', 'LICENSE', 'MODLOG.md', 'README.md', 'THIRD_PARTY_NOTICES.md',
             'build.gradle', 'settings.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat'}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--game-dir', type=Path, help='Compare candidates to the given local game files')
    args = parser.parse_args()
    command = [sys.executable, str(ROOT / 'scripts/audit_source_tree.py')]
    if args.game_dir: command.extend(['--game-dir', str(args.game_dir)])
    subprocess.run(command, check=True)
    items = list(files())
    unknown = [str(rel) for rel, _, _ in items if rel.parts[0] not in DIRECTORIES and rel.as_posix() not in TOP_FILES]
    if unknown: raise SystemExit('Unreviewed top-level paths: ' + ', '.join(unknown))
    if any(kind != 'file' for _, kind, _ in items): raise SystemExit('Source symlink requires review')
    output = args.output.resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists(): raise SystemExit('Output already exists; choose a new path')
    manifest = {'status': 'review only; not legal clearance', 'files': {}}
    with zipfile.ZipFile(output, mode='x', compression=zipfile.ZIP_DEFLATED) as archive:
        for rel, _, data in sorted(items, key=lambda x: x[0].as_posix()):
            name = rel.as_posix()
            archive.writestr(name, data)
            manifest['files'][name] = hashlib.sha256(data).hexdigest()
        archive.writestr('SOURCE_REVIEW_MANIFEST.json', json.dumps(manifest, indent=2, sort_keys=True) + '\n')
    print('Local source review snapshot:', output)
    print('Review provenance, rights, notices, secrets and any future Git history before publication.')

if __name__ == '__main__':
    try: main()
    except (OSError, subprocess.CalledProcessError) as exc:
        print('Source snapshot stopped:', exc, file=sys.stderr); sys.exit(1)
