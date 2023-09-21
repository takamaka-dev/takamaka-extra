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

import java.math.BigInteger;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author giovanni.antino@takamaka.io
 */
@Slf4j
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Balance {

    //h_addr_sibh_abn_path_id
    private String balanceHash;
    //address
    private String address;
    //single_inclusion_block_hash
    private String sibh;
    //absolute_block_number
    private Long absoluteBlockNumber;
    //path_id
    private Long pathId;
    //green_value
    private BigInteger greenValue;
    //red_value
    private BigInteger redValue;
    //green_penalty
    private BigInteger frozenGreen;
    //red_penalty
    private BigInteger frozenRed;
    //penalty_slots
    private Integer penaltySlots;
    //generator_sith
    private String generatorSith;
}
