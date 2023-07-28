/*
 * Copyright 2023 AiliA SA.
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

import io.takamaka.extra.identicon.exceptions.DecodeForwardKeysException;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmForwardKeys {

    public static final String[] extractAddresses(ConcurrentSkipListMap<String, String> fwKeys) throws DecodeForwardKeysException {
        if (fwKeys == null) {
            throw new DecodeForwardKeysException("FW KEYS LIST NULL");
        }
        if (fwKeys.isEmpty()) {
            log.info("no forward keys in block");
            return new String[0];
        }
        return fwKeys.values().toArray(String[]::new);
    }
}
