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
package io.takamaka.extra.media;

import io.takamaka.extra.imaging.ExifOrientation;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * C120 — remove the metadata a PHOTO carries (GPS position, capture time, camera, XMP, IPTC,
 * comments) without touching a single pixel.
 *
 * <p><strong>Line-for-line port</strong> of the Dart
 * {@code wallet-extra-flutter/lib/src/imaging/image_location_stripper.dart}: for the same input
 * bytes the two produce byte-identical output (pinned by {@code java_metadata_clean_vectors.json}).</p>
 *
 * <ul>
 * <li><strong>JPEG</strong> — re-assembled segment by segment. Kept: SOI, APP0 (JFIF), APP2
 * {@code ICC_PROFILE}, APP14 {@code Adobe}, every coding segment and the entropy-coded data.
 * Dropped: every other APPn (EXIF, XMP, IPTC, MPF, vendor blobs) and every COM. Anything after
 * the EOI is cut off. The orientation is read from the source and re-emitted as a minimal
 * orientation-only EXIF block, so a portrait photo stays upright.</li>
 * <li><strong>PNG</strong> — chunks are copied through IEND except {@code eXIf}, {@code tEXt},
 * {@code zTXt}, {@code iTXt}; an {@code eXIf} whose orientation is not 1 is replaced by a minimal
 * one holding only that tag.</li>
 * <li><strong>GIF</strong> — blocks are copied through the trailer except Comment extensions and
 * every Application extension other than {@code NETSCAPE2.0} / {@code ANIMEXTS1.0}.</li>
 * <li><strong>Anything else</strong> throws {@link UnsupportedMediaFormatException}.</li>
 * </ul>
 *
 * <p>No decode, no re-encode: the pixel bytes are byte-identical to the input.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class ImageLocationStripper {

    public static final String MIME_JPEG = "image/jpeg";
    public static final String MIME_PNG = "image/png";
    public static final String MIME_GIF = "image/gif";

    private ImageLocationStripper() {
    }

    /** What {@link #strip} produced. */
    public static final class StrippedImage {

        private final byte[] bytes;
        private final String mediaType;
        private final boolean changed;
        private final int orientation;

        StrippedImage(byte[] bytes, String mediaType, boolean changed, int orientation) {
            this.bytes = bytes;
            this.mediaType = mediaType;
            this.changed = changed;
            this.orientation = orientation;
        }

        /** The image with its metadata removed; the INPUT ARRAY ITSELF when nothing changed. */
        public byte[] getBytes() {
            return bytes;
        }

        /** {@code image/jpeg}, {@code image/png} or {@code image/gif}. */
        public String getMediaType() {
            return mediaType;
        }

        /** Whether anything was actually dropped. */
        public boolean isChanged() {
            return changed;
        }

        /** The EXIF orientation (1..8) the image carried and that was PRESERVED; 1 when absent. */
        public int getOrientation() {
            return orientation;
        }
    }

    /** Whether {@link #strip} accepts these bytes at all (by magic number). */
    public static boolean isSupported(byte[] bytes) {
        return bytes != null && (isJpeg(bytes) || isPng(bytes) || isGif(bytes));
    }

    /** The file extension (with the dot) for a {@link StrippedImage#getMediaType()}. */
    public static String extensionFor(String mediaType) {
        if (MIME_PNG.equals(mediaType)) {
            return ".png";
        }
        if (MIME_GIF.equals(mediaType)) {
            return ".gif";
        }
        return ".jpg";
    }

    /**
     * Strip metadata from a JPEG, PNG or GIF.
     *
     * @throws UnsupportedMediaFormatException for any other format
     * @throws MediaFormatException for a file whose structure cannot be walked
     */
    public static StrippedImage strip(byte[] bytes) {
        if (bytes != null) {
            if (isJpeg(bytes)) {
                return stripJpeg(bytes);
            }
            if (isPng(bytes)) {
                return stripPng(bytes);
            }
            if (isGif(bytes)) {
                return stripGif(bytes);
            }
        }
        throw new UnsupportedMediaFormatException(
                "only JPEG, PNG and GIF can be stripped; refusing to guess for this format");
    }

    // ---------------------------------------------------------------------
    // JPEG
    // ---------------------------------------------------------------------

    private static boolean isJpeg(byte[] b) {
        return b.length >= 4 && u8(b, 0) == 0xFF && u8(b, 1) == 0xD8 && u8(b, 2) == 0xFF;
    }

    private static final int SOI = 0xD8;
    private static final int EOI = 0xD9;
    private static final int SOS = 0xDA;
    private static final int APP0 = 0xE0;
    private static final int APP1 = 0xE1;
    private static final int APP2 = 0xE2;
    private static final int APP14 = 0xEE;
    private static final int APP15 = 0xEF;
    private static final int COM = 0xFE;

    private static final byte[] ICC_PROFILE_SIG = "ICC_PROFILE\0".getBytes(StandardCharsets.ISO_8859_1);
    private static final byte[] ADOBE_SIG = "Adobe".getBytes(StandardCharsets.ISO_8859_1);

    private static StrippedImage stripJpeg(byte[] src) {
        final int orientation = ExifOrientation.read(src);
        final ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        boolean changed = false;
        boolean wroteOrientation = false;

        out.write(0xFF);
        out.write(SOI);
        int pos = 2;

        // Header segments up to the first SOS.
        while (true) {
            if (pos + 4 > src.length) {
                throw new MediaFormatException("JPEG ends before the first scan");
            }
            if (u8(src, pos) != 0xFF) {
                throw new MediaFormatException("JPEG desynchronised at offset " + pos);
            }
            final int marker = u8(src, pos + 1);
            if (marker == 0xFF) {
                pos++; // fill byte
                continue;
            }
            if (marker == SOI || (marker >= 0xD0 && marker <= 0xD7)) {
                pos += 2; // standalone marker, no length field
                continue;
            }
            if (marker == EOI) {
                throw new MediaFormatException("JPEG has no scan before its EOI");
            }
            final int len = u16be(src, pos + 2);
            if (len < 2 || pos + 2 + len > src.length) {
                throw new MediaFormatException("JPEG segment 0x" + Integer.toHexString(marker)
                        + " has an impossible length " + len + " at offset " + pos);
            }
            final int segStart = pos;
            final int segEnd = pos + 2 + len;

            if (marker == SOS) {
                // The pixels start here. Before them, the one metadata tag we keep.
                if (orientation != ExifOrientation.NORMAL && !wroteOrientation) {
                    write(out, orientationOnlyApp1(orientation));
                    wroteOrientation = true;
                }
                pos = copyScanData(src, segStart, out);
                break;
            }

            if (isMetadataSegment(marker, src, pos + 4, segEnd)) {
                changed = true;
                // Re-emit the orientation in place of the EXIF block, where it was.
                if (marker == APP1 && orientation != ExifOrientation.NORMAL && !wroteOrientation) {
                    write(out, orientationOnlyApp1(orientation));
                    wroteOrientation = true;
                }
            } else {
                out.write(src, segStart, segEnd - segStart);
            }
            pos = segEnd;
        }

        if (pos < src.length || u8(src, src.length - 1) != EOI || u8(src, src.length - 2) != 0xFF) {
            changed = true; // trailer after EOI cut off, or a missing EOI supplied
        }

        return new StrippedImage(changed ? out.toByteArray() : src, MIME_JPEG, changed, orientation);
    }

    /** Everything that is ABOUT the photo rather than the photo: dropped. */
    private static boolean isMetadataSegment(int marker, byte[] src, int payloadStart, int payloadEnd) {
        if (marker == COM) {
            return true;
        }
        if (marker < APP0 || marker > APP15) {
            return false; // coding segment
        }
        if (marker == APP0) {
            return false; // JFIF
        }
        if (marker == APP2 && startsWith(src, payloadStart, payloadEnd, ICC_PROFILE_SIG)) {
            return false;
        }
        if (marker == APP14 && startsWith(src, payloadStart, payloadEnd, ADOBE_SIG)) {
            return false;
        }
        return true; // EXIF, XMP, IPTC, MPF, vendor blobs, unknown APPn
    }

    /**
     * Copy from the SOS at {@code sosStart} through the EOI, walking the entropy-coded data
     * properly (a progressive JPEG has several scans with DHT/DNL segments between them). Returns
     * the offset just after the EOI.
     */
    private static int copyScanData(byte[] src, int sosStart, ByteArrayOutputStream out) {
        int pos = sosStart;
        while (true) {
            // At a marker: the EOI (2 bytes) or one with a length field.
            if (pos + 2 > src.length || u8(src, pos) != 0xFF) {
                throw new MediaFormatException("JPEG scan structure broken at offset " + pos);
            }
            final int marker = u8(src, pos + 1);
            if (marker == EOI) {
                out.write(0xFF);
                out.write(EOI);
                return pos + 2;
            }
            if (pos + 4 > src.length) {
                throw new MediaFormatException("JPEG scan structure broken at offset " + pos);
            }
            final int len = u16be(src, pos + 2);
            if (len < 2 || pos + 2 + len > src.length) {
                throw new MediaFormatException("JPEG segment in scan area has an impossible length "
                        + len + " at offset " + pos);
            }
            final int segEnd = pos + 2 + len;
            out.write(src, pos, segEnd - pos);
            pos = segEnd;
            if (marker != SOS) {
                continue;
            }

            // Entropy-coded data: FF is followed by 00 (stuffed) or D0..D7 (RSTn); anything else
            // is the next real marker.
            final int dataStart = pos;
            boolean truncated = false;
            while (true) {
                if (pos + 1 >= src.length) {
                    truncated = true;
                    break;
                }
                if (u8(src, pos) == 0xFF) {
                    final int next = u8(src, pos + 1);
                    if (next == 0x00 || (next >= 0xD0 && next <= 0xD7) || next == 0xFF) {
                        pos += next == 0xFF ? 1 : 2;
                        continue;
                    }
                    break; // a real marker
                }
                pos++;
            }
            if (truncated) {
                // No EOI at all — a truncated photo. Keep what there is; decoders tolerate it,
                // and it is what the user picked.
                out.write(src, dataStart, src.length - dataStart);
                out.write(0xFF);
                out.write(EOI);
                return src.length;
            }
            out.write(src, dataStart, pos - dataStart);
        }
    }

    /** A minimal APP1: {@code Exif\0\0} then {@link #orientationOnlyTiff}. 36 bytes with the marker. */
    static byte[] orientationOnlyApp1(int orientation) {
        final byte[] tiff = orientationOnlyTiff(orientation);
        final int len = 2 + 6 + tiff.length;
        final byte[] b = new byte[4 + 6 + tiff.length];
        b[0] = (byte) 0xFF;
        b[1] = (byte) APP1;
        b[2] = (byte) ((len >> 8) & 0xFF);
        b[3] = (byte) (len & 0xFF);
        b[4] = 0x45; // E
        b[5] = 0x78; // x
        b[6] = 0x69; // i
        b[7] = 0x66; // f
        // b[8], b[9] = 0
        System.arraycopy(tiff, 0, b, 10, tiff.length);
        return b;
    }

    /**
     * A big-endian TIFF: header, IFD0 with the single Orientation entry, no next IFD. 26 bytes.
     * The body of both the JPEG APP1 and the PNG eXIf.
     */
    static byte[] orientationOnlyTiff(int orientation) {
        return new byte[]{
            0x4D, 0x4D, // MM
            0x00, 0x2A,
            0, 0, 0, 8, // IFD0 right after the header
            0, 1, // one entry
            0x01, 0x12, // Orientation
            0, 3, // SHORT
            0, 0, 0, 1, // count
            (byte) ((orientation >> 8) & 0xFF), (byte) (orientation & 0xFF), // value, left-justified
            0, 0,
            0, 0, 0, 0 // next IFD: none
        };
    }

    // ---------------------------------------------------------------------
    // PNG
    // ---------------------------------------------------------------------

    private static final byte[] PNG_SIG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static boolean isPng(byte[] b) {
        return startsWith(b, 0, b.length, PNG_SIG);
    }

    /**
     * Chunk types dropped from a PNG. {@code eXIf} is the EXIF block (GPS lives there); the three
     * text chunks carry XMP ({@code iTXt}, GPS again), software, author, comments.
     */
    private static final Set<String> PNG_DROPPED_CHUNKS
            = new HashSet<>(Arrays.asList("eXIf", "tEXt", "zTXt", "iTXt"));

    private static StrippedImage stripPng(byte[] src) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        boolean changed = false;
        int orientation = ExifOrientation.NORMAL;
        write(out, PNG_SIG);
        int pos = 8;
        while (true) {
            if (pos + 8 > src.length) {
                throw new MediaFormatException("PNG ends before IEND");
            }
            final long len = u32be(src, pos);
            final long endL = pos + 12L + len;
            if (endL > src.length) {
                throw new MediaFormatException("PNG chunk at offset " + pos + " claims " + len
                        + " bytes past the end");
            }
            final int end = (int) endL;
            final String type = new String(src, pos + 4, 4, StandardCharsets.ISO_8859_1);
            if (PNG_DROPPED_CHUNKS.contains(type)) {
                changed = true;
                if (type.equals("eXIf") && orientation == ExifOrientation.NORMAL) {
                    // The chunk is a bare TIFF. Keep its orientation — and only that — where the
                    // chunk was, so a rotated PNG stays upright.
                    orientation = ExifOrientation.readTiff(src, pos + 8, end - 4);
                    if (orientation != ExifOrientation.NORMAL) {
                        write(out, pngChunk("eXIf", orientationOnlyTiff(orientation)));
                    }
                }
            } else {
                out.write(src, pos, end - pos);
            }
            pos = end;
            if (type.equals("IEND")) {
                break;
            }
        }
        if (pos < src.length) {
            changed = true; // trailer after IEND — cut off
        }
        return new StrippedImage(changed ? out.toByteArray() : src, MIME_PNG, changed, orientation);
    }

    /** length + type + data + CRC32(type + data). */
    private static byte[] pngChunk(String type, byte[] data) {
        final byte[] t = type.getBytes(StandardCharsets.ISO_8859_1);
        final java.util.zip.CRC32 crc32 = new java.util.zip.CRC32();
        crc32.update(t, 0, t.length);
        crc32.update(data, 0, data.length);
        final long crc = crc32.getValue();
        final int len = data.length;
        final byte[] b = new byte[12 + len];
        b[0] = (byte) ((len >> 24) & 0xFF);
        b[1] = (byte) ((len >> 16) & 0xFF);
        b[2] = (byte) ((len >> 8) & 0xFF);
        b[3] = (byte) (len & 0xFF);
        System.arraycopy(t, 0, b, 4, 4);
        System.arraycopy(data, 0, b, 8, len);
        b[8 + len] = (byte) ((crc >> 24) & 0xFF);
        b[9 + len] = (byte) ((crc >> 16) & 0xFF);
        b[10 + len] = (byte) ((crc >> 8) & 0xFF);
        b[11 + len] = (byte) (crc & 0xFF);
        return b;
    }

    // ---------------------------------------------------------------------
    // GIF
    // ---------------------------------------------------------------------

    private static boolean isGif(byte[] b) {
        return b.length >= 13
                && b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x38 // GIF8
                && (b[4] == 0x37 || b[4] == 0x39) && b[5] == 0x61; // 7a / 9a
    }

    private static final int GIF_EXTENSION = 0x21;
    private static final int GIF_IMAGE = 0x2C;
    private static final int GIF_TRAILER = 0x3B;
    private static final int GIF_COMMENT = 0xFE;
    private static final int GIF_APPLICATION = 0xFF;

    /** The loop-control application extensions every animated GIF needs. */
    private static final Set<String> GIF_KEPT_APPLICATIONS
            = new HashSet<>(Arrays.asList("NETSCAPE2.0", "ANIMEXTS1.0"));

    private static StrippedImage stripGif(byte[] src) {
        final ByteArrayOutputStream out = new ByteArrayOutputStream(src.length);
        boolean changed = false;
        // Header (6) + logical screen descriptor (7) + optional global colour table.
        int pos = 13;
        final int packed = u8(src, 10);
        if ((packed & 0x80) != 0) {
            pos += 3 * (2 << (packed & 0x07));
        }
        if (pos > src.length) {
            throw new MediaFormatException("GIF ends inside its colour table");
        }
        out.write(src, 0, pos);

        while (true) {
            if (pos >= src.length) {
                out.write(GIF_TRAILER); // truncated: supply the trailer
                changed = true;
                break;
            }
            final int block = u8(src, pos);
            if (block == GIF_TRAILER) {
                out.write(GIF_TRAILER);
                pos++;
                break;
            }
            if (block == GIF_IMAGE) {
                // Descriptor (10) + optional local colour table + LZW min code size + data sub-blocks.
                if (pos + 10 > src.length) {
                    throw new MediaFormatException("GIF image descriptor truncated");
                }
                int p = pos + 10;
                final int lp = u8(src, pos + 9);
                if ((lp & 0x80) != 0) {
                    p += 3 * (2 << (lp & 0x07));
                }
                p += 1; // LZW minimum code size
                p = gifSkipSubBlocks(src, p);
                out.write(src, pos, p - pos);
                pos = p;
                continue;
            }
            if (block == GIF_EXTENSION) {
                if (pos + 2 > src.length) {
                    throw new MediaFormatException("GIF extension truncated");
                }
                final int label = u8(src, pos + 1);
                final int end = gifSkipSubBlocks(src, pos + 2);
                final boolean keep;
                if (label == GIF_COMMENT) {
                    keep = false;
                } else if (label == GIF_APPLICATION) {
                    keep = GIF_KEPT_APPLICATIONS.contains(gifApplicationId(src, pos + 2));
                } else {
                    keep = true; // graphic control, plain text
                }
                if (keep) {
                    out.write(src, pos, end - pos);
                } else {
                    changed = true;
                }
                pos = end;
                continue;
            }
            throw new MediaFormatException("GIF: unknown block 0x" + Integer.toHexString(block)
                    + " at offset " + pos);
        }
        if (pos < src.length) {
            changed = true; // bytes after the trailer — cut off
        }
        return new StrippedImage(changed ? out.toByteArray() : src, MIME_GIF, changed,
                ExifOrientation.NORMAL);
    }

    /**
     * The 11-byte identifier+auth code of an application extension whose first sub-block starts
     * at {@code p}; "" when malformed (→ dropped).
     */
    private static String gifApplicationId(byte[] src, int p) {
        if (p + 12 > src.length || u8(src, p) != 11) {
            return "";
        }
        return new String(src, p + 1, 11, StandardCharsets.ISO_8859_1);
    }

    /** Skip GIF data sub-blocks (length byte + data, ended by a 0 length). */
    private static int gifSkipSubBlocks(byte[] src, int p) {
        while (true) {
            if (p >= src.length) {
                throw new MediaFormatException("GIF sub-blocks run past the end");
            }
            final int n = u8(src, p);
            p += 1 + n;
            if (n == 0) {
                return p;
            }
        }
    }

    // ---------------------------------------------------------------------

    private static void write(ByteArrayOutputStream out, byte[] b) {
        out.write(b, 0, b.length);
    }

    private static boolean startsWith(byte[] b, int from, int to, byte[] prefix) {
        if (to - from < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (b[from + i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16be(byte[] b, int i) {
        return ((b[i] & 0xFF) << 8) | (b[i + 1] & 0xFF);
    }

    private static long u32be(byte[] b, int i) {
        return ((long) (b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8)
                | (b[i + 3] & 0xFF);
    }
}
