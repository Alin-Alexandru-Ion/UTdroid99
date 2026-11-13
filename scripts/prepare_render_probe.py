"""Create a pinned renderer research copy with GNU-unique symbols made global."""
import hashlib
import os
import pathlib
import struct

root=pathlib.Path(__file__).resolve().parents[1]
research=pathlib.Path(os.environ.get('UT99_RESEARCH_DIR', root/'research'))
def prepare(name, expected, expected_count):
 source=research/'SystemARM64'/name
 target=research/'launcher-probe'/name
 data=bytearray(source.read_bytes())
 if hashlib.sha256(data).hexdigest() != expected: raise SystemExit(f'Unexpected original {name} hash')
 if data[:6] != b'\x7fELF\x02\x01': raise SystemExit('Expected ELF64 LE')
 off=struct.unpack_from('<Q',data,40)[0]
 size,count=struct.unpack_from('<HH',data,58)
 edits=[]
 for i in range(count):
    sh=off+i*size
    if struct.unpack_from('<I',data,sh+4)[0] != 11: continue
    start,length=struct.unpack_from('<QQ',data,sh+24)
    stride=struct.unpack_from('<Q',data,sh+56)[0]
    if stride != 24: raise SystemExit('Unexpected dynamic symbol size')
    for entry in range(start,start+length,stride):
        info=data[entry+4]
        if info >> 4 == 10:
            data[entry+4]=(1 << 4) | (info & 15)
            edits.append(entry+4)
 if len(edits)!=expected_count: raise SystemExit(f'Unexpected GNU unique count {len(edits)}')
 target.write_bytes(data)
 print(f'{name} probe: {len(edits)} symbol bindings changed, SHA-256 {hashlib.sha256(data).hexdigest()}')
prepare('Render.so','7b41c3849d9c0af8a9eb00b8a42063cf224677589e94819d47b40f03e6801181',4)
prepare('IpDrv.so','26a925876f45f080ece50988a9c07ada11a29f49897c6e412ed52562c2cdc170',1)
