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
package io.takamaka.extra.utils;

import io.takamaka.extra.beans.CombinedRSAAESBean;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import io.takamaka.extra.exceptions.TkmCryptoExtraException;
import static io.takamaka.extra.identicon.IdenticonManager.i;
import io.takamaka.wallet.InstanceWalletKeystoreInterface;
import io.takamaka.wallet.TkmCypherProviderBCRSA4096ENC;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.exceptions.InvalidCypherException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.FixedParameters;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.CipherOutputStream;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64InputStream;
import org.apache.commons.codec.binary.Base64OutputStream;
import org.apache.commons.text.RandomStringGenerator;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.bouncycastle.crypto.io.DigestOutputStream;
import org.bouncycastle.util.io.TeeInputStream;
import org.bouncycastle.util.io.TeeOutputStream;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmEncryptionUtils {

    public static final String fromPasswordEncryptedContent(String password, String scope, EncMessageBean encMessageBean) throws InvalidCypherException, WalletException {
        try {
            final String theMessage;
            switch (encMessageBean.getTkVersion()) {
                case "v0_1_a":
                    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), scope.getBytes(), encMessageBean.getIterations(), encMessageBean.getOutputKeyLengthBit());
                    SecretKeyFactory skf = SecretKeyFactory.getInstance(encMessageBean.getPasswordHashAlgorithm());
                    byte[] secretKey = skf.generateSecret(spec).getEncoded();
                    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, encMessageBean.getKeySpecAlgorithm());

                    byte[] iv = TkmSignUtils.fromB64URLToByteArray(encMessageBean.getEncryptedMessage()[0]);
                    byte[] content = TkmSignUtils.fromB64URLToByteArray(encMessageBean.getEncryptedMessage()[1]);

                    Cipher cipher = Cipher.getInstance(encMessageBean.getTransformation());
                    cipher.init(Cipher.DECRYPT_MODE, secretKeySpec, new IvParameterSpec(iv));
                    theMessage = new String(cipher.doFinal(content), encMessageBean.getEncoding());
                    break;

                default:
                    throw new InvalidCypherException("unrecognized version " + encMessageBean.getTkVersion());
            }
            return theMessage;
        } catch (NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | InvalidKeyException | InvalidAlgorithmParameterException | IllegalBlockSizeException | BadPaddingException | UnsupportedEncodingException ex) {
            throw new WalletException(ex);
        }
    }

    /**
     *
     * @param password UTF8 password, any lenght
     * @param content UTF8 text content
     * @param scope UTF8 Salt
     * @param version
     * @return
     * @throws io.takamaka.wallet.exceptions.InvalidCypherException
     */
    public static final EncMessageBean toPasswordEncryptedContent(String password, String content, String scope, String version) throws WalletException {
        try {
            final EncMessageBean encMessageBean;
            switch (version) {
                case "v0_1_a":
                    encMessageBean = new EncMessageBean(
                            EncryptionContext.v0_1_a.getPasswordHashAlgorithm(),
                            EncryptionContext.v0_1_a.getIterations(),
                            EncryptionContext.v0_1_a.getTransformation(),
                            EncryptionContext.v0_1_a.getKeySpecAlgorithm(),
                            EncryptionContext.v0_1_a.name(),
                            EncryptionContext.v0_1_a.getOutputKeyLengthBit(),
                            EncryptionContext.v0_1_a.getEncoding(),
                            null
                    );
                    break;

                default:
                    throw new InvalidCypherException("unrecognized version " + version);
            }
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), scope.getBytes(), encMessageBean.getIterations(), encMessageBean.getOutputKeyLengthBit());
            SecretKeyFactory skf = SecretKeyFactory.getInstance(encMessageBean.getPasswordHashAlgorithm());
            byte[] secretKey = skf.generateSecret(spec).getEncoded();
            SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, encMessageBean.getKeySpecAlgorithm());
            Cipher cipher = Cipher.getInstance(encMessageBean.getTransformation());
            cipher.init(Cipher.ENCRYPT_MODE, secretKeySpec);
            byte[][] encMessageBytes = new byte[][]{cipher.getIV(), cipher.doFinal(content.getBytes(encMessageBean.getEncoding()))};
            String[] mb = new String[encMessageBytes.length];
            for (int i = 0; i < encMessageBytes.length; i++) {
                byte[] encMessageByte = encMessageBytes[i];
                mb[i] = TkmSignUtils.fromByteArrayToB64URL(encMessageByte);
            }
            encMessageBean.setEncryptedMessage(mb);
            return encMessageBean;
        } catch (InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | UnsupportedEncodingException | IllegalBlockSizeException | BadPaddingException ex) {
            throw new WalletException(ex);
        }
    }

    /**
     * There are four inputs for authenticated encryption: the secret key,
     * initialization vector (IV) (sometimes called a nonce†), the plaintext
     * itself, and optional additional authentication data (AAD). The nonce and
     * AAD are passed in the clear. There are two outputs: the ciphertext, which
     * is exactly the same length as the plaintext, and an authentication tag
     * (the "tag"). The tag is sometimes called the message authentication code
     * (MAC) or integrity check value (ICV).
     *
     * The term "IV" is used here to mean exactly the same as "nonce"
     *
     * @param password
     * @param scope
     * @param version
     * @param inputStreamE
     * @param outputStreamE
     * @param bufferSizeExponent 2^x buffer byte size example: [10 -> 2^10 =
     * 1024] or [12 -> 2^12 = 4096] bytes = 1 kibibyte
     * @param processedBytes zeroed when process start
     * @return
     * @throws io.takamaka.wallet.exceptions.InvalidCypherException
     */
    public static final AbstractMap.SimpleImmutableEntry<String, StreamEncryptedDescriptor> streamPasswordEncrypt(
            final String password,
            final String scope,
            final String version,
            final InputStream inputStreamE,
            final OutputStream outputStreamE,
            final int bufferSizeExponent,
            final AtomicLong processedBytes
    ) throws InvalidCypherException, WalletException {
        try {
            final int bufferBytes = (int) Math.pow(2, bufferSizeExponent);
            processedBytes.set(0L);
            final StreamEncryptedDescriptor sed;
            switch (version) {
                case "v0_2_a_stream_gcm":
                    sed = new StreamEncryptedDescriptor(
                            EncryptionContext.v0_2_a_stream_gcm.getPasswordHashAlgorithm(),
                            EncryptionContext.v0_2_a_stream_gcm.getIterations(),
                            EncryptionContext.v0_2_a_stream_gcm.getTransformation(),
                            EncryptionContext.v0_2_a_stream_gcm.getKeySpecAlgorithm(),
                            EncryptionContext.v0_2_a_stream_gcm.name(),
                            EncryptionContext.v0_2_a_stream_gcm.getOutputKeyLengthBit(),
                            EncryptionContext.v0_2_a_stream_gcm.getEncoding(),
                            null,
                            EncryptionContext.v0_2_a_stream_gcm.getIvByteLength(),
                            EncryptionContext.v0_2_a_stream_gcm.getTagBitLength(),
                            null,
                            getRandomSaltWithScope256bitB64(scope),
                            EncryptionContext.v0_2_a_stream_gcm.getDigestHash()
                    );
                    break;

                default:
                    throw new InvalidCypherException("unrecognized version " + version);
            }
            final byte[] saltBytes = TkmSignUtils.fromHexToByteArray(sed.getSalt());
            final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, sed.getIterations(), sed.getOutputKeyLengthBit());
            final GCMParameterSpec iv = generateIv(sed.getTagLengthBit(), sed.getIvLengthByte());
            sed.setIv(TkmSignUtils.fromByteArrayToHexString(iv.getIV()));
            final SecretKeyFactory skf = SecretKeyFactory.getInstance(sed.getPasswordHashAlgorithm());
            final byte[] secretKey = skf.generateSecret(spec).getEncoded();
            final SecretKey secret = new SecretKeySpec(secretKey, sed.getKeySpecAlgorithm());

            final Cipher cipher = Cipher.getInstance(sed.getTransformation(), "BC");
            //in future release must be changed to be more flexible
            final String digestHash = EncryptionContext.v0_2_a_stream_gcm.getDigestHash();

            final int shad = Integer.parseInt(digestHash.split("-")[1]);
            final SHA3Digest shA3DigestEnc = new SHA3Digest(shad);
            final SHA3Digest shA3DigestPlain = new SHA3Digest(shad);
            //final SHA3Digest shA3Digest = new SHA3Digest(shad);
            if (!shA3DigestEnc.getAlgorithmName().toLowerCase().equals(digestHash.toLowerCase())) {
                throw new WalletException("invalid hash algorithm");
            }
            final DigestOutputStream digestOutputStreamEnc = new DigestOutputStream(shA3DigestEnc);

            final TeeOutputStream teeOutputStream = new TeeOutputStream(outputStreamE, digestOutputStreamEnc);
            final Base64OutputStream base64OutputStream = new Base64OutputStream(teeOutputStream);
            final CipherOutputStream cipherOutputStream = new CipherOutputStream(base64OutputStream, cipher);
            cipher.init(Cipher.ENCRYPT_MODE, secret, iv);

            byte[] buffer = new byte[bufferBytes];
            int bytesRead;

            final DigestOutputStream digestOutputStreamPlain = new DigestOutputStream(shA3DigestPlain);
            final TeeInputStream teeInputStreamPlain = new TeeInputStream(inputStreamE, digestOutputStreamPlain);

            while ((bytesRead = teeInputStreamPlain.read(buffer)) != -1) {
                cipherOutputStream.write(buffer, 0, bytesRead);
                processedBytes.accumulateAndGet(bytesRead, Long::sum);

            }

            digestOutputStreamEnc.flush();
            digestOutputStreamPlain.flush();
            cipherOutputStream.flush();
            base64OutputStream.flush();
            teeOutputStream.flush();
            digestOutputStreamEnc.close();
            digestOutputStreamPlain.close();
            cipherOutputStream.close();
            base64OutputStream.close();
            teeOutputStream.close();
            teeInputStreamPlain.close();

            final String hexHashEnc = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamEnc.getDigest());
            final String hexHashPlain = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamPlain.getDigest());
            sed.setEncryptedContentHash(hexHashEnc);
            final AbstractMap.SimpleImmutableEntry<String, StreamEncryptedDescriptor> res = new AbstractMap.SimpleImmutableEntry<String, StreamEncryptedDescriptor>(hexHashPlain, sed);
            return res;
        } catch (InvalidAlgorithmParameterException | NoSuchProviderException | TkmCryptoExtraException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | UnsupportedEncodingException ex) {
            throw new WalletException("cypher error", ex);
        } catch (IOException ex) {
            throw new WalletException("buffer error", ex);
        }
    }

    public static final void streamPasswordDecrypt(
            final String password,
            final StreamEncryptedDescriptor sed,
            final String version,
            final InputStream inputStreamE,
            final OutputStream outputStreamE,
            final int bufferSizeExponent,
            final AtomicLong processedBytes,
            final String plainHash
    ) throws InvalidCypherException, WalletException {
        try {
            final int bufferBytes = (int) Math.pow(2, bufferSizeExponent);
            processedBytes.set(0L);
            //hash
            //final MessageDigest digest = MessageDigest.getInstance(sed.getDigestHashFunction(), "BC");
            //enc
            final byte[] saltBytes = TkmSignUtils.fromHexToByteArray(sed.getSalt());
            final PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, sed.getIterations(), sed.getOutputKeyLengthBit());
            final GCMParameterSpec iv = new GCMParameterSpec(sed.getTagLengthBit(), TkmSignUtils.fromHexToByteArray(sed.getIv()));
            //sed.setIv(TkmSignUtils.fromByteArrayToB64(iv.getIV()));
            final SecretKeyFactory skf = SecretKeyFactory.getInstance(sed.getPasswordHashAlgorithm());
            final byte[] secretKey = skf.generateSecret(spec).getEncoded();
            final SecretKey secret = new SecretKeySpec(secretKey, sed.getKeySpecAlgorithm());

            final Cipher cipher = Cipher.getInstance(sed.getTransformation(), "BC");
            //in future release must be changed to be more flexible
            final String digestHash = EncryptionContext.v0_2_a_stream_gcm.getDigestHash();

            final int shad = Integer.parseInt(digestHash.split("-")[1]);
            final SHA3Digest shA3DigestEnc = new SHA3Digest(shad);
            final SHA3Digest shA3DigestPlain = new SHA3Digest(shad);
            if (!shA3DigestEnc.getAlgorithmName().toLowerCase().equals(digestHash.toLowerCase())) {
                throw new WalletException("invalid hash algorithm");
            }
            final DigestOutputStream digestOutputStreamEnc = new DigestOutputStream(shA3DigestEnc);
            final DigestOutputStream digestOutputStreamPlain = new DigestOutputStream(shA3DigestPlain);

            final TeeOutputStream teeOutputStream = new TeeOutputStream(outputStreamE, digestOutputStreamPlain);

            cipher.init(Cipher.DECRYPT_MODE, secret, iv);
            //ByteArrayOutputStream tempDupStream = new ByteArrayOutputStream(bufferBytes);
            final TeeInputStream teeInputStream = new TeeInputStream(inputStreamE, digestOutputStreamEnc);
            final Base64InputStream base64InputStream = new Base64InputStream(teeInputStream);//decode
            final CipherInputStream cipherInputStream = new CipherInputStream(base64InputStream, cipher);//decrypt

            final byte[] buffer = new byte[bufferBytes];
            int bytesRead;
            while ((bytesRead = cipherInputStream.read(buffer)) != -1) {
                teeOutputStream.write(buffer, 0, bytesRead);
                processedBytes.accumulateAndGet(bytesRead, Long::sum);

            }
            digestOutputStreamPlain.flush();
            cipherInputStream.close();
            teeInputStream.close();
            base64InputStream.close();
            digestOutputStreamEnc.flush();
            digestOutputStreamEnc.close();
            digestOutputStreamPlain.close();

            //byte[] encodedhash = digest.digest();
            final String hexHashEnc = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamEnc.getDigest());
            final String hexHashPlain = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamPlain.getDigest());
            if (!sed.getEncryptedContentHash().equals(hexHashEnc)) {
                String errMsg = String.format("invalid encrypted content hash, declared hash %1$s does not match calculated hash %2$s", sed.getEncryptedContentHash(), hexHashEnc);
                throw new WalletException(errMsg);
            }

            if (!TkmTextUtils.isNullOrBlank(plainHash)) {
                if (!plainHash.equals(hexHashPlain)) {
                    String errMsg = String.format("invalid plain content hash, declared hash %1$s does not match calculated hash %2$s", plainHash, hexHashPlain);
                    throw new WalletException(errMsg);
                }
            }

        } catch (InvalidAlgorithmParameterException | NoSuchProviderException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | UnsupportedEncodingException ex) {
            throw new WalletException("cypher error", ex);
        } catch (IOException ex) {
            throw new WalletException("buffer error", ex);
        }
    }

    public static final String streamCalcHash(
            final InputStream in,
            final String hashingAlgorothm,
            final int bufferSizeExponent,
            final AtomicLong processedBytes
    ) throws WalletException {
        final int bufferBytes = (int) Math.pow(2, bufferSizeExponent);
        //in future release must be changed to be more flexible
        final String digestHash = EncryptionContext.v0_2_a_stream_gcm.getDigestHash();
        final int shad = Integer.parseInt(digestHash.split("-")[1]);
        final SHA3Digest shA3DigestEnc = new SHA3Digest(shad);
//            final SHA3Digest shA3DigestPlain = new SHA3Digest(shad);
        if (!shA3DigestEnc.getAlgorithmName().toLowerCase().equals(digestHash.toLowerCase())) {
            throw new WalletException("invalid hash algorithm");
        }
        final DigestOutputStream digestOutputStreamEnc = new DigestOutputStream(shA3DigestEnc);
        final byte[] buffer = new byte[bufferBytes];
        int bytesRead;
        try {
            while ((bytesRead = in.read(buffer)) != -1) {
                digestOutputStreamEnc.write(buffer, 0, bytesRead);
                processedBytes.accumulateAndGet(bytesRead, Long::sum);
            }
            digestOutputStreamEnc.flush();
            digestOutputStreamEnc.close();
            final String hexHashEnc = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamEnc.getDigest());
            return hexHashEnc;
        } catch (IOException ex) {
            throw new WalletException("buffer error", ex);
        }
//        return null;

    }

    /**
     * Using default 128 bit len and strong istance. This is specifically for
     * long term symmetric encryption of large files.
     *
     * @param tagLengthBit 128 is default (preferable)
     * @param ivByteLen 12 bytes is default (preferable)
     * @return
     * @throws TkmCryptoExtraException
     */
    public static final GCMParameterSpec generateIv(int tagLengthBit, int ivByteLen) throws TkmCryptoExtraException {
        try {
            byte[] iv = new byte[ivByteLen];
            SecureRandom instanceStrong = SecureRandom.getInstanceStrong();
            instanceStrong.nextBytes(iv);
            return new GCMParameterSpec(tagLengthBit, iv);
        } catch (NoSuchAlgorithmException ex) {
            throw new TkmCryptoExtraException(ex);
        }
    }

    public static final String getRandomSaltWithScope256bitB64(String scope) throws TkmCryptoExtraException {
        try {
            RandomStringGenerator generator = new RandomStringGenerator.Builder()
                    .withinRange('0', 'z')
                    .filteredBy(Character::isLetterOrDigit)
                    .get();
            String b64hash = TkmSignUtils.Hash256ToHex(scope + generator.generate(256));
            return b64hash;
        } catch (HashEncodeException | HashAlgorithmNotFoundException | HashProviderNotFoundException ex) {
            throw new TkmCryptoExtraException(ex);
        }
    }

    /**
     * *
     *
     * @param rsaPublicKey
     * @param message
     * @return
     * @throws WalletException
     */
    public static final CombinedRSAAESBean encryptRSAAES(String rsaPublicKey, String message) throws WalletException {

        CombinedRSAAESBean crab = new CombinedRSAAESBean();
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange('0', 'z')
                .filteredBy(Character::isLetterOrDigit)
                .get();
        String secretKey = generator.generate(400);
        String rsaEncPubKey = TkmCypherProviderBCRSA4096ENC.encrypt(rsaPublicKey, secretKey);

        crab.setRSAEncryptedKey(rsaEncPubKey);
        crab.setScope(AdvancedScopeContext.RSA_KEY_ENCRYPTION_AES_CYPHERTEXT.name());

        EncMessageBean passwordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(
                secretKey,
                message,
                AdvancedScopeContext.RSA_KEY_ENCRYPTION_AES_CYPHERTEXT.name(),
                EncryptionContext.v0_1_a.name());

        crab.setAesContentBean(passwordEncryptedContent);
        return crab;

    }

    /**
     * *
     *
     * @param crab
     * @param iwk
     * @param index
     * @return
     * @throws WalletException
     */
    public static final String decryptRSAAES(CombinedRSAAESBean crab, InstanceWalletKeystoreInterface iwk, int index) throws WalletException {
        String message = null;
        String password = decryptSecretKey(crab, iwk, index);
        message = TkmEncryptionUtils.fromPasswordEncryptedContent(password, crab.getScope(), crab.getAesContentBean());
        return message;
    }

    /**
     * *
     *
     * @param crab
     * @param iwk
     * @param index
     * @return
     * @throws WalletException
     */
    public static final String decryptSecretKey(CombinedRSAAESBean crab, InstanceWalletKeystoreInterface iwk, int index) throws WalletException {
        String secret;
        secret = TkmCypherProviderBCRSA4096ENC.decrypt(iwk, index, crab.getRSAEncryptedKey());
        return secret;
    }

    /**
     *
     * @param secret
     * @param alogorithm es "SHA-256"
     * @param message
     * @return
     * @throws NoSuchAlgorithmException
     */
    public static final byte[] getHMACDigestUTF8(String secret, String alogorithm, String message) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance(alogorithm);
        String text = "Text to hash, cryptographically.";

        // Change this to UTF-16 if needed
        md.update(text.getBytes(StandardCharsets.UTF_8));
        byte[] digest = md.digest();
        return digest;
    }

}
