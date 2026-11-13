"""Fetch pinned Android ARM64 PRoot test binaries; outputs remain private research files."""
import hashlib
import io
import tarfile
import urllib.request
from pathlib import Path

out = Path(__file__).resolve().parents[2] / 'research/linux-native-runtime/arm64-v8a'
out.mkdir(parents=True, exist_ok=True)
packages = (
    ('pool/main/p/proot/proot_5.1.107.102_aarch64.deb', '10a394b585c2fc2b2c8502544f4e5f1be581dc8bfff79e8ba62f16207423e030'),
    ('pool/main/libt/libtalloc/libtalloc_2.5.0_aarch64.deb', '556591f43bb773ad8777e1a29522640866a55f95dab71914418b94a8c58ad5a7'),
    ('pool/main/liba/libandroid-shmem/libandroid-shmem_0.7_aarch64.deb', '0da3a24d558b93c92bcf8d611e0826a99ff96e396b148e6cdf33b47c47c57ff6'),
)
needed = {'bin/proot': 'libutproot.so', 'libexec/proot/loader': 'libutprootloader.so',
          'lib/libtalloc.so.2.5.0': 'libtalloc.so.2', 'lib/libandroid-shmem.so': 'libandroid-shmem.so'}
for url, digest in packages:
    data = urllib.request.urlopen('https://packages.termux.dev/apt/termux-main/' + url, timeout=60).read()
    if hashlib.sha256(data).hexdigest() != digest:
        raise ValueError('package hash mismatch: ' + url)
    if data[:8] != b'!<arch>\n': raise ValueError('invalid deb')
    pos = 8
    while pos < len(data):
        header = data[pos:pos + 60]; name = header[:16].decode().strip().rstrip('/'); size = int(header[48:58]); pos += 60
        raw = data[pos:pos + size]; pos += size + size % 2
        if name.startswith('data.tar.'):
            with tarfile.open(fileobj=io.BytesIO(raw), mode='r:*') as archive:
                for entry in archive:
                    path = entry.name.removeprefix('./')
                    for suffix, target in needed.items():
                        if path.endswith('/' + suffix) and entry.isfile():
                            (out / target).write_bytes(archive.extractfile(entry).read())
                            print(target, (out / target).stat().st_size)
            break
if any(not (out / item).is_file() for item in needed.values()):
    raise SystemExit('Required Android runtime binaries missing')
