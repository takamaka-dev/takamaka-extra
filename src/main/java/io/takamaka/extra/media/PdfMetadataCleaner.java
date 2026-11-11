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

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * C120 (documents) — remove the metadata a PDF carries about its author and its making,
 * LENGTH-PRESERVING so the cross-reference table stays valid and no object moves.
 *
 * <p><strong>Port</strong> of the Dart {@code tkmChat/lib/services/media_cleanup/pdf_metadata_cleaner.dart};
 * same input → byte-identical output.</p>
 *
 * <ul>
 * <li>every Info value ({@code /Author}, {@code /Creator}, {@code /Producer}, {@code /CreationDate},
 * {@code /ModDate}, {@code /Subject}, {@code /Keywords}) is overwritten with spaces (literal
 * strings) or {@code 2}s (hex strings) of the same byte length — {@code /Title} is KEPT, it is
 * content;</li>
 * <li>every {@code /Metadata N G R} reference is overwritten with spaces and the referenced
 * stream's bytes are ZEROED;</li>
 * <li>every uncompressed {@code <?xpacket begin … <?xpacket end="w"?>} block is blanked, wherever
 * it sits.</li>
 * </ul>
 *
 * <p>Nothing is parsed as a PDF: this is a byte-level pass (each byte is one Latin-1 character).</p>
 *
 * <h2>Why the three patterns are matched by hand</h2>
 *
 * <p>The Dart side uses three ECMAScript regular expressions. {@code java.util.regex} differs
 * from them exactly where a PDF can hurt: {@code \s} does not include U+00A0 in Java, {@code \b}
 * treats Latin-1 letters as word characters, and a {@code (?:\\.|[^\\)])*} loop recurses once per
 * character (a long literal string is a {@code StackOverflowError}). Each pattern is unambiguous
 * (no alternative overlaps another), so a deterministic scanner matches exactly what the
 * backtracking engine matches; the scanners below are those.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class PdfMetadataCleaner {

    private PdfMetadataCleaner() {
    }

    /** What {@link #clean} did. */
    public static final class PdfCleanResult {

        private final byte[] bytes;
        private final int infoValuesBlanked;
        private final int metadataRefsBlanked;
        private final int metadataStreamsZeroed;
        private final int xpacketsBlanked;

        PdfCleanResult(byte[] bytes, int info, int refs, int streams, int packets) {
            this.bytes = bytes;
            this.infoValuesBlanked = info;
            this.metadataRefsBlanked = refs;
            this.metadataStreamsZeroed = streams;
            this.xpacketsBlanked = packets;
        }

        /** A copy of the input with the metadata blanked — always the same length. */
        public byte[] getBytes() {
            return bytes;
        }

        /** Info-dictionary values blanked ({@code /Author}, {@code /CreationDate}, …). */
        public int getInfoValuesBlanked() {
            return infoValuesBlanked;
        }

        /** {@code /Metadata N G R} references blanked. */
        public int getMetadataRefsBlanked() {
            return metadataRefsBlanked;
        }

        /** XMP streams zeroed. */
        public int getMetadataStreamsZeroed() {
            return metadataStreamsZeroed;
        }

        /** Uncompressed {@code <?xpacket …?>} blocks blanked wherever they sat. */
        public int getXpacketsBlanked() {
            return xpacketsBlanked;
        }

        public boolean isChanged() {
            return infoValuesBlanked + metadataRefsBlanked + metadataStreamsZeroed + xpacketsBlanked > 0;
        }
    }

    public static boolean isPdf(byte[] b) {
        return b != null && b.length >= 5
                && b[0] == 0x25 && b[1] == 0x50 && b[2] == 0x44 && b[3] == 0x46 && b[4] == 0x2D; // %PDF-
    }

    private static final byte[][] INFO_KEYS = {
        ascii("Author"), ascii("Creator"), ascii("Producer"), ascii("CreationDate"),
        ascii("ModDate"), ascii("Subject"), ascii("Keywords")
    };
    private static final byte[] METADATA = ascii("/Metadata");
    private static final byte[] OBJ = ascii("obj");
    private static final byte[] STREAM = ascii("stream");
    private static final byte[] ENDOBJ = ascii("endobj");
    private static final byte[] ENDSTREAM = ascii("endstream");
    private static final byte[] XPACKET_BEGIN = ascii("<?xpacket begin=");
    private static final byte[] XPACKET_END = ascii("<?xpacket end=");

    public static PdfCleanResult clean(byte[] input) {
        final byte[] in = input; // matched against, never written
        final byte[] out = input.clone();
        final int n = in.length;
        int info = 0;
        int refs = 0;
        int streams = 0;
        int packets = 0;

        // /(Author|…|Keywords)\s*(\((?:\\.|[^\\)])*\)|<[0-9A-Fa-f\s]*>)
        for (int i = 0; i < n;) {
            if (in[i] != '/') {
                i++;
                continue;
            }
            final int[] m = matchInfoValue(in, i);
            if (m == null) {
                i++;
                continue;
            }
            final int start = m[0];
            final int end = m[1];
            final byte fill = in[start] == '(' ? (byte) 0x20 : (byte) 0x32; // ' ' or '2'
            boolean touched = false;
            for (int k = start + 1; k < end - 1; k++) {
                if (fill == 0x32 && (out[k] == 0x0A || out[k] == 0x0D || out[k] == 0x20)) {
                    continue;
                }
                if (out[k] != fill) {
                    out[k] = fill;
                    touched = true;
                }
            }
            if (touched) {
                info++; // an already-blank value is not a change
            }
            i = end;
        }

        // /Metadata\s+(\d+)\s+(\d+)\s+R
        final Set<String> metadataObjects = new LinkedHashSet<>();
        for (int i = 0; i < n;) {
            if (in[i] != '/' || !regionMatches(in, i, METADATA)) {
                i++;
                continue;
            }
            int p = i + METADATA.length;
            final int ws1 = skipWs(in, p);
            final int d1 = skipDigits(in, ws1);
            final int ws2 = skipWs(in, d1);
            final int d2 = skipDigits(in, ws2);
            final int ws3 = skipWs(in, d2);
            if (ws1 == p || d1 == ws1 || ws2 == d1 || d2 == ws2 || ws3 == d2 || ws3 >= n || in[ws3] != 'R') {
                i++;
                continue;
            }
            final int end = ws3 + 1;
            metadataObjects.add(latin1(in, ws1, d1) + " " + latin1(in, ws2, d2));
            for (int k = i; k < end; k++) {
                out[k] = 0x20;
            }
            refs++;
            i = end;
        }

        // (?<![0-9])<id>\s+obj\b  for every referenced "N G"
        for (String id : metadataObjects) {
            final byte[] idBytes = id.getBytes(StandardCharsets.ISO_8859_1);
            for (int s = 0; s + idBytes.length <= n;) {
                if (!regionMatches(in, s, idBytes) || (s > 0 && isDigit(in[s - 1]))) {
                    s++;
                    continue;
                }
                final int p = s + idBytes.length;
                final int ws = skipWs(in, p);
                if (ws == p || !regionMatches(in, ws, OBJ)
                        || (ws + OBJ.length < n && isWordChar(in[ws + OBJ.length]))) {
                    s++;
                    continue;
                }
                final int oEnd = ws + OBJ.length;
                s = oEnd;

                final int streamKw = indexOf(in, STREAM, oEnd);
                final int endobj = indexOf(in, ENDOBJ, oEnd);
                if (streamKw < 0 || (endobj >= 0 && streamKw > endobj)) {
                    continue;
                }
                int dataStart = streamKw + STREAM.length;
                if (dataStart + 1 < n && in[dataStart] == '\r' && in[dataStart + 1] == '\n') {
                    dataStart += 2;
                } else if (dataStart < n && in[dataStart] == '\n') {
                    dataStart += 1;
                }
                final int endstream = indexOf(in, ENDSTREAM, dataStart);
                if (endstream < 0) {
                    continue;
                }
                for (int k = dataStart; k < endstream; k++) {
                    out[k] = 0x00;
                }
                streams++;
            }
        }

        // <\?xpacket begin=.*?<\?xpacket end=[^>]*\?>   (dotAll)
        for (int i = 0; i < n;) {
            if (in[i] != '<' || !regionMatches(in, i, XPACKET_BEGIN)) {
                i++;
                continue;
            }
            final int end = matchXpacketEnd(in, i + XPACKET_BEGIN.length);
            if (end < 0) {
                i++;
                continue;
            }
            for (int k = i; k < end; k++) {
                if (out[k] != 0x00) {
                    out[k] = 0x20; // keep zeroed regions zero
                }
            }
            packets++;
            i = end;
        }

        return new PdfCleanResult(out, info, refs, streams, packets);
    }

    /**
     * Match the Info-value pattern at {@code slash} (which holds '/'). Returns
     * {@code {valueStart, matchEnd}} or {@code null}.
     */
    private static int[] matchInfoValue(byte[] in, int slash) {
        final int n = in.length;
        for (byte[] key : INFO_KEYS) {
            if (!regionMatches(in, slash + 1, key)) {
                continue;
            }
            // No key is a prefix of another, so at most one can match here.
            final int v = skipWs(in, slash + 1 + key.length);
            if (v >= n) {
                return null;
            }
            if (in[v] == '(') {
                // \((?:\\.|[^\\)])*\)
                int q = v + 1;
                while (true) {
                    if (q >= n) {
                        return null;
                    }
                    final byte c = in[q];
                    if (c == '\\') {
                        if (q + 1 >= n) {
                            return null;
                        }
                        q += 2;
                    } else if (c == ')') {
                        return new int[]{v, q + 1};
                    } else {
                        q++;
                    }
                }
            }
            if (in[v] == '<') {
                // <[0-9A-Fa-f\s]*>
                int q = v + 1;
                while (q < n && (isHex(in[q]) || isWs(in[q]))) {
                    q++;
                }
                return q < n && in[q] == '>' ? new int[]{v, q + 1} : null;
            }
            return null;
        }
        return null;
    }

    /**
     * From {@code from} (just after {@code <?xpacket begin=}), the end of the SHORTEST
     * {@code .*?<\?xpacket end=[^>]*\?>}; -1 when there is none.
     */
    private static int matchXpacketEnd(byte[] in, int from) {
        int search = from;
        while (true) {
            final int e = indexOf(in, XPACKET_END, search);
            if (e < 0) {
                return -1;
            }
            final int a = e + XPACKET_END.length;
            int g = a;
            while (g < in.length && in[g] != '>') {
                g++;
            }
            if (g >= in.length) {
                return -1; // no '>' after this candidate, so none after any later one
            }
            // [^>]* cannot cross a '>', so the "?>" must end at the FIRST '>' after "end=".
            if (g - 1 >= a && in[g - 1] == '?') {
                return g + 1;
            }
            search = e + 1;
        }
    }

    // ---- ECMAScript character classes, restricted to one byte ----------------------------

    /** ECMAScript {@code \s} within Latin-1: TAB LF VT FF CR SPACE and NBSP (U+00A0). */
    private static boolean isWs(byte b) {
        final int c = b & 0xFF;
        return (c >= 0x09 && c <= 0x0D) || c == 0x20 || c == 0xA0;
    }

    private static boolean isDigit(byte b) {
        return b >= '0' && b <= '9';
    }

    private static boolean isHex(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'A' && b <= 'F') || (b >= 'a' && b <= 'f');
    }

    /** ECMAScript {@code \w}: ASCII letters, digits, underscore. */
    private static boolean isWordChar(byte b) {
        return (b >= '0' && b <= '9') || (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z') || b == '_';
    }

    private static int skipWs(byte[] in, int p) {
        while (p < in.length && isWs(in[p])) {
            p++;
        }
        return p;
    }

    private static int skipDigits(byte[] in, int p) {
        while (p < in.length && isDigit(in[p])) {
            p++;
        }
        return p;
    }

    private static boolean regionMatches(byte[] in, int at, byte[] what) {
        if (at < 0 || at + what.length > in.length) {
            return false;
        }
        for (int i = 0; i < what.length; i++) {
            if (in[at + i] != what[i]) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(byte[] in, byte[] what, int from) {
        final byte first = what[0];
        for (int i = Math.max(from, 0); i + what.length <= in.length; i++) {
            if (in[i] == first && regionMatches(in, i, what)) {
                return i;
            }
        }
        return -1;
    }

    private static String latin1(byte[] in, int from, int to) {
        return new String(in, from, to - from, StandardCharsets.ISO_8859_1);
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }
}
