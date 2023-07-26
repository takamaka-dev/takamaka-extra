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
package io.takamaka.extra.beans;

import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.utils.TkmAddressUtils;
import io.takamaka.wallet.utils.KeyContexts;
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
@AllArgsConstructor
@NoArgsConstructor
public class CompactAddressBean implements Comparable<CompactAddressBean> {

//    public CompactAddressBean(String original) throws AddressNotRecognizedException {
//        CompactAddressBean toCompactAddress = AddressUtils.toCompactAddress(original);
//        this.original = original;
//        this.defaultShort = toCompactAddress.defaultShort;
//        this.type = toCompactAddress.getType();
//
//    }
    private String original;
    private String defaultShort;
    private TkmAddressUtils.TypeOfAddress type;

    @Override
    public int compareTo(CompactAddressBean o) {
        return original.compareTo(o.original);
    }

}
