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

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Objects;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
public class BalanceBean implements Serializable, Comparable<BalanceBean> {

    private String address;
    private BigInteger greenBalance;
    private BigInteger redBalance;
    private BigInteger greenPenalty;
    private BigInteger redPenalty;
    private int penaltySlots;
    private String generatorSith;

    /**
     * required by jackson
     */
    public BalanceBean() {
    }

    public BalanceBean(String generatorSith, String address, BigInteger greenBalance, BigInteger redBalance) {
        this.address = address;
        this.greenBalance = greenBalance;
        this.redBalance = redBalance;
        greenPenalty = BigInteger.ZERO;
        redPenalty = BigInteger.ZERO;
        penaltySlots = 0;
        this.generatorSith = generatorSith;
    }

    public BalanceBean(String generatorSith, String address, BigInteger greenBalance, BigInteger redBalance, BigInteger greenPenalty, BigInteger redPenalty, int penaltySlots) {
        this.address = address;
        this.greenBalance = greenBalance;
        this.redBalance = redBalance;
        this.greenPenalty = greenPenalty;
        this.redPenalty = redPenalty;
        this.penaltySlots = penaltySlots;
        this.generatorSith = generatorSith;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public BigInteger getGreenBalance() {
        return greenBalance;
    }

    public void setGreenBalance(BigInteger greenBalance) {
        this.greenBalance = greenBalance;
    }

    public BigInteger getRedBalance() {
        return redBalance;
    }

    public void setRedBalance(BigInteger redBalance) {
        this.redBalance = redBalance;
    }

    public BigInteger getGreenPenalty() {
        return greenPenalty;
    }

    public void setGreenPenalty(BigInteger greenPenalty) {
        this.greenPenalty = greenPenalty;
    }

    public BigInteger getRedPenalty() {
        return redPenalty;
    }

    public void setRedPenalty(BigInteger redPenalty) {
        this.redPenalty = redPenalty;
    }

    public int getPenaltySlots() {
        return penaltySlots;
    }

    public void setPenaltySlots(int penaltySlots) {
        this.penaltySlots = penaltySlots;
    }

    public String getGeneratorSith() {
        return generatorSith;
    }

    @Override
    public int hashCode() {
        int hash = 3;
        hash = 41 * hash + Objects.hashCode(this.address);
        hash = 41 * hash + Objects.hashCode(this.greenBalance);
        hash = 41 * hash + Objects.hashCode(this.redBalance);
        hash = 41 * hash + Objects.hashCode(this.greenPenalty);
        hash = 41 * hash + Objects.hashCode(this.redPenalty);
        hash = 41 * hash + this.penaltySlots;
        hash = 41 * hash + Objects.hashCode(this.generatorSith);
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
        final BalanceBean other = (BalanceBean) obj;
        if (this.penaltySlots != other.penaltySlots) {
            return false;
        }
        if (!Objects.equals(this.address, other.address)) {
            return false;
        }
        if (!Objects.equals(this.generatorSith, other.generatorSith)) {
            return false;
        }
        if (!Objects.equals(this.greenBalance, other.greenBalance)) {
            return false;
        }
        if (!Objects.equals(this.redBalance, other.redBalance)) {
            return false;
        }
        if (!Objects.equals(this.greenPenalty, other.greenPenalty)) {
            return false;
        }
        if (!Objects.equals(this.redPenalty, other.redPenalty)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(BalanceBean o) {
        return this.address.compareTo(o.address);
    }

}
