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

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.takamaka.extra.identicon.exceptions.TkmBalanceException;
import io.takamaka.extra.utils.HashFunctionalInterfaceChecked;
import java.beans.Transient;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.function.Function;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author giovanni.antino@takamaka.io
 */
@Slf4j
//@Data
@Getter
@ToString
@EqualsAndHashCode
public class TkmBalance implements Serializable, Comparable<TkmBalance> {

    //h_addr_sibh_abn
    @Setter(AccessLevel.NONE)
    private String balanceHash;
    //address
    @Setter(AccessLevel.NONE)
    final private String address;
    //single_inclusion_block_hash
    private String sibh;
    //absolute_block_number
    private Long absoluteBlockNumber;
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
    //modified
    private boolean modified;
    //hashFunction
    @Setter(AccessLevel.NONE)
    @JsonIgnore
    private HashFunctionalInterfaceChecked<String, String> hashFunction;

    public TkmBalance(String address, String sibh, Long absoluteBlockNumber, BigInteger greenValue, BigInteger redValue, BigInteger frozenGreen, BigInteger frozenRed, Integer penaltySlots, String generatorSith, boolean isNewBalance, HashFunctionalInterfaceChecked<String, String> hashFunction) throws TkmBalanceException {
        this.address = address;
        this.sibh = sibh;
        this.absoluteBlockNumber = absoluteBlockNumber;
        this.greenValue = greenValue;
        this.redValue = redValue;
        this.frozenGreen = frozenGreen;
        this.frozenRed = frozenRed;
        this.penaltySlots = penaltySlots;
        this.generatorSith = generatorSith;
        this.modified = isNewBalance;
        this.hashFunction = hashFunction;
        refreshHash();
    }

    private void refreshHash() throws TkmBalanceException {
        this.balanceHash = this.hashFunction.apply(this.address + this.sibh + this.absoluteBlockNumber);
    }

    private void setModified() throws TkmBalanceException {
        this.modified = true;
//        refreshHash();
    }

//    public void setBalanceHash(String balanceHash) throws TkmBalanceException {
//        this.balanceHash = balanceHash;
//        setModified();
//    }
//    public void setAddress(String address) throws TkmBalanceException {
//        this.address = address;
//        setModified();
//    }
    public void setSibh(String sibh) throws TkmBalanceException {
        this.sibh = sibh;
        refreshHash();
//        setModified();
    }

    public void setAbsoluteBlockNumber(Long absoluteBlockNumber) throws TkmBalanceException {
        this.absoluteBlockNumber = absoluteBlockNumber;
        refreshHash();
//        setModified();
    }

    public void setGreenValue(BigInteger greenValue) throws TkmBalanceException {
        this.greenValue = greenValue;
        setModified();
    }

    public void setRedValue(BigInteger redValue) throws TkmBalanceException {
        this.redValue = redValue;
        setModified();
    }

    public void setFrozenGreen(BigInteger frozenGreen) throws TkmBalanceException {
        this.frozenGreen = frozenGreen;
        setModified();
    }

    public void setFrozenRed(BigInteger frozenRed) throws TkmBalanceException {
        this.frozenRed = frozenRed;
        setModified();
    }

    public void setPenaltySlots(Integer penaltySlots) throws TkmBalanceException {
        this.penaltySlots = penaltySlots;
        setModified();
    }

    public void setGeneratorSith(String generatorSith) {
        this.generatorSith = generatorSith;
    }

    @Override
    public int compareTo(TkmBalance o) {
        return getBalanceHash().compareTo(o.getBalanceHash());
    }
}
