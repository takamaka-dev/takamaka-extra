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
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
@Slf4j
public class StakeBean implements Serializable, Comparable<StakeBean> {

    private String fromAddress;
    private ConcurrentSkipListMap<String, StakeBeanInternal> stakeBetList;
    private ConcurrentSkipListMap<Long, ConcurrentSkipListSet<StakeBeanInternal>> stakeBetListByDate;

    public StakeBean() {
    }

    protected StakeBean(String fromAddress) {
        this.fromAddress = fromAddress;
        this.stakeBetList = new ConcurrentSkipListMap<String, StakeBeanInternal>();
        this.stakeBetListByDate = new ConcurrentSkipListMap<Long, ConcurrentSkipListSet<StakeBeanInternal>>();
    }

    public void clearStakes() {
        stakeBetList = new ConcurrentSkipListMap<String, StakeBeanInternal>();
        stakeBetListByDate = new ConcurrentSkipListMap<Long, ConcurrentSkipListSet<StakeBeanInternal>>();
    }

    public String getFromAddress() {
        return fromAddress;
    }

    public void setFromAddress(String fromAddress) {
        this.fromAddress = fromAddress;
    }

    public void addStake(TransactionBox tbox) {
        if (tbox != null && tbox.isValid()) {
            String sortingSith = TkmTextUtils.getSortingString(tbox.sith());
            StakeBeanInternal sbi = TkmStakeBeanHelper.createStakeBeanInternal(tbox);
            stakeBetList.put(sortingSith, sbi);
            if (stakeBetListByDate.get(tbox.notBeforeUNIX()) == null) {
                stakeBetListByDate.put(tbox.notBeforeUNIX(), new ConcurrentSkipListSet<StakeBeanInternal>());
            }
            stakeBetListByDate.get(tbox.notBeforeUNIX()).add(sbi);
            //stakeBetListByDate.put(tbox., sbi)
        } else {
            System.out.println("null or invalid tbox");
            log.error( "null or invalid tbox");
        }

    }

    protected void addStakeInternal(StakeBeanInternal sbi) {
        if (sbi != null) {
            String sortingSith = TkmTextUtils.getSortingString(sbi.getSith());
            stakeBetList.put(sortingSith, sbi);
            if (stakeBetListByDate.get(sbi.getStakeDate()) == null) {
                stakeBetListByDate.put(sbi.getStakeDate(), new ConcurrentSkipListSet<StakeBeanInternal>());
            }
            stakeBetListByDate.get(sbi.getStakeDate()).add(sbi);
        } else {
            log.error( "null or invalid SBI");
        }
    }

    public ConcurrentSkipListMap<String, StakeBeanInternal> getStakeBetList() {
        return stakeBetList;
    }

    public ConcurrentSkipListMap<Long, ConcurrentSkipListSet<StakeBeanInternal>> getStakeBetListByDate() {
        return stakeBetListByDate;
    }

    @Override
    public int hashCode() {
        int hash = 5;
        hash = 47 * hash + Objects.hashCode(this.fromAddress);
        hash = 47 * hash + Objects.hashCode(this.stakeBetList);
        hash = 47 * hash + Objects.hashCode(this.stakeBetListByDate);
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
        final StakeBean other = (StakeBean) obj;
        if (!Objects.equals(this.fromAddress, other.fromAddress)) {
            return false;
        }
        if (!Objects.equals(this.stakeBetList, other.stakeBetList)) {
            return false;
        }
        if (!Objects.equals(this.stakeBetListByDate, other.stakeBetListByDate)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(StakeBean o) {
        return this.fromAddress.compareTo(o.fromAddress);
    }

}
