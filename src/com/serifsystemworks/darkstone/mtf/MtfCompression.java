package com.serifsystemworks.darkstone.mtf;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Darkstone MTF per-entry compression (LZ-ish backref stream).
 * Magic: AE BE or AF BE, 12-byte header, then flag-byte bitstream.
 * <p>
 * Decompress is implemented. Compress is required for safe DATA.MTF repack.
 */
public final class MtfCompression {

    public static final int MAGIC_AE = 0xAE;
    public static final int MAGIC_AF = 0xAF;
    public static final int MAGIC_BE = 0xBE;
    public static final int HEADER_SIZE = 12;

    private MtfCompression() {}

    public static boolean isCompressed(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }
        int m1 = data[0] & 0xFF;
        int m2 = data[1] & 0xFF;
        return (m1 == MAGIC_AE || m1 == MAGIC_AF) && m2 == MAGIC_BE;
    }

    public static boolean isCompressedMagic(int m1, int m2) {
        return (m1 == MAGIC_AE || m1 == MAGIC_AF) && m2 == MAGIC_BE;
    }

    /**
     * @param compressedData full entry payload starting at the 12-byte header (or raw if uncompressed)
     * @param expectedSize   decompressed size from TOC
     */
    public static byte[] decompress(byte[] compressedData, int expectedSize) throws IOException {
        if (compressedData == null || compressedData.length < 2) {
            throw new IOException("Data too short");
        }
        if (!isCompressed(compressedData)) {
            // Stored uncompressed — return as-is (trim/pad to expected if needed)
            if (compressedData.length == expectedSize) {
                return compressedData;
            }
            byte[] out = new byte[expectedSize];
            System.arraycopy(compressedData, 0, out, 0, Math.min(compressedData.length, expectedSize));
            return out;
        }
        if (compressedData.length < HEADER_SIZE) {
            throw new IOException("Compressed entry shorter than 12-byte header");
        }

        ByteBuffer header = ByteBuffer.wrap(compressedData, 0, HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        header.get(); // magic1
        header.get(); // magic2
        header.getShort(); // unknown flags
        int compressedSize = header.getInt(); // size of compressed stream (often includes header)
        int decompressedSize = header.getInt();
        if (expectedSize <= 0) {
            expectedSize = decompressedSize;
        }

        byte[] decompressed = new byte[expectedSize];
        int outPos = 0;
        int inPos = HEADER_SIZE;
        // Some archives report compressedSize as total entry size; stay within buffer either way
        int inEnd = compressedData.length;

        while (outPos < expectedSize && inPos < inEnd) {
            int control = compressedData[inPos++] & 0xFF;
            for (int b = 0; b < 8 && outPos < expectedSize && inPos < inEnd; b++) {
                if ((control & (1 << b)) != 0) {
                    decompressed[outPos++] = compressedData[inPos++];
                } else {
                    if (inPos + 1 >= inEnd) {
                        break;
                    }
                    int word = (compressedData[inPos] & 0xFF) | ((compressedData[inPos + 1] & 0xFF) << 8);
                    inPos += 2;
                    int count = ((word >>> 10) & 0x3F) + 3;
                    int offset = word & 0x3FF;
                    if (offset == 0) {
                        // invalid / edge — treat carefully
                        break;
                    }
                    int src = outPos - offset;
                    for (int i = 0; i < count && outPos < expectedSize; i++) {
                        if (src < 0) {
                            break;
                        }
                        // allow overlapping copies (src can advance into just-written bytes)
                        decompressed[outPos++] = decompressed[src++];
                    }
                }
            }
        }
        return decompressed;
    }

    /**
     * Placeholder compressor — stores uncompressed with no AE/BE header.
     * TODO: implement real backref encoder so repacked DATA.MTF stays near original size.
     */
    public static byte[] compress(byte[] raw) {
        if (raw == null) {
            return new byte[0];
        }
        // Uncompressed store: game accepts some uncompressed entries (e.g. MP2),
        // but mass-uncompressed DATA.MTF will not match retail layout/size.
        return raw.clone();
    }
}
