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
 * C120 (videos) — {@link Mp4MetadataCleaner}: location and timestamps gone, every sample byte and
 * every offset untouched, no re-encode. Port of tkmChat
 * {@code test/services/media_cleanup/mp4_metadata_cleaner_test.dart}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class Mp4MetadataCleanerTest {

    @TempDir
    Path tmp;

    private Mp4MetadataCleaner.Mp4CleanResult last;

    private byte[] cleanBytes(byte[] src) throws Exception {
        Path in = tmp.resolve("in.mp4");
        Path out = tmp.resolve("out.mp4");
        Files.write(in, src);
        last = Mp4MetadataCleaner.cleanFile(in, out);
        assertArrayEquals(src, Files.readAllBytes(in), "the source is never modified");
        return Files.readAllBytes(out);
    }

    @Test
    @DisplayName("an Android camera layout: udta/©xyz and meta become free boxes, mdat is untouched")
    public void androidLayout() throws Exception {
        byte[] src = androidMp4("+46.0108+008.9562/");
        int udtaOffset = lastUdtaOffset;
        assertTrue(hasBytes(src, "©xyz"));
        assertTrue(hasBytes(src, "+46.0108"));

        byte[] out = cleanBytes(src);
        assertEquals(src.length, out.length, "nothing moves");
        assertTrue(last.isChanged());
        assertTrue(last.getBlanked().contains("moov/udta@" + udtaOffset), last.getBlanked().toString());
        assertTrue(last.getBlanked().stream().anyMatch(b -> b.startsWith("moov/meta@")));
        assertFalse(hasBytes(out, "©xyz"));
        assertFalse(hasBytes(out, "+46.0108"));
        assertFalse(hasBytes(out, "com.android.version"));

        // Same box skeleton: ftyp, moov(mvhd, free, free, trak(...)), mdat.
        List<BoxInfo> a = walkBoxes(src);
        List<BoxInfo> b = walkBoxes(out);
        assertEquals(a.size(), b.size());
        for (int i = 0; i < a.size(); i++) {
            assertEquals(a.get(i).offset, b.get(i).offset);
            assertEquals(a.get(i).size, b.get(i).size);
            String t = a.get(i).type;
            assertEquals(t.equals("udta") || t.equals("meta") ? "free" : t, b.get(i).type, t + " at " + a.get(i).offset);
        }
        // The picture: byte-identical.
        BoxInfo mdat = firstBox(src, "mdat");
        assertArrayEquals(Arrays.copyOfRange(src, mdat.offset, mdat.offset + mdat.size),
                Arrays.copyOfRange(out, mdat.offset, mdat.offset + mdat.size));
        // The blanked payloads are ZERO, not merely relabelled.
        BoxInfo udta = firstBox(src, "udta");
        for (int i = udta.offset + 8; i < udta.offset + udta.size; i++) {
            assertEquals(0, out[i]);
        }
    }

    @Test
    @DisplayName("creation/modification time are zeroed in mvhd, tkhd and mdhd")
    public void timestampsZeroed() throws Exception {
        byte[] src = androidMp4("+1.0+2.0/", true, 0xD1234567L, false, false);
        byte[] out = cleanBytes(src);
        assertEquals(6, last.getTimestampsZeroed(), "2 per header x mvhd, tkhd, mdhd");
        for (String t : new String[]{"mvhd", "tkhd", "mdhd"}) {
            BoxInfo box = firstBox(out, t);
            for (int i = box.offset + 12; i < box.offset + 20; i++) {
                assertEquals(0, out[i], t);
            }
            // the rest of the header (timescale, duration…) is still there
            assertArrayEquals(Arrays.copyOfRange(src, box.offset + 20, box.offset + box.size),
                    Arrays.copyOfRange(out, box.offset + 20, box.offset + box.size), t + " tail");
        }
    }

    @Test
    @DisplayName("version-1 headers (64-bit times) and a 64-bit box size are handled")
    public void version1AndLargeSize() throws Exception {
        byte[] v1 = cat(bytes(1, 0, 0, 0), filled(8, 0x11), filled(8, 0), u32(1000), filled(8, 0x22));
        byte[] large = cat(u32(1), l1("uuid"), u32(0), u32(16 + 20), filled(20, 0x7A));
        byte[] src = cat(box("ftyp", l1("isom"), u32(0), l1("isom")), large, box("moov", box("mvhd", v1)));
        byte[] out = cleanBytes(src);
        assertEquals(1, last.getTimestampsZeroed(), "only the creation time was non-zero");
        assertEquals(Arrays.asList("uuid@20"), last.getBlanked());
        assertArrayEquals(cat(box("ftyp", l1("isom"), u32(0), l1("isom")),
                cat(u32(1), l1("free"), u32(0), u32(36), filled(20, 0)),
                box("moov", box("mvhd", cat(bytes(1, 0, 0, 0), filled(16, 0), u32(1000), filled(8, 0x22))))), out);
    }

    @Test
    @DisplayName("a trak-level udta and a top-level XMP uuid box are blanked too")
    public void trakUdtaAndUuid() throws Exception {
        byte[] src = androidMp4("+1.0+2.0/", true, 0xD0000001L, true, true);
        assertTrue(hasBytes(src, "xmpmeta"));
        byte[] out = cleanBytes(src);
        assertTrue(last.getBlanked().stream().anyMatch(b -> b.startsWith("moov/trak/udta@")));
        assertTrue(last.getBlanked().stream().anyMatch(b -> b.startsWith("uuid@")));
        assertFalse(hasBytes(out, "xmpmeta"));
        assertFalse(hasBytes(out, "somewhere"));
        assertEquals(src.length, out.length);
    }

    @Test
    @DisplayName("a clean MP4 reports no change and stays byte-identical")
    public void cleanStaysIdentical() throws Exception {
        byte[] src = androidMp4(null, false, 0, false, false);
        byte[] out = cleanBytes(src);
        assertFalse(last.isChanged());
        assertArrayEquals(src, out);
    }

    @Test
    @DisplayName("WebM (no ftyp) and HEIC are refused before anything is written")
    public void refusals() throws Exception {
        byte[] webm = cat(bytes(0x1A, 0x45, 0xDF, 0xA3), filled(60, 0x11));
        Path src = tmp.resolve("clip.webm");
        Files.write(src, webm);
        assertThrows(UnsupportedMediaFormatException.class,
                () -> Mp4MetadataCleaner.cleanFile(src, tmp.resolve("out.webm")));
        assertArrayEquals(webm, Files.readAllBytes(src));
        assertFalse(Files.exists(tmp.resolve("out.webm")));

        byte[] heic = cat(bytes(0, 0, 0, 0x18), l1("ftypheic"), u32(0), l1("mif1heic"),
                box("meta", bytes(0, 0, 0, 0), filled(40, 1)));
        Path h = tmp.resolve("photo.heic");
        Files.write(h, heic);
        assertThrows(UnsupportedMediaFormatException.class,
                () -> Mp4MetadataCleaner.cleanFile(h, tmp.resolve("out.heic")));
        assertFalse(Files.exists(tmp.resolve("out.heic")));
    }

    @Test
    @DisplayName("a box that lies about its size is a MediaFormatException, not a silent pass")
    public void lyingBox() throws Exception {
        byte[] src = androidMp4("+1.0+2.0/");
        src[firstBox(src, "moov").offset] = 0x7F; // run past the file
        Path bad = tmp.resolve("bad.mp4");
        Files.write(bad, src);
        assertThrows(MediaFormatException.class, () -> Mp4MetadataCleaner.cleanFile(bad, tmp.resolve("out.mp4")));

        // a 64-bit size near Long.MAX_VALUE must not overflow the bounds check into a "valid" box
        byte[] huge = cat(box("ftyp", l1("isom"), u32(0), l1("isom")),
                u32(1), l1("uuid"), u32(0x7FFFFFFFL), u32(0xFFFFFFF0L), filled(32, 0x7A));
        Path h = tmp.resolve("huge.mp4");
        Files.write(h, huge);
        assertThrows(MediaFormatException.class, () -> Mp4MetadataCleaner.cleanFile(h, tmp.resolve("out2.mp4")));
        assertEquals(huge.length, Files.size(tmp.resolve("out2.mp4")), "the copy was not grown");
    }
}
