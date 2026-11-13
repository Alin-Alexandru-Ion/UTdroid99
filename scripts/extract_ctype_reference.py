#!/usr/bin/env python3
"""Extract pinned glibc C-locale tables as an ignored test reference."""
import hashlib
import re
import struct
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'research/glibc-reference/C-ctype.c'
expected = '7e2a69821d5bb269f80d69804513bcf2a1d146f87be6f18aae09b2db1264b796'
data = src.read_bytes()
if hashlib.sha256(data).hexdigest() != expected:
    raise SystemExit('glibc reference hash mismatch')
text = data.decode('utf-8')
def section(name):
    match = re.search(r'\b' + re.escape(name) + r'\[\d+\].*?=\s*(.*?);', text, re.S)
    if not match: raise SystemExit('Missing reference table: ' + name)
    return re.sub(r'/\*.*?\*/', '', match.group(1), flags=re.S)
part = section('_nl_C_LC_CTYPE_class')
octets = [int(x, 8) for x in re.findall(r'\\([0-7]{3})', part)]
if len(octets) != 768: raise SystemExit(f'Unexpected class-table byte count: {len(octets)}')
def int_table(name):
    values = [int(x, 16) for x in re.findall(r'0x[0-9a-fA-F]+', section(name))]
    if len(values) != 384: raise SystemExit(f'Unexpected {name} count: {len(values)}')
    return values
upper = int_table('_nl_C_LC_CTYPE_toupper')
lower = int_table('_nl_C_LC_CTYPE_tolower')
out = bytes(octets) + struct.pack('<384I', *lower) + struct.pack('<384I', *upper)
destination = root / 'research/ctype-reference.bin'
destination.write_bytes(out)
print(f'{destination}: {len(out)} bytes sha256={hashlib.sha256(out).hexdigest()}')
