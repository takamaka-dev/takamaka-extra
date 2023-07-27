/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.beans;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author giovanni.antino@h2tcoin.com
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FileMessageBean {

    /**
     * {
     *
     * "Content-Type":"text/plain",
     *
     * "platform":"ios",
     *
     * "X-Parsed-By":"org.apache.tika.parser.EmptyParser", "mime":"text/plain",
     *
     * "resourceName":"test.txt", "type":"raw",
     *
     * "tags":[ "pollo", "gatto", "file di test", "è un test accentato più" ]
     *
     * "mime":"image/jpeg",
     * 
     * "type":"raw",
     * 
     * "data":"dGVzdCBmaWxlIGNvbnRlbnQgaXMgdGV4dAo.",
     *
     * }
     *
     */
    //mandatory
    @JsonProperty("Content-Type")
    private String contentType;
    //mandatory
    @JsonProperty("platform")
    private String platform;
    @JsonProperty("X-Parsed-By")
    private String xParsedBy;
    @JsonProperty("resourceName")
    private String resourceName;
    @JsonProperty("tags")
    private String[] tags;
    @JsonProperty("mime")
    private String mime;
    @JsonProperty("type")
    private String type;
    //mandatory
    @JsonProperty("data")
    private String data;

}
