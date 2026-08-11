/*
 * Copyright 2026 AiliA SA.
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

import io.takamaka.extra.beans.StreamEncryptedDescriptor;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.Security;
import java.util.AbstractMap;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Pins the LOAD-BEARING protocol assumption behind attachment privacy:
 *
 * <p><b>Every encryption round of the same plaintext MUST produce a different ciphertext.</b>
 *
 * <p>The relay stores attachments as opaque blobs keyed by the hash of the wire body. Because every round
 * draws a fresh random salt and a fresh random IV, the same file encrypted twice yields a different body
 * and a different content hash. The consequences are what the protocol actually relies on:
 *
 * <ul>
 *   <li><b>The server cannot tell whether two uploads are the same object.</b> Equal size is necessary but
 *       never sufficient for identity — any two plaintexts of equal length produce equal-size blobs, and the
 *       <i>same</i> plaintext produces a <i>different</i> blob every round. So the relay cannot dedup,
 *       cannot correlate re-uploads of one file across conversations, and cannot confirm that a blob it
 *       already holds is the blob it is being handed again.</li>
 *   <li>This is why invariant <b>R5</b> (one wire blob = one message in one conversation) costs nothing to
 *       honour: there is no dedup to give up.</li>
 *   <li>The only cross-round invariants are the ones computed over the PLAINTEXT — its SHA3-256 (which
 *       travels as {@code unencrypted_content_hash}, <i>inside</i> the message ciphertext, never visible to
 *       the server) and its length.</li>
 * </ul>
 *
 * <p>A regression here would be silent and total: a fixed or derived salt/IV would make the wire body a
 * deterministic function of the plaintext, handing the relay a content-addressable index of everything
 * every user has ever sent — without any decryption and without any error. Nothing else in the suite fails
 * if that happens, which is precisely why this test exists (see VB-29, {@code Messages b6e4ecb}, for the
 * same class of defect caught in the conversation-key generator).
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class StreamEncryptNonDeterminismTest {

    private static final String VERSION = "v0_2_a_stream_gcm";
    private static final String SCOPE = "attachment-content";
    private static final int BUFFER_EXPONENT = 16;

    /** The same key for both rounds — the divergence must come from salt/IV, not from a different secret. */
    private static final String KEY = "conversation-symmetric-key-fixed-for-this-test";

    @BeforeAll
    public static void registerBC() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /** One encryption round's outputs. (Java 11 module — no records.) */
    private static final class Round {

        private final String plaintextHash;
        private final StreamEncryptedDescriptor sed;
        private final byte[] wire;

        Round(String plaintextHash, StreamEncryptedDescriptor sed, byte[] wire) {
            this.plaintextHash = plaintextHash;
            this.sed = sed;
            this.wire = wire;
        }

        String plaintextHash() {
            return plaintextHash;
        }

        StreamEncryptedDescriptor sed() {
            return sed;
        }

        byte[] wire() {
            return wire;
        }
    }

    private static Round encryptOnce(byte[] plaintext) throws Exception {
        ByteArrayOutputStream wire = new ByteArrayOutputStream();
        AbstractMap.SimpleImmutableEntry<String, StreamEncryptedDescriptor> res
                = TkmEncryptionUtils.streamPasswordEncrypt(
                        KEY, SCOPE, VERSION,
                        new ByteArrayInputStream(plaintext), wire,
                        BUFFER_EXPONENT, new AtomicLong());
        return new Round(res.getKey(), res.getValue(), wire.toByteArray());
    }

    @Test
    public void sameplaintext_twice_producesDifferentCiphertext() throws Exception {
        byte[] plaintext = "the same bytes, encrypted twice under the same key".getBytes("UTF-8");

        Round a = encryptOnce(plaintext);
        Round b = encryptOnce(plaintext);

        // --- what MUST differ: everything derived from the random inputs -------------------------------
        assertNotEquals(a.sed().getSalt(), b.sed().getSalt(),
                "a fresh salt per round is what makes the ciphertext unpredictable");
        assertNotEquals(a.sed().getIv(), b.sed().getIv(),
                "a fresh GCM nonce per round; reusing one under the same key is catastrophic for GCM");
        assertNotEquals(a.sed().getEncryptedContentHash(), b.sed().getEncryptedContentHash(),
                "the content hash addresses the CIPHERTEXT, so it must not be stable across rounds");
        assertFalse(java.util.Arrays.equals(a.wire(), b.wire()),
                "THE load-bearing property: the same plaintext must never produce the same wire body");

        // --- what MUST stay equal: everything computed over the PLAINTEXT -----------------------------
        assertEquals(a.plaintextHash(), b.plaintextHash(),
                "the plaintext hash is the only cross-round identity; it is what uch carries end to end");
        assertEquals(a.wire().length, b.wire().length,
                "size is a function of plaintext length and encoding only — it leaks length, nothing more");
    }

    @Test
    public void equalSize_doesNotImplyEqualContent() throws Exception {
        // Two DIFFERENT plaintexts of identical length: the server sees two same-size blobs and, by design,
        // has no way to tell them apart from two encryptions of one file.
        byte[] one = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA".getBytes("UTF-8");
        byte[] two = "BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB".getBytes("UTF-8");
        assertEquals(one.length, two.length, "fixture precondition: equal plaintext length");

        Round a = encryptOnce(one);
        Round b = encryptOnce(two);

        assertEquals(a.wire().length, b.wire().length,
                "equal-length plaintexts give equal-size blobs — size carries no identity");
        assertNotEquals(a.plaintextHash(), b.plaintextHash(),
                "the plaintexts genuinely differ");
        assertNotEquals(a.sed().getEncryptedContentHash(), b.sed().getEncryptedContentHash(),
                "and so do the blobs — but the server cannot use size to learn any of this");
    }

    @Test
    public void roundTrip_stillWorks_underFreshSaltAndIv() throws Exception {
        // Positive control: the non-determinism above must not be achieved by breaking decryption.
        // Without this, a pipeline that emitted random garbage would pass both tests above.
        byte[] plaintext = "round-trip control payload".getBytes("UTF-8");

        Round a = encryptOnce(plaintext);
        ByteArrayOutputStream back = new ByteArrayOutputStream();
        TkmEncryptionUtils.streamPasswordDecrypt(
                KEY, a.sed(), VERSION,
                new ByteArrayInputStream(a.wire()), back,
                BUFFER_EXPONENT, new AtomicLong(), a.plaintextHash());

        assertArrayEquals(plaintext, back.toByteArray(),
                "the descriptor carries the salt and IV, so a fresh pair per round is still decryptable");
    }
}
