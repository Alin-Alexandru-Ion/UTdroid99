#!/usr/bin/env python3
"""Conservative pre-publication screen; not a substitute for rights review."""
from pathlib import Path
import argparse
import hashlib
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {'research', 'build', '.gradle', '.cxx', '.git', '.idea', '__pycache__'}
GAME_SUFFIXES = {'.unr', '.utx', '.uax', '.umx', '.u', '.int'}
ARTIFACT_SUFFIXES = {'.apk', '.aab', '.so', '.dll', '.dylib', '.exe', '.pdb', '.zip', '.tar', '.bz2', '.tgz', '.gz', '.xz', '.7z', '.rar', '.log', '.pyc'}
PRIVATE_SUFFIXES = {'.keystore', '.jks', '.p12', '.pfx', '.key', '.pem'}
SECRET_PATTERNS = [re.compile(rb'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----'), re.compile(rb'\bAKIA[A-Z0-9]{16}\b'), re.compile(rb'\bghp_[A-Za-z0-9]{36}\b'), re.compile(rb'\bgithub_pat_[A-Za-z0-9_]{40,}\b')]
GRADLE_WRAPPER_SHA256 = '7d3a4ac4de1c32b59bc6a4eb8ecb8e612ccd0cf1ae1e99f66902da64df296172'

def files():
    for path in ROOT.rglob('*'):
        rel = path.relative_to(ROOT)
        if any(p in SKIP_DIRS for p in rel.parts): continue
        if path.is_symlink():
            yield rel, 'symlink', b''
        elif path.is_file():
            if path.name == 'local.properties' or path.suffix == '.iml': continue
            yield rel, 'file', path.read_bytes()

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--game-dir', type=Path, help='Optional local game folder for exact-byte comparison')
    args = parser.parse_args()
    candidates = list(files())
    game_hashes = set()
    if args.game_dir:
        if not args.game_dir.is_dir(): parser.error('Game directory does not exist')
        sizes = {len(data) for _, kind, data in candidates if kind == 'file'}
        for path in args.game_dir.rglob('*'):
            if path.is_file() and not path.is_symlink() and path.stat().st_size in sizes:
                game_hashes.add(hashlib.sha256(path.read_bytes()).digest())
    errors = []
    count = 0
    for rel, kind, data in candidates:
        count += 1
        suffix = rel.suffix.lower()
        if kind == 'symlink': errors.append((rel, 'symlink requires review'))
        elif suffix in PRIVATE_SUFFIXES or rel.name == '.env' or rel.name.startswith('.env.'):
            errors.append((rel, 'possible private key or environment file'))
        elif suffix in GAME_SUFFIXES: errors.append((rel, 'possible game data'))
        elif suffix in ARTIFACT_SUFFIXES or data.startswith((b'\x7fELF', b'MZ', b'BZh', b'\x1f\x8b', b'7z\xbc\xaf\x27\x1c')):
            errors.append((rel, 'binary or generated artifact'))
        elif rel.as_posix() != 'gradle/wrapper/gradle-wrapper.jar' and data.startswith(b'PK\x03\x04'):
            errors.append((rel, 'archive requires review'))
        if any(pattern.search(data) for pattern in SECRET_PATTERNS): errors.append((rel, 'possible credential'))
        if rel.as_posix() == 'gradle/wrapper/gradle-wrapper.jar' and hashlib.sha256(data).hexdigest() != GRADLE_WRAPPER_SHA256:
            errors.append((rel, 'does not match the published Gradle 8.14.3 wrapper checksum'))
        if kind == 'file' and hashlib.sha256(data).digest() in game_hashes:
            errors.append((rel, 'identical to a file in the selected game folder'))
    for rel, issue in errors: print(f'FAIL {rel.as_posix()}: {issue}')
    print(f'Scanned {count} candidate files; {len(errors)} findings.')
    if args.game_dir: print('Compared matching sizes against the selected game folder.')
    print('Manual provenance, rights, full secret scan, dependency review and Git history review remain required.')
    return bool(errors)

if __name__ == '__main__': sys.exit(main())
