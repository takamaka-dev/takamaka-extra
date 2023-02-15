/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.files;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.takamaka.extra.beans.TkmMetadata;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 *
 * @author tyranneo
 */
@Slf4j
public class MetadataUtils {

    public static final String getOsIdentifyingString() {
        return System.getProperty("os.name");
    }

    public static final TkmMetadata collectMetadata(FileInputStream fileIn, String[] tags) throws IOException, SAXException, TikaException {
        TkmMetadata tkmMetadata = new TkmMetadata();
        Metadata extractMetadatatUsingParser = extractMetadatatUsingParser(fileIn, tkmMetadata);

        String[] names = extractMetadatatUsingParser.names();

        Map<String, String> mappedMetaData = new HashMap<>();
        Map<String, String> mappedExtraMetadata = new HashMap<>();

        for (String name : names) {
            mappedMetaData.put(name, extractMetadatatUsingParser.get(name));
        }
        
        ArrayList<String> tagsArray = new ArrayList<>();
        if (tags != null && tags.length != 0) {

            
            for (String tag : tags) {
                String trimmedTag = StringUtils.trimToNull(tag);
                if (!TkmTextUtils.isNullOrBlank(trimmedTag)) {
                    tagsArray.add(trimmedTag);
                }
            }
        }
        tkmMetadata.setTags(tagsArray.toArray(String[]::new));
        ConcurrentSkipListMap<String, String> extraMetadata = new ConcurrentSkipListMap<>();
        mappedMetaData.entrySet().forEach((single) -> {
            try {
                switch (single.getKey()) {
                    case "Content-Type":
                    case "mime":
                        tkmMetadata.setContentType(single.getValue());
                        tkmMetadata.setMime(single.getValue());
                        break;
                    case "X-Parsed-By":
                        tkmMetadata.setContentType(single.getValue());
                        break;
                    default:
                        extraMetadata.put(
                                single.getKey(), single.getValue()
                        );
                }
            } catch (Exception ex) {
                log.warn("Unreadable metadata", ex);
            }
        });
        tkmMetadata.setExtraMetadata(extraMetadata);
        tkmMetadata.setPlatform(getOsIdentifyingString());
        tkmMetadata.setType("raw");
        return tkmMetadata;
    }

    public static final String fromFileToB64String(File selectedFile) throws IOException {
        byte[] byteFile = FileUtils.readFileToByteArray(selectedFile);
        return TkmSignUtils.fromByteArrayToB64URL(byteFile);
    }

    public static final Metadata extractMetadatatUsingParser(InputStream stream, TkmMetadata tkmMetadata)
            throws IOException, SAXException, TikaException {

        Parser parser = new AutoDetectParser();
        ContentHandler handler = new BodyContentHandler();
        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        parser.parse(stream, handler, metadata, context);

        tkmMetadata.setXParsedBy(parser.getClass().getName());
        return metadata;
    }

}
