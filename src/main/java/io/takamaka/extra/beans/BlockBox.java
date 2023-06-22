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

import io.takamaka.wallet.beans.InternalBlockBean;
import io.takamaka.wallet.beans.TransactionBean;
import io.takamaka.wallet.beans.TransactionBox;
import java.util.concurrent.ConcurrentSkipListMap;
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
@NoArgsConstructor
@AllArgsConstructor
public class BlockBox {

    /**
     * @return single inclusion block hash
     */
    private String singleInclusionBlockHash;
    /**
     * json(GSon) of the TransactionBean
     */
    private String transactionJson;
    private TransactionBean tb;
    private InternalBlockBean ibb;
    private TransactionBox coinbase;
    private TransactionBox blockHash;
    private TransactionBox previousBlock;
    private ConcurrentSkipListMap<String, String> forwardKeys;
    private boolean valid;
}
