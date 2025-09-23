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
import org.apache.commons.text.RandomStringGenerator;

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
     * @return
     * @throws io.takamaka.wallet.exceptions.InvalidCypherException
     */
    public static final StreamEncryptedDescriptor streamPasswordEncrypt(
            final String password,
            final String scope,
            final String version,
            final InputStream inputStreamE,
            final OutputStream outputStreamE
    ) throws InvalidCypherException, WalletException {
        try {
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
            //hash
            final MessageDigest digest = MessageDigest.getInstance("SHA3-256", "BC");
            //enc
            final byte[] saltBytes = TkmSignUtils.fromHexToByteArray(sed.getSalt());
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, sed.getIterations(), sed.getOutputKeyLengthBit());
            String string = new String(spec.getPassword());
            final GCMParameterSpec iv = generateIv(sed.getTagLengthBit(), sed.getIvLengthByte());
            sed.setIv(TkmSignUtils.fromByteArrayToHexString(iv.getIV()));
            SecretKeyFactory skf = SecretKeyFactory.getInstance(sed.getPasswordHashAlgorithm());
            byte[] secretKey = skf.generateSecret(spec).getEncoded();
            SecretKey secret = new SecretKeySpec(secretKey, sed.getKeySpecAlgorithm());
            
            Cipher cipher = Cipher.getInstance(sed.getTransformation(), "BC");
            //CipherInputStream inputStream = new CipherInputStream(inputStreamE, cipher);
            CipherOutputStream outputStream = new CipherOutputStream(outputStreamE, cipher);
            cipher.init(Cipher.ENCRYPT_MODE, secret, iv);
            //byte[] buffer = new byte[32];
            
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = inputStreamE.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.close();
//            //int bytesRead = 0;
//            int bytesRead = 0;// = inputStream.read(buffer); //data
//            bytesRead = inputStream.read(buffer);
//            while (bytesRead != -1) {
//                byte[] output = cipher.update(buffer, 0, bytesRead); //enc
//                
//                //cipher.up
//                if (output != null) { //enc
//                    outputStream.write(output); //enc
//                    digest.update(output);//hash
//                    
//                } //enc              
//                bytesRead = inputStream.read(buffer);
//            }
            log.info("iv " + iv);
            byte[] iv1 = cipher.getIV();
            
            log.info("iv " + iv1);
            //outputStream.flush();
            
            byte[] encodedhash = digest.digest();
            final String hexHash = TkmSignUtils.fromByteArrayToHexString(encodedhash);
            sed.setEncryptedContentHash(hexHash);
            return sed;
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
            final OutputStream outputStreamE
    ) throws InvalidCypherException, WalletException {
        try {

            //hash
            final MessageDigest digest = MessageDigest.getInstance("SHA3-256", "BC");
            //enc
            final byte[] saltBytes = TkmSignUtils.fromHexToByteArray(sed.getSalt());
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), saltBytes, sed.getIterations(), sed.getOutputKeyLengthBit());
            String string = new String(spec.getPassword());
            GCMParameterSpec iv = new GCMParameterSpec(sed.getTagLengthBit(), TkmSignUtils.fromHexToByteArray(sed.getIv()));
            //sed.setIv(TkmSignUtils.fromByteArrayToB64(iv.getIV()));
            SecretKeyFactory skf = SecretKeyFactory.getInstance(sed.getPasswordHashAlgorithm());
            byte[] secretKey = skf.generateSecret(spec).getEncoded();
            SecretKey secret = new SecretKeySpec(secretKey, sed.getKeySpecAlgorithm());
            
            Cipher cipher = Cipher.getInstance(sed.getTransformation(), "BC");
            cipher.init(Cipher.DECRYPT_MODE, secret, iv);
            CipherInputStream cipherInputStream = new CipherInputStream(inputStreamE, cipher);
            byte[] buffer = new byte[1024];
            int bytesRead;
            while ((bytesRead = cipherInputStream.read(buffer)) != -1) {
                outputStreamE.write(buffer, 0, bytesRead);
            }
            cipherInputStream.close();
//            byte[] buffer = new byte[32];
//            //int bytesRead = 0;
//            int bytesRead;// = inputStream.read(buffer); //data
//
//            while ((bytesRead = inputStream.read(buffer)) != -1) {
//                System.out.print((char) bytesRead);
//                byte[] output = cipher.update(buffer, 0, bytesRead); //enc
//                if (output != null) { //enc
//                    outputStream.write(output); //enc
//                    digest.update(buffer);//hash
//                } //enc
//
//            }
            byte[] encodedhash = digest.digest();
            final String hexHash = TkmSignUtils.fromByteArrayToHexString(encodedhash);
            if (!sed.getEncryptedContentHash().equals(sed.getEncryptedContentHash())) {
                String errMsg = String.format("invalid encrypted content hash, declared hash %1$s does not match calculated hash %2$s", sed.getEncryptedContentHash(), hexHash);
                throw new WalletException(errMsg);
            }
            
        } catch (InvalidAlgorithmParameterException | NoSuchProviderException | InvalidKeyException | NoSuchAlgorithmException | InvalidKeySpecException | NoSuchPaddingException | UnsupportedEncodingException ex) {
            throw new WalletException("cypher error", ex);
        } catch (IOException ex) {
            throw new WalletException("buffer error", ex);
        }
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
