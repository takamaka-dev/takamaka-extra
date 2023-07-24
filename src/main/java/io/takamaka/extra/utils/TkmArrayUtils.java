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

import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListSet;

/**
 *
 * @author Giovanni
 */
public class TkmArrayUtils {

    public static final ConcurrentSkipListSet<String> filterNullToSet(String[]... strArr) {
        final ConcurrentSkipListSet<String> res = new ConcurrentSkipListSet<String>();
        Arrays.asList(strArr).parallelStream().map((arrWN) -> {
            if (arrWN != null) {
                List<String> l = Arrays.asList(arrWN).parallelStream().filter(s -> !TkmTextUtils.isNullOrBlank(s)).toList();
                return l;
            } else {
                return null;
            }
        }).filter(p -> p != null)
                .forEach(nnl -> {
                    res.addAll(nnl);
                });
        return res;
    }
}
