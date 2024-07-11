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
package io.takamaka.extra.utils;

import io.takamaka.extra.beans.ESBean;
import io.takamaka.extra.exceptions.EpochSlotBeanException;
import io.takamaka.extra.exceptions.ForwardKeyException;
import io.takamaka.extra.identicon.exceptions.DecodeForwardKeysException;
import java.util.concurrent.ConcurrentSkipListMap;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmForwardKeys {

    public static final String[] extractAddresses(ConcurrentSkipListMap<String, String> fwKeys) throws DecodeForwardKeysException {
        if (fwKeys == null) {
            throw new DecodeForwardKeysException("FW KEYS LIST NULL");
        }
        if (fwKeys.isEmpty()) {
            log.info("no forward keys in block");
            return new String[0];
        }
        return fwKeys.values().toArray(String[]::new);
    }

    public static final ESBean getEpochSlot(String epochSlot) throws EpochSlotBeanException {
        ESBean result = null;
        try {
            String removeE = epochSlot.substring(1);
            String[] splitAtS = removeE.split("S");
            Integer epoch = Integer.valueOf(splitAtS[0]);
            Integer slot = Integer.valueOf(splitAtS[1]);
            if (epoch < 0 | slot < 0) {
                throw new ForwardKeyException("[decoding] negative parameter epoch: " + epoch + " slot: " + slot);
            }
            if (epoch > 99999 | slot > 99999) {
                throw new ForwardKeyException("[decoding] invalid parameter value, is too large"
                        + " must be less than 99999,"
                        + " epoch: " + epoch
                        + " slot: " + slot);
            }
            result = new ESBean(epoch, slot);
        } catch (ForwardKeyException | NumberFormatException ex) {
            throw new EpochSlotBeanException("[decoding] error crating bean for string " + epochSlot, ex);
        }
        return result;
    }

    public static final String getProposedKeyName(Integer epoch, Integer slot) throws ForwardKeyException {
        if (epoch == null | slot == null) {
            throw new ForwardKeyException("[encoding] null parameter epoch: " + epoch + " slot: " + slot);
        }
        if (epoch < 0 | slot < 0) {
            throw new ForwardKeyException("[encoding] negative parameter epoch: " + epoch + " slot: " + slot);
        }
        if (epoch > 99999 | slot > 99999) {
            throw new ForwardKeyException("[encoding] invalid parameter value, is too large"
                    + " must be less than 99999,"
                    + " epoch: " + epoch
                    + " slot: " + slot);
        }
        StringBuilder sb = new StringBuilder("E");
        sb.append(String.format("%05d", epoch));
        sb.append("S");
        sb.append(String.format("%05d", slot));
        return sb.toString();
    }

    public static final String getProposedKeyName(ESBean eSBean) throws ForwardKeyException {
        return getProposedKeyName(eSBean.getEpoch(), eSBean.getSlot());
    }

}
