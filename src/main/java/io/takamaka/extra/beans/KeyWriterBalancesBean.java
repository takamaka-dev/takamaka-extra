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

import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;

/**
 *this class maps the addresses in hex format to those in url format.
 * Conversion is necessary because addresses in url format contain unsafe
 * characters for many filesystems
 * 
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */

@Slf4j
public class KeyWriterBalancesBean {
    private ConcurrentSkipListMap<String, String> hexAddressSetToUrl64;
    private ConcurrentSkipListMap<String, String> url64AddressSetToHex;
    private ConcurrentSkipListMap<String, BalanceBean> hexToBalanceBeanMap;

    /**
     * return the mapper from hex to url64
     *
     * @return
     */
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

    protected void setHexToBalanceBean(ConcurrentSkipListMap<String, BalanceBean> hexToBalanceBeanSet) {
        this.hexToBalanceBeanMap = hexToBalanceBeanSet;
    }

    public BalanceBean getBalanceBeanByHex(String hexAddr) {
        return hexToBalanceBeanMap.get(hexAddr);
    }

    public BalanceBean getBalanceBeanByURL64(String urlAddr) {
        //String hexAddr = ;
        //System.out.println("hexToBalanceBeanMap " + hexToBalanceBeanMap.size());
        //System.out.println("url64AddressSetToHex " + url64AddressSetToHex.size());
        return hexToBalanceBeanMap.get(url64AddressSetToHex.get(urlAddr));
    }

    public ConcurrentSkipListMap<String, BalanceBean> getHexToBalanceList() {
        return hexToBalanceBeanMap;
    }

    /**
     * add if BB is not present overwrite if present FAIL if address is not part
     * of this set
     *
     * @param hexAddr
     * @param bb
     * @return
     */
    public boolean writeHexBalance(String hexAddr, BalanceBean bb) {
        if (TkmTextUtils.isNullOrBlank(hexAddr) | bb == null) {
            log.error("balance is null: " + (bb == null) + " or address is null " + hexAddr);
            return false;
        }
        if (hexAddressSetToUrl64.containsKey(hexAddr)) {
            hexToBalanceBeanMap.put(hexAddr, bb);
            return true;
        } else {
            log.error("address does not exist in set, balance could not be added" + hexAddr);
            return false;
        }
    }

    /**
     * add if BB is not present overwrite if present FAIL if address is not part
     * of this set
     *
     * @param url64Addr
     * @param bb
     * @return
     */
    public boolean writeURL64Balance(String url64Addr, BalanceBean bb) {
        if (TkmTextUtils.isNullOrBlank(url64Addr)) {
            log.error("balance is null: " + (bb == null) + " or address is null " + url64Addr);
            return false;
        }
        if (url64AddressSetToHex.containsKey(url64Addr)) {
            return writeHexBalance(url64AddressSetToHex.get(url64Addr), bb);
        } else {
            log.error( "address does not exist in set, balance could not be added" + url64Addr);
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

    public void addTransactionToSet(String urlAddr, BalanceBean bb) {
        synchronized (this) {
            String hexAddr = TkmSignUtils.fromB64UrlToHEX(urlAddr);
            hexAddressSetToUrl64.put(hexAddr, urlAddr);
            url64AddressSetToHex.put(urlAddr, hexAddr);
            hexToBalanceBeanMap.put(hexAddr, bb);
        }
    }
}
