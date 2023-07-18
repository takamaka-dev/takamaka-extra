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

import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.NullAddressException;
import io.takamaka.extra.identicon.exceptions.UnsupportedAddressFunctionException;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class AddressUtils {

    public static enum TypeOfAddress {
        ed25519,
        qTesla,
        undefined
    }

    public static final String getBookmarkAddress(CompactAddressBean cab) throws UnsupportedAddressFunctionException {
        if (TkmTextUtils.isNullOrBlank(cab.getDefaultShort())) {
            throw new UnsupportedAddressFunctionException("missing default short");
        }
        return TkmSignUtils.fromB64UrlToHEX(cab.getDefaultShort());
    }

    public static final CompactAddressBean toCompactAddress(String address) throws AddressNotRecognizedException {
        byte[] addrBytes;
        if (TkmTextUtils.isNullOrBlank(address)) {
            throw new AddressNotRecognizedException("null or zero char");
        }
        CompactAddressBean compactAddressBean = new CompactAddressBean();
        compactAddressBean.setOriginal(address.trim());
        compactAddressBean.setType(TypeOfAddress.undefined);
        addrBytes = TkmSignUtils.fromB64URLToByteArray(address);
        if (addrBytes == null) {
            compactAddressBean.setType(TypeOfAddress.undefined);
            setDefaultShort(compactAddressBean);
        } else {
            switch (address.length()) {
                case 44:
                    log.info("ed25519");
                    if (addrBytes.length != 32) {
                        compactAddressBean.setType(TypeOfAddress.undefined);
                        setDefaultShort(compactAddressBean);
                    } else {
                        compactAddressBean.setType(TypeOfAddress.ed25519);
                        compactAddressBean.setOriginal(TkmSignUtils.fromByteArrayToB64URL(addrBytes));
                    }
                    break;
                case 19840:
                    log.info("qTesla");
                    //addrBytes = TkmSignUtils.fromB64URLToByteArray(address);
                    if (addrBytes.length != 14880) {
                        compactAddressBean.setType(TypeOfAddress.undefined);
                    } else {
                        compactAddressBean.setType(TypeOfAddress.qTesla);
                        compactAddressBean.setOriginal(TkmSignUtils.fromByteArrayToB64URL(addrBytes));
                    }
                    setDefaultShort(compactAddressBean);
                    break;
                default:
                    compactAddressBean.setType(TypeOfAddress.undefined);
                    setDefaultShort(compactAddressBean);
            }
        }
        return compactAddressBean;
    }

    private static void setDefaultShort(CompactAddressBean compactAddressBean) throws AddressNotRecognizedException {
        try {
            compactAddressBean.setDefaultShort(TkmSignUtils.Hash384B64URL(compactAddressBean.getOriginal()));
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
            log.error("address can't be encoded " + compactAddressBean.getOriginal(), ex);
            throw new AddressNotRecognizedException(ex);
        }
    }

}
