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
 * C120 (documents) — {@link OoxmlMetadataCleaner}: author/company/dates and the GPS of embedded
 * photos gone; the document body untouched. Port of tkmChat
 * {@code test/services/media_cleanup/ooxml_metadata_cleaner_test.dart}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class OoxmlMetadataCleanerTest {

    @TempDir
    Path tmp;

    private OoxmlMetadataCleaner.OoxmlCleanResult clean(byte[] zip) throws Exception {
        Path f = tmp.resolve("in.docx");
        Files.write(f, zip);
        OoxmlMetadataCleaner.OoxmlCleanResult r = OoxmlMetadataCleaner.clean(f);
        assertArrayEquals(zip, Files.readAllBytes(f), "the source is never modified");
        return r;
    }

    private static String utf8(byte[] b) {
        return new String(b, StandardCharsets.UTF_8);
    }

    @Test
    @DisplayName("core/app scrubbed, custom removed, embedded photo stripped, body identical")
    public void scrubbed() throws Exception {
        byte[] image = geotaggedJpeg();
        byte[] src = docx(true, image);
        assertTrue(OoxmlMetadataCleaner.isOoxml(src));

        OoxmlMetadataCleaner.OoxmlCleanResult r = clean(src);
        assertTrue(r.isChanged());
        assertEquals(Arrays.asList("docProps/core.xml", "docProps/app.xml", "docProps/custom.xml (removed)"),
                r.getPropertyParts());
        assertEquals(Arrays.asList("word/media/image1.jpeg"), r.getImagesStripped());

        Map<String, byte[]> out = unzip(r.getBytes());
        String core = utf8(out.get("docProps/core.xml"));
        for (String gone : new String[]{"Giovanni", "tech@example.org", "2026-09-16", "Budget"}) {
            assertFalse(core.contains(gone), gone);
        }
        assertEquals(OoxmlMetadataCleaner.EMPTY_CORE, core);
        assertEquals(OoxmlMetadataCleaner.EMPTY_APP, utf8(out.get("docProps/app.xml")));
        assertNull(out.get("docProps/custom.xml"));
        assertFalse(utf8(out.get("[Content_Types].xml")).contains("custom.xml"));
        assertTrue(utf8(out.get("[Content_Types].xml")).contains("/docProps/app.xml"), "only that Override went");
        assertFalse(utf8(out.get("_rels/.rels")).contains("custom.xml"));
        assertTrue(utf8(out.get("_rels/.rels")).contains("rId3"));
        assertEquals(DOCUMENT, utf8(out.get("word/document.xml")));

        byte[] cleanedImage = out.get("word/media/image1.jpeg");
        assertArrayEquals(ImageLocationStripper.strip(image).getBytes(), cleanedImage);
        assertFalse(hasBytes(cleanedImage, "Sony"));
        assertArrayEquals(pixels(image), pixels(cleanedImage));

        // the rewritten [Content_Types].xml and _rels/.rels move to the end, as on the Dart side
        assertEquals(Arrays.asList("docProps/core.xml", "docProps/app.xml", "word/document.xml",
                "word/media/image1.jpeg", "[Content_Types].xml", "_rels/.rels"),
                out.keySet().stream().collect(Collectors.toList()));
    }

    @Test
    @DisplayName("without custom.xml the entry order is the source's; directories, stored and broken media survive")
    public void orderAndOddEntries() throws Exception {
        byte[] broken = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x45, 0x78);
        byte[] png = withChunksAfterIhdr(structuralPng(), chunk("tEXt", l1("Author\0me")));
        byte[] src = zip(
                z("[Content_Types].xml", "<Types/>"),
                new Z("ppt/media/", new byte[0], true),
                z("ppt/media/broken.jpg", broken),
                stored("ppt/media/pic.png", png),
                stored("ppt/media/clean.gif", structuralGif("89a", true)),
                z("ppt/media/notes.bin", l1("not an image")),
                z("ppt/media/empty.jpg", new byte[0]),
                z("docProps/app.xml", APP));
        OoxmlMetadataCleaner.OoxmlCleanResult r = clean(src);
        assertEquals(Arrays.asList("docProps/app.xml"), r.getPropertyParts());
        assertEquals(Arrays.asList("ppt/media/pic.png"), r.getImagesStripped());
        Map<String, byte[]> out = unzip(r.getBytes());
        assertEquals(Arrays.asList("[Content_Types].xml", "ppt/media/", "ppt/media/broken.jpg", "ppt/media/pic.png",
                "ppt/media/clean.gif", "ppt/media/notes.bin", "ppt/media/empty.jpg", "docProps/app.xml"),
                out.keySet().stream().collect(Collectors.toList()));
        assertArrayEquals(broken, out.get("ppt/media/broken.jpg"), "a broken embedded image is kept as it is");
        assertArrayEquals(structuralPng(), out.get("ppt/media/pic.png"));
        assertArrayEquals(structuralGif("89a", true), out.get("ppt/media/clean.gif"));
        assertEquals("<Types/>", utf8(out.get("[Content_Types].xml")));
    }

    @Test
    @DisplayName("non-ASCII bytes around the removed Override survive exactly")
    public void nonAsciiContentTypes() throws Exception {
        String ct = "<Types><!-- café 中 --><Override PartName=\"/docProps/custom.xml\" ContentType=\"x\"/></Types>";
        byte[] src = zip(z("[Content_Types].xml", ct), z("docProps/custom.xml", CUSTOM));
        Map<String, byte[]> out = unzip(clean(src).getBytes());
        assertEquals("<Types><!-- café 中 --></Types>", utf8(out.get("[Content_Types].xml")));
    }

    @Test
    @DisplayName("a plain zip is not OOXML; a package with nothing to scrub is returned as-is")
    public void notOoxmlAndUnchanged() throws Exception {
        assertFalse(OoxmlMetadataCleaner.isOoxml(zip(z("readme.txt", "hi"))));
        assertFalse(OoxmlMetadataCleaner.isOoxml(null));
        OoxmlMetadataCleaner.OoxmlCleanResult r = clean(zip(z("[Content_Types].xml", "<Types/>"), z("word/document.xml", DOCUMENT)));
        assertFalse(r.isChanged());
        assertNull(r.getBytes());
    }
}
