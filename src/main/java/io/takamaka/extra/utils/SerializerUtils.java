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
import com.fasterxml.jackson.databind.SerializationFeature;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.beans.FileMessageBean;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Iris Dimni iris.dimni@takamaka.io
 */
@Slf4j
public class SerializerUtils {

    public static final String TSQUERY_PARAM_STRING = "^[\\p{Alnum}\\p{Blank}\\p{Space}]$";
    public static final Pattern TSQUERY_PARAM_PATTERN = Pattern.compile(TSQUERY_PARAM_STRING);

    public static final FileMessageBean decodeTagsString(String message) {
        try {
            return TkmTextUtils.getJacksonMapper().readValue(message, FileMessageBean.class);
        } catch (JsonProcessingException ex) {
            log.debug("message can not be decoded ", ex);

        }
        return null;
    }

    public static final String removeUnsafeTsQuery(String message) {
        char[] chArr = message.toCharArray();
        char[] resArray = new char[chArr.length];
        IntStream.range(0, chArr.length).parallel().forEach((index) -> {
            if (TSQUERY_PARAM_PATTERN.matcher(String.valueOf(chArr[index])).find()) {
                resArray[index] = chArr[index];
            } else {
                resArray[index] = ' ';
            }
        });
        return String.valueOf(resArray);
    }

    public static final String getJson(EncMessageBean encMessageBean) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper()
                .configure(SerializationFeature.INDENT_OUTPUT, false)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .writeValueAsString(encMessageBean);
    }

    public static final EncMessageBean getEncMessageBeanFromJson(String encMessageBeanJson) throws JsonProcessingException {
        return TkmTextUtils.getJacksonMapper().readValue(encMessageBeanJson, EncMessageBean.class);
    }
}
