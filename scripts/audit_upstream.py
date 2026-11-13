#!/usr/bin/env python3
"""Read ELF members of a tar archive without extracting paths or running them."""
import argparse
import hashlib
import json
import re
import subprocess
import tarfile
import tempfile
from pathlib import Path


def digest(path):
    h = hashlib.sha256()
    with open(path, 'rb') as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(chunk)
    return h.hexdigest()


def main():
    p = argparse.ArgumentParser()
    p.add_argument('archive', type=Path)
    p.add_argument('--readelf', required=True, help='path to llvm-readelf or readelf')
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    version = subprocess.run([args.readelf, '--version'], text=True, capture_output=True, check=True).stdout.splitlines()[0]
    report = {'archive': args.archive.name, 'sha256': digest(args.archive), 'readelf': version,
              'files': [], 'skipped': [], 'dependency_resolution': {}, 'limitations': [
                  'Static DT_NEEDED basename candidates only; runtime dlopen, loader search rules and ABI compatibility are not proven.',
                  'Only regular archive members up to 256 MiB are inspected; archive symlinks are not followed.',
                  'Export candidates are dynamic defined symbols; their semantics are not established.']}
    with tarfile.open(args.archive, 'r:bz2') as tar, tempfile.TemporaryDirectory() as tmp:
        for member in tar:
            if not member.isfile():
                continue
            if member.size > 256 * 1024 * 1024:
                report['skipped'].append({'path': member.name, 'reason': 'over 256 MiB limit'})
                continue
            f = tar.extractfile(member)
            if f is None:
                report['skipped'].append({'path': member.name, 'reason': 'not readable'})
                continue
            if f.read(4) != b'\x7fELF':
                continue
            f.seek(0)
            data = f.read()
            temp = Path(tmp) / 'member.elf'
            temp.write_bytes(data)
            cmd = [args.readelf, '--file-header', '--program-headers', '--dynamic', '--version-info', '--relocations', '--dyn-syms', '--wide', str(temp)]
            result = subprocess.run(cmd, capture_output=True, text=True, encoding='utf-8', errors='replace')
            raw = result.stdout
            needed = re.findall(r'Shared library: \[(.*?)\]', raw)
            exports = []
            for line in raw.splitlines():
                if re.search(r'\b(?:GLOBAL|WEAK)\b', line) and not re.search(r'\bUND\b', line):
                    fields = line.split()
                    if len(fields) >= 8 and re.match(r'^\d+:$', fields[0]): exports.append(fields[7])
            report['files'].append({'path': member.name, 'size': len(data), 'sha256': hashlib.sha256(data).hexdigest(),
                'readelf_exit': result.returncode, 'readelf_stderr': result.stderr, 'needed': needed,
                'interpreter': re.findall(r'Requesting program interpreter: (.*?)\]', raw),
                'search_paths': re.findall(r'(?:Library rpath|Library runpath): \[(.*?)\]', raw),
                'symbol_versions': sorted(set(re.findall(r'\b(?:GLIBC|GLIBCXX|CXXABI)_[0-9.]+', raw))),
                'relocation_types': sorted(set(re.findall(r'\bR_[A-Z0-9_]+\b', raw))),
                'has_tls_segment': bool(re.search(r'^\s*TLS\s', raw, re.M)),
                'exports': sorted(set(exports)), 'readelf_output': raw})
    names = {}
    for item in report['files']:
        names.setdefault(Path(item['path']).name, []).append(item['path'])
    for item in report['files']:
        report['dependency_resolution'][item['path']] = {name: names.get(name, []) for name in item['needed']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"ELF files: {len(report['files'])}; skipped: {len(report['skipped'])}; readelf errors: {sum(x['readelf_exit'] != 0 for x in report['files'])}")
    print('Report:', args.output)


if __name__ == '__main__': main()
