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

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;

/**
 * The single implementation of the §PREVIEW-SPEC thumbnail algorithm for every Java client.
 *
 * <h2>Why this lives in {@code takamaka-extra}</h2>
 *
 * <p>Two Java consumers need it — {@code shell} had its own copy and {@code chat-web-gui} had none
 * at all, so the same blob acquired a preview or not depending on which client sent it. The
 * algorithm is normative protocol behaviour, so one copy is the only arrangement that can stay
 * conformant. The Dart mirror is {@code wallet-extra-flutter/lib/src/imaging/thumbnail_helper.dart},
 * the same pairing as {@code IdentiColorHelper} / {@code identi_color_helper.dart}.</p>
 *
 * <h2>The algorithm</h2>
 *
 * <pre>
 * if (source.length &lt;= maxPreviewBytes) -&gt; INLINE, generate NO preview      // rule 1
 * decode; bake EXIF orientation into the pixels (JPEG only; failure =&gt; 1)   // rule 3
 * if (max(w,h) &gt; 256) scale the longest edge to 256   // NEVER upscale      // rule 2
 * encode: opaque -&gt; JPEG q0.80 ; has real alpha -&gt; PNG                      // rule 5
 * if (encoded &gt; maxPreviewBytes) -&gt; NO preview, and the caller LOGS it      // rule 6
 * </pre>
 *
 * <h2>Deliberately NOT parameterised by the protocol</h2>
 *
 * <p>{@code maxPreviewBytes} is an argument rather than a constant because this module sits
 * <em>below</em> {@code Messages} in the dependency order — importing
 * {@code InlineContentLimits.MAX_INLINE_BYTES} here would invert the layering. Callers pass it.
 * That also makes every threshold in the class testable without a protocol dependency.</p>
 *
 * <h2>Purity</h2>
 *
 * <p>Bytes in, bytes and a decision out. No file I/O, no Spring, no logging framework — the caller
 * owns the log line, because the caller knows the filename and the conversation. Java 11: no
 * records, no switch expressions, no text blocks.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @since 0.6.0
 */
public final class ThumbnailHelper {

    /**
     * Longest edge of a generated preview, in pixels.
     *
     * <p>A THRESHOLD as well as a target: a source already within it is re-encoded but
     * <strong>never upscaled</strong>. Upscaling spends bytes to add no information, and the
     * previous implementations did it to every small image they touched.</p>
     */
    public static final int MAX_PREVIEW_EDGE_PX = 256;

    /** JPEG quality for opaque previews. ~6 KB for a 256px photo, against a 51 200 B ceiling. */
    public static final float DEFAULT_JPEG_QUALITY = 0.80f;

    /** {@value} — the preview type for opaque sources. */
    public static final String MIME_JPEG = "image/jpeg";

    /** {@value} — the preview type for sources with real transparency. */
    public static final String MIME_PNG = "image/png";

    /**
     * Refuse to decode an image claiming more than this many pixels.
     *
     * <p>A decompression bomb is a few hundred bytes on the wire and gigabytes in the heap. The
     * dimensions are read from the header WITHOUT decoding, so this costs nothing on honest input.
     * 100 MP is far above any camera a user will attach and far below what hurts.</p>
     */
    public static final long MAX_SOURCE_PIXELS = 100L * 1000L * 1000L;

    /**
     * Largest source a caller should READ INTO MEMORY in order to preview it.
     *
     * <p>This class takes {@code byte[]}, deliberately — it is pure, and file I/O belongs to the
     * caller. The consequence is that the caller decides how much to read, and "read the whole
     * attachment" is wrong when attachments may be a gigabyte. Callers must check the file size
     * against this before slurping, and skip the preview when it is exceeded.</p>
     *
     * <p>{@value} bytes. Far above any photograph and any image worth previewing; far below what
     * turns a preview into an out-of-memory condition on a shared JVM.</p>
     *
     * @see #isWorthReadingForPreview(long, String)
     */
    public static final long MAX_SOURCE_BYTES_FOR_PREVIEW = 64L * 1024L * 1024L;

    /**
     * Whether a caller should read this file at all in order to attempt a preview.
     *
     * <p>Two cheap gates, both on metadata: it must claim to be an image, and it must be small
     * enough to hold in memory. Neither decodes anything.</p>
     *
     * @param sizeBytes the source's size on disk
     * @param mediaType the source's declared type; only {@code image/*} is worth attempting
     * @return {@code true} when the caller may read the bytes and call
     *         {@link #generatePreview(byte[], int)}
     */
    public static boolean isWorthReadingForPreview(long sizeBytes, String mediaType) {
        return mediaType != null
                && mediaType.toLowerCase().startsWith("image/")
                && sizeBytes > 0
                && sizeBytes <= MAX_SOURCE_BYTES_FOR_PREVIEW;
    }

    private ThumbnailHelper() {
    }

    /**
     * Generate a preview per §PREVIEW-SPEC, using the default 256 px / q0.80 settings.
     *
     * @param source         the original file bytes; may be {@code null}
     * @param maxPreviewBytes the byte ceiling — pass {@code InlineContentLimits.MAX_INLINE_BYTES}
     * @return the outcome; never {@code null}, never throws
     */
    public static ThumbnailResult generatePreview(byte[] source, int maxPreviewBytes) {
        return generatePreview(source, maxPreviewBytes, MAX_PREVIEW_EDGE_PX, DEFAULT_JPEG_QUALITY);
    }

    /**
     * Generate a preview for a producer that has ALREADY COMMITTED to the blob transport —
     * skipping rule 1 only.
     *
     * <p><strong>Why this exists, and why it is not a loophole.</strong>
     * Rule 1 ("content that fits inline gets no preview") rests on a premise: that a receiver
     * who is not sent a preview is sent <em>the whole object</em> instead, so a preview would
     * duplicate what they already hold. That premise is true only on the inline transport.</p>
     *
     * <p>A producer that always uploads blobs — {@code shell} and {@code chat-web-gui} both do
     * today — does not satisfy it. Applying rule 1 to such a producer would send a small image as
     * a blob <strong>and</strong> strip its thumbnail, which is worse than either conformant
     * outcome: the receiver holds nothing and sees nothing. That is a regression dressed as
     * conformance.</p>
     *
     * <p>The genuine fix for those producers is to CHOOSE the inline transport for small
     * content — tracked separately as N-23 (transport choice), not as part of
     * §PREVIEW-CONFORMANCE. Until they do, this entry point keeps them conformant on every rule
     * they can actually satisfy (2..6) and honest about the one they cannot.</p>
     *
     * @param source          the original file bytes; may be {@code null}
     * @param maxPreviewBytes the byte ceiling for the encoded preview
     * @return the outcome; never {@link ThumbnailResult.Outcome#INLINE}
     */
    public static ThumbnailResult generatePreviewForBlob(byte[] source, int maxPreviewBytes) {
        return generate(source, maxPreviewBytes, MAX_PREVIEW_EDGE_PX, DEFAULT_JPEG_QUALITY, false);
    }

    /**
     * Generate a preview per §PREVIEW-SPEC.
     *
     * @param source          the original file bytes; may be {@code null}
     * @param maxPreviewBytes the byte ceiling for both the inline decision and the encoded preview
     * @param maxEdgePx       longest-edge target; a source already within it is not upscaled
     * @param jpegQuality     0..1 for opaque sources
     * @return the outcome; never {@code null}, never throws
     */
    public static ThumbnailResult generatePreview(byte[] source, int maxPreviewBytes,
            int maxEdgePx, float jpegQuality) {
        return generate(source, maxPreviewBytes, maxEdgePx, jpegQuality, true);
    }

    private static ThumbnailResult generate(byte[] source, int maxPreviewBytes,
            int maxEdgePx, float jpegQuality, boolean applyInlineRule) {
        if (source == null || source.length == 0) {
            return ThumbnailResult.of(ThumbnailResult.Outcome.EMPTY_SOURCE, "source is empty");
        }

        // Rule 1, and it comes FIRST on purpose. Content that fits inline needs no preview, and
        // deciding that before decoding means small hostile input is never decoded at all.
        if (applyInlineRule && source.length <= maxPreviewBytes) {
            return ThumbnailResult.of(ThumbnailResult.Outcome.INLINE,
                    "source is " + source.length + " bytes, within the " + maxPreviewBytes
                    + "-byte inline limit: send inline, no preview needed");
        }

        try {
            long[] dims = readDimensionsWithoutDecoding(source);
            if (dims != null && dims[0] * dims[1] > MAX_SOURCE_PIXELS) {
                return ThumbnailResult.of(ThumbnailResult.Outcome.NOT_AN_IMAGE,
                        "refusing to decode " + dims[0] + "x" + dims[1] + " ("
                        + (dims[0] * dims[1]) + " px, over the " + MAX_SOURCE_PIXELS + " guard)");
            }

            BufferedImage decoded;
            try {
                decoded = ImageIO.read(new ByteArrayInputStream(source));
            } catch (IOException | RuntimeException ex) {
                // RuntimeException too: ImageIO plugins throw unchecked on malformed input.
                return ThumbnailResult.of(ThumbnailResult.Outcome.NOT_AN_IMAGE,
                        "not decodable as an image: " + ex.getMessage());
            }
            if (decoded == null) {
                return ThumbnailResult.of(ThumbnailResult.Outcome.NOT_AN_IMAGE,
                        "no ImageIO reader recognised these bytes");
            }

            // Rule 3 — bake orientation BEFORE measuring, because orientations 5..8 exchange the
            // axes and it is the DISPLAYED dimensions that the scale rule is about.
            final int orientation = ExifOrientation.read(source);
            BufferedImage upright = applyOrientation(decoded, orientation);

            final int srcW = upright.getWidth();
            final int srcH = upright.getHeight();

            // Rule 2 — scale the longest edge to maxEdgePx, and never the other way.
            BufferedImage scaled = scaleDown(upright, maxEdgePx);

            // Rule 5 — format follows transparency, not the source's format.
            final boolean alpha = hasRealTransparency(scaled);
            final String mime = alpha ? MIME_PNG : MIME_JPEG;
            final byte[] encoded = alpha ? encodePng(scaled) : encodeJpeg(scaled, jpegQuality);

            // Rule 6 — a preview that does not fit is not sent. The caller logs it.
            if (encoded.length > maxPreviewBytes) {
                return new ThumbnailResult(ThumbnailResult.Outcome.TOO_LARGE, null, mime,
                        scaled.getWidth(), scaled.getHeight(), srcW, srcH, orientation,
                        "preview encoded to " + encoded.length + " bytes as " + mime
                        + ", over the " + maxPreviewBytes + "-byte limit: DROPPED");
            }

            return new ThumbnailResult(ThumbnailResult.Outcome.PREVIEW, encoded, mime,
                    scaled.getWidth(), scaled.getHeight(), srcW, srcH, orientation,
                    "ok");
        } catch (IOException | RuntimeException | OutOfMemoryError ex) {
            // Nothing a peer can send may propagate out of here as a failure of the MESSAGE.
            // A missing preview is a cosmetic loss; an exception escaping is a delivery failure.
            return ThumbnailResult.of(ThumbnailResult.Outcome.NOT_AN_IMAGE,
                    "preview generation failed: " + ex);
        }
    }

    /**
     * Read {@code {width, height}} from the image header without decoding the pixels.
     *
     * @return the dimensions, or {@code null} when they cannot be determined cheaply
     */
    private static long[] readDimensionsWithoutDecoding(byte[] source) {
        try (ImageInputStream iis = ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            if (iis == null) {
                return null;
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                return new long[]{reader.getWidth(0), reader.getHeight(0)};
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException ex) {
            return null; // the full decode below will produce the real verdict
        }
    }

    /**
     * Bake an EXIF orientation into the pixels.
     *
     * @return a new upright image, or {@code src} unchanged for orientation 1
     */
    static BufferedImage applyOrientation(BufferedImage src, int orientation) {
        if (orientation <= 1 || orientation > 8) {
            return src;
        }
        final int w = src.getWidth();
        final int h = src.getHeight();
        final boolean swap = ExifOrientation.swapsAxes(orientation);
        final int destW = swap ? h : w;
        final int destH = swap ? w : h;

        BufferedImage dest = new BufferedImage(destW, destH, preferredType(src));
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            AffineTransform tx = ExifOrientation.transformFor(orientation, w, h);
            g.drawImage(src, tx, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    /**
     * Scale so the longest edge is {@code maxEdgePx}, preserving aspect ratio.
     *
     * <p>Returns the input untouched when it already fits: <strong>never upscale</strong>. The
     * previous shell and tkmChat implementations both upscaled, spending bytes on interpolated
     * pixels that carry no information.</p>
     */
    static BufferedImage scaleDown(BufferedImage src, int maxEdgePx) {
        final int w = src.getWidth();
        final int h = src.getHeight();
        if (maxEdgePx <= 0 || Math.max(w, h) <= maxEdgePx) {
            return src;
        }
        final int newW;
        final int newH;
        if (w >= h) {
            newW = maxEdgePx;
            newH = Math.max(1, (int) Math.round((double) h / w * maxEdgePx));
        } else {
            newH = maxEdgePx;
            newW = Math.max(1, (int) Math.round((double) w / h * maxEdgePx));
        }
        BufferedImage dest = new BufferedImage(newW, newH, preferredType(src));
        Graphics2D g = dest.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            g.drawImage(src, 0, 0, newW, newH, null);
        } finally {
            g.dispose();
        }
        return dest;
    }

    private static int preferredType(BufferedImage src) {
        return src.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
    }

    /**
     * Whether the image ACTUALLY uses transparency, as opposed to merely having a channel for it.
     *
     * <p>The distinction is worth the scan. A screenshot PNG carries a fully-opaque alpha channel;
     * treating "has an alpha channel" as "needs PNG" is precisely how a 17 KB photo became an
     * 80 KB preview — 4.6x the original file, the measured mechanism behind N-14. The scan runs on
     * the ALREADY-SCALED image, so it is bounded at 256x256 = 65 536 pixels.</p>
     */
    static boolean hasRealTransparency(BufferedImage img) {
        if (!img.getColorModel().hasAlpha()) {
            return false;
        }
        final int w = img.getWidth();
        final int h = img.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                if ((img.getRGB(x, y) >>> 24) != 0xFF) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Encode as JPEG at the given quality.
     *
     * <p>Flattens onto opaque RGB first. The JDK JPEG writer given a 4-channel ARGB raster writes
     * a file that decodes with inverted colours in most readers — a silent corruption, not an
     * error.</p>
     */
    static byte[] encodeJpeg(BufferedImage img, float quality) throws IOException {
        BufferedImage rgb = img;
        if (img.getType() != BufferedImage.TYPE_INT_RGB) {
            rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            try {
                // Composite over white: JPEG has no alpha, and the alternative default is black,
                // which turns a transparent logo into a black rectangle.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, img.getWidth(), img.getHeight());
                g.drawImage(img, 0, 0, null);
            } finally {
                g.dispose();
            }
        }

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IOException("no JPEG writer available");
        }
        ImageWriter writer = writers.next();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0f, Math.min(1f, quality)));
            }
            writer.write(null, new IIOImage(rgb, null, null), param);
            out.flush();
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    /** Encode as PNG, preserving the alpha channel. */
    static byte[] encodePng(BufferedImage img) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        if (!ImageIO.write(img, "PNG", baos)) {
            throw new IOException("no PNG writer available");
        }
        return baos.toByteArray();
    }

    /**
     * Identify an encoded image by MAGIC BYTES.
     *
     * <p>Normative: a preview's type is determined by its content, never by the placeholder's
     * {@code media_type} — which describes the ORIGINAL OBJECT and routinely differs (a JPEG
     * preview of a PNG blob is conformant, not a bug).</p>
     *
     * @param bytes encoded image data; may be {@code null} or short
     * @return one of {@code image/jpeg}, {@code image/png}, {@code image/gif}, {@code image/webp},
     *         or {@code null} when unrecognised
     */
    public static String sniffMediaType(byte[] bytes) {
        if (bytes == null || bytes.length < 4) {
            return null;
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return MIME_JPEG;
        }
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
            return MIME_PNG;
        }
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == '8') {
            return "image/gif";
        }
        if (bytes.length >= 12
                && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}
