"""Transform one pinned launcher for a bounded Android TLS experiment."""
import hashlib
import os
import pathlib
import struct

root = pathlib.Path(__file__).resolve().parents[1]
research = pathlib.Path(os.environ.get('UT99_RESEARCH_DIR', root / 'research'))
src = research / 'launcher-probe/ut-launch.so'
dst = research / 'launcher-probe/ut-launch-tls.so'
data = bytearray(src.read_bytes())
if hashlib.sha256(data).hexdigest() != '661e0b5cd728a5faf5b5a62ebf8c3d9c20c633387ba6f654a0a2eaceab285af8':
    raise SystemExit('Unexpected launcher probe hash')
phoff = struct.unpack_from('<Q',data,32)[0]
phsize, phnum = struct.unpack_from('<HH',data,54)
changed = 0
for n in range(phnum):
    p = phoff + n * phsize
    kind, flags = struct.unpack_from('<II',data,p)
    offset, address, _, length = struct.unpack_from('<QQQQ',data,p+8)
    if kind != 1 or not flags & 1: continue
    for pos in range(offset, offset+length-15, 4):
        first, second, third, fourth = struct.unpack_from('<IIII',data,pos)
        if first & 0x9f00001f != 0x90000000 or (second,third,fourth) != (0xf9478800,0xd503201f,0xd503201f): continue
        pc = address + pos - offset
        imm = ((first >> 29) & 3) | (((first >> 5) & 0x7ffff) << 2)
        if imm & (1 << 20): imm -= 1 << 21
        if (pc & ~0xfff) + (imm << 12) != 0x30000: continue
        delta = 0x69c0 - (pc + 4)  # known __libc_start_main PLT, now TLS trampoline
        if delta % 4 or not -(1 << 27) <= delta < (1 << 27): raise SystemExit('BL out of range')
        struct.pack_into('<IIII',data,pos,0xa9bf07fe,0x94000000 | ((delta >> 2) & 0x3ffffff),0xa8c107fe,0xd503201f)
        changed += 1
if changed != 138: raise SystemExit(f'Unexpected TLS access count: {changed}')
shoff = struct.unpack_from('<Q',data,40)[0]
shsize, shnum = struct.unpack_from('<HH',data,58)
relocations = 0
for n in range(shnum):
    p = shoff + n * shsize
    if struct.unpack_from('<I',data,p+4)[0] != 4: continue  # SHT_RELA
    off, size = struct.unpack_from('<QQ',data,p+24)
    entry = struct.unpack_from('<Q',data,p+56)[0]
    if entry != 24: raise SystemExit('Unexpected RELA entry size')
    for pos in range(off,off+size,entry):
        target, info = struct.unpack_from('<QQ',data,pos)
        if target == 0x30f10:
            if info & 0xffffffff != 0x406: raise SystemExit('Unexpected TLS relocation')
            struct.pack_into('<Q',data,pos+8,0)  # R_AARCH64_NONE
            relocations += 1
if relocations != 1: raise SystemExit(f'Unexpected TLS relocation count: {relocations}')
dst.write_bytes(data)
print(f'TLS probe copy: {dst} sites={changed} relocation={relocations} SHA-256 {hashlib.sha256(data).hexdigest()}')
