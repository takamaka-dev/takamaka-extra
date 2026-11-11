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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static io.takamaka.extra.media.SyntheticMedia.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C120 (documents) — {@link PdfMetadataCleaner}: author/dates/XMP gone, Title kept, every byte
 * offset preserved so the xref still resolves. Port of tkmChat
 * {@code test/services/media_cleanup/pdf_metadata_cleaner_test.dart}, plus the cases that pin the
 * hand-written scanners to the ECMAScript regular expressions of the Dart side.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class PdfMetadataCleanerTest {

    private static String text(byte[] b) {
        return new String(b, StandardCharsets.ISO_8859_1);
    }

    @Test
    @DisplayName("Info values blanked, Title kept, XMP reference + stream gone, same length")
    public void infoAndXmp() {
        byte[] src = pdf();
        String in = text(src);
        assertTrue(in.contains("/Author (Giovanni A.)"));
        assertTrue(in.contains("/Metadata 6 0 R"));
        assertTrue(in.contains("GPSLatitude"));

        PdfMetadataCleaner.PdfCleanResult r = PdfMetadataCleaner.clean(src);
        String out = text(r.getBytes());
        assertEquals(src.length, r.getBytes().length, "length-preserving");
        assertTrue(r.isChanged());
        assertEquals(7, r.getInfoValuesBlanked(), "Author Creator Producer CreationDate ModDate Subject Keywords");
        assertEquals(1, r.getMetadataRefsBlanked());
        assertEquals(1, r.getMetadataStreamsZeroed());
        assertEquals(1, r.getXpacketsBlanked());

        assertTrue(out.contains("/Title (Quarterly notes)"), "Title is content");
        for (String gone : new String[]{"Giovanni", "Pages 13.2", "macOS", "20260916", "home, garden",
            "4D696C616E6F", "/Metadata 6 0 R", "GPSLatitude", "xpacket"}) {
            assertFalse(out.contains(gone), gone);
        }
        assertTrue(out.contains("/Author (           )"), "same byte length, spaces");
        assertTrue(out.contains("/Subject <222222222222>"), "hex strings are filled with 2s");

        // Every object still starts where the xref says it does.
        Matcher sx = Pattern.compile("startxref\\n(\\d+)").matcher(out);
        assertTrue(sx.find());
        int xref = Integer.parseInt(sx.group(1));
        assertEquals("xref", out.substring(xref, xref + 4));
        Matcher e = Pattern.compile("(\\d{10}) 00000 n").matcher(out);
        int i = 0;
        while (e.find()) {
            i++;
            int off = Integer.parseInt(e.group(1));
            String head = i + " 0 obj";
            assertEquals(head, out.substring(off, off + head.length()), "object " + i);
        }
        assertEquals(6, i);
        assertTrue(out.contains("(hello) Tj"), "the content stream is untouched");
        assertArrayEquals(pdf(), src, "the input array is never written");
    }

    @Test
    @DisplayName("a second pass finds nothing: already-blank values are not a change")
    public void idempotent() {
        PdfMetadataCleaner.PdfCleanResult once = PdfMetadataCleaner.clean(pdf());
        PdfMetadataCleaner.PdfCleanResult twice = PdfMetadataCleaner.clean(once.getBytes());
        assertFalse(twice.isChanged());
        assertEquals(0, twice.getInfoValuesBlanked());
        assertEquals(0, twice.getMetadataRefsBlanked() + twice.getMetadataStreamsZeroed() + twice.getXpacketsBlanked());
        assertArrayEquals(once.getBytes(), twice.getBytes());
    }

    @Test
    @DisplayName("isPdf is a magic-number check")
    public void magic() {
        assertTrue(PdfMetadataCleaner.isPdf(pdf()));
        assertFalse(PdfMetadataCleaner.isPdf(geotaggedJpeg()));
        assertFalse(PdfMetadataCleaner.isPdf(null));
    }

    private static String clean(String s) {
        return text(PdfMetadataCleaner.clean(l1(s)).getBytes());
    }

    @Test
    @DisplayName("literal strings: escapes, an escaped ')', a long value, an unterminated one")
    public void literalStrings() {
        assertEquals("/Author (           ) /X", clean("/Author (a \\) b \\\\ c) /X"));
        assertEquals("/Keywords \n(  )", clean("/Keywords \n(ab)"), "NBSP is ECMAScript whitespace");
        assertEquals("/Author (never closed", clean("/Author (never closed"));
        assertEquals("/Author (x\\", clean("/Author (x\\"), "a backslash at the very end");
        assertEquals("/Title (kept) /Authors (kept)", clean("/Title (kept) /Authors (kept)"));

        StringBuilder big = new StringBuilder("/Producer (");
        for (int i = 0; i < 200_000; i++) {
            big.append(i % 7 == 0 ? "\\)" : "x");
        }
        big.append(")");
        PdfMetadataCleaner.PdfCleanResult r = PdfMetadataCleaner.clean(l1(big.toString()));
        assertEquals(1, r.getInfoValuesBlanked(), "no recursion per character");
        assertEquals(' ', r.getBytes()[r.getBytes().length - 2]);
    }

    @Test
    @DisplayName("hex strings: whitespace inside is kept (LF, CR, space) or filled (TAB); a non-hex one is left")
    public void hexStrings() {
        assertEquals("/Subject <22 22\n22\r22222>", clean("/Subject <4D 69\n6C\r61\t6E>"));
        assertEquals("/Subject <4G>", clean("/Subject <4G>"));
        assertEquals("/Subject <</A 1>>", clean("/Subject <</A 1>>"));
        assertEquals(0, PdfMetadataCleaner.clean(l1("/Subject <2222>")).getInfoValuesBlanked());
    }

    @Test
    @DisplayName("/Metadata references: every whitespace form, the object found by exact id, streams zeroed")
    public void metadataRefs() {
        String src = "/Metadata\n7\t0\rR\n"
                + "17 0 obj\n<<>>\nstream\nKEEP-17\nendstream\nendobj\n"
                + "7 0 objé<<>>\nstream\r\nGONE-A\nendstream\nendobj\n"
                + "7 0 obj\n<< /NoStream true >>\nendobj\n"
                + "7 0 obj\n<<>>stream\nGONE-B\nendstream\nendobj\n"
                + "7 0 objx stream\nKEEP-X\nendstream\n"
                + "7  0 obj\nstream\nKEEP-SPACES\nendstream\n"
                + "/Metadata 7 0 Rx /Metadata7 0 R";
        PdfMetadataCleaner.PdfCleanResult r = PdfMetadataCleaner.clean(l1(src));
        String out = text(r.getBytes());
        assertEquals(2, r.getMetadataRefsBlanked(), "the first, and '/Metadata 7 0 R' before the x");
        assertTrue(out.startsWith(" ".repeat(15) + "\n17 0 obj"), "the reference became spaces");
        assertTrue(out.contains("KEEP-17"), "17 0 is not 7 0");
        assertFalse(out.contains("GONE-A"), "obj followed by a Latin-1 letter is still a word boundary");
        assertFalse(out.contains("GONE-B"));
        assertTrue(out.contains("KEEP-X"), "objx is not obj");
        assertTrue(out.contains("KEEP-SPACES"), "the id is matched with exactly one space");
        assertTrue(out.contains("/Metadata7 0 R"));
        assertEquals(2, r.getMetadataStreamsZeroed());
        assertEquals(src.length(), out.length());
    }

    @Test
    @DisplayName("xpackets: the shortest block, an end without '?>' skipped, an unterminated begin left")
    public void xpackets() {
        String a = "<?xpacket begin='' id='1'?>AAA<?xpacket end='w'?>";
        String src = "x" + a + "MID<?xpacket begin=''?>B<?xpacket end=>C<?xpacket end='r'?>TAIL<?xpacket begin=''?>open";
        PdfMetadataCleaner.PdfCleanResult r = PdfMetadataCleaner.clean(l1(src));
        String out = text(r.getBytes());
        assertEquals(2, r.getXpacketsBlanked());
        assertTrue(out.startsWith("x" + " ".repeat(a.length()) + "MID "));
        assertFalse(out.contains("B<?xpacket end=>C"));
        assertTrue(out.endsWith("TAIL<?xpacket begin=''?>open"));
        assertEquals(src.length(), out.length());
    }
}
