/*
 * Copyright 2025 AiliA SA.
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
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class StreamEncryptedDescriptor implements Serializable {

    private static final long serialVersionUID = -5363192068927539306L;

    @JsonProperty("pa")
    private String passwordHashAlgorithm;
    @JsonProperty("it")
    private Integer iterations;
    @JsonProperty("tr")
    private String transformation;
    @JsonProperty("ka")
    private String keySpecAlgorithm;
    @JsonProperty("tv")
    private String tkVersion;
    @JsonProperty("kl")
    private Integer outputKeyLengthBit;
    @JsonProperty("ec")
    private String encoding;
    @JsonProperty("iv")
    private String iv;
    @JsonProperty("iv_length_byte")
    private Integer ivLengthByte;
    @JsonProperty("tag_length_bit")
    private Integer tagLengthBit;
    @JsonProperty("encrypted_content_hash")
    private String encryptedContentHash;
    @JsonProperty("salt")
    private String salt;
    @JsonProperty("digest_hash_function")
    private String digestHashFunction;

}
