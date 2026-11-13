"""Create a private diagnostic copy of the pinned Linux PIE, clearing only DF_1_PIE."""
import hashlib
import os
import pathlib
import struct

root = pathlib.Path(__file__).resolve().parents[1]
research = pathlib.Path(os.environ.get('UT99_RESEARCH_DIR', root / 'research'))
source = research / 'SystemARM64/ut-bin-arm64'
destination = research / 'launcher-probe/ut-launch.so'
data = bytearray(source.read_bytes())
expected = '726b4fbeea173ce62e02a81f76638e939389e1be8b40fea88f0ac83b4d21e390'
if hashlib.sha256(data).hexdigest() != expected:
    raise SystemExit('Unexpected original launcher hash')
if data[:6] != b'\x7fELF\x02\x01' or struct.unpack_from('<H', data, 18)[0] != 183:
    raise SystemExit('Expected little-endian AArch64 ELF64')
phoff = struct.unpack_from('<Q', data, 32)[0]
entsize, count = struct.unpack_from('<HH', data, 54)
dynamic = []
for i in range(count):
    pos = phoff + i * entsize
    p_type, = struct.unpack_from('<I', data, pos)
    if p_type == 2:
        offset, = struct.unpack_from('<Q', data, pos + 8)
        size, = struct.unpack_from('<Q', data, pos + 32)
        dynamic.append((offset, size))
if len(dynamic) != 1:
    raise SystemExit('Expected exactly one PT_DYNAMIC')
changed = 0
offset, size = dynamic[0]
for pos in range(offset, offset + size, 16):
    tag, value = struct.unpack_from('<QQ', data, pos)
    if tag == 0:
        break
    if tag == 0x6ffffffb:
        if not value & 0x08000000:
            raise SystemExit('DF_1_PIE missing')
        struct.pack_into('<Q', data, pos + 8, value & ~0x08000000)
        changed += 1
if changed != 1:
    raise SystemExit('Expected exactly one DF_1_PIE change')
destination.parent.mkdir(parents=True, exist_ok=True)
destination.write_bytes(data)
print(f'Diagnostic copy: {destination} SHA-256 {hashlib.sha256(data).hexdigest()}')
