package org.ut99android.host;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

/** Protocol-level check using a local in-process display server. */
public final class LocalFramebufferClientTest {
    public static void main(String[] args) throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread peer = new Thread(() -> {
                try (Socket socket = server.accept()) {
                    socket.setSoTimeout(5000);
                    DataInputStream in = new DataInputStream(socket.getInputStream());
                    DataOutputStream out = new DataOutputStream(socket.getOutputStream());
                    out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                    byte[] version = new byte[12]; in.readFully(version);
                    if (!Arrays.equals(version, "RFB 003.008\n".getBytes(StandardCharsets.US_ASCII))) throw new AssertionError("version");
                    out.writeByte(1); out.writeByte(1); out.flush();
                    if (in.readUnsignedByte() != 1) throw new AssertionError("security selection");
                    out.writeInt(0); out.flush();
                    if (in.readUnsignedByte() != 1) throw new AssertionError("shared flag");
                    out.writeShort(2); out.writeShort(2); out.write(new byte[16]); out.writeInt(4); out.writeBytes("test"); out.flush();
                    byte[] settings = new byte[28]; in.readFully(settings);
                    if (settings[0] != 0 || settings[20] != 2 || settings[27] != 0) throw new AssertionError("pixel/encoding setup");
                    byte[] request = new byte[10]; in.readFully(request);
                    if (request[0] != 3 || request[1] != 0) throw new AssertionError("initial request");
                    out.writeByte(0); out.writeByte(0); out.writeShort(1);
                    out.writeShort(0); out.writeShort(0); out.writeShort(2); out.writeShort(2); out.writeInt(0);
                    byte[] data = {3,2,1,0, 6,5,4,0, 9,8,7,0, 12,11,10,0};
                    for (byte b : data) { out.writeByte(b); out.flush(); }
                    byte[] events = new byte[14]; in.readFully(events);
                    if (!Arrays.equals(events, new byte[]{5,1,0,1,0,0, 4,1,0,0,0,0,(byte)0xff,27})) throw new AssertionError("pointer/key event");
                } catch (Throwable t) { error.set(t); }
            });
            peer.start();
            try (LocalFramebufferClient client = new LocalFramebufferClient(server.getLocalPort())) {
                try {
                    client.receive((w,h,p) -> {
                        if (w != 2 || h != 2 || !Arrays.equals(p, new int[]{0xff010203,0xff040506,0xff070809,0xff0a0b0c}))
                            throw new AssertionError("wrong pixel data");
                        try { client.pointer(1, 0, true); client.key(0xff1b, true); }
                        catch (Exception e) { throw new RuntimeException(e); }
                    });
                } catch (java.io.IOException expected) { /* Peer closes after receiving events. */ }
            }
            peer.join(6000);
            if (peer.isAlive()) throw new AssertionError("server hung");
            if (error.get() != null) throw new AssertionError(error.get());
        }
        System.out.println("Local display handshake, fragmented frame and touch/key events passed");
    }
}
