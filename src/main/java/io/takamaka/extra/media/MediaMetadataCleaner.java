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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * C120 — one entry point for "remove location &amp; metadata" on anything that can be attached,
 * dispatched on MAGIC BYTES (never on the name alone — a {@code .jpg} that is a HEIC is refused,
 * a video named {@code clip.bin} is still a video).
 *
 * <p><strong>Port</strong> of the Dart {@code MediaMetadataCleaner.cleanToFile}
 * (tkmChat {@code lib/services/media_cleanup/media_metadata_cleaner.dart}).</p>
 *
 * <table>
 * <caption>dispatch</caption>
 * <tr><th>Bytes</th><th>Cleaner</th><th>Failure means</th></tr>
 * <tr><td>JPEG / PNG / GIF</td><td>{@link ImageLocationStripper}</td><td>left out</td></tr>
 * <tr><td>{@code ftyp} (MP4/MOV/3GP)</td><td>{@link Mp4MetadataCleaner} — file to file, no re-encode</td><td>left out</td></tr>
 * <tr><td>{@code %PDF-}</td><td>{@link PdfMetadataCleaner} — length-preserving</td><td>left out</td></tr>
 * <tr><td>zip + an Office media type</td><td>{@link OoxmlMetadataCleaner} — parts rewritten</td><td>left out</td></tr>
 * <tr><td>any other image/video type (WebP, HEIC, AVIF, TIFF, WebM, …)</td><td>—</td><td>left out (cannot vouch)</td></tr>
 * <tr><td>anything else</td><td>—</td><td><strong>used unchanged</strong>, the user is told</td></tr>
 * </table>
 *
 * <p>The output is a NEW file in {@code outDir}, named {@code <stem>_nolocation.<ext>}; the
 * source is never modified or deleted. Videos are cleaned file-to-file; images and documents are
 * held in memory up to {@link #MAX_IN_MEMORY_BYTES}.</p>
 *
 * <h2>The media type</h2>
 *
 * <p>The Dart side is handed the picker's MIME type, which tkmChat derives from the file NAME
 * ({@code lookupMimeType}). {@link #clean(Path, Path, MetadataCleanOptions)} does the same with
 * {@link #mediaTypeForName}; {@link #clean(Path, Path, MetadataCleanOptions, String)} takes it
 * from the caller. The type is only consulted where the Dart side consults it: to tell an Office
 * package from a plain zip, and to refuse an image/video no cleaner exists for (WebP, WebM and
 * TIFF have no magic-byte rule here, exactly as on the Dart side: under a name that hides them
 * they pass through, told).</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class MediaMetadataCleaner {

    private MediaMetadataCleaner() {
    }

    /**
     * Images and documents above this are not cleaned in memory: documents pass through (told),
     * images are left out.
     */
    public static final long MAX_IN_MEMORY_BYTES = 96L * 1024 * 1024;

    /** The suffix of a cleaned copy's stem. */
    public static final String SUFFIX = "_nolocation";

    /** {@link CleanOutcome#getDetail()} when the switch is off. */
    public static final String DETAIL_OFF = "metadata removal is off";

    private enum Kind {
        IMAGE, VIDEO, PDF, OOXML, UNSUPPORTED_MEDIA, OTHER
    }

    /** {@link #clean(Path, Path, MetadataCleanOptions, String)} with the media type taken from the file name. */
    public static CleanOutcome clean(Path src, Path outDir, MetadataCleanOptions options) {
        return clean(src, outDir, options, mediaTypeForName(fileName(src)));
    }

    /**
     * Clean {@code src} into {@code outDir}. Never throws for a bad file: every failure is a
     * {@link CleanOutcome.Status#FAILED} outcome, and nothing is left in {@code outDir}.
     *
     * @param src       the file to clean; never modified
     * @param outDir    where the cleaned copy goes (created when missing)
     * @param options   {@code null} or {@link MetadataCleanOptions#off()} → the file is used as it is
     * @param mediaType the declared media type, or {@code null} ({@code application/octet-stream})
     */
    public static CleanOutcome clean(Path src, Path outDir, MetadataCleanOptions options, String mediaType) {
        if (options == null || !options.isEnabled()) {
            return new CleanOutcome(CleanOutcome.Status.PASSTHROUGH, src, DETAIL_OFF);
        }
        final String mime = mediaType == null ? "application/octet-stream" : mediaType;
        final String name = fileName(src);
        Path written = null;
        try {
            final long size = Files.size(src);
            final byte[] head = Mp4MetadataCleaner.readHead(src, 64);
            final Kind kind = classify(head, mime);
            switch (kind) {
                case IMAGE: {
                    if (size > MAX_IN_MEMORY_BYTES) {
                        return failed(src, "image too large to clean in memory");
                    }
                    final ImageLocationStripper.StrippedImage r
                            = ImageLocationStripper.strip(Files.readAllBytes(src));
                    if (!r.isChanged()) {
                        return unchanged(src);
                    }
                    written = sibling(outDir, name, ImageLocationStripper.extensionFor(r.getMediaType()));
                    write(written, r.getBytes());
                    return cleaned(written, "EXIF/XMP removed, orientation " + r.getOrientation() + " kept");
                }
                case VIDEO: {
                    final String ext = extension(name);
                    written = sibling(outDir, name, ext.isEmpty() ? ".mp4" : ext);
                    Files.createDirectories(written.getParent());
                    final Mp4MetadataCleaner.Mp4CleanResult r = Mp4MetadataCleaner.cleanFile(src, written);
                    if (!r.isChanged()) {
                        deleteQuietly(written);
                        return unchanged(src);
                    }
                    return cleaned(written, "blanked " + String.join(", ", r.getBlanked()) + "; "
                            + r.getTimestampsZeroed() + " timestamps zeroed");
                }
                case PDF: {
                    if (size > MAX_IN_MEMORY_BYTES) {
                        return tooLargeDocument(src, size);
                    }
                    final PdfMetadataCleaner.PdfCleanResult r = PdfMetadataCleaner.clean(Files.readAllBytes(src));
                    if (!r.isChanged()) {
                        return unchanged(src);
                    }
                    written = sibling(outDir, name, ".pdf");
                    write(written, r.getBytes());
                    return cleaned(written, r.getInfoValuesBlanked() + " info values, "
                            + r.getMetadataRefsBlanked() + " XMP refs, "
                            + r.getMetadataStreamsZeroed() + " XMP streams, "
                            + r.getXpacketsBlanked() + " packets");
                }
                case OOXML: {
                    if (size > MAX_IN_MEMORY_BYTES) {
                        return tooLargeDocument(src, size);
                    }
                    if (!OoxmlMetadataCleaner.isOoxml(Files.readAllBytes(src))) {
                        return new CleanOutcome(CleanOutcome.Status.PASSTHROUGH, src,
                                "a zip, not an Office document");
                    }
                    final OoxmlMetadataCleaner.OoxmlCleanResult r = OoxmlMetadataCleaner.clean(src);
                    if (!r.isChanged()) {
                        return unchanged(src);
                    }
                    written = sibling(outDir, name, extension(name));
                    write(written, r.getBytes());
                    return cleaned(written, "parts " + String.join(", ", r.getPropertyParts())
                            + "; images " + r.getImagesStripped().size());
                }
                case UNSUPPORTED_MEDIA:
                    return failed(src, "no cleaner can vouch for this image/video format");
                default:
                    return new CleanOutcome(CleanOutcome.Status.PASSTHROUGH, src,
                            "no cleaner for this file type");
            }
        } catch (IOException | RuntimeException e) {
            deleteQuietly(written);
            return failed(src, String.valueOf(e.getMessage() == null ? e.toString() : e.getMessage()));
        }
    }

    // ---------------------------------------------------------------------

    private static CleanOutcome cleaned(Path out, String detail) {
        return new CleanOutcome(CleanOutcome.Status.CLEANED, out, detail);
    }

    private static CleanOutcome unchanged(Path src) {
        return new CleanOutcome(CleanOutcome.Status.UNCHANGED, src, "nothing to remove");
    }

    private static CleanOutcome failed(Path src, String detail) {
        return new CleanOutcome(CleanOutcome.Status.FAILED, src, detail);
    }

    private static CleanOutcome tooLargeDocument(Path src, long size) {
        return new CleanOutcome(CleanOutcome.Status.PASSTHROUGH, src,
                "document too large to clean in memory (" + size + " B)");
    }

    private static void write(Path out, byte[] bytes) throws IOException {
        Files.createDirectories(out.getParent());
        Files.write(out, bytes);
    }

    private static void deleteQuietly(Path p) {
        if (p == null) {
            return;
        }
        try {
            Files.deleteIfExists(p);
        } catch (IOException | RuntimeException ignored) {
            // best effort
        }
    }

    private static Kind classify(byte[] h, String mimeType) {
        if (ImageLocationStripper.isSupported(h)) {
            return Kind.IMAGE;
        }
        if (Mp4MetadataCleaner.isIsoBmff(h)) {
            // HEIC/AVIF photos: same signature, not cleanable → left out.
            return Mp4MetadataCleaner.isStillImageBrand(h) ? Kind.UNSUPPORTED_MEDIA : Kind.VIDEO;
        }
        if (PdfMetadataCleaner.isPdf(h)) {
            return Kind.PDF;
        }
        final String mime = mimeType.toLowerCase(Locale.ROOT);
        if (h.length >= 4 && h[0] == 0x50 && h[1] == 0x4B && h[2] == 0x03 && h[3] == 0x04) {
            // A zip: OOXML if it is one (checked on the whole file by the cleaner); the declared
            // type is only a hint that avoids reading a plain .zip fully.
            if (mime.contains("officedocument") || mime.contains("msword")
                    || mime.contains("ms-excel") || mime.contains("ms-powerpoint")) {
                return Kind.OOXML;
            }
            return Kind.OTHER;
        }
        if (mimeType.startsWith("image/") || mimeType.startsWith("video/")) {
            return Kind.UNSUPPORTED_MEDIA;
        }
        return Kind.OTHER;
    }

    // ---- names -------------------------------------------------------------------------

    private static String fileName(Path p) {
        final Path n = p.getFileName();
        return n == null ? "" : n.toString();
    }

    /** {@code <outDir>/<stem>_nolocation<ext>} — {@code ext} with its dot, or empty. */
    static Path sibling(Path outDir, String name, String ext) {
        return outDir.resolve(stem(name) + SUFFIX + ext);
    }

    /**
     * The extension with its dot, as Dart's {@code path.extension}: the last dot counts, a
     * leading dot does not ({@code ".bashrc"} → {@code ""}).
     */
    static String extension(String name) {
        final int dot = name.lastIndexOf('.');
        return dot <= 0 ? "" : name.substring(dot);
    }

    static String stem(String name) {
        final int dot = name.lastIndexOf('.');
        return dot <= 0 ? name : name.substring(0, dot);
    }

    private static final Map<String, String> MEDIA_TYPES = new HashMap<>();

    static {
        final String[][] t = {
            {"jpg", "image/jpeg"}, {"jpeg", "image/jpeg"}, {"png", "image/png"}, {"gif", "image/gif"},
            {"webp", "image/webp"}, {"heic", "image/heic"}, {"heif", "image/heif"}, {"avif", "image/avif"},
            {"tif", "image/tiff"}, {"tiff", "image/tiff"}, {"bmp", "image/bmp"}, {"svg", "image/svg+xml"},
            {"ico", "image/x-icon"}, {"jxl", "image/jxl"}, {"psd", "image/vnd.adobe.photoshop"},
            {"dng", "image/x-adobe-dng"}, {"cr2", "image/x-canon-cr2"}, {"nef", "image/x-nikon-nef"},
            {"arw", "image/x-sony-arw"},
            {"mp4", "video/mp4"}, {"m4v", "video/x-m4v"}, {"mov", "video/quicktime"}, {"3gp", "video/3gpp"},
            {"3g2", "video/3gpp2"}, {"webm", "video/webm"}, {"mkv", "video/x-matroska"},
            {"avi", "video/x-msvideo"}, {"wmv", "video/x-ms-wmv"}, {"flv", "video/x-flv"},
            {"mpg", "video/mpeg"}, {"mpeg", "video/mpeg"}, {"ogv", "video/ogg"}, {"ts", "video/mp2t"},
            {"pdf", "application/pdf"}, {"zip", "application/zip"},
            {"docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {"dotx", "application/vnd.openxmlformats-officedocument.wordprocessingml.template"},
            {"xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {"xltx", "application/vnd.openxmlformats-officedocument.spreadsheetml.template"},
            {"pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {"ppsx", "application/vnd.openxmlformats-officedocument.presentationml.slideshow"},
            {"potx", "application/vnd.openxmlformats-officedocument.presentationml.template"},
            {"docm", "application/vnd.ms-word.document.macroenabled.12"},
            {"dotm", "application/vnd.ms-word.template.macroenabled.12"},
            {"xlsm", "application/vnd.ms-excel.sheet.macroenabled.12"},
            {"xltm", "application/vnd.ms-excel.template.macroenabled.12"},
            {"xlsb", "application/vnd.ms-excel.sheet.binary.macroenabled.12"},
            {"pptm", "application/vnd.ms-powerpoint.presentation.macroenabled.12"},
            {"ppsm", "application/vnd.ms-powerpoint.slideshow.macroenabled.12"},
            {"potm", "application/vnd.ms-powerpoint.template.macroenabled.12"},
            {"doc", "application/msword"}, {"xls", "application/vnd.ms-excel"},
            {"ppt", "application/vnd.ms-powerpoint"}
        };
        for (String[] e : t) {
            MEDIA_TYPES.put(e[0], e[1]);
        }
    }

    /**
     * The media type a file NAME declares (the types the dispatcher cares about, as the Dart
     * {@code mime} package names them); {@code application/octet-stream} for anything else.
     */
    public static String mediaTypeForName(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        final int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "application/octet-stream";
        }
        final String t = MEDIA_TYPES.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
        return t == null ? "application/octet-stream" : t;
    }
}
