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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static io.takamaka.extra.media.SyntheticMedia.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C120 cross-platform vectors: {@code src/test/resources/java_metadata_clean_vectors.json}.
 *
 * <p>Each vector is a whole input file (standard Base64), the name it is cleaned under, and what
 * the Java cleaner makes of it:</p>
 * <ul>
 * <li>{@code expected.dispatcher} — {@link MediaMetadataCleaner#clean}: status, the output file
 * name, the detail line, and the SHA3-256 of the output file. For an Office package the output
 * zip is NOT hashed (two deflaters never agree): instead {@code entries} lists every entry, in
 * order, with the SHA3-256 of its uncompressed bytes.</li>
 * <li>{@code expected.core} — the format's own cleaner called directly
 * ({@link ImageLocationStripper}, {@link Mp4MetadataCleaner}, {@link PdfMetadataCleaner},
 * {@link OoxmlMetadataCleaner}), with its counters; {@code "result": "unreadable"} alone for an
 * Office package whose zip cannot be read (the cleaner throws).</li>
 * <li>{@code expected.coreCapped} (some Office vectors) — the Office cleaner called with a
 * ceiling on what the package may inflate to, one node per ceiling:
 * {@code maxUncompressedBytes} and {@code "result"}: {@code "read"}, or {@code "unreadable"} when
 * the entries REALLY inflate past it (the cleaner throws), whatever size the zip declares for
 * them (C120 F1). The production ceiling is 512 MB on both platforms — a vector cannot carry
 * that, so the limit is a parameter of the two cleaners.</li>
 * </ul>
 *
 * <p>Dart consumers (each reads a byte-identical copy at {@code test/fixtures/}):
 * wallet-extra-flutter {@code test/imaging/java_metadata_clean_vectors_test.dart} (the
 * {@code image} vectors, core only) and tkmChat
 * {@code test/services/media_cleanup/java_metadata_clean_vectors_test.dart} (every vector).</p>
 *
 * <h2>The committed file is the vector</h2>
 *
 * <p>A normal run only READS the file: it re-runs the Java cleaners on every committed input and
 * requires the committed expectation, node for node. The outcome of every case is ALSO declared
 * by hand in {@link #cases()}, so a hash is never the only thing standing behind a vector. To
 * regenerate after changing {@link #cases()}: {@code mvn -o test -Dtest=MetadataCleanVectorTest
 * -Dc120.vectors.write=true}, then copy the file to the two Dart fixture paths. (The Office
 * inputs go through {@code java.util.zip.Deflater}, so a regeneration on another JDK may change
 * their Base64 without changing any expectation.)</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class MetadataCleanVectorTest {

    static final String RESOURCE = "java_metadata_clean_vectors.json";

    private static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    private static final String CLEANED = "cleaned";
    private static final String UNCHANGED = "unchanged";
    private static final String PASSTHROUGH = "passthrough";
    private static final String FAILED = "failed";

    /** One vector as declared here: the input, and the outcome stated by hand. */
    private static final class Case {

        final String name;
        final String description;
        /** image | mp4 | pdf | ooxml | other — which cleaner {@code expected.core} is for. */
        final String format;
        final String fileName;
        final byte[] input;
        final String status;
        /** Ceilings for {@code expected.coreCapped} (Office only); none for most vectors. */
        long[] caps = new long[0];

        Case capped(long... caps) {
            this.caps = caps;
            return this;
        }

        Case(String name, String format, String fileName, String status, String description, byte[] input) {
            this.name = name;
            this.description = description;
            this.format = format;
            this.fileName = fileName;
            this.input = input;
            this.status = status;
        }
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = MetadataCleanVectorTest.class.getClassLoader().getResourceAsStream(name)) {
            assertNotNull(in, name + " is missing from the test resources");
            return in.readAllBytes();
        }
    }

    private static List<Case> cases() throws Exception {
        List<Case> all = new ArrayList<>();
        final String img = "image";

        // ---- JPEG ----
        byte[] xmp = segment(0xE1, l1("http://ns.adobe.com/xap/1.0/\0<x:xmpmeta><exif:GPSLatitude>45,27.2N</exif:GPSLatitude></x:xmpmeta>"));
        byte[] com = segment(0xFE, l1("shot at home"));
        byte[] icc = segment(0xE2, cat(l1("ICC_PROFILE\0"), bytes(1, 1), filled(40, 0x5A)));
        byte[] adobe = segment(0xEE, cat(l1("Adobe"), bytes(0, 100, 0, 0, 0, 0, 1)));
        byte[] mpf = segment(0xE2, cat(l1("MPF\0"), filled(30, 0x11)));
        byte[] iptc = segment(0xED, cat(l1("Photoshop 3.0\0008BIM"), filled(20, 0x33)));
        byte[] vendor = segment(0xEA, cat(l1("AROT\0"), filled(30, 0x22)));

        all.add(new Case("jpeg_progressive_exif", img, "IMG_progressive.jpg", CLEANED,
                "the real camera JPEG of the Dart stripper test (SOF2, EXIF IFD0 with 6 entries): every scan copied",
                resource("media/progressive_exif.jpg")));
        all.add(new Case("jpeg_geotagged_o6_mm", img, "IMG_1.jpg", CLEANED,
                "EXIF (MM) with Make, DateTime, GPS IFD and orientation 6: replaced by the orientation-only APP1",
                structuralJpeg(geoApp1(6, true))));
        all.add(new Case("jpeg_geotagged_o8_ii", img, "IMG_2.JPEG", CLEANED,
                "the same EXIF in II byte order, orientation 8; the re-emitted block is always MM; .JPEG becomes .jpg",
                structuralJpeg(geoApp1(8, false))));
        all.add(new Case("jpeg_orientation_1_gps", img, "IMG_3.jpg", CLEANED,
                "orientation 1: the EXIF block is dropped and NO EXIF is written back",
                structuralJpeg(geoApp1(1, true))));
        all.add(new Case("jpeg_xmp_before_exif_com", img, "IMG_4.jpg", CLEANED,
                "XMP APP1, then EXIF (orientation 3), then COM: the orientation APP1 lands where the FIRST APP1 was",
                structuralJpeg(xmp, geoApp1(3, true), com)));
        all.add(new Case("jpeg_icc_adobe_kept", img, "IMG_5.jpg", CLEANED,
                "ICC_PROFILE (APP2) and Adobe (APP14) kept; MPF (APP2), IPTC (APP13), vendor APP10, EXIF dropped",
                structuralJpeg(icc, mpf, geoApp1(6, true), iptc, adobe, vendor)));
        all.add(new Case("jpeg_plain", img, "plain.jpg", UNCHANGED,
                "JFIF + coding segments only: nothing to remove", structuralJpeg()));
        all.add(new Case("jpeg_icc_only", img, "icc.jpg", UNCHANGED,
                "only kept segments (ICC, Adobe): nothing to remove", structuralJpeg(icc, adobe)));
        all.add(new Case("jpeg_trailer_after_eoi", img, "motion.jpg", CLEANED,
                "no metadata segment, but a motion-photo trailer after the EOI: cut off",
                cat(structuralJpeg(), l1("....ftypmp42"), filled(200, 0x33))));
        byte[] whole = structuralJpeg(geoApp1(6, true));
        all.add(new Case("jpeg_truncated_scan", img, "cut.jpg", CLEANED,
                "the entropy-coded data ends without an EOI: kept as it is, EOI supplied",
                Arrays.copyOf(whole, whole.length - 5)));
        all.add(new Case("jpeg_fill_bytes_unchanged", img, "fill.jpg", UNCHANGED,
                "a fill byte (FF FF) and a standalone RST0 between header segments, no metadata: reported "
                + "unchanged, the input is used as it is",
                cat(bytes(0xFF, 0xD8, 0xFF), jfif(), bytes(0xFF, 0xD0), codingAndScan(), bytes(0xFF, 0xD9))));
        all.add(new Case("jpeg_fill_bytes_with_com", img, "fill_com.jpg", CLEANED,
                "the same with a COM: the rebuilt file also loses the fill byte and the standalone marker",
                cat(bytes(0xFF, 0xD8, 0xFF), jfif(), bytes(0xFF, 0xD0), com, codingAndScan(), bytes(0xFF, 0xD9))));
        all.add(new Case("jpeg_multi_scan", img, "scans.jpg", CLEANED,
                "two scans with a DHT between them, FF FF fill inside the entropy data, a DNL before the EOI",
                cat(bytes(0xFF, 0xD8), jfif(), geoApp1(5, false), codingAndScan(),
                        segment(0xC4, cat(bytes(0x10), filled(16, 0))),
                        segment(0xDA, bytes(1, 1, 0x00, 0, 63, 0)),
                        bytes(0x01, 0xFF, 0xFF, 0x00, 0x02, 0xFF, 0xD7, 0x03),
                        segment(0xDC, bytes(0, 16)), bytes(0xFF, 0xD9))));
        all.add(new Case("jpeg_lying_segment_length", img, "bad.jpg", FAILED,
                "APP1 claims 65535 bytes in an 8-byte file", bytes(0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x45, 0x78)));
        all.add(new Case("jpeg_eoi_before_scan", img, "empty_scan.jpg", FAILED,
                "SOI, JFIF, EOI: no scan", cat(bytes(0xFF, 0xD8), jfif(), bytes(0xFF, 0xD9))));

        // ---- PNG ----
        byte[] gpsTiff = bytes(
                0x4D, 0x4D, 0x00, 0x2A, 0, 0, 0, 8, 0, 2,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0,
                0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 38,
                0, 0, 0, 0, 0, 1,
                0x00, 0x01, 0, 2, 0, 0, 0, 2, 0x4E, 0, 0, 0,
                0, 0, 0, 0);
        byte[] png = structuralPng();
        all.add(new Case("png_text_and_exif", img, "shot.png", CLEANED,
                "an unreadable eXIf, tEXt, iTXt (XMP) and zTXt after IHDR: all dropped, the rest byte-identical",
                withChunksAfterIhdr(png,
                        chunk("eXIf", cat(l1("Exif\0\0MM\0*"), filled(20, 1))),
                        chunk("tEXt", l1("Comment\0taken at home")),
                        chunk("iTXt", l1("XML:com.adobe.xmp\0\0\0\0\0<x:xmpmeta GPSLatitude/>")),
                        chunk("zTXt", l1("Software\0\0x")))));
        all.add(new Case("png_orientation_6_gps", img, "rotated.png", CLEANED,
                "eXIf = {Orientation 6, GPS IFD}: replaced by a 26-byte orientation-only eXIf",
                withChunksAfterIhdr(png, chunk("eXIf", gpsTiff))));
        all.add(new Case("png_orientation_5_ii", img, "rotated_ii.png", CLEANED,
                "an II eXIf with orientation 5: the replacement is MM",
                withChunksAfterIhdr(png, chunk("eXIf", orientationTiff(5, false)))));
        all.add(new Case("png_three_exif", img, "three.png", CLEANED,
                "eXIf (1), eXIf (7), eXIf (3): the first non-normal one is kept as orientation-only, the others dropped",
                withChunksAfterIhdr(png, chunk("eXIf", orientationTiff(1, true)),
                        chunk("eXIf", orientationTiff(7, true)), chunk("eXIf", orientationTiff(3, true)))));
        all.add(new Case("png_plain", img, "plain.png", UNCHANGED, "IHDR, IDAT, IEND: nothing to remove", png));
        all.add(new Case("png_trailer_and_late_text", img, "tail.png", CLEANED,
                "a tEXt between IDAT and IEND, and 50 bytes after IEND: both gone",
                cat(Arrays.copyOf(png, png.length - 12), chunk("tEXt", l1("Author\0me")),
                        chunk("IEND", new byte[0]), filled(50, 0x44))));
        byte[] lyingPng = withChunksAfterIhdr(png, chunk("tEXt", l1("a\0b")));
        lyingPng[PNG_IHDR_END] = 0x7F;
        all.add(new Case("png_lying_chunk_length", img, "bad.png", FAILED,
                "a chunk length of 0x7F000003, far past the end", lyingPng));
        all.add(new Case("png_no_iend", img, "cut.png", FAILED,
                "the file ends before IEND", Arrays.copyOf(png, png.length - 12)));

        // ---- GIF ----
        all.add(new Case("gif_comment_and_xmp", img, "anim.gif", CLEANED,
                "a Comment extension and an XMP application extension: dropped",
                structuralGif("89a", true, commentExt("taken at home"),
                        appExt("XMP DataXMP", l1("<x:xmpmeta><exif:GPSLatitude>45,27N</exif:GPSLatitude></x:xmpmeta>")))));
        all.add(new Case("gif_loop_extensions_kept", img, "loop.gif", CLEANED,
                "NETSCAPE2.0 and ANIMEXTS1.0 kept, a 300-byte comment (two sub-blocks) dropped",
                structuralGif("89a", true, appExt("NETSCAPE2.0", bytes(1, 0, 0)),
                        appExt("ANIMEXTS1.0", bytes(1, 0, 0)), commentExt("x".repeat(300)))));
        all.add(new Case("gif_other_blocks", img, "blocks.gif", CLEANED,
                "a graphic control and a plain-text extension kept, an application extension with a 5-byte "
                + "identifier dropped, a second image with a local colour table kept",
                structuralGif("89a", true,
                        bytes(0x21, 0xF9, 4, 0, 10, 0, 0, 0),
                        cat(bytes(0x21, 0x01, 12), filled(12, 0), subBlocks(l1("hi"))),
                        cat(bytes(0x21, 0xFF, 5), l1("SHORT"), bytes(0)),
                        gifImage(true))));
        all.add(new Case("gif87a_truncated", img, "old.gif", CLEANED,
                "a GIF87a that ends without its trailer: the trailer is supplied", structuralGif("87a", false)));
        all.add(new Case("gif_plain", img, "plain.gif", UNCHANGED, "nothing to remove", structuralGif("89a", true)));
        all.add(new Case("gif_junk_after_trailer", img, "junk.gif", CLEANED,
                "bytes after the trailer: cut off", cat(structuralGif("89a", true), l1("junk after"))));
        all.add(new Case("gif_unknown_block", img, "bad.gif", FAILED,
                "a block introducer that is none of 21 / 2C / 3B", structuralGif("89a", true, bytes(0x99, 1, 2))));
        all.add(new Case("gif_sub_blocks_past_end", img, "cut.gif", FAILED,
                "a comment whose sub-block length runs past the end of the file",
                cat(l1("GIF89a"), bytes(2, 0, 2, 0, 0, 0, 0), bytes(0x21, 0xFE, 200, 1, 2, 3))));

        // ---- images nobody can vouch for ----
        all.add(new Case("webp", img, "sticker.webp", FAILED, "RIFF/WEBP: refused, never passed through",
                cat(l1("RIFF"), bytes(0x24, 0, 0, 0), l1("WEBPVP8 "), filled(24, 0))));
        all.add(new Case("tiff", img, "scan.tiff", FAILED, "TIFF: refused",
                cat(bytes(0x49, 0x49, 0x2A, 0x00, 8, 0, 0, 0), filled(16, 0))));
        all.add(new Case("heic_named_jpg", img, "photo.jpg", FAILED,
                "ftyp brand heic under a .jpg name: the bytes decide — a still image, refused",
                cat(bytes(0, 0, 0, 0x18), l1("ftypheic"), u32(0), l1("mif1heic"),
                        box("meta", bytes(0, 0, 0, 0), filled(40, 1)))));
        all.add(new Case("garbage_named_png", img, "notes.png", FAILED,
                "text under an image name: no cleaner can vouch for it", l1("not an image")));
        all.add(new Case("empty_named_jpg", img, "empty.jpg", FAILED, "a zero-byte file under an image name", new byte[0]));

        // ---- MP4 / MOV / 3GP ----
        final String mp4 = "mp4";
        all.add(new Case("mp4_android_camera", mp4, "clip.mp4", CLEANED,
                "ftyp, moov(mvhd, udta(©xyz), meta, trak(tkhd, mdia(mdhd))), mdat: udta and meta become free, 6 times zeroed",
                androidMp4("+46.0108+008.9562/")));
        all.add(new Case("mp4_trak_udta_uuid_as_bin", mp4, "clip.bin", CLEANED,
                "a top-level XMP uuid box and a trak-level udta, picked under a .bin name: a video by its bytes",
                androidMp4("+1.0+2.0/", true, 0xD1234567L, true, true)));
        all.add(new Case("mp4_clean", mp4, "clean.mp4", UNCHANGED,
                "no udta, no meta, times already zero: nothing to remove",
                androidMp4(null, false, 0, false, false)));
        byte[] isom = box("ftyp", l1("isom"), u32(0), l1("isom"));
        all.add(new Case("mov_version1_and_64bit_size", mp4, "movie.mov", CLEANED,
                "a uuid box with a 64-bit size, a version-1 mvhd (64-bit times) whose modification time is already zero",
                cat(isom, cat(u32(1), l1("uuid"), u32(0), u32(16 + 20), filled(20, 0x7A)),
                        box("moov", box("mvhd", cat(bytes(1, 0, 0, 0), filled(8, 0x11), filled(8, 0), u32(1000), filled(8, 0x22)))))));
        all.add(new Case("3gp_size_zero_last_box_no_extension", mp4, "video", CLEANED,
                "trak-level meta, a moov too short to hold times, and a top-level udta of size 0 (to the end of "
                + "the file); no extension, so the copy is named .mp4",
                cat(box("ftyp", l1("3gp5"), u32(0), l1("3gp5")),
                        box("moov", box("mvhd", bytes(0, 0, 0, 0, 1, 2)),
                                box("trak", box("meta", u32(0), filled(10, 0x41)), box("tkhd", bytes(0, 0, 0)))),
                        cat(u32(0), l1("udta"), l1("loci somewhere")))));
        byte[] lyingMoov = androidMp4("+1.0+2.0/");
        lyingMoov[firstBox(lyingMoov, "moov").offset] = 0x7F;
        all.add(new Case("mp4_lying_moov_size", mp4, "bad.mp4", FAILED,
                "the moov box claims to run far past the file", lyingMoov));
        all.add(new Case("webm", mp4, "clip.webm", FAILED, "EBML, no ftyp: refused",
                cat(bytes(0x1A, 0x45, 0xDF, 0xA3), filled(60, 0x11))));

        // 64-bit box sizes close to 2^63: "pos + size" overflows, "size > end - pos" does not. The
        // Dart cleaner used the sum, took the box for a valid one and blanked it without end
        // (fixed 2026-11-11, tkmChat fix/C120-cleaner-edge-cases).
        all.add(new Case("mp4_64bit_size_near_2_63_uuid", mp4, "huge.mp4", FAILED,
                "a top-level uuid box whose 64-bit size is 2^63-1: refused, nothing blanked",
                cat(isom, cat(u32(1), l1("uuid"), u32(0x7FFFFFFFL), u32(0xFFFFFFFFL), filled(20, 0x7A)))));
        all.add(new Case("mp4_64bit_size_near_2_63_moov_udta", mp4, "huge_udta.mp4", FAILED,
                "the same lie on a udta inside a well-formed moov",
                cat(isom, box("moov", cat(u32(1), l1("udta"), u32(0x7FFFFFFFL), u32(0xFFFFFFFFL), filled(12, 0x41))))));
        all.add(new Case("mp4_64bit_size_near_2_63_mdat", mp4, "huge_mdat.mp4", FAILED,
                "and on a box that is only walked past (mdat)",
                cat(isom, cat(u32(1), l1("mdat"), u32(0x7FFFFFFFL), u32(0xFFFFFFF0L), filled(8, 1)))));

        // ---- PDF ----
        final String pdf = "pdf";
        all.add(new Case("pdf_info_and_xmp", pdf, "notes.pdf", CLEANED,
                "a valid one-page PDF: 7 Info values blanked (Title kept), /Metadata reference, XMP stream and packet gone",
                pdf()));
        all.add(new Case("pdf_no_xmp", pdf, "plain.pdf", CLEANED, "the same without the XMP stream",
                pdf("Mario R.", "Minutes", "526F6D61", false)));
        all.add(new Case("pdf_already_clean", pdf, "clean.pdf", UNCHANGED,
                "the output of pdf_info_and_xmp: a second pass finds nothing",
                PdfMetadataCleaner.clean(pdf()).getBytes()));
        all.add(new Case("pdf_pattern_edges", pdf, "edges.pdf", CLEANED,
                "not a valid PDF: one line per corner of the three patterns (escapes, NBSP as whitespace, "
                + "hex strings with whitespace, ids matched exactly, obj before a Latin-1 letter, CRLF after "
                + "stream, the shortest xpacket, unterminated values) — the Dart regular expressions and the "
                + "Java scanners must agree byte for byte",
                l1("%PDF-1.7\n"
                        + "/Author (a \\) b \\\\ c) /X\n"
                        + "/Keywords \n(ab)\n"
                        + "/Title (kept) /Authors (kept)\n"
                        + "/Creator(tight)/Producer\t\f(tabs)\n"
                        + "/CreationDate (D:2026 (nested\\) only escaped))\n"
                        + "/Subject <4D 69\n6C\r61\t6E>\n"
                        + "/Subject <4G> /Subject <</A 1>> /Subject <2222> /ModDate <>\n"
                        + "/Metadata\n7\t0\rR\n"
                        + "17 0 obj\n<<>>\nstream\nKEEP-17\nendstream\nendobj\n"
                        + "7 0 objé<<>>\nstream\r\nGONE-A\nendstream\nendobj\n"
                        + "7 0 obj\n<< /NoStream true >>\nendobj\n"
                        + "7 0 obj\n<<>>stream\nGONE-B\nendstream\nendobj\n"
                        + "7 0 objx stream\nKEEP-X\nendstream\n"
                        + "7 0 obj_ stream\nKEEP-UNDERSCORE\nendstream\n"
                        + "7  0 obj\nstream\nKEEP-SPACES\nendstream\n"
                        + "/Metadata 7 0 Rx /Metadata7 0 R /Metadata 007 0 R /Metadata 8 0 R /Metadata 9 1 R\n"
                        + "007 0 obj stream\nGONE-007\nendstream endobj\n"
                        + "9 1 obj\nstream\rZEROED-CR-ONLY\nendstream endobj\n"
                        + "x<?xpacket begin='' id='1'?>AAA<?xpacket end='w'?>MID"
                        + "<?xpacket begin=''?>B<?xpacket end=>C<?xpacket end='r'?>TAIL\n"
                        + "8 0 obj stream\n<?xpacket begin=''?>INSIDE<?xpacket end='w'?>\nendstream endobj\n"
                        + "<?xpacket begin=''?>open /Author (never closed \\")));

        // ---- Office Open XML ----
        final String ooxml = "ooxml";
        byte[] photo = structuralJpeg(geoApp1(6, true), xmp);
        all.add(new Case("docx_full", ooxml, "report.docx", CLEANED,
                "core + app scrubbed, custom.xml removed with its Override and Relationship (both parts move "
                + "to the end), the embedded photo stripped, the body identical",
                docx(true, photo)));
        all.add(new Case("docx_no_custom", ooxml, "letter.docx", CLEANED,
                "no custom.xml: the entry order is the source's", docx(false, photo)));
        all.add(new Case("pptx_odd_entries", ooxml, "deck.pptx", CLEANED,
                "a directory entry, a broken embedded JPEG (kept as it is), a STORED PNG with a tEXt (stripped), "
                + "a clean GIF, a non-image and an empty file under media/, app.xml only",
                zip(z("[Content_Types].xml", "<Types/>"),
                        new Z("ppt/media/", new byte[0], true),
                        z("ppt/media/broken.jpg", bytes(0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x45, 0x78)),
                        stored("ppt/media/pic.png", withChunksAfterIhdr(png, chunk("tEXt", l1("Author\0me")))),
                        stored("ppt/media/clean.gif", structuralGif("89a", true)),
                        z("ppt/media/notes.bin", l1("not an image")),
                        z("ppt/media/empty.jpg", new byte[0]),
                        z("docProps/app.xml", APP))));
        all.add(new Case("xlsm_macro_enabled", ooxml, "book.xlsm", CLEANED,
                "application/vnd.ms-excel.sheet.macroenabled.12 is an Office type: cleaned",
                zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE), z("xl/workbook.xml", "<workbook/>"))));
        all.add(new Case("xlsx_nothing_to_scrub", ooxml, "empty.xlsx", UNCHANGED,
                "no docProps, no media: the package is used as it is",
                zip(z("[Content_Types].xml", "<Types/>"), z("xl/workbook.xml", "<workbook/>"))));
        // A zip that cannot be read is FAILED — never "unchanged": it was not inspected.
        byte[] small = zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE), z("word/document.xml", DOCUMENT));
        int firstCen = indexOf(small, bytes(0x50, 0x4B, 0x01, 0x02));
        all.add(new Case("docx_no_central_directory", ooxml, "cut.docx", FAILED,
                "the file stops after the entries (PK magic and [Content_Types].xml are there, the central "
                + "directory and the end record are not)",
                Arrays.copyOf(small, firstCen)));
        all.add(new Case("docx_end_record_cut", ooxml, "cut_end.docx", FAILED,
                "the last 10 bytes of the end record are missing", Arrays.copyOf(small, small.length - 10)));
        byte[] badHeader = small.clone();
        badHeader[firstCen + 4 + indexOf(Arrays.copyOfRange(small, firstCen + 4, small.length), bytes(0x50, 0x4B, 0x01, 0x02)) + 2] = 0x7F;
        all.add(new Case("docx_bad_central_header", ooxml, "bad_dir.docx", FAILED,
                "the second central-directory header has a wrong signature", badHeader));
        // custom.xml removed: the two rewritten parts keep every byte outside the removed element,
        // whatever their encoding (Latin-1 E9 / EF next to UTF-8 C3 A9 / C3 AF).
        byte[] override = l1("<Override PartName=\"/docProps/custom.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.custom-properties+xml\"/>");
        byte[] relationship = l1("<Relationship Id=\"rId4\" Type=\"custom-properties\" Target=\"docProps/custom.xml\"/>");
        all.add(new Case("docx_non_ascii_around_custom", ooxml, "accents.docx", CLEANED,
                "custom.xml removed from a package whose [Content_Types].xml and _rels/.rels hold Latin-1 and "
                + "UTF-8 non-ASCII bytes: only the Override and the Relationship go, every other byte is kept",
                zip(z("[Content_Types].xml", cat(l1("<?xml version=\"1.0\" encoding=\"ISO-8859-1\"?><Types><!-- caf"),
                                bytes(0xC3, 0xA9), l1(" --><Default Extension=\"xml\" ContentType=\"application/xml\" Label=\"r"),
                                bytes(0xE9), l1("sum"), bytes(0xE9), l1("\"/>"), override, l1("</Types>"))),
                        z("_rels/.rels", cat(l1("<Relationships><Relationship Id=\"rId1\" Target=\"word/document.xml\" Label=\"na"),
                                bytes(0xEF), l1("ve "), bytes(0xC3, 0xAF), l1("\"/>"), relationship, l1("</Relationships>"))),
                        z("docProps/custom.xml", CUSTOM),
                        z("word/document.xml", DOCUMENT))));
        // A repeated entry name: both sides keep the FIRST entry.
        byte[] repeated = zip(z("[Content_Types].xml", "<Types/>"), z("word/a.txt", "first"), z("word/b.txt", "second"),
                z("docProps/core.xml", CORE));
        byte[] nameB = l1("word/b.txt");
        for (int at = indexOf(repeated, nameB); at >= 0; at = indexOf(repeated, nameB)) {
            repeated[at + 5] = 'a';
        }
        all.add(new Case("docx_repeated_entry_name", ooxml, "twice.docx", CLEANED,
                "word/a.txt is in the package twice (\"first\", then \"second\"): the first one is kept",
                repeated));
        // C120 F1 — the sizes a zip DECLARES are the sender's word. What counts is what an entry
        // really inflates to: for the content (an entry that declares 0 bytes is still read) and
        // for the ceiling (an entry that declares 10 bytes still fills the memory it inflates to).
        all.add(new Case("docx_media_declared_size_zero", ooxml, "zero.docx", CLEANED,
                "word/media/image1.jpeg declares 0 uncompressed bytes (central directory and data descriptor) "
                + "and inflates to a geotagged photo: it is read for what it holds, and stripped",
                withDeclaredSize(zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE),
                        z("word/document.xml", DOCUMENT), z("word/media/image1.jpeg", photo)),
                        "word/media/image1.jpeg", 0)));
        final int mega = 1024 * 1024;
        final long read = "<Types/>".length() + DOCUMENT.length() + mega;
        all.add(new Case("docx_media_inflates_past_declared", ooxml, "bomb_media.docx", CLEANED,
                "word/media/blob.bin declares 10 bytes and inflates to 1 MiB: under a ceiling of exactly the bytes "
                + "read ([Content_Types].xml + word/document.xml + the blob; docProps is not read) the package is "
                + "cleaned, one byte lower it is unreadable",
                withDeclaredSize(zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE),
                        z("word/document.xml", DOCUMENT), z("word/media/blob.bin", filled(mega, 0))),
                        "word/media/blob.bin", 10)).capped(read, read - 1, 1000));
        all.add(new Case("docx_body_inflates_past_declared", ooxml, "bomb_body.docx", CLEANED,
                "the same with a part that is not media: word/big.xml declares 10 bytes and inflates to 1 MiB "
                + "of spaces — every entry that is read counts, not only the images",
                withDeclaredSize(zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE),
                        z("word/document.xml", DOCUMENT), z("word/big.xml", filled(mega, 0x20))),
                        "word/big.xml", 10)).capped(read, read - 1, 1000));
        all.add(new Case("zip_named_docx", "other", "fake.docx", PASSTHROUGH,
                "a zip without [Content_Types].xml under a .docx name: not an Office document",
                zip(z("readme.txt", "hi"))));
        all.add(new Case("zip_plain", "other", "bundle.zip", PASSTHROUGH,
                "a plain zip — even one that holds a core.xml — is never opened",
                zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE))));
        all.add(new Case("docm_not_an_office_type", "other", "macro.docm", PASSTHROUGH,
                "application/vnd.ms-word.document.macroenabled.12 matches none of the four Office hints "
                + "(officedocument, msword, ms-excel, ms-powerpoint): passed through, as on the Dart side",
                zip(z("[Content_Types].xml", "<Types/>"), z("docProps/core.xml", CORE))));

        // ---- everything else ----
        all.add(new Case("text", "other", "notes.txt", PASSTHROUGH, "no cleaner for this type: used unchanged, the user is told",
                l1("hello")));
        return all;
    }

    // ---- what Java makes of one input ------------------------------------------------------

    private static ArrayNode entries(byte[] zip) {
        ArrayNode a = M.createArrayNode();
        for (Map.Entry<String, byte[]> e : unzip(zip).entrySet()) {
            ObjectNode o = a.addObject();
            o.put("name", e.getKey());
            o.put("length", e.getValue().length);
            o.put("sha3", sha3(e.getValue()));
        }
        return a;
    }

    private static ArrayNode strings(List<String> l) {
        ArrayNode a = M.createArrayNode();
        l.forEach(a::add);
        return a;
    }

    private static void deleteTree(Path dir) throws IOException {
        try (Stream<Path> s = Files.walk(dir)) {
            s.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
        }
    }

    /** Run the dispatcher and the format's own cleaner on one input; the result is the expectation. */
    private static ObjectNode expect(String format, String fileName, byte[] input, long... caps) throws Exception {
        ObjectNode expected = M.createObjectNode();
        Path tmp = Files.createTempDirectory("c120_vectors_");
        try {
            Path src = Files.createDirectories(tmp.resolve("in")).resolve(fileName);
            Files.write(src, input);
            Path outDir = tmp.resolve("out");

            CleanOutcome r = MediaMetadataCleaner.clean(src, outDir, MetadataCleanOptions.on());
            ObjectNode d = expected.putObject("dispatcher");
            d.put("status", r.getStatus().name().toLowerCase());
            // a failure's wording is the platform's own exception text: not part of the contract
            if (r.getStatus() == CleanOutcome.Status.FAILED) {
                d.putNull("detail");
            } else {
                d.put("detail", r.getDetail());
            }
            if (r.getStatus() == CleanOutcome.Status.CLEANED) {
                byte[] out = Files.readAllBytes(r.getPath());
                d.put("outputName", r.getPath().getFileName().toString());
                if (format.equals("ooxml")) {
                    d.set("entries", entries(out));
                } else {
                    d.put("outputLength", out.length);
                    d.put("outputSha3", sha3(out));
                }
            } else {
                assertEquals(src, r.getPath());
                d.putNull("outputName");
                assertTrue(!Files.exists(outDir) || Files.list(outDir).count() == 0, fileName + ": nothing written");
            }
            assertEquals(sha3(input), sha3(Files.readAllBytes(src)), fileName + ": the source is never modified");

            if (format.equals("image")) {
                ObjectNode c = expected.putObject("core");
                try {
                    ImageLocationStripper.StrippedImage s = ImageLocationStripper.strip(input);
                    c.put("result", "stripped");
                    c.put("changed", s.isChanged());
                    c.put("orientation", s.getOrientation());
                    c.put("mediaType", s.getMediaType());
                    c.put("length", s.getBytes().length);
                    c.put("sha3", sha3(s.getBytes()));
                } catch (UnsupportedMediaFormatException e) {
                    c.put("result", "unsupported");
                } catch (MediaFormatException e) {
                    c.put("result", "malformed");
                }
            } else if (format.equals("mp4")) {
                ObjectNode c = expected.putObject("core");
                Path out = tmp.resolve("core_out");
                try {
                    Mp4MetadataCleaner.Mp4CleanResult m = Mp4MetadataCleaner.cleanFile(src, out);
                    byte[] bytes = Files.readAllBytes(out);
                    c.put("result", "cleaned");
                    c.put("changed", m.isChanged());
                    c.set("blanked", strings(m.getBlanked()));
                    c.put("timestampsZeroed", m.getTimestampsZeroed());
                    c.put("length", bytes.length);
                    c.put("sha3", sha3(bytes));
                } catch (UnsupportedMediaFormatException e) {
                    c.put("result", "unsupported");
                } catch (MediaFormatException e) {
                    c.put("result", "malformed");
                }
            } else if (format.equals("pdf")) {
                ObjectNode c = expected.putObject("core");
                PdfMetadataCleaner.PdfCleanResult p = PdfMetadataCleaner.clean(input);
                c.put("changed", p.isChanged());
                c.put("infoValuesBlanked", p.getInfoValuesBlanked());
                c.put("metadataRefsBlanked", p.getMetadataRefsBlanked());
                c.put("metadataStreamsZeroed", p.getMetadataStreamsZeroed());
                c.put("xpacketsBlanked", p.getXpacketsBlanked());
                c.put("length", p.getBytes().length);
                c.put("sha3", sha3(p.getBytes()));
            } else if (format.equals("ooxml")) {
                ObjectNode c = expected.putObject("core");
                try {
                    OoxmlMetadataCleaner.OoxmlCleanResult o = OoxmlMetadataCleaner.clean(src);
                    c.put("changed", o.isChanged());
                    c.set("propertyParts", strings(o.getPropertyParts()));
                    c.set("imagesStripped", strings(o.getImagesStripped()));
                    if (o.isChanged()) {
                        c.set("entries", entries(o.getBytes()));
                    }
                } catch (IOException e) {
                    // not a readable zip (only such a vector carries "result")
                    c.put("result", "unreadable");
                }
                if (caps.length > 0) {
                    ArrayNode capped = expected.putArray("coreCapped");
                    for (long cap : caps) {
                        ObjectNode o = capped.addObject();
                        // an int node when it fits, as Jackson reads the committed file back
                        if (cap == (int) cap) {
                            o.put("maxUncompressedBytes", (int) cap);
                        } else {
                            o.put("maxUncompressedBytes", cap);
                        }
                        try {
                            OoxmlMetadataCleaner.clean(src, cap);
                            o.put("result", "read");
                        } catch (IOException e) {
                            o.put("result", "unreadable");
                        }
                    }
                }
            }
        } finally {
            deleteTree(tmp);
        }
        return expected;
    }

    private static ObjectNode build() throws Exception {
        ObjectNode root = M.createObjectNode();
        root.put("description", "C120: what the metadata cleaner makes of a file. Compare the status, the "
                + "output name, the detail line and the SHA3-256 of the output; for an Office package compare "
                + "the entries (name, order, SHA3-256 of the uncompressed bytes), never the zip bytes.");
        root.put("generator", "takamaka-extra MetadataCleanVectorTest (-Dc120.vectors.write=true)");
        root.put("javaReference", "io.takamaka.extra.media.MediaMetadataCleaner.clean(src, outDir, MetadataCleanOptions.on())");
        root.put("inputEncoding", "standard base64, padded");
        root.put("hash", "SHA3-256, lower-case hex");
        root.put("suffix", MediaMetadataCleaner.SUFFIX);
        ArrayNode vectors = root.putArray("vectors");
        for (Case c : cases()) {
            ObjectNode v = vectors.addObject();
            v.put("name", c.name);
            v.put("description", c.description);
            v.put("format", c.format);
            v.put("fileName", c.fileName);
            v.put("mediaType", MediaMetadataCleaner.mediaTypeForName(c.fileName));
            v.put("inputLength", c.input.length);
            v.put("inputSha3", sha3(c.input));
            v.set("expected", expect(c.format, c.fileName, c.input, c.caps));
            v.put("inputBase64", Base64.getEncoder().encodeToString(c.input));
        }
        return root;
    }

    @Test
    @DisplayName("every fresh fixture meets its hand-declared outcome (and -Dc120.vectors.write regenerates)")
    public void freshFixturesMeetTheDeclaredOutcomes() throws Exception {
        ObjectNode root = build();
        List<Case> declared = cases();
        Set<String> names = new HashSet<>();
        for (int i = 0; i < declared.size(); i++) {
            Case c = declared.get(i);
            assertTrue(names.add(c.name), "duplicate vector name " + c.name);
            JsonNode expected = root.get("vectors").get(i).get("expected");
            assertEquals(c.status, expected.get("dispatcher").get("status").asText(), c.name);
            // the ceilings are declared highest first: the first one is what the package really
            // inflates to (read), every lower one is refused
            for (int k = 0; k < c.caps.length; k++) {
                assertEquals(k == 0 ? "read" : "unreadable", expected.get("coreCapped").get(k).get("result").asText(),
                        c.name + " under " + c.caps[k]);
            }
        }
        if (Boolean.getBoolean("c120.vectors.write")) {
            Path out = Paths.get("src", "test", "resources", RESOURCE);
            Files.write(out, (M.writeValueAsString(root) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("⭐ the committed vector file: Java reproduces every expectation in it")
    public void committedVectorsHold() throws Exception {
        JsonNode root = M.readTree(resource(RESOURCE));
        JsonNode vectors = root.get("vectors");
        List<Case> declared = cases();
        assertEquals(declared.size(), vectors.size(), "the file and cases() have drifted: regenerate");
        Set<String> statuses = new HashSet<>();
        Set<String> formats = new HashSet<>();
        for (int i = 0; i < vectors.size(); i++) {
            JsonNode v = vectors.get(i);
            Case c = declared.get(i);
            String name = v.get("name").asText();
            assertEquals(c.name, name, "vector order");
            assertEquals(c.format, v.get("format").asText(), name);
            assertEquals(c.fileName, v.get("fileName").asText(), name);
            byte[] input = Base64.getDecoder().decode(v.get("inputBase64").asText());
            assertEquals(v.get("inputLength").asInt(), input.length, name + ": inputLength");
            assertEquals(v.get("inputSha3").asText(), sha3(input), name + ": inputSha3");
            assertEquals(v.get("mediaType").asText(), MediaMetadataCleaner.mediaTypeForName(c.fileName), name);
            JsonNode expected = v.get("expected");
            assertEquals(c.status, expected.get("dispatcher").get("status").asText(), name + ": declared outcome");
            assertEquals(expected, expect(c.format, c.fileName, input, c.caps), name);
            statuses.add(c.status);
            formats.add(c.format);
        }
        assertEquals(new HashSet<>(Arrays.asList(CLEANED, UNCHANGED, PASSTHROUGH, FAILED)), statuses);
        assertEquals(new HashSet<>(Arrays.asList("image", "mp4", "pdf", "ooxml", "other")), formats);
    }
}
