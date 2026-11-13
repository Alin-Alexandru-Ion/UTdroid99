package org.ut99android.host;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/** Restricted local RFB client used by the experimental Linux display bridge. */
final class LocalFramebufferClient implements AutoCloseable {
    interface Listener { void frame(int width, int height, int[] pixels); }
    private static final int MAX_DIMENSION = 4096;
    private static final int MAX_PIXELS = 4096 * 2160;
    private final Socket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final Object outputLock = new Object();
    private int width, height;
    private int[] pixels;

    LocalFramebufferClient(int port) throws IOException {
        if (port < 1 || port > 65535) throw new IOException("Invalid display port");
        socket = new Socket();
        socket.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 5000);
        socket.setTcpNoDelay(true);
        input = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        output = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        try { handshake(); } catch (IOException | RuntimeException e) { socket.close(); throw e; }
    }
    private void handshake() throws IOException {
        byte[] version = new byte[12]; input.readFully(version);
        String banner = new String(version, StandardCharsets.US_ASCII);
        if (!banner.startsWith("RFB 003.008\n")) throw new IOException("Display requires RFB 3.8: " + banner.trim());
        output.write("RFB 003.008\n".getBytes(StandardCharsets.US_ASCII)); output.flush();
        int count = input.readUnsignedByte();
        if (count == 0) throw new IOException("Display rejected connection: " + readText());
        boolean none = false;
        for (int i = 0; i < count; i++) if (input.readUnsignedByte() == 1) none = true;
        if (!none) throw new IOException("Local display does not offer no-auth mode");
        output.writeByte(1); output.flush();
        if (input.readInt() != 0) throw new IOException("Local display authentication failed: " + readText());
        output.writeByte(1); output.flush(); // Shared display.
        width = input.readUnsignedShort(); height = input.readUnsignedShort();
        if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION || (long)width * height > MAX_PIXELS)
            throw new IOException("Unsupported display dimensions");
        byte[] format = new byte[16]; input.readFully(format); readText();
        pixels = new int[width * height];
        synchronized (outputLock) {
            output.writeByte(0); output.write(new byte[3]); // SetPixelFormat
            output.writeByte(32); output.writeByte(24); output.writeByte(0); output.writeByte(1);
            output.writeShort(255); output.writeShort(255); output.writeShort(255);
            output.writeByte(16); output.writeByte(8); output.writeByte(0); output.write(new byte[3]);
            output.writeByte(2); output.writeByte(0); output.writeShort(1); output.writeInt(0); // Raw encoding only.
            output.flush();
        }
    }
    private String readText() throws IOException {
        int length = input.readInt();
        if (length < 0 || length > 65536) throw new IOException("Invalid display message length");
        byte[] bytes = new byte[length]; input.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
    private void requestFrame(boolean incremental) throws IOException {
        synchronized (outputLock) {
            output.writeByte(3); output.writeByte(incremental ? 1 : 0);
            output.writeShort(0); output.writeShort(0); output.writeShort(width); output.writeShort(height); output.flush();
        }
    }
    void receive(Listener listener) throws IOException {
        requestFrame(false);
        for (;;) {
            int type = input.readUnsignedByte();
            if (type == 0) {
                input.readUnsignedByte(); int rectangles = input.readUnsignedShort();
                for (int n = 0; n < rectangles; n++) {
                    int x = input.readUnsignedShort(), y = input.readUnsignedShort();
                    int w = input.readUnsignedShort(), h = input.readUnsignedShort(), encoding = input.readInt();
                    if (encoding != 0 || w < 1 || h < 1 || x + w > width || y + h > height)
                        throw new IOException("Unsupported or invalid display rectangle");
                    byte[] row = new byte[w * 4];
                    for (int rowIndex = 0; rowIndex < h; rowIndex++) {
                        input.readFully(row);
                        for (int column = 0; column < w; column++) {
                            int i = column * 4;
                            pixels[(y + rowIndex) * width + x + column] = 0xff000000 |
                                    ((row[i + 2] & 255) << 16) | ((row[i + 1] & 255) << 8) | (row[i] & 255);
                        }
                    }
                }
                listener.frame(width, height, pixels);
                requestFrame(true);
            } else if (type == 2) {
                // Bell: no display update.
            } else if (type == 3) {
                byte[] padding = new byte[3]; input.readFully(padding); readText();
            } else throw new IOException("Unsupported display message: " + type);
        }
    }
    void pointer(int x, int y, boolean down) throws IOException {
        synchronized (outputLock) {
            output.writeByte(5); output.writeByte(down ? 1 : 0);
            output.writeShort(Math.max(0, Math.min(width - 1, x)));
            output.writeShort(Math.max(0, Math.min(height - 1, y))); output.flush();
        }
    }
    void key(int keysym, boolean down) throws IOException {
        synchronized (outputLock) {
            output.writeByte(4); output.writeByte(down ? 1 : 0); output.writeShort(0); output.writeInt(keysym); output.flush();
        }
    }
    void centerButton(boolean down) throws IOException { pointer(width / 2, height / 2, down); }
    @Override public void close() throws IOException { socket.close(); }
}
