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

import io.takamaka.extra.beans.BlockBox;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.identicon.exceptions.AddressDecodeException;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.identicon.exceptions.DecodeBlockException;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmRewardUtils {

    /**
     *
     * @param blockBox
     * @return duplicate allowed, no null value, array can be empty
     * @throws DecodeBlockException
     * @throws AddressDecodeException
     */
    public static final String[] extractRewardAddressesRaw(BlockBox blockBox) throws DecodeBlockException, AddressDecodeException {
        if (blockBox == null) {
            throw new DecodeBlockException("null block");
        }
        if (!blockBox.isValid()) {
            throw new DecodeBlockException("invalid block");
        }
        if (blockBox.getIbb().getRewardList() == null) {
            log.warn("null reward list, usually not null empty");
            return new String[0];
        }
        if (blockBox.getIbb().getRewardList().isEmpty()) {
            log.info("no rewards in block");
            return new String[0];
        }

        String[] rawRewardAddresses
                = blockBox
                        .getIbb()
                        .getRewardList()
                        .values()
                        .stream()
                        .map(b -> b.getUrl64Addr())
                        .filter(a -> !TkmTextUtils.isNullOrBlank(a))
                        .toArray(String[]::new);
        return rawRewardAddresses;
    }
}
