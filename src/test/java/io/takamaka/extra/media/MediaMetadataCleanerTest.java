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
 * C120 — {@link MediaMetadataCleaner#clean}: the one call an attach path makes, dispatched on
 * magic bytes; the four outcomes; the one switch. Port of tkmChat
 * {@code test/services/media_cleanup/media_metadata_cleaner_test.dart}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class MediaMetadataCleanerTest {

    @TempDir
    Path tmp;

    private static final MetadataCleanOptions ON = MetadataCleanOptions.on();

    private Path file(String name, byte[] bytes) throws Exception {
        Path in = Files.createDirectories(tmp.resolve("in")).resolve(name);
        Files.write(in, bytes);
        return in;
    }

    private Path out() {
        return tmp.resolve("out");
    }

    private List<String> written() throws Exception {
        if (!Files.exists(out())) {
            return Arrays.asList();
        }
        try (Stream<Path> s = Files.list(out())) {
            return s.map(p -> p.getFileName().toString()).sorted().collect(Collectors.toList());
        }
    }

    @Test
    @DisplayName("⭐ ONE switch, default OFF: nothing is read, nothing is written, the source is used")
    public void offIsTheDefault() throws Exception {
        assertFalse(MetadataCleanOptions.off().isEnabled());
        assertTrue(MetadataCleanOptions.on().isEnabled());
        assertSame(MetadataCleanOptions.off(), MetadataCleanOptions.of(false));
        Path f = file("IMG_1.jpg", geotaggedJpeg());
        for (MetadataCleanOptions o : new MetadataCleanOptions[]{MetadataCleanOptions.off(), null}) {
            CleanOutcome r = MediaMetadataCleaner.clean(f, out(), o);
            assertEquals(CleanOutcome.Status.PASSTHROUGH, r.getStatus());
            assertEquals(f, r.getPath());
            assertEquals(MediaMetadataCleaner.DETAIL_OFF, r.getDetail());
            assertTrue(r.isSendable());
        }
        assertFalse(Files.exists(out()));
        // off never even opens the file
        CleanOutcome missing = MediaMetadataCleaner.clean(tmp.resolve("nope.jpg"), out(), MetadataCleanOptions.off());
        assertEquals(CleanOutcome.Status.PASSTHROUGH, missing.getStatus());
    }

    @Test
    @DisplayName("photo → cleaned copy in outDir, source kept")
    public void photo() throws Exception {
        byte[] src = geotaggedJpeg();
        Path f = file("IMG_1.jpeg", src);
        CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
        assertEquals(CleanOutcome.Status.CLEANED, r.getStatus());
        assertEquals(out().resolve("IMG_1_nolocation.jpg"), r.getPath());
        assertTrue(r.getDetail().contains("orientation 6 kept"), r.getDetail());
        assertFalse(hasBytes(Files.readAllBytes(r.getPath()), "Sony"));
        assertArrayEquals(src, Files.readAllBytes(f));

        // the bytes decide the extension, not the name
        Path lying = file("really_a_png.jpg", withChunksAfterIhdr(structuralPng(), chunk("tEXt", l1("a\0b"))));
        assertEquals(out().resolve("really_a_png_nolocation.png"), MediaMetadataCleaner.clean(lying, out(), ON).getPath());
    }

    @Test
    @DisplayName("video (by bytes, even under a .bin name) → cleaned, same size")
    public void video() throws Exception {
        byte[] src = androidMp4("+46.0+8.9/");
        Path f = file("clip.bin", src);
        CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
        assertEquals(CleanOutcome.Status.CLEANED, r.getStatus());
        assertEquals(out().resolve("clip_nolocation.bin"), r.getPath());
        assertEquals(src.length, Files.size(r.getPath()));
        assertFalse(hasBytes(Files.readAllBytes(r.getPath()), "©xyz"));
        assertTrue(r.getDetail().contains("moov/udta"), r.getDetail());
        assertEquals(out().resolve("noext_nolocation.mp4"),
                MediaMetadataCleaner.clean(file("noext", src), out(), ON).getPath());
    }

    @Test
    @DisplayName("video with nothing to remove → unchanged, no copy left behind")
    public void videoUnchanged() throws Exception {
        Path f = file("clean.mp4", androidMp4(null, false, 0, false, false));
        CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
        assertEquals(CleanOutcome.Status.UNCHANGED, r.getStatus());
        assertEquals(f, r.getPath());
        assertEquals(Arrays.asList(), written());
    }

    @Test
    @DisplayName("PDF → cleaned, same length")
    public void pdfCleaned() throws Exception {
        Path f = file("notes.pdf", pdf());
        CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
        assertEquals(CleanOutcome.Status.CLEANED, r.getStatus());
        assertEquals(out().resolve("notes_nolocation.pdf"), r.getPath());
        assertEquals(Files.size(f), Files.size(r.getPath()));
        String text = new String(Files.readAllBytes(r.getPath()), StandardCharsets.ISO_8859_1);
        assertFalse(text.contains("Giovanni"));
        assertTrue(text.contains("Quarterly notes"));
        assertEquals("7 info values, 1 XMP refs, 1 XMP streams, 1 packets", r.getDetail());
        // a second pass over the cleaned copy: nothing to remove
        assertEquals(CleanOutcome.Status.UNCHANGED, MediaMetadataCleaner.clean(r.getPath(), tmp.resolve("out2"), ON).getStatus());
    }

    @Test
    @DisplayName("Office document → cleaned")
    public void office() throws Exception {
        Path f = file("a.docx", docx(true, geotaggedJpeg()));
        CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
        assertEquals(CleanOutcome.Status.CLEANED, r.getStatus());
        assertEquals(out().resolve("a_nolocation.docx"), r.getPath());
        assertEquals("parts docProps/core.xml, docProps/app.xml, docProps/custom.xml (removed); images 1", r.getDetail());
        Map<String, byte[]> parts = unzip(Files.readAllBytes(r.getPath()));
        assertFalse(new String(parts.get("docProps/core.xml"), StandardCharsets.UTF_8).contains("Giovanni"));
    }

    @Test
    @DisplayName("a type with no cleaner → passthrough (the caller tells the user)")
    public void passthrough() throws Exception {
        CleanOutcome txt = MediaMetadataCleaner.clean(file("notes.txt", l1("hello")), out(), ON);
        assertEquals(CleanOutcome.Status.PASSTHROUGH, txt.getStatus());
        assertEquals("no cleaner for this file type", txt.getDetail());
        byte[] plainZip = zip(z("x.txt", "x"));
        assertEquals("no cleaner for this file type",
                MediaMetadataCleaner.clean(file("bundle.zip", plainZip), out(), ON).getDetail());
        CleanOutcome fake = MediaMetadataCleaner.clean(file("fake.docx", plainZip), out(), ON);
        assertEquals(CleanOutcome.Status.PASSTHROUGH, fake.getStatus());
        assertEquals("a zip, not an Office document", fake.getDetail());
        assertEquals(Arrays.asList(), written());
    }

    @Test
    @DisplayName("an image/video format nobody can vouch for → failed (left out)")
    public void unvouched() throws Exception {
        byte[] webp = cat(l1("RIFF"), bytes(0x24, 0, 0, 0), l1("WEBPVP8 "), filled(24, 0));
        byte[] webm = cat(bytes(0x1A, 0x45, 0xDF, 0xA3), filled(60, 1));
        // a ".jpg" that is really HEIC: the name lies, the bytes decide
        byte[] heic = cat(bytes(0, 0, 0, 0x18), l1("ftypheic"), filled(40, 0));
        for (Path f : new Path[]{file("sticker.webp", webp), file("clip.webm", webm), file("photo.jpg", heic),
            file("scan.tiff", bytes(0x49, 0x49, 0x2A, 0x00, 8, 0, 0, 0)), file("drawing.bmp", l1("BM......"))}) {
            CleanOutcome r = MediaMetadataCleaner.clean(f, out(), ON);
            assertEquals(CleanOutcome.Status.FAILED, r.getStatus(), f.toString());
            assertFalse(r.isSendable());
            assertTrue(r.getDetail().contains("vouch"), r.getDetail());
        }
        // As on the Dart side, WebP has no magic-byte rule: under a name that hides it, it is a
        // file of no known type — passed through, and the caller tells the user.
        assertEquals(CleanOutcome.Status.PASSTHROUGH,
                MediaMetadataCleaner.clean(file("sticker_without_extension", webp), out(), ON).getStatus());
        assertEquals(Arrays.asList(), written());
    }

    @Test
    @DisplayName("a broken file → failed, nothing written")
    public void broken() throws Exception {
        byte[] bad = androidMp4("+1+2/");
        bad[0] = 0x7F; // ftyp claims a huge size
        assertEquals(CleanOutcome.Status.FAILED, MediaMetadataCleaner.clean(file("bad.mp4", bad), out(), ON).getStatus());
        assertEquals(CleanOutcome.Status.FAILED, MediaMetadataCleaner.clean(
                file("bad.jpg", bytes(0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x45, 0x78)), out(), ON).getStatus());
        assertEquals(CleanOutcome.Status.FAILED, MediaMetadataCleaner.clean(
                file("bad.docx", cat(bytes(0x50, 0x4B, 3, 4), l1("[Content_Types].xml"), filled(40, 0))), out(), ON).getStatus());
        assertEquals(CleanOutcome.Status.FAILED,
                MediaMetadataCleaner.clean(tmp.resolve("in").resolve("missing.jpg"), out(), ON).getStatus());
        assertEquals(Arrays.asList(), written());
    }

    @Test
    @DisplayName("names: the last dot counts, a leading dot does not; media types as the Dart mime package")
    public void names() {
        assertEquals(".gz", MediaMetadataCleaner.extension("a.tar.gz"));
        assertEquals("", MediaMetadataCleaner.extension(".bashrc"));
        assertEquals("", MediaMetadataCleaner.extension("noext"));
        assertEquals("a.tar", MediaMetadataCleaner.stem("a.tar.gz"));
        assertEquals(".bashrc", MediaMetadataCleaner.stem(".bashrc"));
        assertEquals("image/webp", MediaMetadataCleaner.mediaTypeForName("A.WEBP"));
        assertEquals("application/octet-stream", MediaMetadataCleaner.mediaTypeForName("noext"));
        assertEquals("application/octet-stream", MediaMetadataCleaner.mediaTypeForName(null));
        assertTrue(MediaMetadataCleaner.mediaTypeForName("a.xlsx").contains("officedocument"));
    }
}
