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
import java.io.InputStream;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static io.takamaka.extra.media.SyntheticMedia.*;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C120 — {@link ImageLocationStripper}: the pixels of a photo go out, its GPS position (and the
 * rest of the metadata) does not. Port of wallet-extra-flutter
 * {@code test/imaging/image_location_stripper_test.dart}.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ImageLocationStripperTest {

    private static byte[] fixture() throws Exception {
        try (InputStream in = ImageLocationStripperTest.class.getClassLoader()
                .getResourceAsStream("media/progressive_exif.jpg")) {
            assertNotNull(in, "media/progressive_exif.jpg is missing from the test resources");
            return in.readAllBytes();
        }
    }

    // ---- JPEG ---------------------------------------------------------------------------

    @Test
    @DisplayName("JPEG: the GPS IFD, capture time and camera are gone; pixels are not")
    public void jpegGpsGonePixelsKept() {
        byte[] src = geotaggedJpeg(6, 64, 40);
        assertTrue(hasBytes(src, "Sony"), "fixture must carry the camera");
        assertTrue(hasBytes(src, bytes(0x88, 0x25)), "fixture must carry a GPS IFD pointer");

        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(r.isChanged());
        assertEquals("image/jpeg", r.getMediaType());
        assertTrue(r.getBytes().length < src.length);
        assertFalse(hasBytes(r.getBytes(), "Sony"));
        assertFalse(hasBytes(r.getBytes(), "2026:09:16"));
        assertFalse(hasBytes(r.getBytes(), bytes(0x88, 0x25)));

        // The entropy-coded data is copied byte for byte: both decode to the same pixels.
        assertArrayEquals(pixels(src), pixels(r.getBytes()));
    }

    @Test
    @DisplayName("JPEG: the EXIF orientation is preserved, and it is the ONLY tag left")
    public void jpegOrientationPreserved() {
        for (int o : new int[]{2, 3, 6, 8}) {
            for (boolean bigEndian : new boolean[]{true, false}) {
                byte[] src = withSegmentAfterSoi(encode(photoLike(64, 40), "jpg"), geoApp1(o, bigEndian));
                ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
                assertEquals(o, r.getOrientation());
                assertEquals(o, ExifOrientation.read(r.getBytes()), "orientation " + o);
                byte[] app1 = ImageLocationStripper.orientationOnlyApp1(o);
                assertEquals(36, app1.length);
                assertTrue(hasBytes(r.getBytes(), app1), "the minimal orientation-only APP1");
                assertEquals(src.length - (4 + 6 + 142) + 36, r.getBytes().length, "nothing else added");
            }
        }
    }

    @Test
    @DisplayName("JPEG: orientation 1 (or none) writes no EXIF block at all")
    public void jpegOrientationOneWritesNoExif() {
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(geotaggedJpeg(1, 64, 40));
        assertTrue(r.isChanged());
        assertFalse(hasBytes(r.getBytes(), "Exif"));
        assertArrayEquals(encode(photoLike(64, 40), "jpg"), r.getBytes());
    }

    @Test
    @DisplayName("JPEG: nothing to strip → unchanged, the same array")
    public void jpegPlainUnchanged() {
        byte[] plain = encode(photoLike(32, 32), "jpg");
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(plain);
        assertFalse(r.isChanged());
        assertSame(plain, r.getBytes());
    }

    @Test
    @DisplayName("JPEG: XMP (APP1, non-EXIF) and COM segments are dropped")
    public void jpegXmpAndComDropped() {
        String xmp = "http://ns.adobe.com/xap/1.0/\0<x:xmpmeta><exif:GPSLatitude>45,27.2N</exif:GPSLatitude></x:xmpmeta>";
        byte[] src = withSegmentAfterSoi(geotaggedJpeg(), segment(0xE1, l1(xmp)));
        src = withSegmentAfterSoi(src, segment(0xFE, l1("shot at home")));
        assertTrue(hasBytes(src, "GPSLatitude"));

        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertFalse(hasBytes(r.getBytes(), "xmpmeta"));
        assertFalse(hasBytes(r.getBytes(), "GPSLatitude"));
        assertFalse(hasBytes(r.getBytes(), "shot at home"));
        assertNotNull(pixels(r.getBytes()));
    }

    @Test
    @DisplayName("JPEG: ICC_PROFILE (APP2) and Adobe (APP14) are KEPT — colour, not metadata")
    public void jpegIccAndAdobeKept() {
        byte[] icc = segment(0xE2, cat(l1("ICC_PROFILE\0"), bytes(1, 1), filled(40, 0x5A)));
        byte[] adobe = segment(0xEE, cat(l1("Adobe"), bytes(0, 100, 0, 0, 0, 0, 1)));
        byte[] src = structuralJpeg(geoApp1(6, true), icc, adobe);
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(hasBytes(r.getBytes(), icc));
        assertTrue(hasBytes(r.getBytes(), adobe));
        assertFalse(hasBytes(r.getBytes(), "Sony"));
    }

    @Test
    @DisplayName("JPEG: other APPn (MPF, vendor blobs) are dropped")
    public void jpegOtherAppDropped() {
        byte[] mpf = segment(0xE2, cat(l1("MPF\0"), filled(30, 0x11)));
        byte[] vendor = segment(0xEA, cat(l1("AROT\0"), filled(30, 0x22)));
        byte[] src = withSegmentAfterSoi(withSegmentAfterSoi(geotaggedJpeg(), mpf), vendor);
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertFalse(hasBytes(r.getBytes(), "MPF"));
        assertFalse(hasBytes(r.getBytes(), "AROT"));
    }

    @Test
    @DisplayName("JPEG: anything after the EOI is cut off (motion-photo trailers)")
    public void jpegTrailerCut() {
        byte[] src = cat(geotaggedJpeg(), l1("....ftypmp42"), filled(200, 0x33));
        byte[] out = ImageLocationStripper.strip(src).getBytes();
        assertFalse(hasBytes(out, "ftypmp42"));
        assertEquals((byte) 0xFF, out[out.length - 2]);
        assertEquals((byte) 0xD9, out[out.length - 1]);

        // a trailer is a change even when there is no metadata segment at all
        byte[] plain = encode(photoLike(16, 16), "jpg");
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(cat(plain, l1("tail")));
        assertTrue(r.isChanged());
        assertArrayEquals(plain, r.getBytes());
    }

    @Test
    @DisplayName("JPEG: a real progressive file with EXIF — every scan copied, same pixels")
    public void jpegProgressiveFixture() throws Exception {
        byte[] src = fixture();
        assertTrue(hasBytes(src, "Exif"));
        assertTrue(hasBytes(src, bytes(0xFF, 0xC2)), "fixture must be progressive (SOF2)");

        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(r.isChanged());
        assertFalse(hasBytes(r.getBytes(), "Exif"));
        int[] ref = pixels(src);
        assertNotNull(ref);
        assertArrayEquals(ref, pixels(r.getBytes()));
    }

    @Test
    @DisplayName("JPEG: a truncated scan (no EOI) keeps what there is and supplies the EOI")
    public void jpegTruncatedScan() {
        byte[] whole = structuralJpeg();
        byte[] cut = Arrays.copyOf(whole, whole.length - 5);
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(cut);
        assertTrue(r.isChanged());
        assertArrayEquals(cat(cut, bytes(0xFF, 0xD9)), r.getBytes());
    }

    @Test
    @DisplayName("JPEG: a header that lies about a segment length is rejected")
    public void jpegLyingLengthRejected() {
        byte[] src = bytes(0xFF, 0xD8, 0xFF, 0xE1, 0xFF, 0xFF, 0x45, 0x78);
        assertThrows(MediaFormatException.class, () -> ImageLocationStripper.strip(src));
        assertThrows(MediaFormatException.class,
                () -> ImageLocationStripper.strip(bytes(0xFF, 0xD8, 0xFF, 0xD9, 0, 0)), "EOI before any scan");
    }

    // ---- PNG ----------------------------------------------------------------------------

    @Test
    @DisplayName("PNG: eXIf and text chunks are dropped, the rest is byte-identical")
    public void pngChunksDropped() {
        byte[] plain = encode(photoLike(24, 16), "png");
        byte[] src = withChunksAfterIhdr(plain,
                chunk("eXIf", cat(l1("Exif\0\0MM\0*"), filled(20, 1))),
                chunk("tEXt", l1("Comment\0taken at home")),
                chunk("iTXt", l1("XML:com.adobe.xmp\0\0\0\0\0<x:xmpmeta GPSLatitude/>")),
                chunk("zTXt", l1("Software\0\0x")));
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(r.isChanged());
        assertEquals("image/png", r.getMediaType());
        for (String t : new String[]{"eXIf", "tEXt", "iTXt", "zTXt", "GPSLatitude"}) {
            assertFalse(hasBytes(r.getBytes(), t), t);
        }
        assertArrayEquals(plain, r.getBytes(), "only the inserted chunks differ");
    }

    @Test
    @DisplayName("PNG: nothing to strip → unchanged; bytes after IEND are cut off")
    public void pngUnchangedAndTrailer() {
        byte[] plain = encode(photoLike(8, 8), "png");
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(plain);
        assertFalse(r.isChanged());
        assertSame(plain, r.getBytes());

        ImageLocationStripper.StrippedImage r2 = ImageLocationStripper.strip(cat(plain, filled(50, 0x44)));
        assertTrue(r2.isChanged());
        assertArrayEquals(plain, r2.getBytes());
    }

    @Test
    @DisplayName("PNG: an eXIf with orientation 6 is replaced by an orientation-only eXIf")
    public void pngOrientationKept() {
        byte[] plain = encode(photoLike(24, 16), "png");
        // A TIFF with IFD0 = {Orientation 6, GPS IFD pointer} and a GPS IFD.
        byte[] tiff = bytes(
                0x4D, 0x4D, 0x00, 0x2A, 0, 0, 0, 8,
                0, 2,
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, 6, 0, 0,
                0x88, 0x25, 0, 4, 0, 0, 0, 1, 0, 0, 0, 38,
                0, 0, 0, 0,
                0, 1,
                0x00, 0x01, 0, 2, 0, 0, 0, 2, 0x4E, 0, 0, 0,
                0, 0, 0, 0);
        assertEquals(6, ExifOrientation.readTiff(tiff));
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(withChunksAfterIhdr(plain, chunk("eXIf", tiff)));
        assertEquals(6, r.getOrientation());
        byte[] minimal = ImageLocationStripper.orientationOnlyTiff(6);
        assertEquals(26, minimal.length, "header + one IFD0 entry + terminator");
        assertEquals(6, ExifOrientation.readTiff(minimal));
        assertArrayEquals(withChunksAfterIhdr(plain, chunk("eXIf", minimal)), r.getBytes());
        assertFalse(hasBytes(r.getBytes(), bytes(0x88, 0x25)), "no GPS pointer");
        assertEquals(6, ExifOrientation.readPng(r.getBytes()));
        assertArrayEquals(pixels(plain), pixels(r.getBytes()));
    }

    @Test
    @DisplayName("PNG: an eXIf with orientation 1 is dropped outright; a lying chunk is rejected")
    public void pngOrientationOneDropped() {
        byte[] plain = encode(photoLike(8, 8), "png");
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(
                withChunksAfterIhdr(plain, chunk("eXIf", orientationTiff(1, true))));
        assertEquals(1, r.getOrientation());
        assertArrayEquals(plain, r.getBytes());

        byte[] lying = plain.clone();
        lying[PNG_IHDR_END] = 0x7F;
        assertThrows(MediaFormatException.class, () -> ImageLocationStripper.strip(lying));
        assertThrows(MediaFormatException.class,
                () -> ImageLocationStripper.strip(Arrays.copyOf(plain, plain.length - 12)), "no IEND");
    }

    // ---- GIF ----------------------------------------------------------------------------

    @Test
    @DisplayName("GIF: comment and XMP application extensions are dropped; the rest is byte-identical")
    public void gifCommentAndXmpDropped() {
        byte[] plain = encode(photoLike(16, 12), "gif");
        assertTrue(ImageLocationStripper.isSupported(plain));
        assertFalse(ImageLocationStripper.strip(plain).isChanged(), "the encoder's own GIF is clean");
        byte[] src = withBlocksAfterHeader(plain,
                commentExt("taken at home"),
                appExt("XMP DataXMP", l1("<x:xmpmeta><exif:GPSLatitude>45,27N</exif:GPSLatitude></x:xmpmeta>")));
        assertTrue(hasBytes(src, "GPSLatitude"));

        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(r.isChanged());
        assertEquals("image/gif", r.getMediaType());
        assertFalse(hasBytes(r.getBytes(), "GPSLatitude"));
        assertFalse(hasBytes(r.getBytes(), "taken at home"));
        assertFalse(hasBytes(r.getBytes(), "XMP Data"));
        assertArrayEquals(plain, r.getBytes());
    }

    @Test
    @DisplayName("GIF: the NETSCAPE2.0 / ANIMEXTS1.0 loop extensions are KEPT")
    public void gifLoopExtensionsKept() {
        byte[] src = structuralGif("89a", true,
                appExt("NETSCAPE2.0", bytes(1, 0, 0)),
                appExt("ANIMEXTS1.0", bytes(1, 0, 0)),
                commentExt("drop me"));
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(src);
        assertTrue(hasBytes(r.getBytes(), "NETSCAPE2.0"));
        assertTrue(hasBytes(r.getBytes(), "ANIMEXTS1.0"));
        assertFalse(hasBytes(r.getBytes(), "drop me"));
        assertArrayEquals(structuralGif("89a", true,
                appExt("NETSCAPE2.0", bytes(1, 0, 0)), appExt("ANIMEXTS1.0", bytes(1, 0, 0))), r.getBytes());
    }

    @Test
    @DisplayName("GIF: nothing to strip → unchanged; a trailer is cut; a missing trailer is supplied")
    public void gifUnchangedTrailerAndTruncation() {
        byte[] plain = structuralGif("89a", true);
        ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(plain);
        assertFalse(r.isChanged());
        assertSame(plain, r.getBytes());

        ImageLocationStripper.StrippedImage r2 = ImageLocationStripper.strip(cat(plain, l1("junk after")));
        assertTrue(r2.isChanged());
        assertArrayEquals(plain, r2.getBytes());

        ImageLocationStripper.StrippedImage r3 = ImageLocationStripper.strip(structuralGif("87a", false));
        assertTrue(r3.isChanged());
        assertArrayEquals(structuralGif("87a", true), r3.getBytes());

        assertThrows(MediaFormatException.class,
                () -> ImageLocationStripper.strip(structuralGif("89a", true, bytes(0x99, 1, 2))), "unknown block");
    }

    // ---- refusals -----------------------------------------------------------------------

    @Test
    @DisplayName("formats it cannot vouch for are REFUSED, never passed through")
    public void refusals() {
        byte[] webp = cat(l1("RIFF"), bytes(0x24, 0, 0, 0), l1("WEBPVP8 "), filled(24, 0));
        assertFalse(ImageLocationStripper.isSupported(webp));
        assertThrows(UnsupportedMediaFormatException.class, () -> ImageLocationStripper.strip(webp));
        byte[] tiff = cat(bytes(0x49, 0x49, 0x2A, 0x00, 8, 0, 0, 0), filled(16, 0));
        assertThrows(UnsupportedMediaFormatException.class, () -> ImageLocationStripper.strip(tiff));
        assertThrows(UnsupportedMediaFormatException.class, () -> ImageLocationStripper.strip(l1("not an image")));
        assertThrows(UnsupportedMediaFormatException.class, () -> ImageLocationStripper.strip(new byte[0]));
        assertThrows(UnsupportedMediaFormatException.class, () -> ImageLocationStripper.strip(null));
    }
}
