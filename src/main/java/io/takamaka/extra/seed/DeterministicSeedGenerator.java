/*
 * Copyright 2024 AiliA SA.
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
package io.takamaka.extra.seed;

import io.takamaka.wallet.beans.KeyBean;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.InvalidWalletIndexException;
import io.takamaka.wallet.exceptions.UnlockWalletException;
import io.takamaka.wallet.exceptions.WalletBurnedException;
import io.takamaka.wallet.exceptions.WalletEmptySeedException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.DefaultInitParameters;
import io.takamaka.wallet.utils.FileHelper;
import io.takamaka.wallet.utils.KeyContexts;
import io.takamaka.wallet.utils.SeedGenerator;
import io.takamaka.wallet.utils.SeededRandom;
import io.takamaka.wallet.utils.TkmTextUtils;
import io.takamaka.wallet.utils.WalletHelper;
import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.bouncycastle.crypto.AsymmetricCipherKeyPair;
import org.bouncycastle.util.encoders.UrlBase64;

/**
 *
 * @author giovanni.antino@h2tcoin.com
 */
@Slf4j
public class DeterministicSeedGenerator implements DeterministicSeedGeneratorInterface {

    private String seed;
    private String currentWalletName;
    private final Object constructorLock = new Object();
    private boolean isInitialized;
    private ConcurrentSkipListMap<String, ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Integer, byte[]>>> scopeIndexSizeSeedBytesMap;

    private void DeterministicSeedGenerator() {
        scopeIndexSizeSeedBytesMap = new ConcurrentSkipListMap<>();
    }

    /**
     * Initializes a new wallet or loads an existing one
     *
     * If the wallet directory does not exist, it will be created If the wallet
     * file does not exist, a new seed and words will be generated using the
     * SeedGenerator and the seed will be written to the wallet file using the
     * WalletHelper If the wallet file exists, the seed is read from the file
     * using the WalletHelper
     *
     * @param password The password to be used for encrypting the keyfile
     * @throws IOException if there is an error reading or writing to the file
     * @throws NoSuchAlgorithmException if the algorithm specified is not
     * available
     * @throws HashEncodeException if an error occurs while encoding
     * @throws InvalidKeySpecException if an error occurs while generating the
     * key
     * @throws HashAlgorithmNotFoundException if an error occurs while
     * generating the key
     * @throws HashProviderNotFoundException if an error occurs while generating
     * the key
     * @throws UnlockWalletException if there is an error with unlocking the
     * wallet
     * @throws NoSuchProviderException if the provider specified is not
     * available
     */
    private void initWallet(String password) throws IOException, NoSuchAlgorithmException, HashEncodeException, InvalidKeySpecException, HashAlgorithmNotFoundException, HashProviderNotFoundException, UnlockWalletException {
        if (!FileHelper.walletDirExists()) {
            //FileHelper.createDir(FileHelper.getDefaultWalletDirectoryPath());
            FileHelper.createDir(FileHelper.getEphemeralWalletDirectoryPath());
        }
        if (!FileHelper.fileExists(Paths.get(FileHelper.getDefaultWalletDirectoryPath().toString(), currentWalletName))) {
            List<String> words = SeedGenerator.generateWords();
            seed = SeedGenerator.generateSeedPWH(words);

            String concat = words.get(0);
            for (int i = 1; i < words.size(); i++) {
                concat += " " + words.get(i);
            }

            KeyBean kb = new KeyBean("POWSEED", KeyContexts.WalletCypher.Ed25519BC, seed, concat);
            try {
                WalletHelper.writeKeyFile(FileHelper.getDefaultWalletDirectoryPath(), currentWalletName, kb, password);

            } catch (NoSuchProviderException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException | BadPaddingException ex) {
                log.error("instance error password", ex);
                throw new UnlockWalletException("instance error password", ex);
            }
        }
        Path currentWalletPath = Paths.get(FileHelper.getDefaultWalletDirectoryPath().toString(), currentWalletName);

        if (FileHelper.fileExists(currentWalletPath)) {
            try {
                //System.out.println("loading " + currentWalletName + " wallet seed...");
                seed = WalletHelper.readKeyFile(currentWalletPath, password).getSeed();
                //System.out.println("seed loaded");
                //System.out.println(seed);
            } catch (InvalidAlgorithmParameterException | FileNotFoundException | NoSuchProviderException | NoSuchPaddingException | InvalidKeyException ex) {
                log.error("initWallet unreadable file?", ex);
                throw new IOException("initWallet unreadable file?", ex);
            }
        }
    }

    /**
     * Quick function for generating a seed without resorting to the 25 words.
     * Generally this call is used for seed creation for single-use keys that
     * are then burned.
     *
     * Initializes a new wallet or loads an existing one
     *
     * If the wallet directory does not exist, it will be created If the wallet
     * file does not exist, a new seed of alphabetic characters will be
     * generated, and the seed will be written to the wallet file If the wallet
     * file exists, the seed is read from the file If the seed is "burned",
     * WalletBurnedException is thrown If the seed is empty,
     * WalletEmptySeedException is thrown
     *
     * @param nCharSeed number of characters for the seed
     * @throws IOException if there is an error reading or writing to the file
     * @throws NoSuchAlgorithmException if the algorithm specified is not
     * available
     * @throws HashEncodeException if an error occurs while encoding
     * @throws InvalidKeySpecException if an error occurs while generating the
     * key
     * @throws HashAlgorithmNotFoundException if an error occurs while
     * generating the key
     * @throws HashProviderNotFoundException if an error occurs while generating
     * the key
     * @throws WalletBurnedException if the seed is "burned"
     * @throws WalletEmptySeed
     */
    private void initWallet(int nCharSeed) throws IOException, NoSuchAlgorithmException, HashEncodeException, InvalidKeySpecException, HashAlgorithmNotFoundException, HashProviderNotFoundException, UnlockWalletException, WalletBurnedException, WalletEmptySeedException {
        if (!FileHelper.walletDirExists()) {
            FileHelper.createDir(FileHelper.getEphemeralWalletDirectoryPath());
        }
        if (!FileHelper.fileExists(Paths.get(FileHelper.getEphemeralWalletDirectoryPath().toString(), currentWalletName))) {
            RandomStringGenerator generator = new RandomStringGenerator.Builder()
                    .withinRange('0', 'z')
                    .filteredBy(Character::isLetterOrDigit)
                    .get();
            seed = generator.generate(nCharSeed);
            FileHelper.writeStringToFile(FileHelper.getEphemeralWalletDirectoryPath(), currentWalletName, seed, false);
        }
        Path currentWalletPath = Paths.get(FileHelper.getEphemeralWalletDirectoryPath().toString(), currentWalletName);

        if (FileHelper.fileExists(currentWalletPath)) {
            try {
                seed = FileHelper.readStringFromFile(Paths.get(FileHelper.getEphemeralWalletDirectoryPath().toString(), currentWalletName));
                if ("burned".equals(seed)) {
                    throw new WalletBurnedException("WALLET IS BURNED");
                }
                if (TkmTextUtils.isNullOrBlank(seed)) {
                    throw new WalletEmptySeedException("WALLET SEED IS EMPTY");
                }
            } catch (FileNotFoundException ex) {
                log.error("instance error nseed", ex);
                throw new IOException("instance error nseed", ex);
            }
        }
    }

    /**
     * This function is designed for rapid creation of test wallets with
     * behavior equivalent to those generated by the 25 words.
     *
     * It initializes the collections for key pairs and public keys, and calls
     * initWallet method to initialize or load an existing wallet using a
     * default hardcoded password
     *
     * @param walletName the name of the wallet file
     * @throws UnlockWalletException if there is an error with unlocking the
     * wallet
     */
    public DeterministicSeedGenerator(String walletName) throws UnlockWalletException {
        DeterministicSeedGenerator();
        synchronized (constructorLock) {
            if (!isInitialized) {
                try {
                    currentWalletName = walletName + DefaultInitParameters.WALLET_EXTENSION;
                    initWallet("Password");
                } catch (IOException | NoSuchAlgorithmException | HashEncodeException | InvalidKeySpecException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
                    log.error("instance error name", ex);
                    throw new UnlockWalletException(ex);
                }
                isInitialized = true;
            }
        }
    }

    /**
     * Quick function for generating a seed without resorting to the 25 words.
     * Generally this call is used for seed creation for single-use keys that
     * are then burned.
     *
     * Initializes a new wallet or loads an existing one
     *
     * If the wallet directory does not exist, it will be created If the wallet
     * file does not exist, a new seed of alphabetic characters will be
     * generated, and the seed will be written to the wallet file If the wallet
     * file exists, the seed is read from the file If the seed is "burned",
     * WalletBurnedException is thrown If the seed is empty,
     * WalletEmptySeedException is thrown
     *
     * @param walletName
     * @param nCharForSeed number of characters for the seed
     * @throws io.takamaka.wallet.exceptions.UnlockWalletException
     * @throws io.takamaka.wallet.exceptions.WalletEmptySeedException
     * @throws WalletBurnedException if the seed is "burned"
     */
    public DeterministicSeedGenerator(String walletName, int nCharForSeed) throws UnlockWalletException, WalletEmptySeedException, WalletBurnedException {
        DeterministicSeedGenerator();
        synchronized (constructorLock) {
            if (!isInitialized) {
                try {
                    currentWalletName = walletName + DefaultInitParameters.WALLET_EXTENSION;
                    initWallet(nCharForSeed);
                } catch (IOException | NoSuchAlgorithmException | HashEncodeException | InvalidKeySpecException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
                    log.error("instance error seed", ex);
                    throw new UnlockWalletException("instance error seed", ex);
                }
                isInitialized = true;
            }
        }

    }

    /**
     * Initializes the collections for seed, and calls initWallet method to
     * initialize or load an existing wallet
     *
     * @param walletName the name of the wallet file
     * @param password the password used to encrypt the keyfile
     * @throws UnlockWalletException if there is an error with unlocking the
     * wallet
     */
    public DeterministicSeedGenerator(String walletName, String password) throws UnlockWalletException {
        DeterministicSeedGenerator();
        synchronized (constructorLock) {
            if (!isInitialized) {
                try {
                    currentWalletName = walletName + DefaultInitParameters.WALLET_EXTENSION;
                    initWallet(password);
                } catch (IOException | NoSuchAlgorithmException | HashEncodeException | InvalidKeySpecException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
                    log.error("instance error name password", ex);
                    throw new UnlockWalletException("instance error name password", ex);
                }
                isInitialized = true;
            }
        }

    }

    @Override
    public byte[] getSeedBytes(String scope, int index, int numOfBytes) throws WalletException {
        if (index < 0 || index >= Integer.MAX_VALUE) {
            throw new InvalidWalletIndexException("index outside wallet range");
        }
        if (numOfBytes < 0 || numOfBytes >= Integer.MAX_VALUE) {
            throw new InvalidWalletIndexException("numOfBytes outside range");
        }
        if (!scopeIndexSizeSeedBytesMap.containsKey(scope)) {
            scopeIndexSizeSeedBytesMap.put(scope, new ConcurrentSkipListMap<>());
        }
        final ConcurrentSkipListMap<Integer, ConcurrentSkipListMap<Integer, byte[]>> atScope = scopeIndexSizeSeedBytesMap.get(scope);
        if (!atScope.containsKey(index)) {
            atScope.put(index, new ConcurrentSkipListMap<>());
        }
        final ConcurrentSkipListMap<Integer, byte[]> scopeAtIndex = atScope.get(index);
        if (!scopeAtIndex.containsKey(numOfBytes)) {
            SeededRandom seededRandomGeneratorAtIndex = getSeededRandomGeneratorAtIndex(scope, index);
            byte[] seedBytes = new byte[numOfBytes];
            seededRandomGeneratorAtIndex.nextBytes(seedBytes);
            scopeAtIndex.put(numOfBytes, seedBytes);
        }

        return scopeAtIndex.get(numOfBytes);

    }

    @Override
    public String getSeedB64Url(String scope, int index, int numOfBytes) throws WalletException {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            UrlBase64.encode(getSeedBytes(scope, index, numOfBytes), baos);
            String stringSeed = baos.toString();
            baos.close();
            return stringSeed;
        } catch (IOException ex) {
            throw new WalletException(ex);
        }
    }

    @Override
    public SeededRandom getSeededRandomGeneratorAtIndex(String scope, int index) throws InvalidWalletIndexException {
        if (index < 0 || index >= Integer.MAX_VALUE) {
            throw new InvalidWalletIndexException("index outside wallet range");
        }
        SeededRandom seededRandom = new SeededRandom(seed, scope, index + 1);
        return seededRandom;
    }

}
