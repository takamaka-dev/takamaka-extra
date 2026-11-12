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

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import javax.imageio.ImageIO;

/**
 * Test helpers: hand-built containers whose STRUCTURE is real (the cleaners never decode samples,
 * so the payloads can be filler). Java twin of tkmChat's
 * {@code test/services/media_cleanup/synthetic_media.dart}, plus the image builders the
 * wallet-extra-flutter stripper test gets from the Dart {@code image} package.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
final class SyntheticMedia {

    private SyntheticMedia() {
    }

    // ---- bytes -------------------------------------------------------------------------

    static byte[] l1(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    static byte[] cat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] p : parts) {
            if (p != null) {
                out.write(p, 0, p.length);
            }
        }
        return out.toByteArray();
    }

    static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) {
            b[i] = (byte) v[i];
        }
        return b;
    }

    static byte[] filled(int n, int v) {
        byte[] b = new byte[n];
        java.util.Arrays.fill(b, (byte) v);
        return b;
    }

    static byte[] u32(long v) {
        return bytes((int) (v >> 24) & 0xFF, (int) (v >> 16) & 0xFF, (int) (v >> 8) & 0xFF, (int) v & 0xFF);
    }

    static boolean hasBytes(byte[] hay, byte[] needle) {
        return indexOf(hay, needle) >= 0;
    }

    static boolean hasBytes(byte[] hay, String needle) {
        return hasBytes(hay, l1(needle));
    }

    static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i + needle.length <= hay.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    static String sha3(byte[] b) {
        try {
            byte[] d = MessageDigest.getInstance("SHA3-256").digest(b);
            StringBuilder sb = new StringBuilder();
            for (byte x : d) {
                sb.append(String.format("%02x", x & 0xFF));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    // ---- JPEG --------------------------------------------------------------------------

    /** A JPEG segment FF marker len payload. */
    static byte[] segment(int marker, byte[] payload) {
        int len = payload.length + 2;
        return cat(bytes(0xFF, marker, (len >> 8) & 0xFF, len & 0xFF), payload);
    }

    /** Insert {@code seg} right after the SOI of {@code jpeg}. */
    static byte[] withSegmentAfterSoi(byte[] jpeg, byte[] seg) {
        return cat(java.util.Arrays.copyOfRange(jpeg, 0, 2), seg,
                java.util.Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    /**
     * An EXIF TIFF with IFD0 = {Make "Sony", Orientation, DateTime "2026:09:16 10:00:00",
     * GPS IFD pointer} and a GPS IFD {GPSLatitudeRef "N", GPSLatitude 45 27 12}.
     */
    static byte[] geoTiff(int orientation, boolean bigEndian) {
        ByteBuffer b = ByteBuffer.allocate(142).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        b.put(bigEndian ? l1("MM") : l1("II")).putShort((short) 0x002A).putInt(8);
        b.putShort((short) 4);
        b.putShort((short) 0x010F).putShort((short) 2).putInt(5).putInt(62); // Make → 62
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putShort((short) 0x0132).putShort((short) 2).putInt(20).putInt(68); // DateTime → 68
        b.putShort((short) 0x8825).putShort((short) 4).putInt(1).putInt(88); // GPS IFD → 88
        b.putInt(0);
        b.put(l1("Sony\0\0"));
        b.put(l1("2026:09:16 10:00:00\0"));
        b.putShort((short) 2);
        b.putShort((short) 0x0001).putShort((short) 2).putInt(2).put(l1("N\0\0\0"));
        b.putShort((short) 0x0002).putShort((short) 5).putInt(3).putInt(118);
        b.putInt(0);
        b.putInt(45).putInt(1).putInt(27).putInt(1).putInt(12).putInt(1);
        return b.array();
    }

    /** The APP1 EXIF segment carrying {@link #geoTiff}. */
    static byte[] geoApp1(int orientation, boolean bigEndian) {
        return segment(0xE1, cat(l1("Exif\0\0"), geoTiff(orientation, bigEndian)));
    }

    static BufferedImage photoLike(int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                img.setRGB(x, y, (((x * 7) & 0xFF) << 16) | (((y * 11) & 0xFF) << 8) | ((x + y) & 0xFF));
            }
        }
        return img;
    }

    static byte[] encode(BufferedImage img, String format) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(img, format, out)) {
                throw new IllegalStateException("no ImageIO writer for " + format);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static int[] pixels(byte[] encoded) {
        try {
            BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(encoded));
            if (img == null) {
                return null;
            }
            return img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** A real (ImageIO) JPEG with a geotagged EXIF APP1 right after the SOI. */
    static byte[] geotaggedJpeg(int orientation, int w, int h) {
        return withSegmentAfterSoi(encode(photoLike(w, h), "jpg"), geoApp1(orientation, true));
    }

    static byte[] geotaggedJpeg() {
        return geotaggedJpeg(6, 32, 24);
    }

    /**
     * A STRUCTURAL baseline JPEG (deterministic, JDK-independent — for the vectors): SOI, APP0
     * JFIF, {@code extra}, DQT, SOF0, DHT, SOS, entropy filler holding a stuffed FF00 and an RST0,
     * EOI. Not decodable; the stripper never decodes.
     */
    static byte[] structuralJpeg(byte[]... extra) {
        return cat(bytes(0xFF, 0xD8), jfif(), cat(extra), codingAndScan(), bytes(0xFF, 0xD9));
    }

    static byte[] jfif() {
        return segment(0xE0, cat(l1("JFIF\0"), bytes(1, 1, 0, 0, 1, 0, 1, 0, 0)));
    }

    static byte[] codingAndScan() {
        byte[] q = new byte[65];
        for (int i = 1; i < 65; i++) {
            q[i] = (byte) (i + 1);
        }
        return cat(
                segment(0xDB, q),
                segment(0xC0, bytes(8, 0, 16, 0, 16, 1, 1, 0x11, 0)),
                segment(0xC4, cat(bytes(0x00), filled(16, 0), bytes())),
                segment(0xDA, bytes(1, 1, 0x00, 0, 63, 0)),
                bytes(0x12, 0x34, 0xFF, 0x00, 0x56, 0xFF, 0xD0, 0x78, 0x9A, 0xFF, 0x00, 0xBC));
    }

    // ---- PNG ---------------------------------------------------------------------------

    static final byte[] PNG_SIG = bytes(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
    static final int PNG_IHDR_END = 8 + 4 + 4 + 13 + 4;

    static byte[] chunk(String type, byte[] data) {
        CRC32 crc = new CRC32();
        crc.update(l1(type));
        crc.update(data);
        return cat(u32(data.length), l1(type), data, u32(crc.getValue()));
    }

    static byte[] withChunksAfterIhdr(byte[] png, byte[]... chunks) {
        return cat(java.util.Arrays.copyOfRange(png, 0, PNG_IHDR_END), cat(chunks),
                java.util.Arrays.copyOfRange(png, PNG_IHDR_END, png.length));
    }

    /** A STRUCTURAL PNG (deterministic): signature, IHDR 4x4, an IDAT of filler, IEND. */
    static byte[] structuralPng() {
        return cat(PNG_SIG,
                chunk("IHDR", cat(u32(4), u32(4), bytes(8, 2, 0, 0, 0))),
                chunk("IDAT", bytes(0x78, 0x9C, 1, 2, 3, 4, 5, 6, 7, 8, 9)),
                chunk("IEND", new byte[0]));
    }

    static byte[] orientationTiff(int orientation, boolean bigEndian) {
        ByteBuffer b = ByteBuffer.allocate(26).order(bigEndian ? ByteOrder.BIG_ENDIAN : ByteOrder.LITTLE_ENDIAN);
        b.put(bigEndian ? l1("MM") : l1("II")).putShort((short) 0x002A).putInt(8);
        b.putShort((short) 1);
        b.putShort((short) 0x0112).putShort((short) 3).putInt(1).putShort((short) orientation).putShort((short) 0);
        b.putInt(0);
        return b.array();
    }

    // ---- GIF ---------------------------------------------------------------------------

    static byte[] subBlocks(byte[] data) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int i = 0; i < data.length; i += 255) {
            int n = Math.min(255, data.length - i);
            out.write(n);
            out.write(data, i, n);
        }
        out.write(0);
        return out.toByteArray();
    }

    static byte[] commentExt(String text) {
        return cat(bytes(0x21, 0xFE), subBlocks(l1(text)));
    }

    static byte[] appExt(String id, byte[] payload) {
        return cat(bytes(0x21, 0xFF, 11), l1(id), subBlocks(payload));
    }

    /** Offset just after the header, screen descriptor and global colour table. */
    static int gifBodyStart(byte[] gif) {
        int pos = 13;
        if ((gif[10] & 0x80) != 0) {
            pos += 3 * (2 << (gif[10] & 0x07));
        }
        return pos;
    }

    static byte[] withBlocksAfterHeader(byte[] gif, byte[]... blocks) {
        int pos = gifBodyStart(gif);
        return cat(java.util.Arrays.copyOfRange(gif, 0, pos), cat(blocks),
                java.util.Arrays.copyOfRange(gif, pos, gif.length));
    }

    /** A STRUCTURAL GIF (deterministic): header, 2-colour table, {@code blocks}, one image, trailer. */
    static byte[] structuralGif(String version, boolean trailer, byte[]... blocks) {
        return cat(l1("GIF" + version), bytes(2, 0, 2, 0, 0x80, 0, 0), bytes(0, 0, 0, 255, 255, 255),
                cat(blocks), gifImage(false), trailer ? bytes(0x3B) : new byte[0]);
    }

    static byte[] gifImage(boolean localTable) {
        return cat(bytes(0x2C, 0, 0, 0, 0, 2, 0, 2, 0, localTable ? 0x80 : 0),
                localTable ? bytes(1, 2, 3, 4, 5, 6) : new byte[0],
                bytes(2), subBlocks(bytes(0x4C, 0x01)));
    }

    // ---- MP4 ---------------------------------------------------------------------------

    static byte[] box(String type, byte[]... payload) {
        byte[] p = cat(payload);
        return cat(u32(8 + p.length), l1(type), p);
    }

    /** Offset of the {@code moov/udta} box in the LAST {@link #androidMp4} built. */
    static int lastUdtaOffset = -1;

    static byte[] androidMp4(String location) {
        return androidMp4(location, true, 0xD0000001L, false, false);
    }

    /**
     * {@code ftyp} + [{@code uuid}] + {@code moov(mvhd, udta(©xyz), meta, trak(tkhd, [udta],
     * mdia(mdhd)))} + {@code mdat} — the Sony/Android camera shape. {@code location} null → no
     * {@code udta}.
     */
    static byte[] androidMp4(String location, boolean meta, long creationTime, boolean trakUdta, boolean xmpUuid) {
        byte[] ftyp = box("ftyp", l1("mp42"), u32(0), l1("isommp42"));
        byte[] times = cat(bytes(0, 0, 0, 0), u32(creationTime), u32(creationTime));
        byte[] mvhd = box("mvhd", times, u32(1000), u32(5000), filled(80, 0));
        byte[] tkhd = box("tkhd", times, u32(1), u32(0), u32(5000), filled(60, 0));
        byte[] mdhd = box("mdhd", times, u32(90000), u32(450000), bytes(0x55, 0xC4, 0, 0));
        byte[] stbl = box("stbl", box("stco", bytes(0, 0, 0, 0), u32(1), u32(0)));
        byte[] minf = box("minf", stbl);
        byte[] mdia = box("mdia", mdhd, minf);
        byte[] trak = box("trak", tkhd,
                trakUdta ? box("udta", box("loci", l1("\0\0\0\0somewhere\0"))) : new byte[0],
                mdia);
        byte[] udta = location == null ? new byte[0]
                : box("udta", box("©xyz", bytes(0, location.length(), 0x15, 0xC7), l1(location)));
        byte[] metaBox = !meta ? new byte[0] : box("meta",
                u32(0),
                box("hdlr", u32(0), u32(0), l1("mdta"), filled(12, 0), bytes(0)),
                box("keys", u32(0), u32(1), box("mdta", l1("com.android.version"))),
                box("ilst", box("\0\0\0\u0001", box("data", u32(1), u32(0), l1("13")))));
        byte[] moov = box("moov", mvhd, udta, metaBox, trak);
        byte[] mdatPayload = new byte[4096];
        for (int i = 0; i < mdatPayload.length; i++) {
            mdatPayload[i] = (byte) ((i * 31 + 7) & 0xFF);
        }
        byte[] mdat = box("mdat", mdatPayload);
        byte[] uuid = !xmpUuid ? new byte[0] : box("uuid",
                bytes(0xBE, 0x7A, 0xCF, 0xCB, 0x97, 0xA9, 0x42, 0xE8, 0x9C, 0x71, 0x99, 0x94, 0x91, 0xE3, 0xAF, 0xAC),
                l1("<x:xmpmeta><exif:GPSLatitude>46,0N</exif:GPSLatitude></x:xmpmeta>"));
        lastUdtaOffset = location == null ? -1 : ftyp.length + uuid.length + 8 + mvhd.length;
        return cat(ftyp, uuid, moov, mdat);
    }

    static final class BoxInfo {

        final String type;
        final int offset;
        final int size;

        BoxInfo(String type, int offset, int size) {
            this.type = type;
            this.offset = offset;
            this.size = size;
        }
    }

    /** Flat pre-order list of every box (descending into moov/trak/mdia/minf/stbl). */
    static List<BoxInfo> walkBoxes(byte[] b) {
        List<BoxInfo> out = new ArrayList<>();
        walkBoxes(b, 0, b.length, out);
        return out;
    }

    private static void walkBoxes(byte[] b, int start, int end, List<BoxInfo> out) {
        int pos = start;
        while (pos + 8 <= end) {
            int size = ((b[pos] & 0xFF) << 24) | ((b[pos + 1] & 0xFF) << 16) | ((b[pos + 2] & 0xFF) << 8) | (b[pos + 3] & 0xFF);
            String type = new String(b, pos + 4, 4, StandardCharsets.ISO_8859_1);
            if (size < 8) {
                break;
            }
            out.add(new BoxInfo(type, pos, size));
            if (type.equals("moov") || type.equals("trak") || type.equals("mdia") || type.equals("minf") || type.equals("stbl")) {
                walkBoxes(b, pos + 8, pos + size, out);
            }
            pos += size;
        }
    }

    static BoxInfo firstBox(byte[] b, String type) {
        for (BoxInfo i : walkBoxes(b)) {
            if (i.type.equals(type)) {
                return i;
            }
        }
        throw new IllegalStateException("no " + type + " box");
    }

    // ---- PDF ---------------------------------------------------------------------------

    static byte[] pdf() {
        return pdf("Giovanni A.", "Quarterly notes", "4D696C616E6F", true);
    }

    /**
     * A minimal, VALID single-page PDF with an Info dictionary and an uncompressed XMP
     * {@code /Metadata} stream referenced from the catalog; the xref offsets are computed.
     */
    static byte[] pdf(String author, String title, String hexSubject, boolean xmp) {
        List<String> objs = new ArrayList<>();
        objs.add("<< /Type /Catalog /Pages 2 0 R" + (xmp ? " /Metadata 6 0 R" : "") + " >>");
        objs.add("<< /Type /Pages /Kids [3 0 R] /Count 1 >>");
        objs.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 200 100] /Contents 4 0 R >>");
        String content = "BT /F1 12 Tf 20 50 Td (hello) Tj ET";
        objs.add("<< /Length " + content.length() + " >>\nstream\n" + content + "\nendstream");
        objs.add("<< /Title (" + title + ") /Author (" + author + ") /Creator (Pages 13.2) /Producer (macOS) "
                + "/CreationDate (D:20260916100000+02'00') /ModDate (D:20260916101500+02'00') /Subject <"
                + hexSubject + "> /Keywords (home, garden) >>");
        if (xmp) {
            String packet = "<?xpacket begin=\"ï»¿\" id=\"W5M0MpCehiHzreSzNTczkc9d\"?>"
                    + "<x:xmpmeta xmlns:x=\"adobe:ns:meta/\"><rdf:RDF><rdf:Description dc:creator=\"Giovanni A.\" "
                    + "exif:GPSLatitude=\"45,27.2N\"/></rdf:RDF></x:xmpmeta><?xpacket end=\"w\"?>";
            objs.add("<< /Type /Metadata /Subtype /XML /Length " + packet.length() + " >>\nstream\n" + packet + "\nendstream");
        }
        StringBuilder sb = new StringBuilder("%PDF-1.4\n%âãÏÓ\n");
        List<Integer> offsets = new ArrayList<>();
        for (int i = 0; i < objs.size(); i++) {
            offsets.add(sb.length());
            sb.append(i + 1).append(" 0 obj\n").append(objs.get(i)).append("\nendobj\n");
        }
        int xref = sb.length();
        sb.append("xref\n0 ").append(objs.size() + 1).append("\n0000000000 65535 f \n");
        for (int o : offsets) {
            sb.append(String.format("%010d", o)).append(" 00000 n \n");
        }
        sb.append("trailer\n<< /Size ").append(objs.size() + 1).append(" /Root 1 0 R /Info 5 0 R >>\nstartxref\n")
                .append(xref).append("\n%%EOF\n");
        return l1(sb.toString());
    }

    // ---- zip / OOXML -------------------------------------------------------------------

    /** One zip entry to write: a name ending in '/' is a directory. */
    static final class Z {

        final String name;
        final byte[] data;
        final boolean stored;

        Z(String name, byte[] data, boolean stored) {
            this.name = name;
            this.data = data;
            this.stored = stored;
        }
    }

    static Z z(String name, String text) {
        return new Z(name, text.getBytes(StandardCharsets.UTF_8), false);
    }

    static Z z(String name, byte[] data) {
        return new Z(name, data, false);
    }

    static Z stored(String name, byte[] data) {
        return new Z(name, data, true);
    }

    /** A zip with fixed entry timestamps (2026-01-01 00:00 local), in the given order. */
    static byte[] zip(Z... entries) {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(bos, StandardCharsets.UTF_8)) {
                for (Z z : entries) {
                    ZipEntry e = new ZipEntry(z.name);
                    e.setTimeLocal(LocalDateTime.of(2026, 1, 1, 0, 0));
                    if (z.stored || z.name.endsWith("/")) {
                        CRC32 crc = new CRC32();
                        crc.update(z.data);
                        e.setMethod(ZipEntry.STORED);
                        e.setSize(z.data.length);
                        e.setCompressedSize(z.data.length);
                        e.setCrc(crc.getValue());
                    }
                    zos.putNextEntry(e);
                    zos.write(z.data);
                    zos.closeEntry();
                }
            }
            return bos.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
            + "<Default Extension=\"jpeg\" ContentType=\"image/jpeg\"/>"
            + "<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>"
            + "<Override PartName=\"/docProps/core.xml\" ContentType=\"application/vnd.openxmlformats-package.core-properties+xml\"/>"
            + "<Override PartName=\"/docProps/app.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.extended-properties+xml\"/>"
            + "<Override PartName=\"/docProps/custom.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.custom-properties+xml\"/>"
            + "</Types>";
    static final String RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
            + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>"
            + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/package/2006/relationships/metadata/core-properties\" Target=\"docProps/core.xml\"/>"
            + "<Relationship Id=\"rId3\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/extended-properties\" Target=\"docProps/app.xml\"/>"
            + "<Relationship Id=\"rId4\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/custom-properties\" Target=\"docProps/custom.xml\"/>"
            + "</Relationships>";
    static final String CORE = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<cp:coreProperties xmlns:cp=\"http://schemas.openxmlformats.org/package/2006/metadata/core-properties\" xmlns:dc=\"http://purl.org/dc/elements/1.1/\" xmlns:dcterms=\"http://purl.org/dc/terms/\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">"
            + "<dc:title>Budget</dc:title><dc:creator>Giovanni Antino</dc:creator><cp:lastModifiedBy>tech@example.org</cp:lastModifiedBy>"
            + "<dcterms:created xsi:type=\"dcterms:W3CDTF\">2026-09-16T08:00:00Z</dcterms:created><cp:revision>14</cp:revision></cp:coreProperties>";
    static final String APP = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
            + "<Properties xmlns=\"http://schemas.openxmlformats.org/officeDocument/2006/extended-properties\">"
            + "<Application>Microsoft Office Word</Application><Company>H2T Coin SA</Company><TotalTime>412</TotalTime></Properties>";
    static final String CUSTOM = "<?xml version=\"1.0\"?><Properties><property name=\"Office\" pid=\"2\"><vt:lpwstr>Lugano</vt:lpwstr></property></Properties>";
    static final String DOCUMENT = "<?xml version=\"1.0\"?><w:document><w:body><w:p><w:r><w:t>Hello budget</w:t></w:r></w:p></w:body></w:document>";

    static byte[] docx(boolean withCustom, byte[] image) {
        List<Z> a = new ArrayList<>();
        a.add(z("[Content_Types].xml", CONTENT_TYPES));
        a.add(z("_rels/.rels", RELS));
        a.add(z("docProps/core.xml", CORE));
        a.add(z("docProps/app.xml", APP));
        a.add(z("word/document.xml", DOCUMENT));
        if (withCustom) {
            a.add(z("docProps/custom.xml", CUSTOM));
        }
        if (image != null) {
            a.add(z("word/media/image1.jpeg", image));
        }
        return zip(a.toArray(new Z[0]));
    }
    private static int le(byte[] b, int at, int n) {
        int v = 0;
        for (int i = n - 1; i >= 0; i--) {
            v = (v << 8) | (b[at + i] & 0xFF);
        }
        return v;
    }

    /**
     * A copy of {@code zip} whose entry {@code name} DECLARES {@code size} uncompressed bytes,
     * wherever a reader may look: the central directory, the local header and the data
     * descriptor. The compressed data is not touched, so the entry still inflates to what it
     * really holds.
     */
    static byte[] withDeclaredSize(byte[] zip, String name, long size) {
        byte[] out = zip.clone();
        byte[] n = name.getBytes(StandardCharsets.UTF_8);
        for (int cen = indexOf(out, bytes(0x50, 0x4B, 0x01, 0x02)); cen >= 0 && le(out, cen, 4) == 0x02014b50;) {
            int nameLen = le(out, cen + 28, 2);
            if (nameLen == n.length && java.util.Arrays.equals(java.util.Arrays.copyOfRange(out, cen + 46, cen + 46 + nameLen), n)) {
                byte[] declared = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt((int) size).array();
                int flags = le(out, cen + 8, 2);
                int compressed = le(out, cen + 20, 4);
                int loc = le(out, cen + 42, 4);
                System.arraycopy(declared, 0, out, cen + 24, 4);
                if ((flags & 0x08) != 0) {
                    // sizes after the data: PK\7\8, crc, compressed size, size
                    int descriptor = loc + 30 + le(out, loc + 26, 2) + le(out, loc + 28, 2) + compressed;
                    if (le(out, descriptor, 4) != 0x08074b50) {
                        throw new IllegalStateException(name + ": no data descriptor where the entry ends");
                    }
                    System.arraycopy(declared, 0, out, descriptor + 12, 4);
                } else {
                    System.arraycopy(declared, 0, out, loc + 22, 4);
                }
                return out;
            }
            cen += 46 + nameLen + le(out, cen + 30, 2) + le(out, cen + 32, 2);
        }
        throw new IllegalStateException(name + " is not in the zip");
    }

    /** name → uncompressed bytes, in archive order (directories included, empty). */
    static java.util.LinkedHashMap<String, byte[]> unzip(byte[] zip) {
        try {
            java.util.LinkedHashMap<String, byte[]> out = new java.util.LinkedHashMap<>();
            try (java.util.zip.ZipInputStream in = new java.util.zip.ZipInputStream(
                    new java.io.ByteArrayInputStream(zip), StandardCharsets.UTF_8)) {
                ZipEntry e;
                while ((e = in.getNextEntry()) != null) {
                    out.put(e.getName(), in.readAllBytes());
                }
            }
            return out;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
