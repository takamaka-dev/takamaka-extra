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
import java.util.Date;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
public class StakeUndoBean implements Serializable, Comparable<StakeUndoBean> {

    private String address;
    private long undoDate;

    public StakeUndoBean() {
    }

    /**
     * get URL64 address
     *
     * @return
     */
    public String getAddress() {
        return address;
    }

    /**
     * set URL64 address
     *
     * @param address
     */
    public void setAddress(String address) {
        this.address = address;
    }

    public long getUndoDate() {
        return undoDate;
    }

    /**
     * set stake undo date as long
     *
     * @param undoDate
     */
    public void setUndoDate(long undoDate) {
        this.undoDate = undoDate;
    }

    /**
     * set stake undo date as date
     *
     * @param undoDate
     */
    public void setUndoDate(Date undoDate) {
        this.undoDate = undoDate.getTime();
    }

    @Override
    public int compareTo(StakeUndoBean o) {
        return this.address.compareTo(o.address);
    }

}
