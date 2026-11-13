#!/usr/bin/env python3
"""Compare versioned Core imports to experiment exports; never load any ELF."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

def rows(text):
    for line in text.splitlines():
        fields = line.split()
        if len(fields) >= 8 and re.fullmatch(r'\d+:', fields[0]):
            yield fields

def main():
    p = argparse.ArgumentParser()
    p.add_argument('--audit', type=Path, required=True)
    p.add_argument('--readelf', required=True)
    p.add_argument('--provider', type=Path, action='append', required=True)
    p.add_argument('--output', type=Path, required=True)
    args = p.parse_args()
    audit = json.loads(args.audit.read_text(encoding='utf-8'))
    core = next(f for f in audit['files'] if f['path'] == 'SystemARM64/Core.so')
    if core['readelf_exit']: raise SystemExit('Core audit failed')
    imports = {(f[7], f[3]) for f in rows(core['readelf_output']) if f[6] == 'UND' and '@GLIBC_' in f[7]}
    exports = {}
    providers = []
    for path in args.provider:
        run = subprocess.run([args.readelf, '--dyn-syms', '-W', str(path)], check=True,
                             capture_output=True, text=True, encoding='utf-8', errors='replace')
        providers.append({'name': path.name, 'sha256': hashlib.sha256(path.read_bytes()).hexdigest()})
        for f in rows(run.stdout):
            if f[6] == 'UND' or f[4] not in ('GLOBAL', 'WEAK') or f[5] not in ('DEFAULT', 'PROTECTED'): continue
            name = f[7].replace('@@', '@')
            exports.setdefault((name, f[3]), []).append(path.name)
    matches = [{'import': name, 'type': kind, 'providers': exports.get((name, kind), [])} for name, kind in sorted(imports)]
    missing = [row for row in matches if not row['providers']]
    report = {'core_sha256': core['sha256'], 'providers': providers, 'required_count': len(matches),
              'matching_count': len(matches) - len(missing), 'missing': missing, 'matches': matches,
              'status': 'INCOMPLETE' if missing else 'UNVERIFIED',
              'limitations': ['Exact symbol name, version and type only; not ABI, behavior or dynamic lookup proof.',
                              'Does not check GCC, C++ or non-GLIBC imports, constructors, data layouts, TLS, interpreter or library search.',
                              'Matching all entries must not be treated as permission or proof to execute the original engine.']}
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print(f"GLIBC imports: {len(matches)}, exact exported candidates: {len(matches)-len(missing)}, missing: {len(missing)}; {report['status']}")
    for row in missing: print(f"  {row['type']} {row['import']}")
    return 1 if missing else 0

if __name__ == '__main__': raise SystemExit(main())
