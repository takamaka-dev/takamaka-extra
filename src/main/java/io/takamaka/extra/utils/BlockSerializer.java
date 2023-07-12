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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.extra.beans.CoinbaseMessageBean;
import io.takamaka.wallet.utils.TkmTextUtils;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class BlockSerializer {

    public static final String toJson(CoinbaseMessageBean cmb) {
        try {
            return TkmTextUtils.getJacksonMapper().writeValueAsString(cmb);
        } catch (JsonProcessingException ex) {
            log.error("can't serialize CoinbaseMessagebean", ex);
            return null;
        }
    }

    public static final CoinbaseMessageBean getCoinBaseMessageBeanFromJson(String coinbaseMessage) {
        try {
            return TkmTextUtils.getJacksonMapper().readValue(coinbaseMessage, CoinbaseMessageBean.class);
        } catch (JsonProcessingException ex) {
            log.error("can't deserialize CoinbaseMessagebean", ex);
            return null;
        }
    }
}