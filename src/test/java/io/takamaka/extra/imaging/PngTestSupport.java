/*
 * Copyright 2026 AiliA SA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.takamaka.extra.imaging;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * Builds PNG files chunk by chunk, so a test controls exactly where an {@code eXIf} chunk sits,
 * what it holds and what its length field claims (C127).
 *
 * <p>{@code ImageIO} cannot write an {@code eXIf} chunk, and the device fixture that exposed the
 * defect (C121: a 1080x720 PNG tagged orientation 6) is in no repository. Hostile shapes — a
 * truncated TIFF, a lying chunk length, a chunk after the pixels — exist in no honest file anyway.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
final class PngTestSupport {

    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    /** Byte length of a whole IHDR chunk: 4 length + 4 type + 13 data + 4 CRC. */
    static final int IHDR_CHUNK_LEN = 25;

    /** Byte length of the bare TIFF built by {@link #orientationTiff}. */
    static final int TIFF_LEN = 26;

    private PngTestSupport() {
    }

    /** One chunk: big-endian length, type, data, CRC-32 over type + data. */
    static byte[] chunk(String type, byte[] data) {
        byte[] t = type.getBytes(StandardCharsets.US_ASCII);
        CRC32 crc = new CRC32();
        crc.update(t, 0, t.length);
        crc.update(data, 0, data.length);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        be32(out, data.length);
        out.write(t, 0, t.length);
        out.write(data, 0, data.length);
        be32(out, (int) crc.getValue());
        return out.toByteArray();
    }

    /**
     * A bare TIFF structure whose IFD0 holds one entry, the orientation tag: 8 byte header +
     * 2 byte entry count + 12 byte entry + 4 byte next-IFD offset = {@value #TIFF_LEN} bytes.
     * This is an {@code eXIf} chunk's whole payload (no {@code "Exif\0\0"} prefix).
     */
    static byte[] orientationTiff(int orientation, boolean bigEndian) {
        byte[] app1 = ExifTestSupport.orientationExif(orientation, bigEndian);
        byte[] tiff = new byte[app1.length - 6];
        System.arraycopy(app1, 6, tiff, 0, tiff.length);
        return tiff;
    }

    /** The IHDR chunk of an 8-bit truecolour image, with or without an alpha channel. */
    static byte[] ihdr(int w, int h, boolean alpha) {
        ByteArrayOutputStream d = new ByteArrayOutputStream();
        be32(d, w);
        be32(d, h);
        d.write(8);               // bit depth
        d.write(alpha ? 6 : 2);   // colour type: RGBA or RGB
        d.write(0);               // compression
        d.write(0);               // filter
        d.write(0);               // no interlace
        return chunk("IHDR", d.toByteArray());
    }

    /**
     * The IDAT chunk for {@code argb} pixels (row-major, {@code w*h} of them), every scanline with
     * filter type 0.
     */
    static byte[] idat(int[] argb, int w, int h, boolean alpha) {
        final int bpp = alpha ? 4 : 3;
        byte[] raw = new byte[h * (1 + w * bpp)];
        int p = 0;
        for (int y = 0; y < h; y++) {
            raw[p++] = 0;
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                raw[p++] = (byte) (c >> 16);
                raw[p++] = (byte) (c >> 8);
                raw[p++] = (byte) c;
                if (alpha) {
                    raw[p++] = (byte) (c >>> 24);
                }
            }
        }
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(raw);
        deflater.finish();
        ByteArrayOutputStream z = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        while (!deflater.finished()) {
            int n = deflater.deflate(buf);
            z.write(buf, 0, n);
        }
        deflater.end();
        return chunk("IDAT", z.toByteArray());
    }

    /**
     * Assemble a PNG: signature, IHDR, {@code beforeIdat} verbatim, IDAT, {@code afterIdat}
     * verbatim, IEND. The two extra parts are raw bytes, not chunks, so a test can pass a
     * well-formed chunk, several, or a damaged one. Either may be {@code null}.
     */
    static byte[] png(int[] argb, int w, int h, boolean alpha, byte[] beforeIdat, byte[] afterIdat) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        write(out, SIGNATURE);
        write(out, ihdr(w, h, alpha));
        if (beforeIdat != null) {
            write(out, beforeIdat);
        }
        write(out, idat(argb, w, h, alpha));
        if (afterIdat != null) {
            write(out, afterIdat);
        }
        write(out, chunk("IEND", new byte[0]));
        return out.toByteArray();
    }

    /** An opaque PNG with no ancillary chunk. */
    static byte[] plain(int[] argb, int w, int h) {
        return png(argb, w, h, false, null, null);
    }

    /** An opaque PNG carrying a well-formed {@code eXIf} chunk right after IHDR. */
    static byte[] tagged(int[] argb, int w, int h, int orientation, boolean bigEndian) {
        return png(argb, w, h, false, chunk("eXIf", orientationTiff(orientation, bigEndian)), null);
    }

    /**
     * Opaque blocks of seeded colour, {@code block} pixels square. Low entropy on purpose: the
     * PNG stays a few kilobytes, small enough to live as Base64 inside a JSON vector.
     */
    static int[] blocks(int w, int h, int block, long seed) {
        return blocks(w, h, block, seed, false);
    }

    /**
     * As {@link #blocks(int, int, int, long)}; with {@code transparency}, every other block is
     * fully transparent — REAL transparency, the kind that selects a PNG preview.
     */
    static int[] blocks(int w, int h, int block, long seed, boolean transparency) {
        final int cols = (w + block - 1) / block;
        final int rows = (h + block - 1) / block;
        int[] palette = new int[cols * rows];
        Random rnd = new Random(seed);
        for (int i = 0; i < palette.length; i++) {
            int rgb = rnd.nextInt(0x1000000);
            boolean clear = transparency && ((i / cols + i % cols) % 2 == 1);
            palette[i] = clear ? 0 : (0xFF000000 | rgb);
        }
        int[] px = new int[w * h];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = palette[(y / block) * cols + (x / block)];
            }
        }
        return px;
    }

    /** Incompressible opaque noise: a PNG of it is large enough to be past the inline rule. */
    static int[] noise(int w, int h, long seed) {
        Random rnd = new Random(seed);
        int[] px = new int[w * h];
        for (int i = 0; i < px.length; i++) {
            px[i] = 0xFF000000 | rnd.nextInt(0x1000000);
        }
        return px;
    }

    static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            write(out, p);
        }
        return out.toByteArray();
    }

    static byte[] head(byte[] b, int keep) {
        byte[] out = new byte[Math.min(keep, b.length)];
        System.arraycopy(b, 0, out, 0, out.length);
        return out;
    }

    private static void write(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static void be32(ByteArrayOutputStream out, int v) {
        out.write((v >>> 24) & 0xFF);
        out.write((v >>> 16) & 0xFF);
        out.write((v >>> 8) & 0xFF);
        out.write(v & 0xFF);
    }
}
