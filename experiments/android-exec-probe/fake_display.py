"""Local-only RFB fixture; no game data or input events leave the emulator."""
import socket
import struct

def read_exact(conn, size):
    chunks = bytearray()
    while len(chunks) < size:
        chunk = conn.recv(size - len(chunks))
        if not chunk:
            raise EOFError()
        chunks.extend(chunk)
    return bytes(chunks)

with socket.socket() as server:
    server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    server.bind(('127.0.0.1', 5901))
    server.listen(1)
    print('fixture listening', flush=True)
    conn, _ = server.accept()
    with conn:
        conn.sendall(b'RFB 003.008\n')
        assert read_exact(conn, 12) == b'RFB 003.008\n'
        conn.sendall(b'\x01\x01')
        assert read_exact(conn, 1) == b'\x01'
        conn.sendall(b'\0\0\0\0')
        assert read_exact(conn, 1) == b'\x01'
        conn.sendall(struct.pack('>HH', 640, 480) + bytes(16) + struct.pack('>I', 7) + b'fixture')
        assert read_exact(conn, 28)[20] == 2
        assert read_exact(conn, 10)[0] == 3
        pixel = bytes((0x20, 0x70, 0xd0, 0))
        conn.sendall(struct.pack('>BBHHHHHi', 0, 0, 1, 0, 0, 640, 480, 0) + pixel * (640 * 480))
        while True:
            kind = read_exact(conn, 1)[0]
            if kind == 3:
                read_exact(conn, 9)
            elif kind == 4:
                pressed, _, key = struct.unpack('>BHI', read_exact(conn, 7))
                print('key', key, pressed, flush=True)
            elif kind == 5:
                mask, x, y = struct.unpack('>BHH', read_exact(conn, 5))
                print('pointer', x, y, mask, flush=True)
            else:
                raise ValueError(f'unexpected RFB message {kind}')
