/*
 * Copyright 2024 AiliA SA.
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
package io.takamaka.extra.beans;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EncMessageBean {

    @JsonProperty("pa")
    private String passwordHashAlgorithm;
    @JsonProperty("it")
    private int iterations;
    @JsonProperty("tr")
    private String transformation;
    @JsonProperty("ka")
    private String keySpecAlgorithm;
    @JsonProperty("tv")
    private String tkVersion;
    @JsonProperty("kl")
    private int outputKeyLengthBit;
    @JsonProperty("ec")
    private String encoding;
    @JsonProperty("em")
    private String[] encryptedMessage;

}
