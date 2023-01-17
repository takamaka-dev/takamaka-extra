/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package io.takamaka.extra.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.lang.annotation.Native;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TkmMetadata {

    @JsonProperty("tags")
    private String[] tags;
    @JsonProperty("Content-Type")
    private String contentType;
    //mandatory
    @JsonProperty("platform")
    private String platform;
    @JsonProperty("X-Parsed-By")
    private String xParsedBy;
    @JsonProperty("resourceName")
    private String resourceName;

    @JsonProperty("mime")
    private String mime;
    @JsonProperty("type")
    private String type;
    @JsonProperty("extraMetadata")
    private ConcurrentSkipListMap<String, String> extraMetadata;
    //mandatory
    @JsonProperty("data")
    private String data;
}
