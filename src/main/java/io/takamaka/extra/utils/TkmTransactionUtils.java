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

import io.takamaka.extra.beans.BlockBox;
import io.takamaka.extra.beans.FileMessageBean;
import io.takamaka.extra.identicon.exceptions.DecodeBlockException;
import io.takamaka.extra.identicon.exceptions.DecodeTransactionException;
import io.takamaka.wallet.utils.TkmTextUtils;
import io.takamaka.wallet.utils.TkmWallet;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Iris Dimni iris.dimni@takamaka.io
 */
@Slf4j
public class TkmTransactionUtils {

    public static final ConcurrentSkipListSet<String> extractAllTransactionAdresses(BlockBox blockBox) throws DecodeBlockException {
        ConcurrentSkipListMap<String, Exception> errorMapper = TkmErrorUtils.getErrorMapper();
        ConcurrentSkipListSet<String> trxAddresses = new ConcurrentSkipListSet<>();
        blockBox.getIbb()
                .getTransactions().parallelStream()
                .map(t -> TkmWallet.verifyTransactionIntegrity(t.getTb())) //transaction box
                .map(tbox -> {
                    if (!tbox.isValid()) {
                        log.error("INVALID TRANSACTION IN BLOCK");
                        TkmErrorUtils.appendException("INVALID TRANSACTION IN BLOCK", new DecodeTransactionException("INVALID TRANSACTION IN BLOCK"), errorMapper);
                    } else {
                        try {
                            return TkmAddressUtils.extractAddressesFromTransaction(tbox);
                        } catch (DecodeTransactionException ex) {
                            TkmErrorUtils.appendException("invalid blockhash transaction", ex, errorMapper);
                        }
                    }
                    return null;
                }).forEach(addrPairs -> {
            if (addrPairs.length > 0) {
                if (!TkmTextUtils.isNullOrBlank(addrPairs[0])) {
                    trxAddresses.add(addrPairs[0]);
                }
                if (!TkmTextUtils.isNullOrBlank(addrPairs[1])) {
                    trxAddresses.add(addrPairs[1]);
                }
            }

        });
        TkmErrorUtils.logAllErrors(errorMapper);
        if (!errorMapper.isEmpty()) {
            throw new DecodeBlockException(errorMapper.firstKey(), errorMapper.firstEntry().getValue());
        }
        return trxAddresses;
    }

    public static final String createMessageInternalIndexed(String message) {
        if (TkmTextUtils.isNullOrBlank(message)) {
            return null;
        }
        List<String> tokens = new ArrayList<>();
        FileMessageBean decodeTagsString = null;
        int currentLen = 0;
        // max byte tslen 1048575
        int maxLen = 1048575 - 1000;

        try {
            decodeTagsString = SerializerUtils.decodeTagsString(message);

        } catch (Exception e) {
            log.debug("message can not be decoded as FileMessageBean", e);
        }

        if (decodeTagsString != null) {
            if (decodeTagsString.getContentType() != null) {
                String dst = "Content-Type " + decodeTagsString.getContentType();
                byte[] tokenBytes = dst.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(dst);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getContentType() != null) {
                String plt = "platform " + decodeTagsString.getPlatform();
                byte[] tokenBytes = plt.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(plt);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getContentType() != null) {
                String xpb = "X-Parsed-By " + decodeTagsString.getXParsedBy();
                byte[] tokenBytes = xpb.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(xpb);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getContentType() != null) {
                String rn = "resourceName " + decodeTagsString.getResourceName();
                byte[] tokenBytes = rn.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(rn);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getTags() != null && decodeTagsString.getTags().length > 0) {
                String tags = "tags " + Arrays.toString(decodeTagsString.getTags());
                byte[] tokenBytes = tags.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(tags);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getTags() != null && decodeTagsString.getTags().length > 0) {
                String mime = "mime " + decodeTagsString.getMime();
                byte[] tokenBytes = mime.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(mime);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
            if (decodeTagsString.getTags() != null && decodeTagsString.getTags().length > 0) {
                String type = "type " + decodeTagsString.getMime();
                byte[] tokenBytes = type.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(type);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
        }

        String[] splittedString = message.split(" ");
        StringBuilder lostFragments = new StringBuilder();

        //first pass (human readable long text)
        for (String token : splittedString) {
            if (token != null) {
                byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(token);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                } else {
                    lostFragments.append(token);
                }
            }
        }
        //machine text
        for (StringTokenizer stringTokenizer = new StringTokenizer(lostFragments.toString()); stringTokenizer.hasMoreTokens();) {
            String token = stringTokenizer.nextToken();
            if (token != null) {
                byte[] tokenBytes = token.getBytes(StandardCharsets.UTF_8);
                int tokenLength = tokenBytes.length;
                if (currentLen < maxLen) {
                    if (tokenLength < 500) {
                        if (tokenLength + currentLen < maxLen) {
                            tokens.add(token);
                        }
                        currentLen += tokenLength;
                        //tsvector function add char for every word appendend
                        maxLen -= 20;

                    }
                }
            }
        }

        if (!tokens.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            String res = "";
            for (Iterator<String> tok = tokens.iterator(); tok.hasNext();) {
                res += tok.next() + " ";
            }
            res = SerializerUtils.removeUnsafeTsQuery(res).trim();
            return res;
        }
        return null;
    }
}
