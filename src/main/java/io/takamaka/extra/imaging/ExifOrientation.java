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

import java.awt.geom.AffineTransform;

/**
 * Reads the EXIF orientation tag out of a JPEG, by hand.
 *
 * <h2>Why by hand</h2>
 *
 * <p>{@code ImageIO.read()} applies no EXIF transform and the JDK exposes no clean API for the
 * tag, so a decoded {@code BufferedImage} is in STORED orientation while every viewer, browser and
 * phone gallery shows the image in DISPLAYED orientation. A thumbnailer that ignores the tag
 * produces sideways previews of perfectly ordinary phone photos.</p>
 *
 * <h2>Fails OPEN, always</h2>
 *
 * <p>This parses <strong>hostile peer-supplied bytes</strong> — the same shape as the RCE-class
 * overflow found in webp4j's hand-written GIF decoder. Every offset is bounds-checked against the
 * array length before use, every read is through a helper that cannot walk off the end, and the
 * IFD entry count is capped. On <em>anything</em> unexpected — truncation, a bad magic, a
 * nonsensical offset, an out-of-range value — the answer is {@link #NORMAL}. This method never
 * throws.</p>
 *
 * <p>That direction is deliberate and normative (§PREVIEW-SPEC): <em>a preview must never fail
 * because peer-supplied EXIF was malformed.</em> The cost of guessing wrong is a sideways
 * thumbnail; the cost of failing closed is no thumbnail at all, decided by the sender.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 0.6.0
 */
public final class ExifOrientation {

    /** The identity orientation, and the answer to every question this class cannot answer. */
    public static final int NORMAL = 1;

    /** TIFF tag number for Orientation. */
    private static final int TAG_ORIENTATION = 0x0112;

    /** TIFF field type SHORT (16-bit unsigned). */
    private static final int TYPE_SHORT = 3;

    /**
     * Sanity cap on IFD0 entry count. A real IFD0 has a few dozen entries; the field is 16-bit so a
     * hostile file can claim 65 535, each costing a 12-byte bounds-checked read. Capping keeps the
     * parse trivially bounded in time as well as in space.
     */
    private static final int MAX_IFD_ENTRIES = 512;

    private ExifOrientation() {
    }

    /**
     * Extract the EXIF orientation of a JPEG.
     *
     * @param bytes the raw source file; may be {@code null}, truncated, or not a JPEG at all
     * @return a value in 1..8, or {@link #NORMAL} when absent, unreadable or not a JPEG
     */
    public static int read(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return NORMAL;
        }
        // SOI. Anything else is not a JPEG, and only JPEG carries EXIF here.
        if (u8(bytes, 0) != 0xFF || u8(bytes, 1) != 0xD8) {
            return NORMAL;
        }

        int pos = 2;
        // Walk the marker segments looking for APP1. A JPEG segment is FF <marker> <len:2> <payload>,
        // where len COUNTS ITSELF, hence the `< 2` guard: a claimed length of 0 or 1 would not
        // advance pos and would spin forever.
        while (pos + 4 <= bytes.length) {
            if (u8(bytes, pos) != 0xFF) {
                return NORMAL; // desynchronised — stop rather than hunt
            }
            int marker = u8(bytes, pos + 1);
            if (marker == 0xD8 || (marker >= 0xD0 && marker <= 0xD9)) {
                pos += 2; // standalone marker, no length field
                continue;
            }
            if (marker == 0xDA) {
                return NORMAL; // start of scan: metadata is behind us
            }
            int len = u16be(bytes, pos + 2);
            if (len < 2 || pos + 2 + len > bytes.length) {
                return NORMAL; // truncated or nonsensical
            }
            if (marker == 0xE1) {
                int found = readFromApp1(bytes, pos + 4, len - 2);
                if (found != NORMAL) {
                    return found;
                }
                // An APP1 that is not EXIF (XMP, for instance) — keep walking.
            }
            pos += 2 + len;
        }
        return NORMAL;
    }

    /**
     * Parse one APP1 payload: {@code "Exif\0\0"} then a TIFF header then IFD0.
     *
     * @param b        the whole file
     * @param off      offset of the APP1 payload (just past the length field)
     * @param declared payload length as declared by the segment header
     */
    private static int readFromApp1(byte[] b, int off, int declared) {
        // "Exif\0\0" is 6 bytes; the TIFF header that follows is 8.
        if (declared < 14 || off + 14 > b.length) {
            return NORMAL;
        }
        if (u8(b, off) != 'E' || u8(b, off + 1) != 'x' || u8(b, off + 2) != 'i'
                || u8(b, off + 3) != 'f' || u8(b, off + 4) != 0 || u8(b, off + 5) != 0) {
            return NORMAL;
        }

        final int tiff = off + 6;               // ALL TIFF offsets are relative to here
        final int end = Math.min(b.length, off + declared);
        if (tiff + 8 > end) {
            return NORMAL;
        }

        final boolean bigEndian;
        int b0 = u8(b, tiff);
        int b1 = u8(b, tiff + 1);
        if (b0 == 'I' && b1 == 'I') {
            bigEndian = false;
        } else if (b0 == 'M' && b1 == 'M') {
            bigEndian = true;
        } else {
            return NORMAL;
        }
        if (u16(b, tiff + 2, bigEndian) != 0x002A) {
            return NORMAL;
        }

        long ifd0 = u32(b, tiff + 4, bigEndian);
        // Offset is relative to `tiff` and must leave room for the 2-byte entry count.
        if (ifd0 < 8 || tiff + ifd0 + 2 > end) {
            return NORMAL;
        }
        int p = (int) (tiff + ifd0);

        int count = u16(b, p, bigEndian);
        if (count <= 0 || count > MAX_IFD_ENTRIES) {
            return NORMAL;
        }
        p += 2;
        // Every entry is exactly 12 bytes; refuse up front if they do not all fit.
        if ((long) p + (long) count * 12L > end) {
            return NORMAL;
        }

        for (int i = 0; i < count; i++) {
            int e = p + i * 12;
            if (u16(b, e, bigEndian) != TAG_ORIENTATION) {
                continue;
            }
            if (u16(b, e + 2, bigEndian) != TYPE_SHORT) {
                return NORMAL; // right tag, wrong type — do not guess
            }
            if (u32(b, e + 4, bigEndian) != 1L) {
                return NORMAL; // right tag, wrong cardinality
            }
            // A SHORT with count 1 fits in the value field, so it is stored INLINE in the
            // first two bytes of that field — never as a pointer. Reading it as an offset
            // is the classic bug here.
            int value = u16(b, e + 8, bigEndian);
            return (value >= 1 && value <= 8) ? value : NORMAL;
        }
        return NORMAL;
    }

    /**
     * The transform that bakes {@code orientation} into pixels, for a source of {@code w x h}.
     *
     * <p>Written as explicit matrices rather than a sequence of {@code rotate}/{@code scale} calls:
     * the concatenation order of {@link AffineTransform} is the reverse of reading order, which is
     * exactly the kind of thing that silently produces a mirrored thumbnail. Each row below states
     * the mapping it implements, so it can be checked by substitution.</p>
     *
     * <p>{@code AffineTransform(m00, m10, m01, m11, m02, m12)} computes
     * {@code X = m00*x + m01*y + m02} and {@code Y = m10*x + m11*y + m12}.</p>
     *
     * @param orientation 1..8; anything else is treated as {@link #NORMAL}
     * @param w           source width
     * @param h           source height
     * @return the transform to draw the source through
     */
    public static AffineTransform transformFor(int orientation, int w, int h) {
        switch (orientation) {
            case 2:  // flip horizontal:  X = w-x, Y = y
                return new AffineTransform(-1, 0, 0, 1, w, 0);
            case 3:  // rotate 180:       X = w-x, Y = h-y
                return new AffineTransform(-1, 0, 0, -1, w, h);
            case 4:  // flip vertical:    X = x,   Y = h-y
                return new AffineTransform(1, 0, 0, -1, 0, h);
            case 5:  // transpose:        X = y,   Y = x
                return new AffineTransform(0, 1, 1, 0, 0, 0);
            case 6:  // rotate 90 CW:     X = h-y, Y = x
                return new AffineTransform(0, 1, -1, 0, h, 0);
            case 7:  // transverse:       X = h-y, Y = w-x
                return new AffineTransform(0, -1, -1, 0, h, w);
            case 8:  // rotate 90 CCW:    X = y,   Y = w-x
                return new AffineTransform(0, -1, 1, 0, 0, w);
            case 1:
            default:
                return new AffineTransform();
        }
    }

    /**
     * Whether this orientation exchanges width and height.
     *
     * <p>Matters for output dimensions, not for the 256px THRESHOLD decision: {@code max(w,h)} is
     * swap-invariant, but a 4000x3000 shot at orientation 6 must preview 192x256, not 256x192.</p>
     *
     * @param orientation 1..8
     * @return {@code true} for the four transposing orientations (5, 6, 7, 8)
     */
    public static boolean swapsAxes(int orientation) {
        return orientation >= 5 && orientation <= 8;
    }

    // ---- bounds-checked primitive reads -------------------------------------------------
    //
    // Every one of these returns a harmless value rather than throwing when asked to read past
    // the end. Callers above still bounds-check before looping; these are the second line.

    private static int u8(byte[] b, int i) {
        return (i < 0 || i >= b.length) ? -1 : (b[i] & 0xFF);
    }

    private static int u16be(byte[] b, int i) {
        if (i < 0 || i + 1 >= b.length) {
            return -1;
        }
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static int u16(byte[] b, int i, boolean bigEndian) {
        if (i < 0 || i + 1 >= b.length) {
            return -1;
        }
        int hi = b[i] & 0xFF;
        int lo = b[i + 1] & 0xFF;
        return bigEndian ? ((hi << 8) | lo) : ((lo << 8) | hi);
    }

    private static long u32(byte[] b, int i, boolean bigEndian) {
        if (i < 0 || i + 3 >= b.length) {
            return -1L;
        }
        long b0 = b[i] & 0xFFL;
        long b1 = b[i + 1] & 0xFFL;
        long b2 = b[i + 2] & 0xFFL;
        long b3 = b[i + 3] & 0xFFL;
        return bigEndian
                ? ((b0 << 24) | (b1 << 16) | (b2 << 8) | b3)
                : ((b3 << 24) | (b2 << 16) | (b1 << 8) | b0);
    }
}
