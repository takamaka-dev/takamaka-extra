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
import io.takamaka.extra.identicon.exceptions.AddressDecodeException;
import io.takamaka.extra.identicon.exceptions.AddressEncodeException;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressNullException;
import io.takamaka.extra.identicon.exceptions.AddressFunctionUnsupportedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.identicon.exceptions.DecodeBlockException;
import io.takamaka.extra.identicon.exceptions.DecodeTransactionException;
import io.takamaka.wallet.beans.TransactionBox;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import io.takamaka.wallet.utils.TkmWallet;
import io.takamaka.wallet.utils.WalletHelper;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmAddressUtils {

    public static enum TypeOfAddress {
        ed25519,
        qTesla,
        undefined
    }

    public static final String getBookmarkAddress(CompactAddressBean cab) throws AddressFunctionUnsupportedException {
        if (TkmTextUtils.isNullOrBlank(cab.getDefaultShort())) {
            throw new AddressFunctionUnsupportedException("missing default short");
        }
        return TkmSignUtils.fromB64UrlToHEX(cab.getDefaultShort());
    }

    /**
     *
     * @param address base64 URL representation of a qTesla or ed25519 address.
     * @return Returns a new bean initialized to the address passed.
     * @throws AddressNotRecognizedException
     * @throws AddressTooLongException
     */
    public static final CompactAddressBean toCompactAddress(String address) throws AddressNotRecognizedException, AddressTooLongException {
        byte[] addrBytes;
        if (TkmTextUtils.isNullOrBlank(address)) {
            throw new AddressNotRecognizedException("null or zero char");
        }
        CompactAddressBean compactAddressBean = new CompactAddressBean();
        compactAddressBean.setOriginal(address.trim());
        if (compactAddressBean.getOriginal().length() > 19840) {
            throw new AddressTooLongException("expected 19840 or less, found " + compactAddressBean.getOriginal().length());
        }
        if (address.length() != address.trim().length()) {
            log.error("In-depth analysis needed. Trimmed length does not match original string length." + "trimed: " + address.trim() + " orig: \"" + address + "\"");
        }
        compactAddressBean.setType(TypeOfAddress.undefined);
        addrBytes = TkmSignUtils.fromB64URLToByteArray(address);
        if (addrBytes == null) {
            compactAddressBean.setType(TypeOfAddress.undefined);
            setDefaultShort(compactAddressBean);
        } else {
            switch (compactAddressBean.getOriginal().length()) {
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

    public static final String[] extractAddressesFromTransaction(TransactionBox tBox) throws DecodeTransactionException {
        if (!tBox.isValid()) {
            log.error("addresses extraction requested on invalid transaction box");
            throw new DecodeTransactionException("addresses extraction requested on invalid transaction box");
        }
        String[] res = new String[2];
        if (!TkmTextUtils.isNullOrBlank(tBox.from())) {
            res[0] = tBox.from();
        }
        if (!TkmTextUtils.isNullOrBlank(tBox.to())) {
            res[1] = tBox.to();
        }
        return res;
    }

    public static final String fromHexToB64URL(String hex) throws AddressDecodeException, AddressEncodeException {
        byte[] fromHexToByteArray = TkmSignUtils.fromHexToByteArray(hex);
        if (fromHexToByteArray == null) {
            throw new AddressDecodeException("can not decode hex address " + hex);
        }
        String fromByteArrayToB64URL = TkmSignUtils.fromByteArrayToB64URL(fromHexToByteArray);
        if (fromByteArrayToB64URL == null) {
            throw new AddressEncodeException("can not encode hex address hex: " + hex + " byteArray " + Arrays.toString(fromHexToByteArray));
        }
        return fromByteArrayToB64URL;
    }

    public static final String fromB64URLToHex(String b64url) throws AddressDecodeException, AddressEncodeException {
        byte[] fromB64URLToByteArray = TkmSignUtils.fromB64URLToByteArray(b64url);
        if (fromB64URLToByteArray == null) {
            throw new AddressDecodeException("can not decode b64url address " + b64url);
        }
        String fromByteArrayToHexString = TkmSignUtils.fromByteArrayToHexString(fromB64URLToByteArray);
        if (fromByteArrayToHexString == null) {
            throw new AddressEncodeException("can not encode byte address b64url: " + b64url + " byteArray " + Arrays.toString(fromB64URLToByteArray));
        }
        return fromByteArrayToHexString;
    }

    public static String get5ZeroPaddedNumberWithPrefix(String prefix, int number) {
        StringBuilder sb = new StringBuilder(prefix);
        sb.append(String.format("%05d", number));
        return sb.toString();
    }

}
