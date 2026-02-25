/*
 * Test Vector Generator for Cross-Language Compatibility Testing.
 *
 * This class generates deterministic test vectors for verifying Flutter
 * implementations of Takamaka encryption utilities.
 */
package io.takamaka.extra.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.wallet.utils.TkmSignUtils;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.crypto.digests.SHA3Digest;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;

/**
 * Generates JSON test vectors for cross-language encryption compatibility testing.
 * The generated JSON file can be used by Flutter/Dart implementations to verify
 * identical cryptographic output.
 *
 * Test vectors are deterministic where possible (fixed IV/nonce) to allow
 * exact comparison between Java and Flutter implementations.
 */
@Slf4j
public class TestVectorGenerator {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    static {
        Security.addProvider(new org.bouncycastle.jce.provider.BouncyCastleProvider());
    }

    /**
     * Main method for command-line execution.
     */
    public static void main(String[] args) throws Exception {
        TestVectorGenerator generator = new TestVectorGenerator();
        generator.generateTestVectors();
    }

    @Test
    public void generateTestVectors() throws Exception {
        log.info("Starting encryption test vector generation...");

        ObjectNode root = mapper.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generator", "TestVectorGenerator.java");
        root.put("generated_at", java.time.Instant.now().toString());

        root.set("pbkdf2_vectors", generatePBKDF2Vectors());
        log.info("Generated PBKDF2 vectors");

        root.set("sha3_256_vectors", generateSHA3256Vectors());
        log.info("Generated SHA3-256 vectors");

        root.set("aes_cbc_vectors", generateAesCbcVectors());
        log.info("Generated AES-CBC vectors");

        root.set("aes_gcm_vectors", generateAesGcmVectors());
        log.info("Generated AES-GCM vectors");

        root.set("round_trip_vectors", generateEncryptDecryptRoundTripVectors());
        log.info("Generated round-trip vectors");

        // Write to file
        Path outputPath = Paths.get("src/test/resources/java_encryption_test_vectors.json");
        Files.createDirectories(outputPath.getParent());
        mapper.writeValue(outputPath.toFile(), root);

        log.info("Encryption test vectors written to: " + outputPath.toAbsolutePath());
    }

    /**
     * Generate PBKDF2-HMAC-SHA512 key derivation test vectors.
     */
    private ArrayNode generatePBKDF2Vectors() throws Exception {
        ArrayNode vectorsArray = mapper.createArrayNode();

        String[][] testCases = {
            {"password", "salt", "20000", "256"},
            {"test-password-12345", "test-scope", "20000", "256"},
            {"complex!@#$%^&*()password", "unicode-scope-日本語", "20000", "256"},
            {"symmetric-key-400chars-abcdefghijklmnopqrstuvwxyz", "conversation-salt-32chars", "20000", "256"},
        };

        for (String[] tc : testCases) {
            String password = tc[0];
            String salt = tc[1];
            int iterations = Integer.parseInt(tc[2]);
            int keyLengthBits = Integer.parseInt(tc[3]);

            PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt.getBytes(StandardCharsets.UTF_8),
                iterations,
                keyLengthBits
            );
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
            byte[] key = skf.generateSecret(spec).getEncoded();

            ObjectNode vectorNode = mapper.createObjectNode();
            vectorNode.put("password", password);
            vectorNode.put("salt", salt);
            vectorNode.put("iterations", iterations);
            vectorNode.put("keyLengthBits", keyLengthBits);
            vectorNode.put("derivedKey_hex", bytesToHex(key));
            vectorNode.put("derivedKey_b64url", TkmSignUtils.fromByteArrayToB64URL(key));

            vectorsArray.add(vectorNode);
        }

        return vectorsArray;
    }

    /**
     * Generate SHA3-256 hash test vectors.
     */
    private ArrayNode generateSHA3256Vectors() throws Exception {
        ArrayNode vectorsArray = mapper.createArrayNode();

        String[] inputs = {
            "Hello, World!",
            "Test input string",
            "My Conversation",
            "unicode-日本語-\uD83D\uDE00",
            "The quick brown fox jumps over the lazy dog",
        };

        for (String input : inputs) {
            SHA3Digest digest = new SHA3Digest(256);
            byte[] inputBytes = input.getBytes(StandardCharsets.UTF_8);
            digest.update(inputBytes, 0, inputBytes.length);
            byte[] hash = new byte[digest.getDigestSize()];
            digest.doFinal(hash, 0);

            ObjectNode vectorNode = mapper.createObjectNode();
            vectorNode.put("input", input);
            vectorNode.put("hash_hex", bytesToHex(hash));
            vectorNode.put("hash_b64url", TkmSignUtils.fromByteArrayToB64URL(hash));

            vectorsArray.add(vectorNode);
        }

        return vectorsArray;
    }

    /**
     * Generate AES-CBC encryption test vectors with fixed IV.
     */
    private ArrayNode generateAesCbcVectors() throws Exception {
        ArrayNode vectorsArray = mapper.createArrayNode();

        // TEST ONLY - DO NOT USE FIXED IVs IN PRODUCTION
        vectorsArray.add(generateAesCbcVector(
            "test-password-12345",
            "Hello, World!",
            "test-scope",
            hexToBytes("00112233445566778899aabbccddeeff") // TEST ONLY - DO NOT USE IN PRODUCTION
        ));

        // TEST ONLY - DO NOT USE FIXED IVs IN PRODUCTION
        vectorsArray.add(generateAesCbcVector(
            "unicode-password",
            "Hello, \uD83D\uDE00 World! 中文",
            "unicode-scope",
            hexToBytes("112233445566778899aabbccddeeff00") // TEST ONLY - DO NOT USE IN PRODUCTION
        ));

        // TEST ONLY - DO NOT USE FIXED IVs IN PRODUCTION
        vectorsArray.add(generateAesCbcVector(
            "long-content-password",
            "This is a longer message that spans multiple AES blocks to test proper CBC chaining.",
            "long-scope",
            hexToBytes("ffeeddccbbaa99887766554433221100") // TEST ONLY - DO NOT USE IN PRODUCTION
        ));

        return vectorsArray;
    }

    private ObjectNode generateAesCbcVector(String password, String content, String scope, byte[] iv) throws Exception {
        // Derive key using PBKDF2
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            scope.getBytes(StandardCharsets.UTF_8),
            20000,
            256
        );
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        byte[] key = skf.generateSecret(spec).getEncoded();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        // Encrypt with AES-CBC
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv));
        byte[] ciphertext = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        ObjectNode vectorNode = mapper.createObjectNode();
        vectorNode.put("password", password);
        vectorNode.put("content", content);
        vectorNode.put("scope", scope);
        vectorNode.put("iv_hex", bytesToHex(iv));
        vectorNode.put("iv_b64url", TkmSignUtils.fromByteArrayToB64URL(iv));
        vectorNode.put("ciphertext_hex", bytesToHex(ciphertext));
        vectorNode.put("ciphertext_b64url", TkmSignUtils.fromByteArrayToB64URL(ciphertext));
        vectorNode.put("derivedKey_hex", bytesToHex(key));
        vectorNode.put("fixed_iv_warning", "TEST ONLY - fixed IV used for deterministic test vectors");

        return vectorNode;
    }

    /**
     * Generate AES-GCM encryption test vectors with fixed nonce.
     */
    private ArrayNode generateAesGcmVectors() throws Exception {
        ArrayNode vectorsArray = mapper.createArrayNode();

        // TEST ONLY - DO NOT USE FIXED NONCES IN PRODUCTION
        vectorsArray.add(generateAesGcmVector(
            "test-password-12345",
            "Hello, World!",
            "test-salt-32chars-for-key-deriv",
            hexToBytes("000102030405060708090a0b") // TEST ONLY - DO NOT USE IN PRODUCTION
        ));

        // TEST ONLY - DO NOT USE FIXED NONCES IN PRODUCTION
        vectorsArray.add(generateAesGcmVector(
            "gcm-password",
            "This is authenticated encrypted content using AES-GCM mode.",
            "gcm-salt-32-characters-exactly!",
            hexToBytes("0f0e0d0c0b0a09080706050f") // TEST ONLY - DO NOT USE IN PRODUCTION
        ));

        return vectorsArray;
    }

    private ObjectNode generateAesGcmVector(String password, String content, String salt, byte[] nonce) throws Exception {
        // Derive key using PBKDF2
        PBEKeySpec spec = new PBEKeySpec(
            password.toCharArray(),
            salt.getBytes(StandardCharsets.UTF_8),
            20000,
            256
        );
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA512");
        byte[] key = skf.generateSecret(spec).getEncoded();
        SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

        // Encrypt with AES-GCM (128-bit tag)
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding", "BC");
        GCMParameterSpec gcmSpec = new GCMParameterSpec(128, nonce);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
        byte[] ciphertextWithTag = cipher.doFinal(content.getBytes(StandardCharsets.UTF_8));

        ObjectNode vectorNode = mapper.createObjectNode();
        vectorNode.put("password", password);
        vectorNode.put("content", content);
        vectorNode.put("salt", salt);
        vectorNode.put("nonce_hex", bytesToHex(nonce));
        vectorNode.put("nonce_b64url", TkmSignUtils.fromByteArrayToB64URL(nonce));
        vectorNode.put("ciphertextWithTag_hex", bytesToHex(ciphertextWithTag));
        vectorNode.put("ciphertextWithTag_b64url", TkmSignUtils.fromByteArrayToB64URL(ciphertextWithTag));
        vectorNode.put("derivedKey_hex", bytesToHex(key));
        vectorNode.put("fixed_nonce_warning", "TEST ONLY - fixed nonce used for deterministic test vectors");

        return vectorNode;
    }

    /**
     * Generate round-trip encryption/decryption test vectors using TkmEncryptionUtils.
     */
    private ArrayNode generateEncryptDecryptRoundTripVectors() throws Exception {
        ArrayNode vectorsArray = mapper.createArrayNode();

        String[][] testCases = {
            {"test-password", "Hello, World!", "test-scope"},
            {"symmetric-key-for-conversation", "This is a chat message content.", "conversation-salt"},
            {"unicode-key-日本語", "Unicode content: 日本語 \uD83D\uDE00", "unicode-scope-\uD83C\uDF89"},
        };

        for (String[] tc : testCases) {
            String password = tc[0];
            String content = tc[1];
            String scope = tc[2];

            EncMessageBean encrypted = TkmEncryptionUtils.toPasswordEncryptedContent(
                password, content, scope, "v0_1_a"
            );

            // Verify decryption works
            String decrypted = TkmEncryptionUtils.fromPasswordEncryptedContent(password, scope, encrypted);

            ObjectNode vectorNode = mapper.createObjectNode();
            vectorNode.put("password", password);
            vectorNode.put("content", content);
            vectorNode.put("scope", scope);
            vectorNode.put("tkVersion", encrypted.getTkVersion());
            vectorNode.put("passwordHashAlgorithm", encrypted.getPasswordHashAlgorithm());
            vectorNode.put("iterations", encrypted.getIterations());
            vectorNode.put("transformation", encrypted.getTransformation());
            vectorNode.put("keySpecAlgorithm", encrypted.getKeySpecAlgorithm());
            vectorNode.put("outputKeyLengthBit", encrypted.getOutputKeyLengthBit());
            vectorNode.put("encoding", encrypted.getEncoding());
            vectorNode.put("iv_b64url", encrypted.getEncryptedMessage()[0]);
            vectorNode.put("ciphertext_b64url", encrypted.getEncryptedMessage()[1]);
            vectorNode.put("decrypted", decrypted);
            vectorNode.put("matches", content.equals(decrypted));

            vectorsArray.add(vectorNode);
        }

        return vectorsArray;
    }

    // Utility methods
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
