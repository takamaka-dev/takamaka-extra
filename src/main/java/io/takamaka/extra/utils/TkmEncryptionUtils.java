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

import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.wallet.exceptions.InvalidCypherException;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.FixedParameters;
import io.takamaka.wallet.utils.TkmSignUtils;
import io.takamaka.wallet.utils.TkmTextUtils;
import java.io.UnsupportedEncodingException;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
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
}
