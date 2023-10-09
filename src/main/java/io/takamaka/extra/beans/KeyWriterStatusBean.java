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

import io.takamaka.wallet.utils.FixedParameters;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;

/**
 * this bean was created to track the evolution of the key writer status
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
public class KeyWriterStatusBean {
    
    private int epoch;
    private int slot;
    private String uid;
    private FixedParameters.HexKeyWriter typeOfTransactions;
    private Map<String, Path> archivePathMap;

    public FixedParameters.HexKeyWriter getTypeOfTransactions() {
        return typeOfTransactions;
    }

    protected void setTypeOfTransactions(FixedParameters.HexKeyWriter typeOfTransactions) {
        this.typeOfTransactions = typeOfTransactions;
    }

    public int getEpoch() {
        return epoch;
    }

    protected void setEpoch(int epoch) {
        this.epoch = epoch;
    }

    public int getSlot() {
        return slot;
    }

    protected void setSlot(int slot) {
        this.slot = slot;
    }

    public String getUid() {
        return uid;
    }

    protected void setUid(String uid) {
        this.uid = uid;
    }

    public Map<String, Path> getArchivePathMap() {
        return archivePathMap;
    }

    protected void setArchivePathMap(Map<String, Path> archivePathMap) {
        this.archivePathMap = archivePathMap;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + this.epoch;
        hash = 67 * hash + this.slot;
        hash = 67 * hash + Objects.hashCode(this.uid);
        hash = 67 * hash + Objects.hashCode(this.typeOfTransactions);
        hash = 67 * hash + Objects.hashCode(this.archivePathMap);
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
        final KeyWriterStatusBean other = (KeyWriterStatusBean) obj;
        if (this.epoch != other.epoch) {
            return false;
        }
        if (this.slot != other.slot) {
            return false;
        }
        if (!Objects.equals(this.uid, other.uid)) {
            return false;
        }
        if (this.typeOfTransactions != other.typeOfTransactions) {
            return false;
        }
        if (!Objects.equals(this.archivePathMap, other.archivePathMap)) {
            return false;
        }
        return true;
    }
    
}
