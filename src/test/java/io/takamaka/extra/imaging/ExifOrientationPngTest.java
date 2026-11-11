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

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C127 — a PNG's {@code eXIf} orientation is READ ({@link ExifOrientation#readPng}) and BAKED into
 * the preview. {@code ImageIO} does not read the chunk, so before this a PNG tagged 6 previewed
 * sideways (256x171 for the C121 device fixture, which should be 171x256).
 *
 * <p>Mirrors {@code wallet-extra-flutter/test/imaging/png_exif_orientation_test.dart}, case for
 * case, and adds the cap on the chunk walk.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ExifOrientationPngTest {

    private static final int MAX_BYTES = 50 * 1024;

    private static final int N = ExifOrientation.NORMAL;

    private static byte[] small(int orientation, boolean bigEndian) {
        return PngTestSupport.tagged(PngTestSupport.noise(8, 8, 7), 8, 8, orientation, bigEndian);
    }

    // ---- readPng --------------------------------------------------------------------------

    @Test
    @DisplayName("readPng reads the eXIf orientation, readImage dispatches to it, read() does not claim a PNG")
    public void readsTheChunkAndDispatches() {
        byte[] tagged = PngTestSupport.tagged(PngTestSupport.noise(40, 20, 7), 40, 20, 6, true);
        assertEquals(6, ExifOrientation.readPng(tagged));
        assertEquals(6, ExifOrientation.readImage(tagged));
        assertEquals(N, ExifOrientation.read(tagged), "the JPEG reader must not claim a PNG");
    }

    @Test
    @DisplayName("every value 1..8 round-trips, in both TIFF byte orders")
    public void everyValueBothByteOrders() {
        for (int o = 1; o <= 8; o++) {
            assertEquals(o, ExifOrientation.readPng(small(o, true)), "MM orientation " + o);
            assertEquals(o, ExifOrientation.readPng(small(o, false)), "II orientation " + o);
            assertEquals(o, ExifOrientation.readImage(small(o, false)), "readImage orientation " + o);
        }
    }

    @Test
    @DisplayName("a PNG without eXIf is NORMAL — and so is one written by ImageIO")
    public void noChunkIsNormal() throws Exception {
        assertEquals(N, ExifOrientation.readPng(PngTestSupport.plain(PngTestSupport.noise(8, 8, 7), 8, 8)));
        BufferedImage img = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
        java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
        ImageIO.write(img, "PNG", b);
        assertEquals(N, ExifOrientation.readPng(b.toByteArray()));
        assertEquals(N, ExifOrientation.readImage(b.toByteArray()));
    }

    @Test
    @DisplayName("out-of-range values, a wrong type and a wrong count are refused")
    public void illegalTagContentIsRefused() {
        assertEquals(N, ExifOrientation.readPng(small(0, true)));
        assertEquals(N, ExifOrientation.readPng(small(9, true)));
        assertEquals(N, ExifOrientation.readPng(small(0x0600, true)), "6 in the wrong byte: not 6");

        int[] px = PngTestSupport.noise(8, 8, 7);
        byte[] wrongType = PngTestSupport.orientationTiff(6, true);
        wrongType[13] = 4; // LONG instead of SHORT
        assertEquals(N, ExifOrientation.readPng(
                PngTestSupport.png(px, 8, 8, false, PngTestSupport.chunk("eXIf", wrongType), null)));
        byte[] wrongCount = PngTestSupport.orientationTiff(6, true);
        wrongCount[17] = 2;
        assertEquals(N, ExifOrientation.readPng(
                PngTestSupport.png(px, 8, 8, false, PngTestSupport.chunk("eXIf", wrongCount), null)));
    }

    @Test
    @DisplayName("fails open: null, empty, not a PNG, cut inside IHDR, cut inside the eXIf chunk")
    public void failsOpenOnTruncation() {
        byte[] tagged = small(6, true);
        assertEquals(N, ExifOrientation.readPng(null));
        assertEquals(N, ExifOrientation.readPng(new byte[0]));
        assertEquals(N, ExifOrientation.readPng(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1}));
        assertEquals(N, ExifOrientation.readPng(PngTestSupport.head(tagged, 20)), "cut inside IHDR");
        assertEquals(N, ExifOrientation.readPng(
                PngTestSupport.head(tagged, 8 + PngTestSupport.IHDR_CHUNK_LEN + 9)),
                "cut inside the eXIf chunk");
        // Every possible cut: never a throw, and never a value the whole file does not hold.
        for (int keep = 0; keep <= tagged.length; keep++) {
            int o = ExifOrientation.readPng(PngTestSupport.head(tagged, keep));
            assertTrue(o == N || o == 6, "cut at " + keep + " answered " + o);
        }
    }

    @Test
    @DisplayName("a truncated or garbage TIFF inside a well-formed eXIf chunk is NORMAL")
    public void malformedTiffInsideAValidChunk() {
        int[] px = PngTestSupport.noise(8, 8, 7);
        byte[] tiff = PngTestSupport.orientationTiff(6, true);
        // 22 bytes = header + count + the entry: the shortest legal read (the next-IFD offset is
        // not needed). Anything shorter cuts the entry.
        for (int keep = 0; keep < PngTestSupport.TIFF_LEN; keep++) {
            byte[] exif = PngTestSupport.chunk("eXIf", PngTestSupport.head(tiff, keep));
            int expected = keep >= 22 ? 6 : N;
            assertEquals(expected, ExifOrientation.readPng(PngTestSupport.png(px, 8, 8, false, exif, null)),
                    "TIFF cut to " + keep + " bytes");
        }
        byte[] garbage = new byte[64];
        java.util.Arrays.fill(garbage, (byte) 0xAA);
        assertEquals(N, ExifOrientation.readPng(
                PngTestSupport.png(px, 8, 8, false, PngTestSupport.chunk("eXIf", garbage), null)));
        // An IFD offset pointing outside the chunk must not read the bytes that follow it.
        byte[] farIfd = PngTestSupport.orientationTiff(6, true);
        farIfd[7] = 100;
        assertEquals(N, ExifOrientation.readPng(
                PngTestSupport.png(px, 8, 8, false, PngTestSupport.chunk("eXIf", farIfd), null)));
    }

    @Test
    @DisplayName("a chunk length that lies — past the end, or above 2^31 — is NORMAL")
    public void hostileChunkLength() {
        byte[] tagged = small(6, true);
        final int lenAt = 8 + PngTestSupport.IHDR_CHUNK_LEN; // first length byte of the eXIf chunk
        byte[] big = tagged.clone();
        big[lenAt] = 0x7F;
        assertEquals(N, ExifOrientation.readPng(big));
        byte[] negative = tagged.clone();
        negative[lenAt] = (byte) 0xFF; // 0xFF00001A: negative as an int
        assertEquals(N, ExifOrientation.readPng(negative));
        byte[] allOnes = tagged.clone();
        for (int i = 0; i < 4; i++) {
            allOnes[lenAt + i] = (byte) 0xFF;
        }
        assertEquals(N, ExifOrientation.readPng(allOnes));
        // The same lie on IHDR, the first chunk walked.
        byte[] ihdr = tagged.clone();
        ihdr[8] = 0x7F;
        assertEquals(N, ExifOrientation.readPng(ihdr));
    }

    @Test
    @DisplayName("an eXIf chunk after IDAT is ignored: the tag must precede the pixels")
    public void chunkAfterThePixelsIsIgnored() {
        int[] px = PngTestSupport.noise(8, 8, 7);
        byte[] exif = PngTestSupport.chunk("eXIf", PngTestSupport.orientationTiff(6, true));
        assertEquals(N, ExifOrientation.readPng(PngTestSupport.png(px, 8, 8, false, null, exif)));
    }

    @Test
    @DisplayName("the chunk walk is capped at 256 chunks")
    public void chunkWalkIsCapped() {
        int[] px = PngTestSupport.noise(8, 8, 7);
        byte[] exif = PngTestSupport.chunk("eXIf", PngTestSupport.orientationTiff(6, true));
        byte[] filler = PngTestSupport.chunk("tEXt", new byte[0]);
        // IHDR is chunk 0, so 254 fillers put eXIf at index 255 — the last one examined.
        assertEquals(6, ExifOrientation.readPng(PngTestSupport.png(px, 8, 8, false,
                PngTestSupport.concat(repeat(filler, 254), exif), null)), "eXIf as the 256th chunk");
        assertEquals(N, ExifOrientation.readPng(PngTestSupport.png(px, 8, 8, false,
                PngTestSupport.concat(repeat(filler, 255), exif), null)), "eXIf as the 257th chunk");
        assertEquals(N, ExifOrientation.readPng(PngTestSupport.png(px, 8, 8, false,
                PngTestSupport.concat(repeat(filler, 5000), exif), null)));
    }

    @Test
    @DisplayName("a truncated HEAD of a file still answers when the chunk is inside it")
    public void headOfAFileIsEnough() {
        byte[] tagged = PngTestSupport.tagged(PngTestSupport.noise(200, 100, 7), 200, 100, 8, true);
        final int through = 8 + PngTestSupport.IHDR_CHUNK_LEN + 12 + PngTestSupport.TIFF_LEN;
        assertEquals(8, ExifOrientation.readPng(PngTestSupport.head(tagged, through + 16)));
        assertEquals(8, ExifOrientation.readPng(PngTestSupport.head(tagged, through - 4)),
                "the chunk's own CRC is not needed");
    }

    @Test
    @DisplayName("readTiff: offsets are relative to the byte-order mark, and `end` is a hard wall")
    public void readTiffDirect() {
        byte[] tiff = PngTestSupport.orientationTiff(5, false);
        assertEquals(5, ExifOrientation.readTiff(tiff));
        assertEquals(5, ExifOrientation.readTiff(tiff, 0, tiff.length));
        assertEquals(5, ExifOrientation.readTiff(tiff, 0, Integer.MAX_VALUE), "end is clamped");
        byte[] padded = PngTestSupport.concat(new byte[11], tiff, new byte[3]);
        assertEquals(5, ExifOrientation.readTiff(padded, 11, 11 + tiff.length));
        assertEquals(N, ExifOrientation.readTiff(padded, 11, 11 + 21), "the entry does not fit before end");
        assertEquals(N, ExifOrientation.readTiff(padded, 10, padded.length), "not at the byte-order mark");
        assertEquals(N, ExifOrientation.readTiff(null));
        assertEquals(N, ExifOrientation.readTiff(null, 0, 0));
        assertEquals(N, ExifOrientation.readTiff(tiff, -1, tiff.length));
        assertEquals(N, ExifOrientation.readTiff(tiff, Integer.MAX_VALUE, Integer.MAX_VALUE));
        assertEquals(N, ExifOrientation.readTiff(tiff, 0, -5));
    }

    @Test
    @DisplayName("readImage: JPEG, PNG, anything else; and nothing ever throws")
    public void readImageDispatchAndFuzz() throws Exception {
        byte[] jpeg = ThumbnailHelper.encodeJpeg(new BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB), 0.8f);
        byte[] taggedJpeg = ExifTestSupport.withApp1(jpeg, ExifTestSupport.orientationExif(7, true));
        assertEquals(7, ExifOrientation.readImage(taggedJpeg));
        assertEquals(N, ExifOrientation.readPng(taggedJpeg), "the PNG reader must not claim a JPEG");
        assertEquals(N, ExifOrientation.readImage(null));
        assertEquals(N, ExifOrientation.readImage(new byte[3]));
        assertEquals(N, ExifOrientation.readImage("GIF89a-not-really".getBytes("US-ASCII")));
        // A bare TIFF is not an image file this dispatcher knows.
        assertEquals(N, ExifOrientation.readImage(PngTestSupport.orientationTiff(6, true)));

        Random rnd = new Random(127);
        byte[] seedPng = small(6, false);
        for (int i = 0; i < 4000; i++) {
            byte[] b;
            if (i % 2 == 0) {
                b = new byte[rnd.nextInt(96)];
                rnd.nextBytes(b);
                if (b.length >= 8 && i % 4 == 0) {
                    System.arraycopy(PngTestSupport.SIGNATURE, 0, b, 0, 8);
                }
            } else {
                b = seedPng.clone(); // a real file with a few bytes flipped
                for (int k = 0; k < 3; k++) {
                    b[rnd.nextInt(b.length)] = (byte) rnd.nextInt(256);
                }
            }
            int o = ExifOrientation.readImage(b);
            assertTrue(o >= 1 && o <= 8, "out of range: " + o);
            int p = ExifOrientation.readPng(b);
            assertTrue(p >= 1 && p <= 8, "out of range: " + p);
        }
    }

    // ---- the preview ----------------------------------------------------------------------

    @Test
    @DisplayName("⭐ a 400x200 PNG with eXIf orientation 6 previews PORTRAIT")
    public void taggedPngPreviewsPortrait() {
        byte[] tagged = PngTestSupport.tagged(PngTestSupport.noise(400, 200, 7), 400, 200, 6, true);
        assertTrue(tagged.length > MAX_BYTES, "fixture must take the preview path: " + tagged.length);

        ThumbnailResult r = ThumbnailHelper.generatePreview(tagged, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertEquals(6, r.getExifOrientation());
        assertEquals(128, r.getPreviewWidth());
        assertEquals(256, r.getPreviewHeight());
        assertEquals(200, r.getSourceWidth(), "displayed width");
        assertEquals(400, r.getSourceHeight(), "displayed height");
        assertEquals(ThumbnailHelper.MIME_JPEG, r.getPreviewMediaType());
    }

    @Test
    @DisplayName("the same PNG untagged previews LANDSCAPE — the control (no double rotation)")
    public void untaggedPngPreviewsLandscape() {
        byte[] plain = PngTestSupport.plain(PngTestSupport.noise(400, 200, 7), 400, 200);
        ThumbnailResult r = ThumbnailHelper.generatePreview(plain, MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        assertEquals(N, r.getExifOrientation());
        assertEquals(256, r.getPreviewWidth());
        assertEquals(128, r.getPreviewHeight());
    }

    @Test
    @DisplayName("the C121 shape: 1080x720 tagged 6 previews 171x256, not 256x171")
    public void c121ShapePreviewsUpright() {
        int[] px = PngTestSupport.blocks(1080, 720, 40, 121);
        ThumbnailResult r = ThumbnailHelper.generatePreviewForBlob(
                PngTestSupport.tagged(px, 1080, 720, 6, true), MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        assertEquals(171, r.getPreviewWidth());
        assertEquals(256, r.getPreviewHeight());
    }

    @Test
    @DisplayName("orientation 3 keeps the shape but turns the pixels; 6 moves the corners a quarter turn")
    public void pixelsAreActuallyTurned() throws Exception {
        final int w = 60;
        final int h = 30;
        // Four flat quadrants: JPEG keeps each one's colour well away from its edges.
        int[] px = new int[w * h];
        final int tl = 0xFFE00000;
        final int tr = 0xFF00C000;
        final int bl = 0xFF0000E0;
        final int br = 0xFFE0E000;
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                px[y * w + x] = y < h / 2 ? (x < w / 2 ? tl : tr) : (x < w / 2 ? bl : br);
            }
        }

        BufferedImage plain = preview(PngTestSupport.plain(px, w, h));
        assertEquals(60, plain.getWidth());
        assertNear(tl, plain.getRGB(5, 5), "control: top-left stays top-left");

        BufferedImage o3 = preview(PngTestSupport.tagged(px, w, h, 3, true));
        assertEquals(60, o3.getWidth());
        assertEquals(30, o3.getHeight());
        assertNear(br, o3.getRGB(5, 5), "180: top-left shows the stored bottom-right");
        assertNear(tl, o3.getRGB(54, 24), "180: bottom-right shows the stored top-left");

        BufferedImage o6 = preview(PngTestSupport.tagged(px, w, h, 6, false));
        assertEquals(30, o6.getWidth());
        assertEquals(60, o6.getHeight());
        assertNear(bl, o6.getRGB(5, 5), "90 CW: top-left shows the stored bottom-left");
        assertNear(tl, o6.getRGB(24, 5), "90 CW: top-right shows the stored top-left");

        BufferedImage o8 = preview(PngTestSupport.tagged(px, w, h, 8, false));
        assertEquals(30, o8.getWidth());
        assertNear(tr, o8.getRGB(5, 5), "90 CCW: top-left shows the stored top-right");
    }

    @Test
    @DisplayName("a tagged PNG with real transparency stays a PNG preview, upright")
    public void alphaPngIsTurnedAndStaysPng() {
        int[] px = PngTestSupport.blocks(300, 200, 20, 127, true);
        byte[] exif = PngTestSupport.chunk("eXIf", PngTestSupport.orientationTiff(8, false));
        ThumbnailResult r = ThumbnailHelper.generatePreviewForBlob(
                PngTestSupport.png(px, 300, 200, true, exif, null), MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        assertEquals(8, r.getExifOrientation());
        assertEquals(ThumbnailHelper.MIME_PNG, r.getPreviewMediaType());
        assertEquals(171, r.getPreviewWidth());
        assertEquals(256, r.getPreviewHeight());
    }

    @Test
    @DisplayName("a malformed eXIf never costs the preview")
    public void malformedChunkStillPreviews() {
        int[] px = PngTestSupport.noise(400, 200, 7);
        byte[] cut = PngTestSupport.chunk("eXIf", PngTestSupport.head(PngTestSupport.orientationTiff(6, true), 15));
        ThumbnailResult r = ThumbnailHelper.generatePreview(
                PngTestSupport.png(px, 400, 200, false, cut, null), MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        assertEquals(N, r.getExifOrientation());
        assertTrue(r.getPreviewWidth() > r.getPreviewHeight());

        // A lying chunk length makes the FILE undecodable; that is an outcome, not an exception.
        byte[] lying = PngTestSupport.tagged(px, 400, 200, 6, true);
        lying[8 + PngTestSupport.IHDR_CHUNK_LEN] = 0x7F;
        ThumbnailResult bad = ThumbnailHelper.generatePreview(lying, MAX_BYTES);
        assertNotNull(bad);
        assertEquals(N, bad.getExifOrientation());
    }

    // ---- helpers --------------------------------------------------------------------------

    private static BufferedImage preview(byte[] source) throws Exception {
        ThumbnailResult r = ThumbnailHelper.generatePreviewForBlob(source, MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        return ImageIO.read(new ByteArrayInputStream(r.getPreviewBytes()));
    }

    private static void assertNear(int expectedRgb, int actualRgb, String what) {
        int d = Math.abs(((expectedRgb >> 16) & 0xFF) - ((actualRgb >> 16) & 0xFF))
                + Math.abs(((expectedRgb >> 8) & 0xFF) - ((actualRgb >> 8) & 0xFF))
                + Math.abs((expectedRgb & 0xFF) - (actualRgb & 0xFF));
        assertTrue(d < 90, what + ": expected ~" + Integer.toHexString(expectedRgb & 0xFFFFFF)
                + " got " + Integer.toHexString(actualRgb & 0xFFFFFF));
    }

    private static byte[] repeat(byte[] part, int times) {
        byte[][] all = new byte[times][];
        java.util.Arrays.fill(all, part);
        return PngTestSupport.concat(all);
    }
}
