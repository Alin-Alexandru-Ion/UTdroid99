import io
import tarfile
from pathlib import Path

root = Path(__file__).resolve().parents[2] / 'research/android-exec-probe'
raw = (root / 'libc6.deb').read_bytes()
if raw[:8] != b'!<arch>\n':
    raise ValueError('not a Debian ar archive')
pos = 8
while pos < len(raw):
    header = raw[pos:pos+60]
    name, size = header[:16].decode().strip().rstrip('/'), int(header[48:58])
    pos += 60
    payload = raw[pos:pos+size]
    pos += size + size % 2
    if name.startswith('data.tar.'):
        with tarfile.open(fileobj=io.BytesIO(payload), mode='r:*') as tar:
            for member in tar:
                if member.name in ('./lib/x86_64-linux-gnu/ld-linux-x86-64.so.2', './lib/x86_64-linux-gnu/libc.so.6'):
                    output = root / ('libguestloader.so' if 'ld-linux' in member.name else 'guest-libc.so')
                    output.write_bytes(tar.extractfile(member).read())
                    print(member.name, '->', output.name)
        break
else:
    raise ValueError('missing data archive')
