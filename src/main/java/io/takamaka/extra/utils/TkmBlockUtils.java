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
import io.takamaka.extra.beans.CoinbaseMessageBean;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.beans.HashBean;
import io.takamaka.extra.identicon.exceptions.AddressDecodeException;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.identicon.exceptions.DecodeBlockException;
import io.takamaka.extra.identicon.exceptions.DecodeTransactionException;
import io.takamaka.wallet.beans.InternalBlockBean;
import io.takamaka.wallet.beans.PrivateBlockTxBean;
import io.takamaka.wallet.beans.TkmCypherBean;
import io.takamaka.wallet.beans.TkmRewardBean;
import io.takamaka.wallet.beans.TransactionBean;
import io.takamaka.wallet.beans.TransactionBox;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.DefaultInitParameters;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import io.takamaka.wallet.utils.TkmWallet;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmBlockUtils {

    /**
     * calculate the sith hash of transactions or return empty string (not null)
     * if the transaction list is empty or null.
     *
     * @param ibb
     * @return
     */
    public static String getTransactionsHash(InternalBlockBean ibb) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        ConcurrentSkipListSet<PrivateBlockTxBean> transactions = ibb.getTransactions();
        ConcurrentSkipListMap<String, HashBean> orderedSiths = new ConcurrentSkipListMap<String, HashBean>();
        if (transactions != null) {
            if (!transactions.isEmpty()) {

                transactions.parallelStream()
                        .filter((PrivateBlockTxBean pbt) -> {
                            if (pbt != null) {
                                String sith = pbt.getSingleInclusionTransactionHash();
                                if (!TkmTextUtils.isNullOrBlank(sith)) {
                                    return true;
                                } else {
                                    log.error("null sith BUG");
                                    return false;
                                }
                            } else {
                                log.error("null pbt BUG");
                                return false;
                            }
                        })
                        .forEach((PrivateBlockTxBean pbt) -> {
                            //KeyContexts.InternalBlockTransactionState transactionValidity = pbt.getTransactionValidity();
                            orderedSiths.put(TkmTextUtils.getSortingString(pbt.getSingleInclusionTransactionHash()), new HashBean(pbt.getSingleInclusionTransactionHash(), pbt.getTransactionValidity()));
                        });

                String sithHash = new String("");
                for (Map.Entry<String, HashBean> entry : orderedSiths.entrySet()) {
                    String key = entry.getKey();
                    HashBean hb = entry.getValue();
                    StringBuilder sb = new StringBuilder();
                    sithHash = sb.append(sithHash).append(hb.getValdity().name()).append(hb.getSith()).toString();
                    sithHash = TkmSignUtils.Hash512ToHex(sithHash);

                }
                return sithHash;

            } else {
                log.debug("empty block");
            }
        } else {
            log.warn("null transactions");
        }
        return "";
    }

    public static String getForwardKeysHash(ConcurrentSkipListMap<String, String> forwardKeys) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        StringBuilder sb = new StringBuilder("");
        if (forwardKeys != null) {
            if (forwardKeys.isEmpty()) {
                sb.append(forwardKeys.isEmpty());
            } else {
                for (Map.Entry<String, String> entry : forwardKeys.entrySet()) {
                    String key = entry.getKey();
                    String val = entry.getValue();
                    sb.append(key).append(val);
                }
            }
        } else {
            sb.append("null");
        }
        return TkmSignUtils.Hash512ToHex(sb.toString());
    }

    public static String getRewardListHash(ConcurrentSkipListMap<String, TkmRewardBean> rewList) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        StringBuilder sb = new StringBuilder("");
        if (rewList != null) {
            if (rewList.isEmpty()) {
                sb.append(rewList.isEmpty());
            } else {
                for (Map.Entry<String, TkmRewardBean> entry : rewList.entrySet()) {
                    String key = entry.getKey();
                    TkmRewardBean val = entry.getValue();
                    //sb.append(key).append(val);
                    StringBuilder rb = new StringBuilder();
                    rb.append(key)
                            .append(val.getFrozenFeeGreen().toString())
                            .append(val.getFrozenFeeRed().toString())
                            .append(val.getGreenValue().toString())
                            .append(val.getRedValue().toString())
                            .append(val.getPenaltySlots())
                            .append(val.getUrl64Addr());
                    sb.append(TkmSignUtils.Hash512ToHex(rb.toString()));
                }
            }
        } else {
            sb.append("null");
        }
        return TkmSignUtils.Hash512ToHex(sb.toString());

    }

    public static String getBlockHash(InternalBlockBean ibb) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        StringBuilder sb = new StringBuilder();
        //transactions
        sb.append(getTransactionsHash(ibb));
        //coinbase
        if (ibb.getCoinbase() != null) {
            TransactionBox tbox = TkmWallet.verifyTransactionIntegrity(ibb.getCoinbase());
            if (tbox.isValid()) {
                sb.append(tbox.sith());
            } else {
                sb.append("invalid");
            }
        } else {
            sb.append("null");
        }
        //previous
        if (ibb.getPreviousBlock() != null) {
            TransactionBox tbox = TkmWallet.verifyTransactionIntegrity(ibb.getPreviousBlock());
            if (tbox.isValid()) {
                sb.append(tbox.sith());
            } else {
                sb.append("invalid");
            }
        } else {
            sb.append("null");
        }
        //forward keys
        sb.append(getForwardKeysHash(ibb.getForwardKeyes()));
        //rewardList
        sb.append(getRewardListHash(ibb.getRewardList()));
        return TkmSignUtils.Hash512ToHex(sb.toString());

    }

    public static String getInternalBlockHash(InternalBlockBean ibb) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        StringBuilder sb = new StringBuilder();
        //transactions
        sb.append(getTransactionsHash(ibb));
        //coinbase
        if (ibb.getCoinbase() != null) {
            TransactionBox tbox = TkmWallet.verifyTransactionIntegrity(ibb.getCoinbase());
            if (tbox.isValid()) {
                sb.append(tbox.sith());
            } else {
                sb.append("invalid");
            }
        } else {
            sb.append("null");
        }
        //previous
        if (ibb.getPreviousBlock() != null) {
            TransactionBox tbox = TkmWallet.verifyTransactionIntegrity(ibb.getPreviousBlock());
            if (tbox.isValid()) {
                sb.append(tbox.sith());
            } else {
                sb.append("invalid");
            }
        } else {
            sb.append("null");
        }
        //forward keys
        sb.append(getForwardKeysHash(ibb.getForwardKeyes()));
        //rewardList
        sb.append(getRewardListHash(ibb.getRewardList()));
        return TkmSignUtils.Hash512ToHex(sb.toString());
    }

    public static BlockBox decodeBlock(String blockJson) throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        BlockBox bb = new BlockBox();
        bb.setValid(false);
        if (TkmTextUtils.isNullOrBlank(blockJson)) {
            bb.setValid(false);
            log.warn("invalid block, null json");
            return bb;
        } else {
            TransactionBean blockTransactionBean = TkmTextUtils.transactionBeanFromJson(blockJson);
            if (blockTransactionBean == null) {
                bb.setValid(false);
                log.warn("invalid transaction bean null");
                return bb;
            }
            if (TkmTextUtils.isNullOrBlank(blockTransactionBean.getSignature())) {
                bb.setValid(false);
                log.warn("invalid block null signature");
                return bb;
            }
            if (TkmTextUtils.isNullOrBlank(blockTransactionBean.getPublicKey())) {
                bb.setValid(false);
                log.warn("invalid block null public key");
                return bb;
            }
            if (TkmTextUtils.isNullOrBlank(blockTransactionBean.getMessage())) {
                bb.setValid(false);
                log.warn("invalid block null message");
                return bb;
            }
            if (TkmTextUtils.isNullOrBlank(blockTransactionBean.getRandomSeed())) {
                bb.setValid(false);
                log.warn("invalid block null seed");
                return bb;
            }
            if (blockTransactionBean.getWalletCypher() == null) {
                bb.setValid(false);
                log.warn("invalid block null cypher");
                return bb;
            }
            TkmCypherBean verifySign = TkmWallet.verifySign(blockTransactionBean);
            if (verifySign.isValid()) {
                String message = blockTransactionBean.getMessage();
                InternalBlockBean ibb = TkmTextUtils.internalBlockBeanFromJson(message);
                if (ibb != null) {
                    if (ibb.getBlockHash() == null) {
                        bb.setValid(false);
                        log.warn("null block hash");
                        return bb;
                    } else {
                        TransactionBox tboxBH = TkmWallet.verifyTransactionIntegrity(ibb.getBlockHash());
                        if (tboxBH == null) {
                            bb.setValid(false);
                            log.warn("null block hash decode");
                            return bb;
                        }
                        if (!tboxBH.isValid()) {
                            bb.setValid(false);
                            log.warn("invalid block hash");
                            return bb;
                        }
                        String calculatedBH = getInternalBlockHash(ibb);

                        if (tboxBH.messageInternalTB().equals(calculatedBH)) {
                            if (tboxBH.epoch() == null || tboxBH.slot() == null) {
                                bb.setValid(false);
                                log.warn("invalid data inside BH transaction");
                                return bb;
                            } else {
                                if (tboxBH.epoch().equals(0) & tboxBH.slot().equals(0)) {
                                    log.info("first block in chain");
                                } else {
                                    if (ibb.getPreviousBlock() == null) {
                                        bb.setValid(false);
                                        log.warn("null previous block");
                                        return bb;
                                    } else {
                                        TransactionBox tboxPrev = TkmWallet.verifyTransactionIntegrity(ibb.getPreviousBlock());
                                        if (tboxPrev == null) {
                                            bb.setValid(false);
                                            log.warn("previous block can not be decoded");
                                            return bb;
                                        }
                                        if (!tboxPrev.isValid()) {
                                            bb.setValid(false);
                                            log.warn("invalid previous block");
                                            return bb;
                                        }
                                        if (tboxPrev.epoch().compareTo(tboxBH.epoch()) > 0) {
                                            bb.setValid(false);
                                            log.warn("previous block epoch in the future");
                                            return bb;
                                        }
                                        if (tboxPrev.epoch().equals(tboxBH.epoch())) {
                                            if (tboxPrev.slot().compareTo(tboxBH.slot()) >= 0) {
                                                bb.setValid(false);
                                                log.warn("previous block slot in the future");
                                                return bb;
                                            }
                                        }
                                        if (tboxPrev.slot().compareTo(DefaultInitParameters.SLOT_PER_EPOCH_INT) >= 0) {
                                            if (tboxPrev.slot().compareTo(tboxBH.slot()) >= 0) {
                                                bb.setValid(false);
                                                log.warn("slot out of range in previous block");
                                                return bb;
                                            }
                                        }

                                        if (tboxPrev.epoch().compareTo(0) < 0) {
                                            bb.setValid(false);
                                            log.warn("negative epoch in previous block");
                                            return bb;
                                        }

                                        if (tboxPrev.slot().compareTo(0) < 0) {
                                            bb.setValid(false);
                                            log.warn("previous block slot negative");
                                            return bb;
                                        }
                                        if (tboxPrev.epoch().compareTo(0) < 0) {
                                            bb.setValid(false);
                                            log.warn("previous block epoch negative");
                                            return bb;
                                        }
                                    }

                                }
                                if (tboxBH.epoch().compareTo(0) < 0) {
                                    bb.setValid(false);
                                    log.warn("negative epoch in block hash");
                                    return bb;
                                }
                                if (tboxBH.slot().compareTo(0) < 0) {
                                    bb.setValid(false);
                                    log.warn("negative epoch in block hash");
                                    return bb;
                                }
                                if (ibb.getTransactionType() == null) {
                                    bb.setValid(false);
                                    log.warn("null type");
                                    return bb;
                                } else {
                                    if (!ibb.getTransactionType().equals(KeyContexts.TransactionType.BLOCK)) {
                                        bb.setValid(false);
                                        log.warn("wrong type of block");
                                        return bb;
                                    }

                                }
                                if (ibb.getCoinbase() == null) {
                                    bb.setValid(false);
                                    log.warn("null coinbase");
                                    return bb;
                                }
                                TransactionBox tboxCoin = TkmWallet.verifyTransactionIntegrity(ibb.getCoinbase());
                                if (tboxCoin == null) {
                                    bb.setValid(false);
                                    log.warn("null coinbase");
                                    return bb;
                                }
                                if (!tboxCoin.isValid()) {
                                    bb.setValid(false);
                                    log.warn("invalid coinbase");
                                    return bb;
                                }
                                if (!tboxCoin.epoch().equals(tboxBH.epoch())) {
                                    bb.setValid(false);
                                    log.warn("invalid coinbase epoch");
                                    return bb;
                                }
                                //if (tboxCoin.slot() != tboxBH.slot()) {
                                if (!tboxCoin.slot().equals(tboxBH.slot())) {
                                    bb.setValid(false);
                                    log.warn("invalid coinbase slot");
                                    return bb;
                                }
                                String coinbaseMessageJson = tboxCoin.messageInternalTB();
                                if (TkmTextUtils.isNullOrBlank(coinbaseMessageJson)) {
                                    bb.setValid(false);
                                    log.warn("null coinbase message");
                                    return bb;
                                }
                                CoinbaseMessageBean coinMessage = BlockSerializer.getCoinBaseMessageBeanFromJson(coinbaseMessageJson);
                                if (coinMessage == null) {
                                    bb.setValid(false);
                                    log.warn("null coinbase message");
                                    return bb;
                                }
                                if (coinMessage.getCoinbase() == null
                                        | coinMessage.getFrozenGreenFees() == null
                                        | coinMessage.getFrozenRedFees() == null
                                        | coinMessage.getGreenFees() == null
                                        | coinMessage.getRedFees() == null) {
                                    bb.setValid(false);
                                    log.warn("invalid coinbase message null fields");
                                    return bb;
                                }
                                if ( //there are penalties
                                        (((coinMessage.getFrozenGreenFees().compareTo(BigInteger.ZERO) > 0)
                                        | (coinMessage.getFrozenRedFees().compareTo(BigInteger.ZERO) > 0))
                                        & //there is a reward
                                        ((coinMessage.getGreenFees().compareTo(BigInteger.ZERO) > 0)
                                        | (coinMessage.getRedFees().compareTo(BigInteger.ZERO) > 0)))) {
                                    bb.setValid(false);
                                    log.warn("there cannot be penalties and at the same time a reward");
                                    return bb;
                                }
                                if ( //there are penalties
                                        (((coinMessage.getFrozenGreenFees().compareTo(BigInteger.ZERO) > 0)
                                        | (coinMessage.getFrozenRedFees().compareTo(BigInteger.ZERO) > 0))
                                        & //there must be exactly 
                                        (coinMessage.getPenaltiesSlots() != DefaultInitParameters.PENALTY_BLOCKS_PER_BLOCK_OVER_THE_LIMIT))) {
                                    bb.setValid(false);
                                    log.warn("wrong number of penalties blocks");
                                    return bb;
                                }
                                if ( //no penalties
                                        (((coinMessage.getGreenFees().compareTo(BigInteger.ZERO) > 0)
                                        | (coinMessage.getRedFees().compareTo(BigInteger.ZERO) > 0)
                                        | (coinMessage.getCoinbase().compareTo(BigInteger.ZERO) > 0))
                                        & //there must be exactly 0 penalty slots 
                                        (coinMessage.getPenaltiesSlots() != 0))) {
                                    bb.setValid(false);
                                    log.warn("wrong number of penalties blocks, must be zero");
                                    return bb;
                                }
                                if (coinMessage.getCoinbase().compareTo(BigInteger.ZERO) < 0
                                        | coinMessage.getFrozenGreenFees().compareTo(BigInteger.ZERO) < 0
                                        | coinMessage.getFrozenRedFees().compareTo(BigInteger.ZERO) < 0
                                        | coinMessage.getGreenFees().compareTo(BigInteger.ZERO) < 0
                                        | coinMessage.getRedFees().compareTo(BigInteger.ZERO) < 0
                                        | coinMessage.getPenaltiesSlots() < 0) {
                                    bb.setValid(false);
                                    log.warn("negative value in message");
                                    return bb;
                                }
                                if (tboxCoin.greenValue() == null | tboxCoin.redValue() == null) {
                                    bb.setValid(false);
                                    log.warn("null mandatory field");
                                    return bb;
                                }
                                if ((!tboxCoin.greenValue().equals(coinMessage.getGreenFees().add(coinMessage.getCoinbase())))
                                        | (!tboxCoin.redValue().equals(coinMessage.getRedFees()))) {
                                    bb.setValid(false);
                                    log.warn("wrong parameters in coinbase");
                                    return bb;
                                }
                                TransactionBox coinbaseTbox = TkmWallet.verifyTransactionIntegrity(ibb.getCoinbase());
                                TransactionBox blockhashTbox = TkmWallet.verifyTransactionIntegrity(ibb.getBlockHash());
                                if (coinbaseTbox == null || !coinbaseTbox.isValid() || blockhashTbox == null || !blockhashTbox.isValid()) {
                                    bb.setValid(false);
                                    log.warn("null mandatory block identifying transaction");
                                    return bb;
                                }
                                TransactionBox previousTbox = null;
                                if (!blockhashTbox.epoch().equals(0) | !blockhashTbox.slot().equals(0)) {
                                    //not first block
                                    TransactionBean previousBlock = ibb.getPreviousBlock();
                                    if (previousBlock == null) {
                                        bb.setValid(false);
                                        log.warn("null mandatory previous block");
                                        return bb;
                                    } else {
                                        previousTbox = TkmWallet.verifyTransactionIntegrity(previousBlock);
                                        if (previousTbox == null || !previousTbox.isValid()) {
                                            bb.setValid(false);
                                            log.warn("previous block can not be decoded");
                                            return bb;
                                        } else {
                                            log.warn("non genesis block, previous is NOT null");
                                        }
                                    }
                                } else {
                                    log.info("genesis block, previous is null");
                                }
                                //ALL OK
                                if (ibb.getForwardKeyes() != null) {
                                    bb.setForwardKeys(ibb.getForwardKeyes());
                                } else {
                                    ibb.setForwardKeys(new ConcurrentSkipListMap<String, String>());
                                }

                                bb.setPreviousBlock(previousTbox);
                                bb.setBlockHash(blockhashTbox);
                                bb.setCoinbase(coinbaseTbox);
                                bb.setValid(true);
                                bb.setIbb(ibb);
                                bb.setSingleInclusionBlockHash(calculatedBH);
                                bb.setTransactionJson(blockJson);
                                bb.setTb(blockTransactionBean);
                                return bb;

                            }
                        } else {
                            bb.setValid(false);
                            log.warn("tampered block invalid hash");
                            return bb;
                        }
                    }
                } else {
                    bb.setValid(false);
                    log.warn("invalid message inside signed transaction");
                    return bb;
                }
            } else {
                log.warn("invalid signature ");
                if (verifySign.getEx() != null) {
//                    verifySign.getEx().printStackTrace();
                    log.warn("invalid signature ", verifySign.getEx());
                }
                return bb;
            }
            //if(transactionBeanFromJson.getPublicKey()!=null)
            //return null;
        }
    }

    /**
     *
     * @param blockBox
     * @return return all non null, non ed addresses
     * @throws DecodeBlockException
     */
    public static final CompactAddressBean[] collectLargeAddresses(BlockBox blockBox) throws DecodeBlockException, AddressDecodeException {
        ConcurrentSkipListSet<String> res = new ConcurrentSkipListSet<String>();
        String[] blockHashAddresses = null;
        String[] coinbaseAddresses = null;
        String[] previousBlockAddresses = null;
        String[] fwKeys = null;
        String[] rewardListAddresses = null;
        String publicKey = blockBox.getTb().getPublicKey();
        ConcurrentSkipListSet<String> trxAddresses = new ConcurrentSkipListSet<>();
        ConcurrentSkipListSet<String> allAddresses = new ConcurrentSkipListSet<>();
        allAddresses.add(publicKey);

        ConcurrentSkipListSet<Exception> tboxExc = new ConcurrentSkipListSet<>();
        if (!blockBox.isValid()) {
            log.error("addresses extraction requested on invalid block");
            throw new DecodeBlockException("addresses extraction requested on invalid block");
        }
        try {
            blockHashAddresses = TkmAddressUtils.extractAddressesFromTransaction(blockBox.getBlockHash());
            //List<String> asList = Arrays.asList(blockHashAddresses);
            //allAddresses.addAll(asList);
        } catch (DecodeTransactionException ex) {
            throw new DecodeBlockException("invalid blockhash transaction", ex);
        }
        try {
            coinbaseAddresses = TkmAddressUtils.extractAddressesFromTransaction(blockBox.getCoinbase());
            //allAddresses.addAll(Arrays.asList(coinbaseAddresses));
        } catch (DecodeTransactionException ex) {
            throw new DecodeBlockException("invalid coinbase transaction", ex);
        }
        try {
            previousBlockAddresses = TkmAddressUtils.extractAddressesFromTransaction(blockBox.getPreviousBlock());
            //allAddresses.addAll(Arrays.asList(previousBlockAddresses));
        } catch (NullPointerException | DecodeTransactionException ex) {
            if (blockBox.getBlockHash().getItb().getEpoch() != 0 || blockBox.getBlockHash().getItb().getSlot() != 0) {
                throw new DecodeBlockException("invalid coinbase transaction", ex);
            } else {
                log.info("block zero");
            }
        }
        if (!blockBox.getForwardKeys().isEmpty()) {
            fwKeys = blockBox.getForwardKeys().values().toArray(String[]::new);
            //allAddresses.addAll(Arrays.asList(fwKeys));
        }
//        if (!blockBox.getIbb().getRewardList().isEmpty()) {
//            rewardListAddresses = blockBox.getIbb().getRewardList().values().stream().map(b -> b.getUrl64Addr()).toArray(String[]::new);
//            //allAddresses.addAll(Arrays.asList(rewardListAddresses));
//        }
        rewardListAddresses = TkmRewardUtils.extractRewardAddressesRaw(blockBox);

        ConcurrentSkipListSet<String> filterNullToSet = TkmArrayUtils.filterNullToSet(
                blockHashAddresses,
                coinbaseAddresses,
                previousBlockAddresses,
                fwKeys,
                rewardListAddresses);
        allAddresses.addAll(filterNullToSet);
        if (!blockBox.getIbb().getTransactions().isEmpty()) {
            blockBox.getIbb()
                    .getTransactions().parallelStream()
                    .map(t -> TkmWallet.verifyTransactionIntegrity(t.getTb())) //transaction box
                    .map(tbox -> {
                        if (!tbox.isValid()) {
                            log.error("INVALID TRANSACTION IN BLOCK");
                            tboxExc.add(new DecodeTransactionException("INVALID TRANSACTION IN BLOCK"));
                        } else {
                            try {
                                return TkmAddressUtils.extractAddressesFromTransaction(tbox);
                            } catch (DecodeTransactionException ex) {
                                tboxExc.add(new DecodeBlockException("invalid blockhash transaction", ex));
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
            if (!tboxExc.isEmpty()) {
                tboxExc.stream().forEach(ex -> {
                    log.error("exception in transaction decoding inside transaction list", ex);
                });
                throw new DecodeBlockException("exception in transaction decoding inside transaction list", tboxExc.first());
            }

            allAddresses.addAll(trxAddresses);
        }
        CompactAddressBean[] nonEdAddrBeans = Arrays
                .stream(allAddresses.toArray(String[]::new))
                .parallel()
                .map(addr -> {
                    try {
                        return TkmAddressUtils.toCompactAddress(addr);
                    } catch (AddressNotRecognizedException | AddressTooLongException ex) {
                        log.info("invalid Address");
                        tboxExc.add(ex);
                        return null;
                    }
                })
                .filter(caddr -> caddr != null)
                .filter(caddr -> !caddr.getType().equals(TkmAddressUtils.TypeOfAddress.ed25519))
                .toArray(CompactAddressBean[]::new);
        if (!tboxExc.isEmpty()) {
            tboxExc.stream().forEach(ex -> {
                log.error("exception in address casting inside block decoding", ex);
            });
            throw new DecodeBlockException("exception in address casting inside block decoding", tboxExc.first());
        }
        //blockBox.getIbb().getRewardList()
        //@Todo complete function
//        blockBox.getIbb().
        return nonEdAddrBeans;
    }

}
