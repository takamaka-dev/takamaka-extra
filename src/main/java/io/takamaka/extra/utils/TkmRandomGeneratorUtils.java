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

import io.takamaka.extra.exceptions.TkmCryptoExtraException;
import org.apache.commons.text.RandomStringGenerator;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class TkmRandomGeneratorUtils {

    public static final String getRandomLetterNumbers(int len) throws TkmCryptoExtraException {
        if (len < 1) {
            throw new TkmCryptoExtraException("invalid string lenght");
        }
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();
        return generator.generate(len);
    }

    public static final String getRandomLetterNumbers(int minLengthInclusive, int maxLengthInclusive) throws TkmCryptoExtraException {
        if (minLengthInclusive < 1) {
            throw new TkmCryptoExtraException("invalid min lenght");
        }
        if (minLengthInclusive > maxLengthInclusive) {
            throw new TkmCryptoExtraException("max length must be greater or equals of min lenght");
        }
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();
        return generator.generate(minLengthInclusive, maxLengthInclusive);
    }

}
