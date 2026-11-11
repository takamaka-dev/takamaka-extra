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

import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * C120 (videos) — remove the metadata an MP4/MOV/3GP container carries WITHOUT re-encoding,
 * without moving a single sample.
 *
 * <p><strong>Port</strong> of the Dart {@code tkmChat/lib/services/media_cleanup/mp4_metadata_cleaner.dart};
 * same input → byte-identical output.</p>
 *
 * <p>Sample offsets ({@code stco}/{@code co64}) are ABSOLUTE file offsets, so nothing may move:
 * every metadata box is turned into a {@code free} box of the SAME size (type rewritten, payload
 * zeroed). Blanked: {@code moov/udta}, {@code moov/meta}, {@code moov/trak/udta},
 * {@code moov/trak/meta}, every top-level {@code uuid} box (XMP), and top-level
 * {@code udta}/{@code meta}. Zeroed: creation and modification time in {@code mvhd}, {@code tkhd}
 * and {@code mdhd} (version 0 and 1 layouts). Untouched: {@code ftyp}, {@code mdat}, {@code moof},
 * {@code free}, every coding/sample-table box.</p>
 *
 * <p>Works file-to-file on a COPY (the source is never modified) through a
 * {@link RandomAccessFile}: a 200 MB video costs disk, not RAM. HEIF-family still images share
 * the {@code ftyp} signature but keep their PICTURE under the top-level {@code meta} box — they
 * are refused, never "cleaned" into a blank picture.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class Mp4MetadataCleaner {

    private Mp4MetadataCleaner() {
    }

    /** What {@link #cleanFile} did. */
    public static final class Mp4CleanResult {

        private final List<String> blanked;
        private final int timestampsZeroed;

        Mp4CleanResult(List<String> blanked, int timestampsZeroed) {
            this.blanked = Collections.unmodifiableList(blanked);
            this.timestampsZeroed = timestampsZeroed;
        }

        /** Boxes blanked, as {@code path/type@offset} strings (for the log). */
        public List<String> getBlanked() {
            return blanked;
        }

        /** Timestamp fields zeroed ({@code mvhd}, {@code tkhd}, {@code mdhd} creation/modification). */
        public int getTimestampsZeroed() {
            return timestampsZeroed;
        }

        public boolean isChanged() {
            return !blanked.isEmpty() || timestampsZeroed > 0;
        }
    }

    /** An {@code ftyp} box at offset 0. WebM/Matroska have no {@code ftyp} at all. */
    public static boolean isIsoBmff(byte[] head) {
        return head != null && head.length >= 12
                && head[4] == 0x66 && head[5] == 0x74 && head[6] == 0x79 && head[7] == 0x70; // ftyp
    }

    private static final Set<String> STILL_IMAGE_BRANDS = new HashSet<>(Arrays.asList(
            "heic", "heix", "hevc", "hevx", "heim", "heis", "hevm", "hevs", // HEIC
            "mif1", "msf1", // HEIF
            "avif", "avis", // AVIF
            "jxl ", "crx " // JPEG XL, Canon raw
    ));

    public static String majorBrand(byte[] head) {
        return head != null && head.length >= 12
                ? new String(head, 8, 4, StandardCharsets.ISO_8859_1) : "";
    }

    public static boolean isStillImageBrand(byte[] head) {
        return STILL_IMAGE_BRANDS.contains(majorBrand(head));
    }

    private static final Set<String> BLANK_IN_MOOV = new HashSet<>(Arrays.asList("udta", "meta"));
    private static final Set<String> BLANK_IN_TRAK = new HashSet<>(Arrays.asList("udta", "meta"));
    private static final Set<String> BLANK_TOP_LEVEL = new HashSet<>(Arrays.asList("uuid", "udta", "meta"));

    /**
     * Copy {@code source} to {@code out} and clean the copy in place. On any failure after the
     * copy the caller owns {@code out} and should delete it.
     *
     * @throws UnsupportedMediaFormatException not MP4/MOV/3GP, or a HEIF/AVIF still image
     * @throws MediaFormatException a box that lies about its size
     * @throws IOException an I/O failure
     */
    public static Mp4CleanResult cleanFile(Path source, Path out) throws IOException {
        final byte[] head = readHead(source, 12);
        if (!isIsoBmff(head)) {
            throw new UnsupportedMediaFormatException("no ftyp box: not MP4/MOV/3GP — refusing to guess");
        }
        if (isStillImageBrand(head)) {
            throw new UnsupportedMediaFormatException("ftyp brand " + majorBrand(head)
                    + " is a still image (HEIF/AVIF), not a video — its meta box IS the picture; refusing");
        }
        Files.copy(source, out, StandardCopyOption.REPLACE_EXISTING);
        final List<String> blanked = new ArrayList<>();
        final int[] stamps = {0};
        try (RandomAccessFile raf = new RandomAccessFile(out.toFile(), "rw")) {
            walk(raf, 0L, raf.length(), "", blanked, stamps);
        }
        return new Mp4CleanResult(blanked, stamps[0]);
    }

    static byte[] readHead(Path f, int n) throws IOException {
        try (InputStream in = Files.newInputStream(f)) {
            return in.readNBytes(n);
        }
    }

    /** Visit the boxes in [start, end), blanking / zeroing as it goes. */
    private static void walk(RandomAccessFile raf, long start, long end, String path,
            List<String> blanked, int[] stamps) throws IOException {
        long pos = start;
        final byte[] hdr = new byte[8];
        while (pos + 8 <= end) {
            raf.seek(pos);
            if (readFully(raf, hdr) < 8) {
                break;
            }
            long size = u32(hdr, 0);
            final String type = new String(hdr, 4, 4, StandardCharsets.ISO_8859_1);
            int headerLen = 8;
            if (size == 1) {
                final byte[] big = new byte[8];
                if (readFully(raf, big) < 8) {
                    break;
                }
                size = (u32(big, 0) << 32) | u32(big, 4);
                headerLen = 16;
            } else if (size == 0) {
                size = end - pos; // to end of file
            }
            // (the subtraction form cannot overflow on a 64-bit size close to Long.MAX_VALUE)
            if (size < headerLen || size > end - pos) {
                throw new MediaFormatException("box " + type + " at " + pos + " claims " + size
                        + " bytes (container ends at " + end + ")");
            }
            if (visit(raf, type, path, pos, headerLen, size, blanked, stamps)) {
                final String childPath = path.isEmpty() ? type : path + "/" + type;
                walk(raf, pos + headerLen, pos + size, childPath, blanked, stamps);
            }
            pos += size;
        }
    }

    /** Returns true to descend into the box. */
    private static boolean visit(RandomAccessFile raf, String type, String path, long offset,
            int headerLen, long size, List<String> blanked, int[] stamps) throws IOException {
        if (path.isEmpty() && BLANK_TOP_LEVEL.contains(type)) {
            blank(raf, offset, headerLen, size);
            blanked.add(type + "@" + offset);
            return false;
        }
        if (path.equals("moov") && BLANK_IN_MOOV.contains(type)) {
            blank(raf, offset, headerLen, size);
            blanked.add("moov/" + type + "@" + offset);
            return false;
        }
        if (path.equals("moov/trak") && BLANK_IN_TRAK.contains(type)) {
            blank(raf, offset, headerLen, size);
            blanked.add("moov/trak/" + type + "@" + offset);
            return false;
        }
        if ((type.equals("mvhd") && path.equals("moov"))
                || (type.equals("tkhd") && path.equals("moov/trak"))
                || (type.equals("mdhd") && path.equals("moov/trak/mdia"))) {
            stamps[0] += zeroTimes(raf, offset + headerLen, size - headerLen);
            return false;
        }
        return type.equals("moov") || type.equals("trak") || type.equals("mdia");
    }

    /** Rewrite the type to {@code free} and zero the payload, chunked. */
    private static void blank(RandomAccessFile raf, long offset, int headerLen, long size) throws IOException {
        raf.seek(offset + 4);
        raf.write(new byte[]{0x66, 0x72, 0x65, 0x65}); // free
        long remaining = size - headerLen;
        raf.seek(offset + headerLen);
        final byte[] zeros = new byte[64 * 1024];
        while (remaining > 0) {
            final int n = (int) Math.min(remaining, zeros.length);
            raf.write(zeros, 0, n);
            remaining -= n;
        }
    }

    /**
     * Zero creation_time and modification_time of a full box whose payload starts at
     * {@code payload}: version(1) flags(3) then two timestamps of 4 bytes (version 0) or 8 bytes
     * (version 1). Returns how many of the two were non-zero.
     */
    private static int zeroTimes(RandomAccessFile raf, long payload, long payloadLen) throws IOException {
        if (payloadLen < 4) {
            return 0;
        }
        raf.seek(payload);
        final byte[] vf = new byte[4];
        if (readFully(raf, vf) < 4) {
            return 0;
        }
        final int width = vf[0] == 1 ? 8 : 4;
        if (payloadLen < 4 + 2 * width) {
            return 0;
        }
        raf.seek(payload + 4);
        final byte[] current = new byte[2 * width];
        final int got = readFully(raf, current);
        int nonZero = 0;
        for (int i = 0; i < 2; i++) {
            for (int k = i * width; k < (i + 1) * width && k < got; k++) {
                if (current[k] != 0) {
                    nonZero++;
                    break;
                }
            }
        }
        if (nonZero == 0) {
            return 0; // already zero: not a change
        }
        raf.seek(payload + 4);
        raf.write(new byte[2 * width]);
        return nonZero;
    }

    /** Read up to {@code buf.length} bytes; returns the count actually read (short at EOF). */
    private static int readFully(RandomAccessFile raf, byte[] buf) throws IOException {
        int total = 0;
        while (total < buf.length) {
            final int n = raf.read(buf, total, buf.length - total);
            if (n < 0) {
                break;
            }
            total += n;
        }
        return total;
    }

    private static long u32(byte[] b, int i) {
        return ((long) (b[i] & 0xFF) << 24) | ((b[i + 1] & 0xFF) << 16) | ((b[i + 2] & 0xFF) << 8)
                | (b[i + 3] & 0xFF);
    }
}
