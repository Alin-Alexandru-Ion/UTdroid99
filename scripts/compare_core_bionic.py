#!/usr/bin/env python3
"""Compare Core's GLIBC-versioned names with NDK Bionic stub exports."""
import argparse
import json
import re
import subprocess
from pathlib import Path

def dynamic_symbols(output):
    for line in output.splitlines():
        fields = line.split()
        if len(fields) >= 8 and re.fullmatch(r'\d+:', fields[0]):
            yield fields

p = argparse.ArgumentParser()
p.add_argument('--audit', required=True, type=Path)
p.add_argument('--readelf', required=True)
p.add_argument('--stub-dir', required=True, type=Path)
p.add_argument('--cxx', type=Path, help='optional Android libc++_shared.so for name-only comparison')
args = p.parse_args()
audit = json.loads(args.audit.read_text(encoding='utf-8'))
core = next(f for f in audit['files'] if f['path'] == 'SystemARM64/Core.so')
if core['readelf_exit']:
    raise SystemExit('Core readelf failed; comparison unavailable')
imports = {f[7].split('@')[0]: f[7] for f in dynamic_symbols(core['readelf_output'])
           if f[6] == 'UND' and '@GLIBC_' in f[7]}
exports = set()
for name in ('libc.so', 'libm.so', 'libdl.so'):
    result = subprocess.run([args.readelf, '--dyn-syms', '-W', str(args.stub_dir / name)],
                            check=True, capture_output=True, text=True)
    exports.update(f[7].split('@')[0] for f in dynamic_symbols(result.stdout)
                   if f[6] != 'UND' and f[4] in ('GLOBAL', 'WEAK') and f[5] in ('DEFAULT', 'PROTECTED'))
report = {'core_sha256': core['sha256'], 'glibc_import_names': len(imports),
          'not_in_stub_exports': sorted(imports[n] for n in imports.keys() - exports),
          'limitations': ['Name-only comparison against supplied NDK stubs, not runtime libraries.',
                          'Matched names do not establish symbol versions, ABI or behavior.',
                          'A missing name might have another provider or require an adapter.']}
if args.cxx:
    cxx_imports = {f[7].split('@')[0] for f in dynamic_symbols(core['readelf_output'])
                   if f[6] == 'UND' and re.search(r'@(GLIBCXX|CXXABI)_', f[7])}
    cxx_dump = subprocess.run([args.readelf, '--dyn-syms', '-W', str(args.cxx)],
                              check=True, capture_output=True, text=True).stdout
    cxx_exports = {f[7].split('@')[0] for f in dynamic_symbols(cxx_dump)
                   if f[6] != 'UND' and f[4] in ('GLOBAL', 'WEAK') and f[5] in ('DEFAULT', 'PROTECTED')}
    report['cxx'] = {'import_names': len(cxx_imports), 'present_names': len(cxx_imports & cxx_exports),
                     'missing_names': sorted(cxx_imports - cxx_exports)}
print(json.dumps(report, indent=2))
