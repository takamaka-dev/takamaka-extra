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

import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
@Slf4j
public class KeyWriterStakeUndoBean {

    private ConcurrentSkipListMap<String, String> hexAddressSetToUrl64;
    private ConcurrentSkipListMap<String, String> url64AddressSetToHex;
    private ConcurrentSkipListMap<String, StakeUndoBean> hexToBalanceBeanMap;

    public ConcurrentSkipListMap<String, String> getHexToBase64URLMapper() {
        return hexAddressSetToUrl64;
    }

    public ConcurrentSkipListMap<String, String> getUrl64ToHexMapper() {
        return url64AddressSetToHex;
    }

    protected void setHexAddressSetToUrl64(ConcurrentSkipListMap<String, String> hexAddressSetToUrl64) {
        this.hexAddressSetToUrl64 = hexAddressSetToUrl64;
    }

    protected void setUrl64AddressSetToHex(ConcurrentSkipListMap<String, String> url64AddressSetToHex) {
        this.url64AddressSetToHex = url64AddressSetToHex;
    }

    protected void setHexToBalanceBean(ConcurrentSkipListMap<String, StakeUndoBean> hexToBalanceBeanSet) {
        this.hexToBalanceBeanMap = hexToBalanceBeanSet;
    }

    public StakeUndoBean getStakeUndoBeanByHex(String hexAddr) {
        return hexToBalanceBeanMap.get(hexAddr);
    }

    public StakeUndoBean getStakeUndoBeanByURL64(String urlAddr) {
        return hexToBalanceBeanMap.get(url64AddressSetToHex.get(urlAddr));
    }

    public ConcurrentSkipListMap<String, StakeUndoBean> getHexToStakeUndoList() {
        return hexToBalanceBeanMap;
    }

    /**
     * add if SU is not present overwrite if present FAIL if address is not part
     * of this set
     *
     * @param hexAddr
     * @param su
     * @return
     */
    public boolean writeHexStakeUndo(String hexAddr, StakeUndoBean su) {
        if (TkmTextUtils.isNullOrBlank(hexAddr) | su == null) {
            log.error( "undo is null: " + (su == null) + " or address is null " + hexAddr);
            return false;
        }
        if (hexAddressSetToUrl64.containsKey(hexAddr)) {
            hexToBalanceBeanMap.put(hexAddr, su);
            return true;
        } else {
            log.error( "address does not exist in set, undo could not be added" + hexAddr);
            return false;
        }
    }

    /**
     * add if SU is not present overwrite if present FAIL if address is not part
     * of this set
     *
     * @param url64Addr
     * @param su
     * @return
     */
    public boolean writeURL64StakeUndo(String url64Addr, StakeUndoBean su) {
        if (TkmTextUtils.isNullOrBlank(url64Addr)) {
            log.error( "undo is null: " + (su == null) + " or address is null " + url64Addr);
            return false;
        }
        if (url64AddressSetToHex.containsKey(url64Addr)) {
            return writeHexStakeUndo(url64AddressSetToHex.get(url64Addr), su);
        } else {
            log.error( "address does not exist in set, undo could not be added" + url64Addr);
            return false;
        }
    }

    protected void addTransactionToSet(String hexAddr, String urlAddr) {
        synchronized (this) {
            hexAddressSetToUrl64.put(hexAddr, urlAddr);
            url64AddressSetToHex.put(urlAddr, hexAddr);
            //hexToBalanceBeanSet.put(hexAddr, bb);
        }
    }

}
