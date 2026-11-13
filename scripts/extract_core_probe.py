#!/usr/bin/env python3
"""Copy only the pinned Core.so archive member into the ignored research area."""
import hashlib
import os
import sys
import tarfile
from pathlib import Path

root = Path(__file__).resolve().parents[1]
research = Path(os.environ.get('UT99_RESEARCH_DIR', root / 'research'))
archive = research / 'OldUnreal-UTPatch469e-Linux-arm64.tar.bz2'
output = research / 'Core.so'
expected_archive = '4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b'
expected_core = 'f0ef4d6bd3b2bf13c4afb8e0ad0c3ea6be6a63182b9d64a67554136e03c3837d'
def sha(data): return hashlib.sha256(data).hexdigest()
if sha(archive.read_bytes()) != expected_archive:
    sys.exit('Archive hash mismatch; refusing extraction')
with tarfile.open(archive, 'r:bz2') as tar:
    member = tar.getmember('SystemARM64/Core.so')
    if not member.isfile() or member.size > 10_000_000:
        sys.exit('Unexpected Core.so member')
    data = tar.extractfile(member).read()
if sha(data) != expected_core:
    sys.exit('Core.so hash mismatch; refusing extraction')
output.write_bytes(data)
print(f'{output}: {expected_core}')
