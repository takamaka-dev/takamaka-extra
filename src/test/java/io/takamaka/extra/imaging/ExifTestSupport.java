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

/**
 * Builds synthetic EXIF APP1 segments so orientation can be tested without committing a binary
 * fixture — and, more importantly, without depending on a photo whose provenance nobody can check.
 *
 * <p>A generated fixture is also the only way to test the HOSTILE cases: truncations, lying
 * lengths and impossible offsets do not occur in files a camera produces.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
final class ExifTestSupport {

    /** Byte length of a well-formed single-tag EXIF payload built by {@link #orientationExif}. */
    static final int WELL_FORMED_PAYLOAD_LEN = 32;

    /**
     * The shortest truncation from which the orientation is still legitimately readable.
     *
     * <p>Payload layout: 6 (Exif\0\0) + 8 (TIFF header) + 2 (IFD0 entry count) + 12 (the entry)
     * = 28. The remaining 4 bytes are the next-IFD offset, which is <em>not</em> needed to read
     * tag {@code 0x0112} out of IFD0 — so a 28-byte truncation is not malformed, it is merely
     * short, and a parser that refused it would be refusing a legal read.</p>
     */
    static final int MIN_PARSEABLE_PAYLOAD_LEN = 28;

    private ExifTestSupport() {
    }

    /**
     * A complete APP1 payload — {@code "Exif\0\0"} plus a TIFF block whose IFD0 holds exactly one
     * entry, the orientation tag.
     *
     * <p>Layout: 6 byte prefix + 8 byte TIFF header + 2 byte entry count + 12 byte entry +
     * 4 byte next-IFD offset = {@value #WELL_FORMED_PAYLOAD_LEN} bytes.</p>
     *
     * @param orientation the value to store, 1..8 (or an illegal one, to test rejection)
     * @param bigEndian   {@code true} for {@code MM}, {@code false} for {@code II}
     */
    static byte[] orientationExif(int orientation, boolean bigEndian) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write('E');
        out.write('x');
        out.write('i');
        out.write('f');
        out.write(0);
        out.write(0);

        // TIFF header
        out.write(bigEndian ? 'M' : 'I');
        out.write(bigEndian ? 'M' : 'I');
        write16(out, 0x002A, bigEndian);
        write32(out, 8, bigEndian);        // IFD0 sits immediately after this 8-byte header

        // IFD0
        write16(out, 1, bigEndian);        // one entry
        write16(out, 0x0112, bigEndian);   // tag: Orientation
        write16(out, 3, bigEndian);        // type: SHORT
        write32(out, 1, bigEndian);        // count: 1
        // A SHORT with count 1 fits the 4-byte value field, so it lives INLINE in the first two
        // bytes of it (in the file's byte order), with the remaining two bytes as padding.
        write16(out, orientation, bigEndian);
        write16(out, 0, bigEndian);
        write32(out, 0, bigEndian);        // no next IFD

        return out.toByteArray();
    }

    /**
     * A deliberately damaged EXIF payload: the well-formed one cut to {@code keep} bytes, padded
     * back out with garbage when {@code keep} exceeds its natural length.
     *
     * <p>Cuts below {@link #WELL_FORMED_PAYLOAD_LEN} truncate somewhere inside the TIFF header or
     * the IFD entry, which is what a corrupted or maliciously-crafted file looks like.</p>
     */
    static byte[] truncatedExif(int orientation, int keep) {
        byte[] full = orientationExif(orientation, false);
        byte[] out = new byte[Math.max(0, keep)];
        for (int i = 0; i < out.length; i++) {
            out[i] = i < full.length ? full[i] : (byte) 0xAA; // 0xAA: not a plausible offset
        }
        return out;
    }

    /**
     * Splice an APP1 segment into a JPEG, immediately after SOI.
     *
     * <p>The segment length is computed from the payload actually supplied, so a truncated payload
     * yields a <em>self-consistent</em> segment — the parser is then tested on malformed CONTENT
     * within valid bounds, which is the harder case. {@link #withLyingApp1Length} covers the other.</p>
     */
    static byte[] withApp1(byte[] jpeg, byte[] app1Payload) {
        return splice(jpeg, app1Payload, app1Payload.length + 2);
    }

    /**
     * Splice in an APP1 whose declared length does not match its payload — a segment claiming far
     * more bytes than the file contains.
     */
    static byte[] withLyingApp1Length(byte[] jpeg, byte[] app1Payload, int declaredLength) {
        return splice(jpeg, app1Payload, declaredLength);
    }

    private static byte[] splice(byte[] jpeg, byte[] payload, int declaredLength) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg[0]); // FF
        out.write(jpeg[1]); // D8
        out.write(0xFF);
        out.write(0xE1);
        out.write((declaredLength >> 8) & 0xFF);
        out.write(declaredLength & 0xFF);
        out.write(payload, 0, payload.length);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }

    private static void write16(ByteArrayOutputStream out, int v, boolean bigEndian) {
        if (bigEndian) {
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
        } else {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
        }
    }

    private static void write32(ByteArrayOutputStream out, int v, boolean bigEndian) {
        if (bigEndian) {
            out.write((v >> 24) & 0xFF);
            out.write((v >> 16) & 0xFF);
            out.write((v >> 8) & 0xFF);
            out.write(v & 0xFF);
        } else {
            out.write(v & 0xFF);
            out.write((v >> 8) & 0xFF);
            out.write((v >> 16) & 0xFF);
            out.write((v >> 24) & 0xFF);
        }
    }
}
