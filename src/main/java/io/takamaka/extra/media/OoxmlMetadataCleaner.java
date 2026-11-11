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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * C120 (documents) — remove the metadata an Office Open XML document ({@code .docx},
 * {@code .xlsx}, {@code .pptx}) carries: author, last editor, company, creation/modification
 * dates, revision count, custom properties, and the EXIF of every embedded photo.
 *
 * <p><strong>Port</strong> of the Dart {@code tkmChat/lib/services/media_cleanup/ooxml_metadata_cleaner.dart}.
 * The package is a zip, and two deflaters never agree byte for byte, so the parity contract is
 * PER ENTRY: same entry names in the same order, each with byte-identical uncompressed content
 * (pinned by {@code java_metadata_clean_vectors.json}).</p>
 *
 * <p>{@code docProps/core.xml} is replaced by an EMPTY core part, {@code docProps/app.xml} by an
 * empty extended-properties part, {@code docProps/custom.xml} is removed together with its
 * {@code <Override>} in {@code [Content_Types].xml} and its {@code <Relationship>} in
 * {@code _rels/.rels}, and every JPEG/PNG/GIF under a {@code media/} folder goes through
 * {@link ImageLocationStripper}. Everything else is copied as it is.</p>
 *
 * <p>Whole package in memory: the caller caps the file size, and {@link #MAX_UNCOMPRESSED_BYTES}
 * caps what the entries may inflate to.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class OoxmlMetadataCleaner {

    private OoxmlMetadataCleaner() {
    }

    /**
     * Ceiling on the total INFLATED size of a package (a zip bomb defence the JVM needs; the Dart
     * side has none). Above it the clean fails, and the caller leaves the file out.
     */
    public static final long MAX_UNCOMPRESSED_BYTES = 512L * 1024 * 1024;

    static final String CORE = "docProps/core.xml";
    static final String APP = "docProps/app.xml";
    static final String CUSTOM = "docProps/custom.xml";
    static final String CONTENT_TYPES = "[Content_Types].xml";
    static final String RELS = "_rels/.rels";

    static final String EMPTY_CORE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" "
            + "xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" "
            + "xmlns:dcmitype=\"http://purl.org/dc/dcmitype/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\"/>";

    static final String EMPTY_APP = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\" "
            + "xmlns:vt=\"http://schemas.openxmlformats.org/officeDocument/2006/docPropsVTypes\"/>";

    private static final Pattern CUSTOM_OVERRIDE
            = Pattern.compile("<Override[^>]*PartName=\"/docProps/custom\\.xml\"[^>]*/>");
    private static final Pattern CUSTOM_RELATIONSHIP
            = Pattern.compile("<Relationship[^>]*Target=\"docProps/custom\\.xml\"[^>]*/>");

    /** What {@link #clean} did. */
    public static final class OoxmlCleanResult {

        private final byte[] bytes;
        private final List<String> propertyParts;
        private final List<String> imagesStripped;

        OoxmlCleanResult(byte[] bytes, List<String> propertyParts, List<String> imagesStripped) {
            this.bytes = bytes;
            this.propertyParts = Collections.unmodifiableList(propertyParts);
            this.imagesStripped = Collections.unmodifiableList(imagesStripped);
        }

        /** The re-zipped package; {@code null} when nothing changed (use the source file). */
        public byte[] getBytes() {
            return bytes;
        }

        /** {@code docProps/*.xml} parts replaced or removed. */
        public List<String> getPropertyParts() {
            return propertyParts;
        }

        /** Embedded images ({@code * /media/ *}) whose metadata was stripped. */
        public List<String> getImagesStripped() {
            return imagesStripped;
        }

        public boolean isChanged() {
            return !propertyParts.isEmpty() || !imagesStripped.isEmpty();
        }
    }

    /** {@code PK\x03\x04} and a {@code [Content_Types].xml} entry — an OOXML package, not just any zip. */
    public static boolean isOoxml(byte[] b) {
        if (b == null || b.length < 30 || b[0] != 0x50 || b[1] != 0x4B || b[2] != 0x03 || b[3] != 0x04) {
            return false;
        }
        final byte[] needle = CONTENT_TYPES.getBytes(StandardCharsets.ISO_8859_1);
        outer:
        for (int i = 0; i + needle.length <= b.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (b[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    /** One entry of the package, held in memory. */
    private static final class Part {

        final String name;
        final boolean directory;
        byte[] data;
        /** {@link ZipEntry#STORED} / {@link ZipEntry#DEFLATED} as in the source; -1 = writer default. */
        final int method;
        /** Source timestamp; -1 = now (a part this cleaner wrote). */
        final long time;

        Part(String name, boolean directory, byte[] data, int method, long time) {
            this.name = name;
            this.directory = directory;
            this.data = data;
            this.method = method;
            this.time = time;
        }
    }

    /**
     * Clean the package at {@code source} (read through its central directory, like the Dart
     * {@code ZipDecoder}); the source file is never modified.
     *
     * @throws IOException not a readable zip, an entry above {@link #MAX_UNCOMPRESSED_BYTES}, I/O
     */
    public static OoxmlCleanResult clean(Path source) throws IOException {
        // name → part, in central-directory order; a repeated name keeps the FIRST entry.
        final Map<String, Part> out = new LinkedHashMap<>();
        final List<String> parts = new ArrayList<>();
        final List<String> images = new ArrayList<>();
        long total = 0;

        try (ZipFile zip = new ZipFile(source.toFile(), StandardCharsets.UTF_8)) {
            final Enumeration<? extends ZipEntry> entries = zip.entries();
            final java.util.Set<String> seen = new java.util.HashSet<>();
            while (entries.hasMoreElements()) {
                final ZipEntry e = entries.nextElement();
                final String name = e.getName();
                if (!seen.add(name)) {
                    continue;
                }
                if (name.endsWith("/") || name.endsWith("\\")) {
                    out.put(name, new Part(name, true, new byte[0], -1, e.getTime()));
                    continue;
                }
                if (name.equals(CORE)) {
                    out.put(name, written(name, EMPTY_CORE));
                    parts.add(name);
                    continue;
                }
                if (name.equals(APP)) {
                    out.put(name, written(name, EMPTY_APP));
                    parts.add(name);
                    continue;
                }
                if (name.equals(CUSTOM)) {
                    parts.add(name + " (removed)");
                    continue;
                }
                final byte[] data;
                try (InputStream in = zip.getInputStream(e)) {
                    data = readCapped(in, MAX_UNCOMPRESSED_BYTES - total);
                }
                total += data.length;
                if (name.contains("/media/") && data.length > 0 && ImageLocationStripper.isSupported(data)) {
                    try {
                        final ImageLocationStripper.StrippedImage r = ImageLocationStripper.strip(data);
                        if (r.isChanged()) {
                            out.put(name, new Part(name, false, r.getBytes(), -1, -1));
                            images.add(name);
                            continue;
                        }
                    } catch (MediaFormatException broken) {
                        // a broken embedded image: keep it as it is, the document is the object
                        // here and the caller reports what was cleaned
                    }
                }
                out.put(name, new Part(name, false, data, e.getMethod(), e.getTime()));
            }
        }

        // A removed custom.xml leaves a dangling relationship + content-type override: drop both
        // so a strict validator does not complain about a missing part. (The rewritten parts move
        // to the END of the package, as on the Dart side.)
        boolean customRemoved = false;
        for (String p : parts) {
            customRemoved |= p.startsWith(CUSTOM);
        }
        if (customRemoved) {
            rewrite(out, CONTENT_TYPES, CUSTOM_OVERRIDE);
            rewrite(out, RELS, CUSTOM_RELATIONSHIP);
        }

        if (parts.isEmpty() && images.isEmpty()) {
            return new OoxmlCleanResult(null, parts, images);
        }
        return new OoxmlCleanResult(zip(out), parts, images);
    }

    private static Part written(String name, String content) {
        return new Part(name, false, content.getBytes(StandardCharsets.UTF_8), -1, -1);
    }

    /**
     * Delete every match of {@code pattern} from the part {@code name} and move the part to the
     * end. The XML is handled as Latin-1 so that every byte outside the removed element survives
     * exactly (the element itself is pure ASCII).
     */
    private static void rewrite(Map<String, Part> out, String name, Pattern pattern) {
        final Part p = out.remove(name);
        if (p == null) {
            return;
        }
        final String xml = new String(p.data, StandardCharsets.ISO_8859_1);
        final String cleaned = pattern.matcher(xml).replaceAll("");
        out.put(name, new Part(name, p.directory, cleaned.getBytes(StandardCharsets.ISO_8859_1), -1, -1));
    }

    private static byte[] zip(Map<String, Part> parts) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
            for (Part p : parts.values()) {
                final ZipEntry e = new ZipEntry(p.name);
                if (p.time >= 0) {
                    e.setTime(p.time);
                }
                if (p.directory || p.method == ZipEntry.STORED) {
                    final CRC32 crc = new CRC32();
                    crc.update(p.data, 0, p.data.length);
                    e.setMethod(ZipEntry.STORED);
                    e.setSize(p.data.length);
                    e.setCompressedSize(p.data.length);
                    e.setCrc(crc.getValue());
                } else {
                    e.setMethod(ZipEntry.DEFLATED);
                }
                zos.putNextEntry(e);
                zos.write(p.data, 0, p.data.length);
                zos.closeEntry();
            }
        }
        return bos.toByteArray();
    }

    private static byte[] readCapped(InputStream in, long budget) throws IOException {
        final ByteArrayOutputStream bos = new ByteArrayOutputStream();
        final byte[] buf = new byte[64 * 1024];
        long left = budget;
        int n;
        while ((n = in.read(buf)) > 0) {
            left -= n;
            if (left < 0) {
                throw new IOException("the package inflates past " + MAX_UNCOMPRESSED_BYTES
                        + " bytes: refusing to hold it in memory");
            }
            bos.write(buf, 0, n);
        }
        return bos.toByteArray();
    }
}
