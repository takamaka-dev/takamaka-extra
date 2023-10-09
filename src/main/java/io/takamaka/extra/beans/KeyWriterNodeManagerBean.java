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
import java.util.Objects;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Alessandro Pasi <alessandro.pasi@takamaka.io>
 */
@Slf4j
public class KeyWriterNodeManagerBean {

    private ConcurrentSkipListMap<String, String> hexAddressSetToUrl64;
    private ConcurrentSkipListMap<String, String> url64AddressSetToHex;
    private ConcurrentSkipListMap<String, NodeManagerBean> hexToNodeManagerBeanMap;

    public ConcurrentSkipListMap<String, String> getHexToBase64URLMapper() {
        return hexAddressSetToUrl64;
    }

    public ConcurrentSkipListMap<String, String> getUrl64ToHexMapper() {
        return url64AddressSetToHex;
    }

    public ConcurrentSkipListMap<String, String> getHexAddressSetToUrl64() {
        return hexAddressSetToUrl64;
    }

    protected void setHexAddressSetToUrl64(ConcurrentSkipListMap<String, String> hexAddressSetToUrl64) {
        this.hexAddressSetToUrl64 = hexAddressSetToUrl64;
    }

    public ConcurrentSkipListMap<String, String> getUrl64AddressSetToHex() {
        return url64AddressSetToHex;
    }

    protected void setUrl64AddressSetToHex(ConcurrentSkipListMap<String, String> url64AddressSetToHex) {
        this.url64AddressSetToHex = url64AddressSetToHex;
    }

    public ConcurrentSkipListMap<String, NodeManagerBean> getHexToNodeManagerBeanMap() {
        return hexToNodeManagerBeanMap;
    }

    protected void setHexToNodeManagerBeanMap(ConcurrentSkipListMap<String, NodeManagerBean> hexToNodeManagerBeanMap) {
        this.hexToNodeManagerBeanMap = hexToNodeManagerBeanMap;
    }

    public NodeManagerBean getNodeManagerBeanByHex(String hexAddr) {
        return hexToNodeManagerBeanMap.get(hexAddr);
    }

    public NodeManagerBean getNodeManagerBeanByURL64(String urlAddr) {
        String hexAddr = url64AddressSetToHex.get(urlAddr);
        NodeManagerBean result = hexToNodeManagerBeanMap.get(hexAddr);

        return result;
    }

    /**
     *
     *
     * @param hexAddr
     * @param bb
     * @return
     */
    public boolean writeHexNode(String hexAddr, NodeManagerBean nmb) {
        if (TkmTextUtils.isNullOrBlank(hexAddr) | nmb == null) {
            log.error("node manager is null: " + (nmb == null) + " or address is null " + hexAddr);
            return false;
        }
        if (hexAddressSetToUrl64.containsKey(hexAddr)) {
            hexToNodeManagerBeanMap.put(hexAddr, nmb);
            return true;
        } else {
            log.error( "address does not exist in set, node links could not be added" + hexAddr);
            return false;
        }
    }

    /**
     *
     *
     * @param url64Addr
     * @param bb
     * @return
     */
    public boolean writeURL64Balance(String url64Addr, NodeManagerBean nmb) {
        if (TkmTextUtils.isNullOrBlank(url64Addr)) {
            log.error( "node manager is null: " + (nmb == null) + " or address is null " + url64Addr);
            return false;
        }
        if (url64AddressSetToHex.containsKey(url64Addr)) {
            return writeHexNode(url64AddressSetToHex.get(url64Addr), nmb);
        } else {
            log.error( "address does not exist in set, node links could not be added" + url64Addr);
            return false;
        }
    }

    protected void addTransactionToSet(String hexAddr, String urlAddr) {
        synchronized (this) {
            hexAddressSetToUrl64.put(hexAddr, urlAddr);
            url64AddressSetToHex.put(urlAddr, hexAddr);
        }
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 67 * hash + Objects.hashCode(this.hexAddressSetToUrl64);
        hash = 67 * hash + Objects.hashCode(this.url64AddressSetToHex);
        hash = 67 * hash + Objects.hashCode(this.hexToNodeManagerBeanMap);
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
        final KeyWriterNodeManagerBean other = (KeyWriterNodeManagerBean) obj;
        if (!Objects.equals(this.hexAddressSetToUrl64, other.hexAddressSetToUrl64)) {
            return false;
        }
        if (!Objects.equals(this.url64AddressSetToHex, other.url64AddressSetToHex)) {
            return false;
        }
        if (!Objects.equals(this.hexToNodeManagerBeanMap, other.hexToNodeManagerBeanMap)) {
            return false;
        }
        return true;
    }

}
