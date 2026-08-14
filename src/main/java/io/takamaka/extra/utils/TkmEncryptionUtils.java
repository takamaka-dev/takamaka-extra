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
 * Encryption utilities, including the {@code v0_2_a_stream_gcm} attachment stream path.
 *
 * <p><b>0.6.0 — DR-030.</b> The content digest moved across the base64 stream on BOTH the encrypt
 * and the decrypt path: {@code encrypted_content_hash} is SHA3-256 of the <b>ciphertext bytes</b>,
 * never of the base64 text that carries them, so wrapping, padding and alphabet are permanently
 * outside a blob's identity. This is a FLAG DAY: the hash IS the blob's identity, so a blob produced
 * by an older build is not readable and is not made readable.</p>
 *
 * <p>⚠️ <b>DR-030 is scoped to the HASH.</b> {@code ChatMediaPlaceholderBean.size} is unaffected and
 * remains the ENCODED length as emitted ({@code ATTACHMENT_PROTOCOL.md} §4.2). A hash is an identity
 * and must be producer-independent; a size is a transfer descriptor and is producer-relative by
 * design. The {@code streamPasswordEncrypt} overload reporting the ciphertext byte count was added
 * for that clause and is retained for §4.3's reserved padded version — read its javadoc before
 * wiring it to anything called "size".</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 * @version 0.6.0
 */
@Slf4j
public class TkmEncryptionUtils {
    /** VB-29: CSPRNG for every RandomStringGenerator in this class. commons-text falls back to
     *  ThreadLocalRandom when no provider is supplied — a 64-bit clock-seeded root shared by the whole
     *  JVM. See rschat-docs/security/PRNG_ENTROPY_AUDIT.md. */
    private static final java.security.SecureRandom TKM_CSPRNG = new java.security.SecureRandom();


    /**
     * Decode a base64 field of an {@code EncMessageBean}, failing with a message that NAMES the field
     * instead of letting a {@code null} reach a cipher (F12/SF-4).
     *
     * <p>{@code TkmSignUtils.fromB64URLToByteArray} returns {@code null} on a decode failure rather than
     * throwing. Passing that to {@code new IvParameterSpec(...)} or {@code doFinal(...)} yields a bare
     * {@link NullPointerException} raised deep inside the JCE, which names neither the field nor the value
     * and points the reader at the cipher rather than at the payload.
     *
     * <p>Accepts either base64 alphabet: {@code em[]} is subject to the same permanent read contract as
     * {@code enc_key} — a peer may legitimately encode it in the other alphabet, and historic values can
     * never be re-encoded. See {@code rschat-docs/security/BASE64_ENCODING_CONTRACT.md}.
     *
     * @param value the base64 field as it arrived on the wire
     * @param fieldName human-readable field name, used in the failure message
     * @return the decoded bytes, never null
     * @throws InvalidCypherException if the field is absent or does not decode
     */
    private static byte[] decodeOrFail(String value, String fieldName) throws InvalidCypherException {
        if (value == null) {
            throw new InvalidCypherException("EncMessageBean " + fieldName + " is missing");
        }
        try {
            byte[] decoded = TkmSignUtils.fromAnyB64ToByteArray(value);
            if (decoded == null) {
                throw new InvalidCypherException("EncMessageBean " + fieldName + " did not decode");
            }
            return decoded;
        } catch (RuntimeException ex) {
            throw new InvalidCypherException("EncMessageBean " + fieldName
                    + " is not valid base64 in either alphabet (length " + value.length() + ")", ex);
        }
    }

    public static final String fromPasswordEncryptedContent(String password, String scope, EncMessageBean encMessageBean) throws InvalidCypherException, WalletException {
        try {
            final String theMessage;
            switch (encMessageBean.getTkVersion()) {
                case "v0_1_a":
                    PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), scope.getBytes(), encMessageBean.getIterations(), encMessageBean.getOutputKeyLengthBit());
                    SecretKeyFactory skf = SecretKeyFactory.getInstance(encMessageBean.getPasswordHashAlgorithm());
                    byte[] secretKey = skf.generateSecret(spec).getEncoded();
                    SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey, encMessageBean.getKeySpecAlgorithm());

                    // F12/SF-4 — fromB64URLToByteArray SWALLOWS a decode failure into null. Feeding that
                    // straight into IvParameterSpec / doFinal produced a bare NullPointerException from deep
                    // inside the cipher: a decode fault reported as a crash in the wrong subsystem, with
                    // nothing naming the field or the value. Fail here, saying which half failed.
                    //
                    // fromAnyB64ToByteArray is used because `em[]` must also survive a peer that encodes it
                    // in the other alphabet — the same permanent read contract as enc_key. See
                    // rschat-docs/security/BASE64_ENCODING_CONTRACT.md.
                    byte[] iv = decodeOrFail(encMessageBean.getEncryptedMessage()[0], "em[0] (IV)");
                    byte[] content = decodeOrFail(encMessageBean.getEncryptedMessage()[1], "em[1] (ciphertext)");

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
     * @return map where key is the unencrypted file hash and value is the encrypted stram descriptor
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
        return streamPasswordEncrypt(password, scope, version, inputStreamE, outputStreamE,
                bufferSizeExponent, processedBytes, new AtomicLong());
    }

    /**
     * As {@link #streamPasswordEncrypt(String, String, String, InputStream, OutputStream, int, AtomicLong)},
     * additionally reporting the CIPHERTEXT byte count.
     *
     * <p>⚠️ <b>This is NOT {@code ChatMediaPlaceholderBean.size}.</b> That field is the ENCODED length
     * of the encrypted data, as emitted ({@code ATTACHMENT_PROTOCOL.md} §4.2) — i.e. the length of the
     * file this method writes to {@code outputStreamE}. Setting {@code size} from the ciphertext count
     * understates it by ~33%, which breaks uploads the server size-checks against the arriving base64
     * and makes a download bar read ~133%. It was briefly done under DR-030 and withdrawn the same
     * day: DR-030 moved the {@code encrypted_content_hash} — an IDENTITY, which must be
     * producer-independent — and nothing else. A size is a transfer descriptor and is
     * producer-relative by design.</p>
     *
     * <p>Provided because the count is a genuine property of the encryption (under {@code v0_2_a} it
     * is the plaintext length plus the 16-byte GCM tag), and {@code ATTACHMENT_PROTOCOL.md} §4.3
     * reserves a future padded version that will need it. No current caller uses it.</p>
     *
     * @param ciphertextBytes out-param, set to the number of ciphertext bytes produced
     * @return plaintext hash + the populated descriptor
     * @since 0.6.0
     */
    public static final AbstractMap.SimpleImmutableEntry<String, StreamEncryptedDescriptor> streamPasswordEncrypt(
            final String password,
            final String scope,
            final String version,
            final InputStream inputStreamE,
            final OutputStream outputStreamE,
            final int bufferSizeExponent,
            final AtomicLong processedBytes,
            final AtomicLong ciphertextBytes
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

            // DR-030: the digest tee sits BETWEEN the cipher and the base64 encoder, so
            // encrypted_content_hash is SHA3-256 of the CIPHERTEXT BYTES.
            //
            // It used to sit on the far side of the encoder — Base64OutputStream(tee(out, digest)) —
            // which fed the digest the base64 TEXT, including the 76-char CRLF wrapping Apache
            // Commons emits by default. That put every property of the encoding inside the
            // attachment's identity: the Dart port emits one unwrapped line and hashed that, so
            // identical ciphertext produced a different encrypted_content_hash depending only on
            // which platform uploaded it (F14/D1/O-10). Hashing the bytes makes wrapping, padding
            // and alphabet permanently irrelevant.
            //
            // Chain: cipher -> tee(base64 -> file, digest) instead of cipher -> base64 -> tee(file, digest).
            final Base64OutputStream base64OutputStream = new Base64OutputStream(outputStreamE);
            final TeeOutputStream teeOutputStream = new TeeOutputStream(base64OutputStream, digestOutputStreamEnc);
            // Counts the CIPHERTEXT, on the same side of the encoder as the digest — so `size` and
            // the hash are measured over exactly the same bytes and cannot disagree.
            final org.apache.commons.io.output.CountingOutputStream ciphertextCounter
                    = new org.apache.commons.io.output.CountingOutputStream(teeOutputStream);
            final CipherOutputStream cipherOutputStream = new CipherOutputStream(ciphertextCounter, cipher);
            cipher.init(Cipher.ENCRYPT_MODE, secret, iv);

            byte[] buffer = new byte[bufferBytes];
            int bytesRead;

            final DigestOutputStream digestOutputStreamPlain = new DigestOutputStream(shA3DigestPlain);
            final TeeInputStream teeInputStreamPlain = new TeeInputStream(inputStreamE, digestOutputStreamPlain);

            while ((bytesRead = teeInputStreamPlain.read(buffer)) != -1) {
                cipherOutputStream.write(buffer, 0, bytesRead);
                processedBytes.accumulateAndGet(bytesRead, Long::sum);

            }

            // Close OUTERMOST-FIRST. CipherOutputStream.close() is what writes the final block and
            // the GCM tag, and those bytes must still reach the digest — so the digest stream can
            // only be closed AFTER the cipher has finished pushing through the tee. The old order
            // closed the digest first; it survived because the tee sat downstream of base64
            // buffering, and it would silently drop the tag bytes from the hash in the new chain.
            cipherOutputStream.flush();
            cipherOutputStream.close();
            teeOutputStream.flush();
            teeOutputStream.close();
            base64OutputStream.flush();
            base64OutputStream.close();
            digestOutputStreamEnc.flush();
            digestOutputStreamEnc.close();
            digestOutputStreamPlain.flush();
            digestOutputStreamPlain.close();
            teeInputStreamPlain.close();

            final String hexHashEnc = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamEnc.getDigest());
            final String hexHashPlain = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamPlain.getDigest());
            ciphertextBytes.set(ciphertextCounter.getByteCount());
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

            // DR-030 (mirror of the encrypt path): the digest tee sits AFTER the base64 decoder, so
            // encrypted_content_hash is verified against the CIPHERTEXT BYTES.
            //
            // The second digest hashes the base64 TEXT as received — the PRE-DR-030 definition. It is
            // never used on success. It exists so that a failure can say WHICH failure it is: under
            // hard-fail, a stale build still emitting the old form and a genuine tampered download
            // would otherwise produce the identical "the server returned the wrong bytes" message,
            // and the stale build — the thing hard-fail exists to surface — would be indistinguishable
            // from a security incident. Cost is one SHA3 over bytes already flowing.
            final SHA3Digest shA3DigestLegacyWire = new SHA3Digest(shad);
            final DigestOutputStream digestOutputStreamLegacyWire = new DigestOutputStream(shA3DigestLegacyWire);
            final TeeInputStream legacyWireTee = new TeeInputStream(inputStreamE, digestOutputStreamLegacyWire);
            final Base64InputStream base64InputStream = new Base64InputStream(legacyWireTee);//decode
            final TeeInputStream teeInputStream = new TeeInputStream(base64InputStream, digestOutputStreamEnc);
            final CipherInputStream cipherInputStream = new CipherInputStream(teeInputStream, cipher);//decrypt

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
            legacyWireTee.close();
            digestOutputStreamEnc.flush();
            digestOutputStreamEnc.close();
            digestOutputStreamLegacyWire.flush();
            digestOutputStreamLegacyWire.close();
            digestOutputStreamPlain.close();

            //byte[] encodedhash = digest.digest();
            final String hexHashEnc = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamEnc.getDigest());
            final String hexHashPlain = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamPlain.getDigest());
            if (!sed.getEncryptedContentHash().equals(hexHashEnc)) {
                // DR-030 hard fail — no remediation. But say WHICH failure it is: if the declared
                // hash matches the pre-DR-030 definition (SHA3 of the base64 wire text), this is a
                // pre-release attachment or a producer that has not been rebuilt, NOT tampering.
                final String legacyWireHash
                        = TkmSignUtils.fromByteArrayToHexString(digestOutputStreamLegacyWire.getDigest());
                final boolean preDr030 = sed.getEncryptedContentHash().equals(legacyWireHash);
                final String errMsg = preDr030
                        ? String.format("PRE-DR-030 attachment: declared hash %1$s is SHA3-256 of the base64 "
                                + "WIRE TEXT, which was the contract before 2026-08-14. Since DR-030 the hash "
                                + "is over the CIPHERTEXT BYTES (%2$s). The content is intact and this is NOT "
                                + "tampering — it was encrypted by a build predating the change, and there is "
                                + "no remediation: it cannot be read. If a CURRENT producer emitted this, that "
                                + "producer has not been rebuilt.",
                                sed.getEncryptedContentHash(), hexHashEnc)
                        : String.format("invalid encrypted content hash, declared hash %1$s does not match "
                                + "calculated hash %2$s", sed.getEncryptedContentHash(), hexHashEnc);
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
                    .usingRandom(TKM_CSPRNG::nextInt)
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
                    .usingRandom(TKM_CSPRNG::nextInt)
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
