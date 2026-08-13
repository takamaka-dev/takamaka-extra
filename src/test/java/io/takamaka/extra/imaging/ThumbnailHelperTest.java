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
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The §PREVIEW-SPEC conformance assertions, Java side.
 *
 * <p>Assertions 1-6 are per-platform properties, checkable in isolation. Assertion 7 (the EXIF
 * orientation property) is the ONLY thing comparable across platforms and has a Dart twin at
 * {@code wallet-extra-flutter/test/imaging/thumbnail_helper_test.dart}.</p>
 *
 * <p>🚫 <strong>No assertion here compares preview BYTES to anything.</strong> Encoders differ by
 * design and no interop path compares thumbnail bytes; a pinned cross-platform thumbnail vector is
 * a fixture that can never pass.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ThumbnailHelperTest {

    /** Stand-in for {@code InlineContentLimits.MAX_INLINE_BYTES}, which this module cannot import. */
    private static final int MAX_BYTES = 50 * 1024;

    // ---- fixtures ---------------------------------------------------------------------

    /**
     * A photographic-ish opaque image: a smooth gradient plus per-pixel noise.
     *
     * <p>The noise matters. A flat or purely synthetic image compresses to almost nothing in PNG,
     * which would make assertion 5's positive control pass vacuously — it would prove that a
     * trivial image is small, not that lossless re-encoding of photographic content inflates.</p>
     */
    private static BufferedImage photoLike(int w, int h, long seed) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Random rnd = new Random(seed);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int base = (int) (127 + 120 * Math.sin(x / 23.0) * Math.cos(y / 17.0));
                int r = clamp(base + rnd.nextInt(60) - 30);
                int g = clamp(base + rnd.nextInt(60) - 30);
                int b = clamp((int) (base * 0.7) + rnd.nextInt(60) - 30);
                img.setRGB(x, y, (r << 16) | (g << 8) | b);
            }
        }
        return img;
    }

    /**
     * An image with REAL transparency, shaped like the content that actually uses it: a smooth
     * translucent disc on a fully transparent field — a sticker or a logo.
     *
     * <p>Deliberately NOT per-pixel noise. Noise inside an alpha image is incompressible, so its
     * 256px PNG lands just over the 51 200 B ceiling and the preview is (correctly) dropped —
     * which would make this fixture test the DROP path while claiming to test alpha retention.
     * {@link #noisyAlphaPreviewIsDroppedNotSilentlyFlattened()} covers that case on purpose.</p>
     */
    private static BufferedImage withAlpha(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                double dx = x - w / 2.0;
                double dy = y - h / 2.0;
                double r = Math.min(w, h) / 2.5;
                boolean inside = dx * dx + dy * dy < r * r;
                int a = inside ? 200 : 0;
                int red = clamp(60 + (int) (180.0 * x / w));
                int grn = clamp(40 + (int) (180.0 * y / h));
                img.setRGB(x, y, (a << 24) | (red << 16) | (grn << 8) | 0x80);
            }
        }
        return img;
    }

    /** The noisy variant, used only to pin the drop path. */
    private static BufferedImage noisyAlpha(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Random rnd = new Random(7);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = rnd.nextInt(256);
                img.setRGB(x, y, (128 << 24) | (c << 16) | (c << 8) | c);
            }
        }
        return img;
    }

    private static int clamp(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }

    private static byte[] png(BufferedImage img) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        ImageIO.write(img, "PNG", b);
        return b.toByteArray();
    }

    private static byte[] jpeg(BufferedImage img, float q) throws IOException {
        return ThumbnailHelper.encodeJpeg(img, q);
    }

    private static boolean isJpeg(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8;
    }

    private static boolean isPng(byte[] b) {
        return b != null && b.length > 3 && (b[0] & 0xFF) == 0x89
                && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    // ---- assertion 1 ------------------------------------------------------------------

    @Test
    @DisplayName("1. a source within the inline limit produces NO preview and goes inline")
    public void smallSourceGoesInlineWithNoPreview() throws Exception {
        byte[] small = jpeg(photoLike(120, 90, 1), 0.6f);
        assertTrue(small.length <= MAX_BYTES, "fixture must be under the limit: " + small.length);

        ThumbnailResult r = ThumbnailHelper.generatePreview(small, MAX_BYTES);

        assertEquals(ThumbnailResult.Outcome.INLINE, r.getOutcome());
        assertTrue(r.shouldInline());
        assertFalse(r.hasPreview(), "a preview of content the receiver already holds is overhead");
        assertNull(r.getPreviewBytes());
    }

    @Test
    @DisplayName("1b. exactly at the limit still goes inline — the boundary is inclusive")
    public void atTheLimitGoesInline() {
        byte[] exact = new byte[MAX_BYTES];
        ThumbnailResult r = ThumbnailHelper.generatePreview(exact, MAX_BYTES);
        assertEquals(ThumbnailResult.Outcome.INLINE, r.getOutcome());
    }

    @Test
    @DisplayName("1c. ⭐ generatePreviewForBlob skips rule 1 — and ONLY rule 1")
    public void blobEntryPointSkipsTheInlineRuleOnly() throws Exception {
        // A producer committed to the blob transport must still preview small content, or the
        // receiver gets neither the object nor a thumbnail. See the method's javadoc for why this
        // is not a loophole: the rule's premise (the receiver already holds it) is false for them.
        byte[] small = jpeg(photoLike(400, 300, 31), 0.6f);
        assertTrue(small.length <= MAX_BYTES, "fixture must be under the limit");

        assertEquals(ThumbnailResult.Outcome.INLINE,
                ThumbnailHelper.generatePreview(small, MAX_BYTES).getOutcome());

        ThumbnailResult blob = ThumbnailHelper.generatePreviewForBlob(small, MAX_BYTES);
        assertEquals(ThumbnailResult.Outcome.PREVIEW, blob.getOutcome());
        assertTrue(blob.hasPreview());

        // Every OTHER rule still applies to it: within the byte cap, longest edge 256, JPEG.
        assertTrue(blob.getPreviewBytes().length <= MAX_BYTES);
        assertEquals(256, Math.max(blob.getPreviewWidth(), blob.getPreviewHeight()));
        assertTrue(isJpeg(blob.getPreviewBytes()));
    }

    @Test
    @DisplayName("1d. the blob entry point still refuses to upscale")
    public void blobEntryPointDoesNotUpscale() throws Exception {
        byte[] tiny = jpeg(photoLike(64, 48, 32), 0.6f);
        ThumbnailResult r = ThumbnailHelper.generatePreviewForBlob(tiny, MAX_BYTES);
        assertTrue(r.hasPreview());
        assertEquals(64, r.getPreviewWidth());
        assertEquals(48, r.getPreviewHeight());
    }

    // ---- assertion 2 ------------------------------------------------------------------

    @Test
    @DisplayName("2. a source over the threshold previews within the limit, or is dropped WITH a reason")
    public void largeSourcePreviewsWithinTheLimit() throws Exception {
        byte[] big = png(photoLike(1600, 1200, 2));
        assertTrue(big.length > MAX_BYTES, "fixture must be over the limit: " + big.length);

        ThumbnailResult r = ThumbnailHelper.generatePreview(big, MAX_BYTES);

        // Three states, never two: a drop must be distinguishable from a success.
        if (r.hasPreview()) {
            assertTrue(r.getPreviewBytes().length <= MAX_BYTES,
                    "preview is " + r.getPreviewBytes().length + " B, over " + MAX_BYTES);
        } else {
            assertEquals(ThumbnailResult.Outcome.TOO_LARGE, r.getOutcome());
            assertTrue(r.getReason().contains("DROPPED"),
                    "a dropped preview must say so, for the log line: " + r.getReason());
        }
    }

    @Test
    @DisplayName("2b. the reference 256px photo preview is far below the ceiling, not near it")
    public void referencePreviewIsSmall() throws Exception {
        byte[] big = png(photoLike(1600, 1200, 3));
        ThumbnailResult r = ThumbnailHelper.generatePreview(big, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        // MAX_INLINE_BYTES is an upper limit, not a target. The spec's measured reference is
        // ~6 337 B against 51 200. Anything close to the ceiling means the encoder settings drifted.
        assertTrue(r.getPreviewBytes().length < MAX_BYTES / 2,
                "a 256px JPEG should be ~6 KB, not " + r.getPreviewBytes().length);
    }

    // ---- assertion 3 ------------------------------------------------------------------

    @Test
    @DisplayName("3. ⭐ a source whose longest edge is already <= 256 is NEVER upscaled")
    public void smallDimensionsAreNeverUpscaled() throws Exception {
        // Over the BYTE limit (so it takes the preview path) but under the PIXEL threshold.
        // Both prior implementations resized this to 256 unconditionally, inventing pixels.
        byte[] src = png(photoLike(200, 150, 4));
        assertTrue(src.length > MAX_BYTES, "fixture must take the preview path: " + src.length);

        ThumbnailResult r = ThumbnailHelper.generatePreview(src, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertEquals(200, r.getPreviewWidth(), "width must be unchanged");
        assertEquals(150, r.getPreviewHeight(), "height must be unchanged");
    }

    @Test
    @DisplayName("3b. a source over 256 is scaled so the LONGEST edge is exactly 256")
    public void longestEdgeBecomes256() throws Exception {
        byte[] wide = png(photoLike(1000, 500, 5));
        ThumbnailResult rw = ThumbnailHelper.generatePreview(wide, MAX_BYTES);
        assertEquals(256, rw.getPreviewWidth());
        assertEquals(128, rw.getPreviewHeight());

        byte[] tall = png(photoLike(500, 1000, 6));
        ThumbnailResult rt = ThumbnailHelper.generatePreview(tall, MAX_BYTES);
        assertEquals(128, rt.getPreviewWidth());
        assertEquals(256, rt.getPreviewHeight());
    }

    // ---- assertion 4 ------------------------------------------------------------------

    @Test
    @DisplayName("4. an opaque source yields JPEG magic bytes")
    public void opaqueYieldsJpeg() throws Exception {
        byte[] src = png(photoLike(1200, 900, 7));
        ThumbnailResult r = ThumbnailHelper.generatePreview(src, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertTrue(isJpeg(r.getPreviewBytes()),
                "opaque content must not be re-encoded as PNG — that is the N-14 bloat");
        assertEquals(ThumbnailHelper.MIME_JPEG, r.getPreviewMediaType());
        assertEquals(ThumbnailHelper.MIME_JPEG,
                ThumbnailHelper.sniffMediaType(r.getPreviewBytes()),
                "magic bytes must agree with the declared preview type");
    }

    @Test
    @DisplayName("4b. ⭐ an alpha source yields PNG and RETAINS its transparency")
    public void alphaYieldsPngAndKeepsAlpha() throws Exception {
        byte[] src = png(withAlpha(900, 900));
        ThumbnailResult r = ThumbnailHelper.generatePreview(src, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertTrue(isPng(r.getPreviewBytes()), "transparency cannot survive JPEG");

        // Retained, not merely "the format could carry it": decode and find a transparent pixel.
        BufferedImage back = ImageIO.read(new java.io.ByteArrayInputStream(r.getPreviewBytes()));
        assertNotNull(back);
        assertTrue(back.getColorModel().hasAlpha(), "the preview lost its alpha channel");
        assertTrue(ThumbnailHelper.hasRealTransparency(back),
                "the alpha channel survived but every pixel is opaque — alpha was flattened");
    }

    @Test
    @DisplayName("4b-ii. ⭐ an alpha preview that will not fit is DROPPED, not silently flattened")
    public void noisyAlphaPreviewIsDroppedNotSilentlyFlattened() throws Exception {
        // The accepted cost of "alpha ⇒ PNG": incompressible alpha content cannot be previewed
        // within 51 200 B at 256px, because the fallback (JPEG) would destroy the transparency
        // that forced PNG in the first place. §PREVIEW-SPEC rule 6 says emit NO preview and LOG.
        //
        // What must NOT happen is a silent JPEG flatten — the user would get a preview showing a
        // black or white rectangle where their transparency was.
        byte[] src = png(noisyAlpha(900, 900));
        ThumbnailResult r = ThumbnailHelper.generatePreview(src, MAX_BYTES);

        assertEquals(ThumbnailResult.Outcome.TOO_LARGE, r.getOutcome(), r.toString());
        assertFalse(r.hasPreview());
        assertTrue(r.getReason().contains("DROPPED"),
                "the drop must be loggable, not inferred from a null: " + r.getReason());
        assertTrue(r.getReason().contains(ThumbnailHelper.MIME_PNG),
                "and must say which encoding overflowed: " + r.getReason());
    }

    @Test
    @DisplayName("4c. ⭐ a USELESS alpha channel does not force PNG")
    public void fullyOpaqueAlphaChannelStillYieldsJpeg() throws Exception {
        // A screenshot: TYPE_INT_ARGB, but every pixel opaque. Treating "has an alpha channel" as
        // "needs PNG" is exactly how a 17 KB photo became an 80 KB preview.
        BufferedImage opaqueArgb = new BufferedImage(900, 700, BufferedImage.TYPE_INT_ARGB);
        BufferedImage content = photoLike(900, 700, 8);
        for (int y = 0; y < 700; y++) {
            for (int x = 0; x < 900; x++) {
                opaqueArgb.setRGB(x, y, 0xFF000000 | content.getRGB(x, y));
            }
        }
        ThumbnailResult r = ThumbnailHelper.generatePreview(png(opaqueArgb), MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertTrue(isJpeg(r.getPreviewBytes()),
                "an alpha channel that is entirely opaque carries no information to preserve");
    }

    // ---- assertion 5 ------------------------------------------------------------------

    @Test
    @DisplayName("5. ⭐ a preview is never larger than its source")
    public void previewIsNeverLargerThanSource() throws Exception {
        for (int[] dim : new int[][]{{1600, 1200}, {800, 600}, {300, 240}, {2000, 300}}) {
            byte[] src = png(photoLike(dim[0], dim[1], dim[0]));
            if (src.length <= MAX_BYTES) {
                continue; // goes inline; no preview to compare
            }
            ThumbnailResult r = ThumbnailHelper.generatePreview(src, MAX_BYTES);
            if (r.hasPreview()) {
                assertTrue(r.getPreviewBytes().length < src.length,
                        dim[0] + "x" + dim[1] + ": preview " + r.getPreviewBytes().length
                        + " B >= source " + src.length + " B");
            }
        }
    }

    @Test
    @DisplayName("5b. ⭐ POSITIVE CONTROL: lossless re-encoding really does inflate photos")
    public void positiveControlPngReEncodingInflates() throws Exception {
        // Without this, assertion 5 could pass vacuously — "preview < source" is trivially true if
        // nothing ever inflates. This proves the failure mode is REAL and reachable, by measuring
        // the exact thing the old implementations did: take a photographic JPEG, scale it to 256px,
        // and re-encode LOSSLESSLY. That is N-14's mechanism.
        BufferedImage photo = photoLike(536, 354, 11);
        byte[] sourceJpeg = jpeg(photo, 0.80f);

        BufferedImage scaled = ThumbnailHelper.scaleDown(photo, 256);
        byte[] asPng = ThumbnailHelper.encodePng(scaled);
        byte[] asJpeg = ThumbnailHelper.encodeJpeg(scaled, 0.80f);

        assertTrue(asPng.length > sourceJpeg.length,
                "the PNG-always path must be shown to INFLATE, else assertion 5 proves nothing: "
                + "source JPEG " + sourceJpeg.length + " B vs 256px PNG " + asPng.length + " B");
        assertTrue(asJpeg.length < sourceJpeg.length,
                "and the conformant path must shrink it: " + asJpeg.length + " B");
    }

    // ---- assertion 6 ------------------------------------------------------------------

    @Test
    @DisplayName("6. ⭐ malformed EXIF yields a preview, not an exception")
    public void malformedExifStillPreviews() throws Exception {
        byte[] good = jpeg(photoLike(900, 700, 12), 0.95f);
        assertTrue(good.length > MAX_BYTES, "fixture must take the preview path");

        // Every prefix of a valid EXIF payload, spliced onto real image data.
        for (int cut = 0; cut < 64; cut++) {
            byte[] mangled = ExifTestSupport.withApp1(good, ExifTestSupport.truncatedExif(6, cut));

            // The point of the whole exercise: a preview still comes out.
            ThumbnailResult r = ThumbnailHelper.generatePreview(mangled, MAX_BYTES);
            assertTrue(r.hasPreview(), "cut=" + cut + " lost the preview: " + r);

            int read = ExifOrientation.read(mangled);
            if (cut < ExifTestSupport.MIN_PARSEABLE_PAYLOAD_LEN) {
                assertEquals(ExifOrientation.NORMAL, read,
                        "a TRUNCATED tag must fail open to orientation 1, cut=" + cut);
            } else {
                // Stated rather than skipped. Once the 12-byte IFD entry is complete (at 28 bytes)
                // the orientation IS legitimately readable — the missing next-IFD offset is not
                // needed for it, and the trailing 0xAA padding sits past everything the parse
                // touches. Asserting NORMAL here would have been asserting that the parser is
                // broken, and would have "passed" only by making it worse.
                assertEquals(6, read, "a complete tag must still parse, cut=" + cut);
            }
        }
    }

    @Test
    @DisplayName("6c. an APP1 whose declared length runs past EOF is refused")
    public void segmentLengthPastEofIsRefused() throws Exception {
        byte[] good = jpeg(photoLike(64, 64, 17), 0.5f);
        byte[] payload = ExifTestSupport.orientationExif(6, false);

        // A segment claiming more bytes than the file contains. This is the case that, unchecked,
        // walks the parser off the end of the array.
        int pastEof = good.length + 1000;
        byte[] lying = ExifTestSupport.withLyingApp1Length(good, payload, pastEof);
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(lying),
                "a segment claiming " + pastEof + " bytes must not be trusted");

        // And a length of 0 or 1, which does not advance the marker walk and would spin forever
        // without the `len < 2` guard. This test hanging IS the failure mode.
        for (int bogus : new int[]{0, 1}) {
            byte[] spin = ExifTestSupport.withLyingApp1Length(good, payload, bogus);
            assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(spin));
        }
    }

    @Test
    @DisplayName("6c-ii. a length that overstates but stays INSIDE the file still parses")
    public void overstatedButInBoundsLengthStillParses() throws Exception {
        // Documented rather than asserted-away. The declared length only governs where the marker
        // WALK resumes; the orientation is read out of the payload that is actually present, and
        // every access is bounds-checked against the real array. So an overstatement that stays
        // inside the file is harmless and the tag is still read. Pinning this stops someone
        // "hardening" it into a refusal and silently losing orientation on files with sloppy
        // segment lengths.
        byte[] good = jpeg(photoLike(900, 700, 19), 0.95f);
        byte[] payload = ExifTestSupport.orientationExif(6, false);
        int inBounds = good.length / 2;
        assertTrue(inBounds > payload.length, "fixture must overstate but stay in bounds");

        byte[] lying = ExifTestSupport.withLyingApp1Length(good, payload, inBounds);
        assertEquals(6, ExifOrientation.read(lying));
    }

    @Test
    @DisplayName("6d. an out-of-range orientation value is refused, not passed through")
    public void outOfRangeOrientationIsRefused() throws Exception {
        byte[] base = jpeg(photoLike(64, 64, 18), 0.8f);
        for (int bad : new int[]{0, 9, 255, 65535}) {
            byte[] tagged = ExifTestSupport.withApp1(base, ExifTestSupport.orientationExif(bad, false));
            assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(tagged),
                    "orientation " + bad + " is not a legal value");
        }
    }

    @Test
    @DisplayName("6b. the EXIF parser never throws, on anything")
    public void exifParserNeverThrows() throws Exception {
        byte[] jpg = jpeg(photoLike(64, 64, 13), 0.8f);
        // Truncations.
        for (int n = 0; n <= jpg.length; n++) {
            byte[] cut = new byte[n];
            System.arraycopy(jpg, 0, cut, 0, n);
            assertTrue(ExifOrientation.read(cut) >= 1);
        }
        // Random noise, and structured noise that looks like a header.
        Random rnd = new Random(99);
        for (int i = 0; i < 500; i++) {
            byte[] noise = new byte[rnd.nextInt(200)];
            rnd.nextBytes(noise);
            if (noise.length > 1) {
                noise[0] = (byte) 0xFF;
                noise[1] = (byte) 0xD8;
            }
            assertTrue(ExifOrientation.read(noise) >= 1);
        }
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(null));
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(new byte[0]));
    }

    // ---- assertion 7 (cross-platform property) -----------------------------------------

    @Test
    @DisplayName("7. ⭐ a 400x200 source with EXIF orientation 6 previews PORTRAIT")
    public void exifOrientationSixPreviewsPortrait() throws Exception {
        // Stored 400x200 (landscape), displayed 200x400 (portrait). This is THE cross-platform
        // property — the one thing comparable between Java and Dart, because it is about policy
        // rather than bytes. The Dart twin asserts the same shape.
        byte[] landscape = jpeg(photoLike(400, 200, 14), 0.98f);
        byte[] tagged = ExifTestSupport.withApp1(landscape, ExifTestSupport.orientationExif(6, false));

        assertEquals(6, ExifOrientation.read(tagged), "fixture did not carry the tag");
        assertTrue(tagged.length > MAX_BYTES, "fixture must take the preview path: " + tagged.length);

        ThumbnailResult r = ThumbnailHelper.generatePreview(tagged, MAX_BYTES);

        assertTrue(r.hasPreview(), r.toString());
        assertTrue(r.getPreviewHeight() > r.getPreviewWidth(),
                "orientation 6 must be baked into the pixels: got "
                + r.getPreviewWidth() + "x" + r.getPreviewHeight());
        assertEquals(200, r.getSourceWidth(), "displayed width");
        assertEquals(400, r.getSourceHeight(), "displayed height");
    }

    @Test
    @DisplayName("7b. the untagged twin of that fixture previews LANDSCAPE — the control")
    public void untaggedTwinPreviewsLandscape() throws Exception {
        // Positive control for the test above: identical pixels, no APP1. If this also came out
        // portrait, assertion 7 would be measuring the fixture generator, not the orientation code.
        byte[] landscape = jpeg(photoLike(400, 200, 14), 0.98f);
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.read(landscape));

        ThumbnailResult r = ThumbnailHelper.generatePreview(landscape, MAX_BYTES);
        assertTrue(r.hasPreview(), r.toString());
        assertTrue(r.getPreviewWidth() > r.getPreviewHeight(),
                "got " + r.getPreviewWidth() + "x" + r.getPreviewHeight());
    }

    @Test
    @DisplayName("7c. both TIFF byte orders are read — II and MM")
    public void bothByteOrdersParse() throws Exception {
        byte[] base = jpeg(photoLike(64, 64, 15), 0.8f);
        for (int o = 1; o <= 8; o++) {
            assertEquals(o, ExifOrientation.read(
                    ExifTestSupport.withApp1(base, ExifTestSupport.orientationExif(o, false))),
                    "little-endian orientation " + o);
            assertEquals(o, ExifOrientation.read(
                    ExifTestSupport.withApp1(base, ExifTestSupport.orientationExif(o, true))),
                    "big-endian orientation " + o);
        }
    }

    @Test
    @DisplayName("7d. the eight orientation transforms are exact permutations of the corners")
    public void orientationTransformsAreExact() {
        // 2x2 with four distinct colours: every transform must be a lossless rearrangement, so a
        // wrong matrix (a mirror where a rotation belongs) is caught by corner identity rather
        // than by eyeballing a thumbnail. Uses TYPE_INT_RGB and nearest-neighbour-exact geometry.
        int tl = 0xFFFF0000, tr = 0xFF00FF00, bl = 0xFF0000FF, br = 0xFFFFFF00;
        BufferedImage src = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        src.setRGB(0, 0, tl);
        src.setRGB(1, 0, tr);
        src.setRGB(0, 1, bl);
        src.setRGB(1, 1, br);

        // Expected top-left corner after each orientation, per the EXIF definition.
        int[] expectedTopLeft = {0, tl, tr, br, bl, tl, bl, br, tr};
        for (int o = 1; o <= 8; o++) {
            BufferedImage out = ThumbnailHelper.applyOrientation(src, o);
            assertEquals(2, out.getWidth());
            assertEquals(2, out.getHeight());
            assertEquals(expectedTopLeft[o], out.getRGB(0, 0),
                    "orientation " + o + " put the wrong pixel top-left");
        }
    }

    // ---- degenerate inputs --------------------------------------------------------------

    @Test
    @DisplayName("non-image bytes over the limit are reported as NOT_AN_IMAGE, not as a failure")
    public void nonImageIsReportedNotThrown() {
        byte[] notAnImage = new byte[MAX_BYTES + 1];
        new Random(5).nextBytes(notAnImage);
        ThumbnailResult r = ThumbnailHelper.generatePreview(notAnImage, MAX_BYTES);
        assertEquals(ThumbnailResult.Outcome.NOT_AN_IMAGE, r.getOutcome());
        assertFalse(r.hasPreview());
    }

    @Test
    @DisplayName("empty and null sources are reported, not thrown")
    public void emptySourceIsReported() {
        assertEquals(ThumbnailResult.Outcome.EMPTY_SOURCE,
                ThumbnailHelper.generatePreview(null, MAX_BYTES).getOutcome());
        assertEquals(ThumbnailResult.Outcome.EMPTY_SOURCE,
                ThumbnailHelper.generatePreview(new byte[0], MAX_BYTES).getOutcome());
    }

    @Test
    @DisplayName("⭐ isWorthReadingForPreview refuses to slurp a gigabyte to make a thumbnail")
    public void previewReadGateBoundsWhatCallersLoad() {
        // The class takes byte[], so the CALLER decides how much to read, and "read the whole
        // attachment" is wrong when attachments may be 1 GB. Both gates are metadata-only.
        assertTrue(ThumbnailHelper.isWorthReadingForPreview(2_000_000, "image/jpeg"));
        assertTrue(ThumbnailHelper.isWorthReadingForPreview(
                ThumbnailHelper.MAX_SOURCE_BYTES_FOR_PREVIEW, "image/png"));

        assertFalse(ThumbnailHelper.isWorthReadingForPreview(
                ThumbnailHelper.MAX_SOURCE_BYTES_FOR_PREVIEW + 1, "image/jpeg"),
                "a source over the read cap must not be loaded");
        assertFalse(ThumbnailHelper.isWorthReadingForPreview(1_000_000_000L, "video/mp4"));
        assertFalse(ThumbnailHelper.isWorthReadingForPreview(1024, "application/pdf"));
        assertFalse(ThumbnailHelper.isWorthReadingForPreview(1024, null));
        assertFalse(ThumbnailHelper.isWorthReadingForPreview(0, "image/jpeg"));

        // Case-insensitive: a peer may send "IMAGE/JPEG".
        assertTrue(ThumbnailHelper.isWorthReadingForPreview(1024, "IMAGE/JPEG"));
    }

    @Test
    @DisplayName("sniffMediaType identifies by magic bytes, and admits when it cannot")
    public void sniffIdentifiesByMagicBytes() throws Exception {
        assertEquals("image/jpeg", ThumbnailHelper.sniffMediaType(jpeg(photoLike(8, 8, 1), 0.8f)));
        assertEquals("image/png", ThumbnailHelper.sniffMediaType(png(photoLike(8, 8, 1))));
        assertNull(ThumbnailHelper.sniffMediaType(null));
        assertNull(ThumbnailHelper.sniffMediaType(new byte[]{1, 2, 3, 4, 5}));
    }

    @Test
    @DisplayName("scaleDown returns the SAME instance when nothing needs doing")
    public void scaleDownIsIdentityWhenInRange() {
        BufferedImage src = photoLike(100, 80, 1);
        assertTrue(src == ThumbnailHelper.scaleDown(src, 256), "must not copy for nothing");
    }

    @Test
    @DisplayName("a JPEG-encoded preview of an alpha source composites onto white, not black")
    public void alphaFlattensToWhiteWhenForcedToJpeg() throws Exception {
        BufferedImage transparent = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
        byte[] out = ThumbnailHelper.encodeJpeg(transparent, 0.9f);
        BufferedImage back = ImageIO.read(new java.io.ByteArrayInputStream(out));
        int px = back.getRGB(32, 32) & 0xFFFFFF;
        assertTrue(((px >> 16) & 0xFF) > 240 && ((px >> 8) & 0xFF) > 240 && (px & 0xFF) > 240,
                "fully transparent must flatten to white, got " + Integer.toHexString(px));
    }

    @Test
    @DisplayName("the same input twice gives the same preview — generation is deterministic")
    public void generationIsDeterministic() throws Exception {
        byte[] src = png(photoLike(800, 600, 21));
        ThumbnailResult a = ThumbnailHelper.generatePreview(src, MAX_BYTES);
        ThumbnailResult b = ThumbnailHelper.generatePreview(src, MAX_BYTES);
        assertTrue(a.hasPreview() && b.hasPreview());
        assertArrayEquals(a.getPreviewBytes(), b.getPreviewBytes());
    }
}
