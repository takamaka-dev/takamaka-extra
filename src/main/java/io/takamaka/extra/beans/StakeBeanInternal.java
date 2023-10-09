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

import io.takamaka.wallet.beans.TransactionBox;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
@Slf4j
public class StakeBeanInternal implements Serializable, Comparable<StakeBeanInternal> {

    private String sith;
    private String toAddress;
    private BigInteger value;
    private long stakeDate;

    public StakeBeanInternal() {
    }

    /**
     * compare the two objects by transforming their sith into the version by
     * comparison using the function
     * {@code TkmTextUtils.getSortingString(sith)}. Null values are not
     * supported and must be managed a priori..
     *
     * @param t
     * @return
     */
    @Override
    public int compareTo(StakeBeanInternal t) {
        return TkmTextUtils.getSortingString(this.sith).compareTo(TkmTextUtils.getSortingString(t.sith)); //To change body of generated methods, choose Tools | Templates.
    }

    protected StakeBeanInternal(TransactionBox tbox) {
        if (tbox != null && tbox.isValid()) {
            this.sith = tbox.sith();
            this.toAddress = tbox.to();
            this.value = tbox.greenValue();
            this.stakeDate = tbox.notBeforeUNIX();
        } else {
            log.error( "null or invalid tbox");
        }
    }

    protected StakeBeanInternal(String sith, String toAddress, BigInteger value, long stakeDate) {
        this.sith = sith;
        this.toAddress = toAddress;
        this.value = value;
        this.stakeDate = stakeDate;
    }

    public String getSith() {
        return sith;
    }

    public String getToAddress() {
        return toAddress;
    }

    public BigInteger getValue() {
        return value;
    }

    public long getStakeDate() {
        return stakeDate;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 37 * hash + Objects.hashCode(this.sith);
        hash = 37 * hash + Objects.hashCode(this.toAddress);
        hash = 37 * hash + Objects.hashCode(this.value);
        hash = 37 * hash + (int) (this.stakeDate ^ (this.stakeDate >>> 32));
        return hash;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final StakeBeanInternal other = (StakeBeanInternal) obj;
        if (this.stakeDate != other.stakeDate) {
            return false;
        }
        if (!Objects.equals(this.sith, other.sith)) {
            return false;
        }
        if (!Objects.equals(this.toAddress, other.toAddress)) {
            return false;
        }
        if (!Objects.equals(this.value, other.value)) {
            return false;
        }
        return true;
    }

}
