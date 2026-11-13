package org.ut99android.host;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

/** Experimental offline transformations for the exact 469e ARM64 input. */
final class PatchElfTransformer {
    private static final String LAUNCHER = "726b4fbeea173ce62e02a81f76638e939389e1be8b40fea88f0ac83b4d21e390";
    private static final String PIE_RESULT = "661e0b5cd728a5faf5b5a62ebf8c3d9c20c633387ba6f654a0a2eaceab285af8";
    private static final String TLS_RESULT = "fafd3829192f7f829c9b40bf2d7ece98439dd5de2e5a4d27e8eab67e815af0a4";
    private PatchElfTransformer() {}

    static String sha256(byte[] data) throws IOException {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(data);
            StringBuilder result = new StringBuilder(64);
            for (byte b : hash) result.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return result.toString();
        } catch (NoSuchAlgorithmException e) { throw new IOException("SHA-256 unavailable", e); }
    }

    private static ByteBuffer checked(byte[] input, String expected) throws IOException {
        if (!sha256(input).equals(expected)) throw new IOException("Unexpected patch component hash");
        if (input.length < 64 || input[0] != 0x7f || input[1] != 'E' || input[2] != 'L'
                || input[3] != 'F' || input[4] != 2 || input[5] != 1)
            throw new IOException("Expected little-endian ELF64");
        ByteBuffer b = ByteBuffer.wrap(input.clone()).order(ByteOrder.LITTLE_ENDIAN);
        if (Short.toUnsignedInt(b.getShort(18)) != 183) throw new IOException("Expected ARM64 ELF");
        return b;
    }

    private static int range(long offset, long size, int length) throws IOException {
        if (offset < 0 || size < 0 || offset > length || size > length - offset || offset > Integer.MAX_VALUE)
            throw new IOException("Invalid ELF range");
        return (int)offset;
    }
    private static int u16(ByteBuffer b, int offset) { return Short.toUnsignedInt(b.getShort(offset)); }

    static byte[] launcher(byte[] input) throws IOException {
        try {
            ByteBuffer b = checked(input, LAUNCHER);
            int phSize = u16(b, 54), phCount = u16(b, 56);
            int phOff = range(b.getLong(32), (long)phSize * phCount, b.limit());
            if (phSize < 56) throw new IOException("Invalid program header size");
            int dynamic = 0, pie = 0;
            for (int n = 0; n < phCount; ++n) {
                int p = phOff + n * phSize;
                if (b.getInt(p) != 2) continue;
                ++dynamic;
                long size = b.getLong(p + 32);
                int start = range(b.getLong(p + 8), size, b.limit());
                for (int pos = start; pos + 16 <= start + size; pos += 16) {
                    long tag = b.getLong(pos), value = b.getLong(pos + 8);
                    if (tag == 0) break;
                    if (tag == 0x6ffffffbL) {
                        if ((value & 0x08000000L) == 0) throw new IOException("Missing PIE flag");
                        b.putLong(pos + 8, value & ~0x08000000L); ++pie;
                    }
                }
            }
            if (dynamic != 1 || pie != 1 || !sha256(b.array()).equals(PIE_RESULT))
                throw new IOException("Unexpected launcher PIE result");
            patchTls(b);
            if (!sha256(b.array()).equals(TLS_RESULT)) throw new IOException("Unexpected launcher TLS result");
            return b.array();
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) { throw new IOException("Malformed launcher", e); }
    }

    private static void patchTls(ByteBuffer b) throws IOException {
        int phSize = u16(b, 54), phCount = u16(b, 56);
        int phOff = range(b.getLong(32), (long)phSize * phCount, b.limit());
        int changed = 0;
        for (int n = 0; n < phCount; ++n) {
            int p = phOff + n * phSize;
            if (b.getInt(p) != 1 || (b.getInt(p + 4) & 1) == 0) continue;
            long length = b.getLong(p + 32), address = b.getLong(p + 16);
            int start = range(b.getLong(p + 8), length, b.limit());
            for (int pos = start; pos + 16L <= start + length; pos += 4) {
                int first = b.getInt(pos);
                if ((first & 0x9f00001f) != 0x90000000 || b.getInt(pos + 4) != 0xf9478800
                        || b.getInt(pos + 8) != 0xd503201f || b.getInt(pos + 12) != 0xd503201f) continue;
                long pc = address + pos - start;
                int imm = ((first >>> 29) & 3) | (((first >>> 5) & 0x7ffff) << 2);
                if ((imm & (1 << 20)) != 0) imm -= 1 << 21;
                if ((pc & ~0xfffL) + ((long)imm << 12) != 0x30000) continue;
                long delta = 0x69c0 - (pc + 4);
                if (delta % 4 != 0 || delta < -(1L << 27) || delta >= (1L << 27)) throw new IOException("TLS branch out of range");
                b.putInt(pos, 0xa9bf07fe); b.putInt(pos + 4, 0x94000000 | ((int)(delta >> 2) & 0x3ffffff));
                b.putInt(pos + 8, 0xa8c107fe); b.putInt(pos + 12, 0xd503201f); ++changed;
            }
        }
        if (changed != 138) throw new IOException("Unexpected TLS instruction count");
        int shSize = u16(b, 58), shCount = u16(b, 60);
        if (shSize < 64) throw new IOException("Invalid section header size");
        int shOff = range(b.getLong(40), (long)shSize * shCount, b.limit()), count = 0;
        for (int n = 0; n < shCount; ++n) {
            int p = shOff + n * shSize;
            if (b.getInt(p + 4) != 4) continue;
            long size = b.getLong(p + 32), stride = b.getLong(p + 56);
            if (stride != 24 || size % stride != 0) throw new IOException("Unexpected RELA size");
            int start = range(b.getLong(p + 24), size, b.limit());
            for (int pos = start; pos < start + size; pos += (int)stride) {
                if (b.getLong(pos) != 0x30f10) continue;
                if ((b.getLong(pos + 8) & 0xffffffffL) != 0x406) throw new IOException("Unexpected TLS relocation");
                b.putLong(pos + 8, 0); ++count;
            }
        }
        if (count != 1) throw new IOException("Unexpected TLS relocation count");
    }

    static byte[] makeSymbolsGlobal(byte[] input, String expectedHash, int expectedCount) throws IOException {
        try {
            ByteBuffer b = checked(input, expectedHash);
            int size = u16(b, 58), count = u16(b, 60), changed = 0;
            if (size < 64) throw new IOException("Invalid section header size");
            int offset = range(b.getLong(40), (long)size * count, b.limit());
            for (int i = 0; i < count; ++i) {
                int p = offset + i * size;
                if (b.getInt(p + 4) != 11) continue;
                long length = b.getLong(p + 32), stride = b.getLong(p + 56);
                if (stride != 24 || length % stride != 0) throw new IOException("Unexpected symbol size");
                int start = range(b.getLong(p + 24), length, b.limit());
                for (int entry = start; entry < start + length; entry += (int)stride) {
                    int info = b.get(entry + 4) & 0xff;
                    if ((info >> 4) == 10) { b.put(entry + 4, (byte)(0x10 | (info & 15))); ++changed; }
                }
            }
            if (changed != expectedCount) throw new IOException("Unexpected symbol count");
            return b.array();
        } catch (IndexOutOfBoundsException | IllegalArgumentException e) { throw new IOException("Malformed engine module", e); }
    }
}
