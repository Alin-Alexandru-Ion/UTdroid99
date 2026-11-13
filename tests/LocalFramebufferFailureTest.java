package org.ut99android.host;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

public final class LocalFramebufferFailureTest {
    private static void check(String kind) throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        try (ServerSocket server = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread peer = new Thread(() -> {
                try (Socket s = server.accept()) {
                    DataInputStream in = new DataInputStream(s.getInputStream());
                    DataOutputStream out = new DataOutputStream(s.getOutputStream());
                    if (kind.equals("version")) { out.write("RFB 003.003\n".getBytes(StandardCharsets.US_ASCII)); out.flush(); return; }
                    out.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII)); out.flush();
                    in.readFully(new byte[12]);
                    if (kind.equals("auth")) { out.writeByte(1); out.writeByte(2); out.flush(); return; }
                    out.writeByte(1); out.writeByte(1); out.flush(); in.readUnsignedByte();
                    out.writeInt(0); out.flush(); in.readUnsignedByte();
                    out.writeShort(65535); out.writeShort(65535); out.flush();
                } catch (Throwable t) { failure.set(t); }
            });
            peer.start();
            try (LocalFramebufferClient ignored = new LocalFramebufferClient(server.getLocalPort())) {
                throw new AssertionError(kind + " was accepted");
            } catch (java.io.IOException expected) { /* Closed and rejected. */ }
            peer.join(6000);
            if (peer.isAlive()) throw new AssertionError("server hung");
            if (failure.get() != null) throw new AssertionError(failure.get());
        }
    }
    public static void main(String[] args) throws Exception {
        check("version"); check("auth"); check("dimensions");
        System.out.println("Rejected unsupported protocol, authentication and oversized framebuffer");
    }
}
