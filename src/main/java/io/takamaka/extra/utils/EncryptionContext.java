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
package io.takamaka.extra.utils;

import java.nio.charset.StandardCharsets;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@AllArgsConstructor
public enum EncryptionContext {
    v0_1_a("PBKDF2WithHmacSHA512", 20000, "AES/CBC/PKCS5Padding", "AES", 256, StandardCharsets.UTF_8.name());
    @Getter
    private String passwordHashAlgorithm;
    @Getter
    private int iterations;
    @Getter
    private String transformation;
    @Getter
    private String keySpecAlgorithm;
    @Getter
    private int outputKeyLengthBit;
    @Getter
    private String encoding;

}
