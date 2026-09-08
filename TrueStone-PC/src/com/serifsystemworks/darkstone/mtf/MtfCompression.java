package com.serifsystemworks.darkstone.mtf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Darkstone MTF per-entry compression (LZ-ish backref stream).
 * Magic: AE BE or AF BE, 12-byte header, then flag-byte bitstream.
 * <p>
 * Bitstream format (matches {@link #decompress}):
 * <pre>
 *   repeat:
 *     controlByte                  -- bit b (LSB first) = 1 -> literal, 0 -> backref
 *     for b in 0..7:
 *       if control bit set:  1 literal byte
 *       else:                2-byte LE word:
 *                               bits 0-9   = offset (1..1023) back from current pos
 *                               bits 10-15 = (matchLength - 3), so length is 3..66
 * </pre>
 * {@code decompress} was reverse-engineered and validated first; {@code compress}
 * implements a real hash-chain LZ77 encoder producing a stream that decompress()
 * can losslessly invert. It is NOT guaranteed to reproduce retail files'
 * compressed bytes exactly — a different (but equally valid) match parse will
 * still decompress correctly, but that only matters if something outside this
 * archive format checksums the *compressed* bytes rather than the decompressed
 * content. See the MTF README section on open questions.
 */
public final class MtfCompression {

    public static final int MAGIC_AE = 0xAE;
    public static final int MAGIC_AF = 0xAF;
    public static final int MAGIC_BE = 0xBE;
    public static final int HEADER_SIZE = 12;

    private static final int MAX_OFFSET = 1023;      // 10-bit offset field
    private static final int MIN_MATCH = 3;
    private static final int MAX_MATCH = 66;         // 6-bit length field + 3
    private static final int HASH_BITS = 15;
    private static final int HASH_SIZE = 1 << HASH_BITS;
    private static final int MAX_CHAIN_STEPS = 128;  // search-depth cap, keeps compression fast

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
     * Compress {@code raw} into the MTF backref format using magic {@code 0xAE 0xBE}.
     * Equivalent to {@code compress(raw, MAGIC_AE)}.
     */
    public static byte[] compress(byte[] raw) {
        return compress(raw, MAGIC_AE);
    }

    /**
     * Compress {@code raw} into the MTF backref format.
     *
     * @param raw    decompressed bytes to encode
     * @param magic1 MAGIC_AE or MAGIC_AF — meaning of the two variants is not
     *               yet confirmed; AE is used as the default.
     */
    public static byte[] compress(byte[] raw, int magic1) {
        if (raw == null) {
            raw = new byte[0];
        }
        byte[] body = encodeBody(raw);

        ByteBuffer out = ByteBuffer.allocate(HEADER_SIZE + body.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte) magic1);
        out.put((byte) MAGIC_BE);
        out.putShort((short) 0);                 // flags: unknown meaning, 0 for our own output
        out.putInt(HEADER_SIZE + body.length);   // compressedSize: header + body
        out.putInt(raw.length);                  // decompressedSize
        out.put(body);
        return out.array();
    }

    /** Encode just the token bitstream (no header), using hash-chain LZ77 match search. */
    private static byte[] encodeBody(byte[] raw) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(16, raw.length / 2));
        int n = raw.length;

        // head[h] = most recent position whose 3-byte prefix hashes to h, or -1
        int[] head = new int[HASH_SIZE];
        Arrays.fill(head, -1);
        // prev[pos] = previous position sharing the same hash (chain)
        int[] prev = new int[Math.max(1, n)];

        int pos = 0;
        int pendingControl = 0;
        int pendingCount = 0;
        byte[] pendingTokens = new byte[16]; // at most 8 tokens x 2 bytes
        int pendingTokenLen = 0;

        while (pos < n) {
            int bestLen = 0;
            int bestOffset = 0;

            if (pos + MIN_MATCH <= n) {
                int h = hash3(raw, pos);
                int cand = head[h];
                int windowStart = Math.max(0, pos - MAX_OFFSET);
                int steps = 0;
                while (cand >= windowStart && steps < MAX_CHAIN_STEPS) {
                    int len = matchLength(raw, cand, pos, n);
                    if (len > bestLen) {
                        bestLen = len;
                        bestOffset = pos - cand;
                        if (bestLen >= MAX_MATCH) {
                            break;
                        }
                    }
                    cand = prev[cand];
                    steps++;
                }
            }

            if (bestLen >= MIN_MATCH) {
                int word = (bestOffset & 0x3FF) | (((bestLen - 3) & 0x3F) << 10);
                pendingTokens[pendingTokenLen++] = (byte) (word & 0xFF);
                pendingTokens[pendingTokenLen++] = (byte) ((word >>> 8) & 0xFF);
                // control bit for a match stays 0 — nothing to OR in

                int end = pos + bestLen;
                for (; pos < end; pos++) {
                    insertHash(raw, pos, n, head, prev);
                }
            } else {
                pendingControl |= (1 << pendingCount);
                pendingTokens[pendingTokenLen++] = raw[pos];
                insertHash(raw, pos, n, head, prev);
                pos++;
            }

            pendingCount++;
            if (pendingCount == 8) {
                out.write(pendingControl);
                out.write(pendingTokens, 0, pendingTokenLen);
                pendingControl = 0;
                pendingCount = 0;
                pendingTokenLen = 0;
            }
        }

        if (pendingCount > 0) {
            out.write(pendingControl);
            out.write(pendingTokens, 0, pendingTokenLen);
        }

        return out.toByteArray();
    }

    private static void insertHash(byte[] raw, int pos, int n, int[] head, int[] prev) {
        if (pos + MIN_MATCH <= n) {
            int h = hash3(raw, pos);
            prev[pos] = head[h];
            head[h] = pos;
        }
    }

    private static int hash3(byte[] data, int pos) {
        int b0 = data[pos] & 0xFF;
        int b1 = data[pos + 1] & 0xFF;
        int b2 = data[pos + 2] & 0xFF;
        int h = (b0 << 16) | (b1 << 8) | b2;
        h *= 0x9E3779B1;
        return (h >>> (32 - HASH_BITS)) & (HASH_SIZE - 1);
    }

    private static int matchLength(byte[] data, int a, int b, int n) {
        int max = Math.min(MAX_MATCH, n - b);
        int len = 0;
        while (len < max && data[a + len] == data[b + len]) {
            len++;
        }
        return len;
    }
}
