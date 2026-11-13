#!/usr/bin/env python3
"""Compare audited ELF SDL imports to a candidate library's dynamic exports."""
import argparse
import hashlib
import json
import re
import subprocess
from pathlib import Path

def symbols(text, undefined):
    result = set()
    for line in text.splitlines():
        fields = line.split()
        if len(fields) < 8 or not re.fullmatch(r'\d+:', fields[0]):
            continue
        if ((fields[6] == 'UND') == undefined and fields[4] in ('GLOBAL', 'WEAK')
                and (undefined or fields[5] in ('DEFAULT', 'PROTECTED'))):
            name = fields[7].split('@')[0]
            if name.startswith('SDL_'):
                result.add(name)
    return result

p = argparse.ArgumentParser()
p.add_argument('--audit', type=Path, required=True)
p.add_argument('--candidate', type=Path, required=True)
p.add_argument('--readelf', required=True)
args = p.parse_args()
audit = json.loads(args.audit.read_text(encoding='utf-8'))
imports = set().union(*(symbols(f['readelf_output'], True) for f in audit['files']))
dump = subprocess.run([args.readelf, '-d', '--dyn-syms', '-W', str(args.candidate)], check=True, capture_output=True, text=True).stdout
exports = symbols(dump, False)
soname = re.findall(r'Library soname: \[(.*?)\]', dump)
print(json.dumps({'candidate_sha256': hashlib.sha256(args.candidate.read_bytes()).hexdigest(),
                  'soname': soname, 'import_count': len(imports),
                  'present_count': len(imports & exports), 'missing': sorted(imports - exports),
                  'limitation': 'Names only; ABI, behavior, initialization and symbol version compatibility not established.'}, indent=2))
