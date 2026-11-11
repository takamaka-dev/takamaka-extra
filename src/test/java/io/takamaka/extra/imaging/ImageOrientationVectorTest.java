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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * C127 cross-platform vector: {@code src/test/resources/java_image_orientation_vectors.json}.
 *
 * <p>Each vector is a whole image file (standard Base64) with the orientation
 * {@link ExifOrientation#readImage} must report and, where the file is decodable, the preview
 * {@link ThumbnailHelper#generatePreviewForBlob} must produce: outcome, width x height, displayed
 * source size and media type. <strong>Not pixels and not encoded bytes</strong> — ImageIO and the
 * Dart {@code image} package encode differently; what is normative is the policy.</p>
 *
 * <p>The Dart consumer is {@code wallet-extra-flutter/test/imaging/java_image_orientation_vectors_test.dart},
 * reading a copy of the same file at {@code test/fixtures/java_image_orientation_vectors.json}.
 * The two copies must stay byte-identical.</p>
 *
 * <h2>The committed file is the vector</h2>
 *
 * <p>A normal run only READS the file and checks the Java side against it — it never rewrites it,
 * so a build leaves the tree clean and a changed expectation is a visible diff. The expectations
 * are written by hand in {@link #cases()}, not recorded from the code under test. To regenerate
 * after changing {@link #cases()}: {@code mvn -o test -Dtest=ImageOrientationVectorTest
 * -Dc127.vectors.write=true}, then copy the file to the Dart fixture path. (The input bytes go
 * through {@code java.util.zip.Deflater} and the JDK JPEG writer, so a regeneration on another JDK
 * may change the Base64 without changing any expectation.)</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class ImageOrientationVectorTest {

    static final String RESOURCE = "java_image_orientation_vectors.json";

    /** The byte ceiling the vectors are generated under (51 200 = the protocol's inline limit). */
    static final int MAX_PREVIEW_BYTES = 51200;

    private static final int W = 300;
    private static final int H = 200;
    private static final long SEED = 127L;

    private static final ObjectMapper M = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    /** One vector as declared here: the input and the expectation, both stated by hand. */
    private static final class Case {

        final String name;
        final String description;
        final String format;
        final byte[] input;
        final int orientation;
        /** {@code null} when the file is not decodable and only the orientation is comparable. */
        final int[] previewWh;
        final int[] sourceWh;
        final String mime;

        Case(String name, String description, String format, byte[] input, int orientation,
                int[] previewWh, int[] sourceWh, String mime) {
            this.name = name;
            this.description = description;
            this.format = format;
            this.input = input;
            this.orientation = orientation;
            this.previewWh = previewWh;
            this.sourceWh = sourceWh;
            this.mime = mime;
        }
    }

    private static final int[] LANDSCAPE = {256, 171};
    private static final int[] PORTRAIT = {171, 256};
    private static final int[] SRC_LANDSCAPE = {W, H};
    private static final int[] SRC_PORTRAIT = {H, W};

    private static List<Case> cases() throws Exception {
        final int[] px = PngTestSupport.blocks(W, H, 20, SEED);
        final String jpg = ThumbnailHelper.MIME_JPEG;
        List<Case> all = new ArrayList<>();

        all.add(new Case("png_no_exif", "300x200 PNG with no eXIf chunk", "png",
                PngTestSupport.plain(px, W, H), 1, LANDSCAPE, SRC_LANDSCAPE, jpg));

        for (int o = 1; o <= 8; o++) {
            boolean bigEndian = (o % 2) == 1;
            boolean swap = o >= 5;
            all.add(new Case("png_orientation_" + o + (bigEndian ? "_mm" : "_ii"),
                    "300x200 PNG, eXIf after IHDR, orientation " + o + ", TIFF byte order "
                    + (bigEndian ? "MM" : "II"), "png",
                    PngTestSupport.tagged(px, W, H, o, bigEndian), o,
                    swap ? PORTRAIT : LANDSCAPE, swap ? SRC_PORTRAIT : SRC_LANDSCAPE, jpg));
        }

        byte[] tiff6 = PngTestSupport.orientationTiff(6, true);
        all.add(new Case("png_exif_truncated_tiff",
                "eXIf chunk well-formed, its TIFF cut to 15 bytes (inside the IFD entry): fails open", "png",
                PngTestSupport.png(px, W, H, false,
                        PngTestSupport.chunk("eXIf", PngTestSupport.head(tiff6, 15)), null),
                1, LANDSCAPE, SRC_LANDSCAPE, jpg));
        all.add(new Case("png_exif_shortest_legal_tiff",
                "TIFF cut to 22 bytes: the next-IFD offset is missing but the entry is whole, so 6 is read", "png",
                PngTestSupport.png(px, W, H, false,
                        PngTestSupport.chunk("eXIf", PngTestSupport.head(tiff6, 22)), null),
                6, PORTRAIT, SRC_PORTRAIT, jpg));
        all.add(new Case("png_exif_value_out_of_range",
                "orientation value 9: refused, NORMAL", "png",
                PngTestSupport.tagged(px, W, H, 9, true), 1, LANDSCAPE, SRC_LANDSCAPE, jpg));
        all.add(new Case("png_exif_after_idat",
                "eXIf (6) placed after IDAT: ignored, the tag must precede the pixels", "png",
                PngTestSupport.png(px, W, H, false, null, PngTestSupport.chunk("eXIf", tiff6)),
                1, LANDSCAPE, SRC_LANDSCAPE, jpg));

        byte[] filler = PngTestSupport.chunk("tEXt", new byte[0]);
        byte[][] f254 = new byte[255][];
        byte[][] f255 = new byte[256][];
        java.util.Arrays.fill(f254, filler);
        java.util.Arrays.fill(f255, filler);
        f254[254] = PngTestSupport.chunk("eXIf", tiff6);
        f255[255] = PngTestSupport.chunk("eXIf", tiff6);
        all.add(new Case("png_exif_as_chunk_256",
                "254 empty chunks then eXIf (6): the 256th chunk, the last one the walk examines", "png",
                PngTestSupport.png(px, W, H, false, PngTestSupport.concat(f254), null),
                6, null, null, null));
        all.add(new Case("png_exif_as_chunk_257",
                "255 empty chunks then eXIf (6): past the 256-chunk cap, NORMAL", "png",
                PngTestSupport.png(px, W, H, false, PngTestSupport.concat(f255), null),
                1, null, null, null));

        byte[] lying = PngTestSupport.tagged(px, W, H, 6, true);
        lying[8 + PngTestSupport.IHDR_CHUNK_LEN] = 0x7F;
        all.add(new Case("png_exif_lying_chunk_length",
                "eXIf chunk length 0x7F00001A, far past the end: NORMAL (file not decodable, orientation only)",
                "png", lying, 1, null, null, null));
        all.add(new Case("png_head_only",
                "the first 100 bytes of a tagged (8) PNG: the chunk is inside, the pixels are not "
                + "(orientation only)", "png",
                PngTestSupport.head(PngTestSupport.tagged(px, W, H, 8, false), 100), 8, null, null, null));

        all.add(new Case("png_alpha_orientation_8",
                "300x200 RGBA PNG with fully transparent blocks, orientation 8: the preview stays PNG", "png",
                PngTestSupport.png(PngTestSupport.blocks(W, H, 20, SEED, true), W, H, true,
                        PngTestSupport.chunk("eXIf", PngTestSupport.orientationTiff(8, false)), null),
                8, PORTRAIT, SRC_PORTRAIT, ThumbnailHelper.MIME_PNG));

        BufferedImage rgb = new BufferedImage(W, H, BufferedImage.TYPE_INT_RGB);
        rgb.setRGB(0, 0, W, H, px, 0, W);
        byte[] jpeg = ThumbnailHelper.encodeJpeg(rgb, 0.8f);
        all.add(new Case("jpeg_no_exif", "300x200 baseline JPEG, no APP1", "jpeg",
                jpeg, 1, LANDSCAPE, SRC_LANDSCAPE, jpg));
        all.add(new Case("jpeg_orientation_6",
                "the same JPEG with an EXIF APP1, orientation 6: every format is baked, not only PNG", "jpeg",
                ExifTestSupport.withApp1(jpeg, ExifTestSupport.orientationExif(6, false)),
                6, PORTRAIT, SRC_PORTRAIT, jpg));
        return all;
    }

    // ---- the checks -----------------------------------------------------------------------

    /** Check the Java side against one vector, whether freshly built or read from the file. */
    private static void check(String name, byte[] input, int orientation, JsonNode preview) {
        assertEquals(orientation, ExifOrientation.readImage(input), name + ": readImage");
        boolean png = "image/png".equals(ThumbnailHelper.sniffMediaType(input));
        assertEquals(png ? orientation : 1, ExifOrientation.readPng(input), name + ": readPng");
        assertEquals(png ? 1 : orientation, ExifOrientation.read(input), name + ": read (JPEG)");

        ThumbnailResult r = ThumbnailHelper.generatePreviewForBlob(input, MAX_PREVIEW_BYTES);
        assertNotNull(r, name);
        if (preview == null || preview.isNull()) {
            return; // not decodable everywhere: only the orientation is comparable
        }
        assertEquals(preview.get("outcome").asText(), r.getOutcome().name(), name + ": outcome " + r);
        assertEquals(orientation, r.getExifOrientation(), name + ": reported orientation");
        assertEquals(preview.get("width").asInt(), r.getPreviewWidth(), name + ": preview width");
        assertEquals(preview.get("height").asInt(), r.getPreviewHeight(), name + ": preview height");
        assertEquals(preview.get("sourceWidth").asInt(), r.getSourceWidth(), name + ": displayed source width");
        assertEquals(preview.get("sourceHeight").asInt(), r.getSourceHeight(), name + ": displayed source height");
        assertEquals(preview.get("mime").asText(), r.getPreviewMediaType(), name + ": declared mime");
        assertEquals(preview.get("mime").asText(), ThumbnailHelper.sniffMediaType(r.getPreviewBytes()),
                name + ": mime by magic bytes");
        assertTrue(r.getPreviewBytes().length <= MAX_PREVIEW_BYTES, name + ": within the ceiling");
        assertEquals(ExifOrientation.NORMAL, ExifOrientation.readImage(r.getPreviewBytes()),
                name + ": a preview carries no orientation of its own");
    }

    private static ObjectNode build() throws Exception {
        ObjectNode root = M.createObjectNode();
        root.put("description", "C127: EXIF orientation of an image file (JPEG APP1 / PNG eXIf) and the "
                + "preview it yields. Compare orientation, preview size, displayed source size and mime; "
                + "never pixels or encoded bytes.");
        root.put("generator", "takamaka-extra ImageOrientationVectorTest (-Dc127.vectors.write=true)");
        root.put("javaReference", "io.takamaka.extra.imaging.ExifOrientation.readImage + "
                + "ThumbnailHelper.generatePreviewForBlob");
        root.put("inputEncoding", "standard base64, padded");
        root.put("maxPreviewBytes", MAX_PREVIEW_BYTES);
        root.put("seed", SEED);
        ArrayNode vectors = root.putArray("vectors");
        for (Case c : cases()) {
            ObjectNode v = vectors.addObject();
            v.put("name", c.name);
            v.put("description", c.description);
            v.put("format", c.format);
            v.put("inputLength", c.input.length);
            v.put("expectedOrientation", c.orientation);
            if (c.previewWh == null) {
                v.putNull("expectedPreview");
            } else {
                ObjectNode p = v.putObject("expectedPreview");
                p.put("outcome", "PREVIEW");
                p.put("width", c.previewWh[0]);
                p.put("height", c.previewWh[1]);
                p.put("sourceWidth", c.sourceWh[0]);
                p.put("sourceHeight", c.sourceWh[1]);
                p.put("mime", c.mime);
            }
            v.put("inputBase64", Base64.getEncoder().encodeToString(c.input));
        }
        return root;
    }

    @Test
    @DisplayName("the hand-written expectations hold for freshly built fixtures (and -Dc127.vectors.write regenerates)")
    public void freshFixturesMeetTheDeclaredExpectations() throws Exception {
        ObjectNode root = build();
        for (JsonNode v : root.get("vectors")) {
            check(v.get("name").asText(), Base64.getDecoder().decode(v.get("inputBase64").asText()),
                    v.get("expectedOrientation").asInt(), v.get("expectedPreview"));
        }
        if (Boolean.getBoolean("c127.vectors.write")) {
            Path out = Paths.get("src", "test", "resources", RESOURCE);
            Files.write(out, (M.writeValueAsString(root) + "\n").getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("⭐ the committed vector file: Java reproduces every expectation in it")
    public void committedVectorsHold() throws Exception {
        JsonNode root;
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(RESOURCE)) {
            assertNotNull(in, RESOURCE + " is missing from the test resources");
            root = M.readTree(in);
        }
        assertEquals(MAX_PREVIEW_BYTES, root.get("maxPreviewBytes").asInt());
        JsonNode vectors = root.get("vectors");
        List<Case> declared = cases();
        assertEquals(declared.size(), vectors.size(), "the file and cases() have drifted: regenerate");
        boolean anyTurned = false;
        boolean anyPngPreview = false;
        for (int i = 0; i < vectors.size(); i++) {
            JsonNode v = vectors.get(i);
            Case c = declared.get(i);
            String name = v.get("name").asText();
            assertEquals(c.name, name, "vector order");
            assertEquals(c.orientation, v.get("expectedOrientation").asInt(), name + ": expectation drifted");
            byte[] input = Base64.getDecoder().decode(v.get("inputBase64").asText());
            assertEquals(v.get("inputLength").asInt(), input.length, name + ": inputLength");
            assertFalse(input.length == 0, name);
            JsonNode p = v.get("expectedPreview");
            check(name, input, v.get("expectedOrientation").asInt(), p);
            if (p != null && !p.isNull()) {
                anyTurned |= p.get("height").asInt() > p.get("width").asInt();
                anyPngPreview |= "image/png".equals(p.get("mime").asText());
            }
        }
        assertTrue(anyTurned, "no vector exercises a quarter turn");
        assertTrue(anyPngPreview, "no vector exercises a PNG preview");
    }
}
