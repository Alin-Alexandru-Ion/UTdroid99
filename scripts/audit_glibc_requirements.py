#!/usr/bin/env python3
"""Inventory GNU-versioned imports against selected NDK Bionic stubs, by module."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

def symbols(text):
    for line in text.splitlines():
        f = line.split()
        if len(f) >= 8 and re.fullmatch(r'\d+:', f[0]):
            yield f

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--audit', type=Path, required=True)
    p.add_argument('--readelf', required=True)
    p.add_argument('--stub-dir', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    audit = json.loads(args.audit.read_text(encoding='utf-8'))
    providers = {}
    for library in ('libc.so', 'libm.so', 'libdl.so'):
        path = args.stub_dir / library
        dump = subprocess.run([args.readelf, '--dyn-syms', '-W', str(path)], check=True, capture_output=True, text=True).stdout
        for f in symbols(dump):
            if f[6] != 'UND' and f[4] in ('GLOBAL', 'WEAK') and f[5] in ('DEFAULT', 'PROTECTED'):
                name = f[7].split('@')[0]
                providers.setdefault(name, []).append({'stub': library, 'type': f[3], 'symbol': f[7]})
    entries = {}
    for module in audit['files']:
        if module['readelf_exit']:
            raise SystemExit('Readelf failure in ' + module['path'])
        for f in symbols(module['readelf_output']):
            if f[6] != 'UND' or '@GLIBC_' not in f[7]:
                continue
            name = f[7].split('@')[0]
            key = (f[7], f[3])
            entry = entries.setdefault(key, {'import': f[7], 'type': f[3], 'modules': set(), 'ndk_candidates': providers.get(name, [])})
            entry['modules'].add(module['path'])
    rows = []
    for entry in entries.values():
        entry['modules'] = sorted(entry['modules'])
        rows.append(entry)
    rows.sort(key=lambda x: (x['import'], x['type']))
    report = {'source_archive_sha256': audit['sha256'], 'modules_audited': len(audit['files']),
              'stub_dir': str(args.stub_dir), 'stub_sha256': {n: hashlib.sha256((args.stub_dir / n).read_bytes()).hexdigest() for n in ('libc.so', 'libm.so', 'libdl.so')},
              'imports': rows, 'limitations': ['Name/type inventory only; a matching symbol is not an ABI or behavior match.',
              'Stubs do not prove runtime behavior, library lookup order or GLIBC symbol version availability.',
              'Dynamic lookups, opaque structure layouts and imports outside GLIBC_* are not covered.']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"Modules: {len(audit['files'])}; distinct imports: {len(rows)}; without stub name: {sum(not row['ndk_candidates'] for row in rows)}")

if __name__ == '__main__': main()
