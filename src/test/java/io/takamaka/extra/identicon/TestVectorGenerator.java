/*
 * Copyright 2025 AiliA SA.
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
package io.takamaka.extra.identicon;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.IdenticonException;
import io.takamaka.extra.utils.TkmAddressUtils;
import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

/**
 * Generates JSON test vectors for cross-platform validation of identicon generation.
 * The generated JSON file can be used by Flutter/Dart implementations to verify
 * byte-for-byte identical output.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TestVectorGenerator {

    private static final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Main method for command-line execution.
     */
    public static void main(String[] args) throws IOException {
        TestVectorGenerator generator = new TestVectorGenerator();
        generator.generateTestVectors();
    }

    // Test addresses for identicon generation
    // Ed25519 addresses are 44 chars (32 bytes base64url encoded)
    private static final String[] TEST_ADDRESSES = {
        "yzrhYG_yVL_Cswdg6tiTEx0nTKSPwcfd75J4BP2n0C4.",  // Ed25519 (44 chars)
        "WsJLuVzgJuTzZ8qGLSXiGZGKqX_-YiKvz3kXm1N0L2o.",  // Ed25519 (44 chars)
        "abcdefABCDEF0123456789_-abcdefABCDEF0123456.",  // Ed25519 pattern (44 chars)
        "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",  // All A's (44 chars)
        "________________________________________________",  // 48 underscores - will be undefined type
        "00000000000000000000000000000000000000000000",  // All zeros (44 chars)
    };

    // Test hex strings for color extraction (6 chars each)
    private static final String[] COLOR_HEX_VECTORS = {
        "000000",  // Black fg, white bg
        "ffffff",  // White fg (but all > 220, so black bg)
        "ff0000",  // Red fg, white bg
        "00ff00",  // Green fg, white bg
        "0000ff",  // Blue fg, white bg
        "dddddd",  // All 221 - edge case (black bg)
        "dcdcdc",  // All 220 - edge case (white bg)
        "123456",  // Random color
        "abcdef",  // Random color
        "fedcba",  // Light colors (will have black bg)
    };

    // Test hex strings for rotation extraction (2 chars each)
    private static final String[] ROTATION_HEX_VECTORS = {
        "00", "01", "02", "03", "04", "05", "06", "07",
        "08", "09", "0a", "0b", "0c", "0d", "0e", "0f",
        "10", "20", "30", "40", "50", "60", "70", "80",
        "90", "a0", "b0", "c0", "d0", "e0", "f0", "ff",
    };

    // Test hex strings for 32x32 block generation (3 chars)
    private static final String[] BLOCK_32_HEX_VECTORS = {
        "000", "100", "200", "300", "400", "500", "600", "700",
        "800", "900", "a00", "b00", "c00", "d00", "e00", "f00",
        "0ff", "1ff", "abc", "def", "123", "456", "789", "fed",
    };

    // Test hex strings for full identicon (64 chars - SHA3-256 output)
    private static final String[] FULL_HEX_VECTORS = {
        "a5907fa2fc0fcb336d648805be46da11ab850e5c44934279c921a855ed50825a",
        "0000000000000000000000000000000000000000000000000000000000000000",
        "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff",
        "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0",
        "fedcba9876543210fedcba9876543210fedcba9876543210fedcba9876543210",
    };

    @Test
    public void generateTestVectors() throws IOException {
        log.info("Starting test vector generation...");

        ObjectNode root = mapper.createObjectNode();
        root.put("version", "1.0.0");
        root.put("generator", "TestVectorGenerator.java");
        root.put("generated_at", java.time.Instant.now().toString());
        root.put("png_encoding_note",
                "PNG hashes are Java-reference-only (platform-specific encoding). "
                + "Use matrix hashes for cross-platform validation instead of PNG hashes.");

        // 1. Generate base blocks (16 blocks, 16x16 matrices)
        root.set("base_blocks", generateBaseBlocks());
        log.info("Generated base blocks");

        // 2. Generate rotated blocks (16 blocks x 4 rotations)
        root.set("rotated_blocks", generateRotatedBlocks());
        log.info("Generated rotated blocks");

        // 3. Generate color extraction vectors
        root.set("color_extraction_vectors", generateColorVectors());
        log.info("Generated color extraction vectors");

        // 4. Generate rotation extraction vectors
        root.set("rotation_extraction_vectors", generateRotationVectors());
        log.info("Generated rotation extraction vectors");

        // 5. Generate matrix transformation vectors
        root.set("matrix_transformation_vectors", generateMatrixTransformationVectors());
        log.info("Generated matrix transformation vectors");

        // 6. Generate 32x32 block vectors
        root.set("block_32_vectors", generateBlock32Vectors());
        log.info("Generated 32x32 block vectors");

        // 7. Generate full identicon vectors
        root.set("identicon_vectors", generateIdenticonVectors());
        log.info("Generated identicon vectors");

        // 8. Generate address utility vectors
        root.set("address_utility_vectors", generateAddressUtilityVectors());
        log.info("Generated address utility vectors");

        // Write to file
        Path outputPath = Paths.get("src/test/resources/java_identicon_test_vectors.json");
        Files.createDirectories(outputPath.getParent());
        mapper.writeValue(outputPath.toFile(), root);

        log.info("Test vectors written to: " + outputPath.toAbsolutePath());
    }

    private ArrayNode generateBaseBlocks() {
        ArrayNode blocksArray = mapper.createArrayNode();

        for (int blockIndex = 0; blockIndex < IdentiBaseBlocks.BLOCKS.length; blockIndex++) {
            ObjectNode blockNode = mapper.createObjectNode();
            blockNode.put("block_index", blockIndex);
            blockNode.set("matrix", matrixToArrayNode(IdentiBaseBlocks.BLOCKS[blockIndex]));
            blockNode.put("matrix_hash", computeMatrixHash(IdentiBaseBlocks.BLOCKS[blockIndex]));
            blocksArray.add(blockNode);
        }

        return blocksArray;
    }

    private ArrayNode generateRotatedBlocks() {
        ArrayNode rotatedArray = mapper.createArrayNode();
        int[][][][] blocks = IdenticonManager.i().getBlocks();

        for (int blockIndex = 0; blockIndex < blocks.length; blockIndex++) {
            for (int rotationIndex = 0; rotationIndex < 4; rotationIndex++) {
                ObjectNode rotatedNode = mapper.createObjectNode();
                rotatedNode.put("block_index", blockIndex);
                rotatedNode.put("rotation_index", rotationIndex);
                rotatedNode.put("rotation_name", getRotationName(rotationIndex));
                rotatedNode.set("matrix", matrixToArrayNode(blocks[blockIndex][rotationIndex]));
                rotatedNode.put("matrix_hash", computeMatrixHash(blocks[blockIndex][rotationIndex]));
                rotatedArray.add(rotatedNode);
            }
        }

        return rotatedArray;
    }

    private String getRotationName(int index) {
        switch (index) {
            case 0: return "original";
            case 1: return "mirror_vertical";
            case 2: return "mirror_horizontal";
            case 3: return "mirror_h_plus_v";
            default: return "unknown";
        }
    }

    private ArrayNode generateColorVectors() {
        ArrayNode colorArray = mapper.createArrayNode();

        for (String hex : COLOR_HEX_VECTORS) {
            ObjectNode colorNode = mapper.createObjectNode();
            colorNode.put("input_hex", hex);

            Color fgColor = IdentiColorHelper.fgColorFromHex(hex);
            ObjectNode fgNode = mapper.createObjectNode();
            fgNode.put("r", fgColor.getRed());
            fgNode.put("g", fgColor.getGreen());
            fgNode.put("b", fgColor.getBlue());
            colorNode.set("foreground_color", fgNode);

            Color bgColor = IdentiColorHelper.bgColorFromHex(hex);
            ObjectNode bgNode = mapper.createObjectNode();
            bgNode.put("r", bgColor.getRed());
            bgNode.put("g", bgColor.getGreen());
            bgNode.put("b", bgColor.getBlue());
            colorNode.set("background_color", bgNode);

            colorArray.add(colorNode);
        }

        return colorArray;
    }

    private ArrayNode generateRotationVectors() {
        ArrayNode rotationArray = mapper.createArrayNode();

        for (String hex : ROTATION_HEX_VECTORS) {
            ObjectNode rotationNode = mapper.createObjectNode();
            rotationNode.put("input_hex", hex);

            int[] rotation = IdentiColorHelper.getRotation(hex);
            ArrayNode rotArray = mapper.createArrayNode();
            for (int r : rotation) {
                rotArray.add(r);
            }
            rotationNode.set("rotation_indices", rotArray);

            rotationArray.add(rotationNode);
        }

        return rotationArray;
    }

    private ArrayNode generateMatrixTransformationVectors() {
        ArrayNode transformArray = mapper.createArrayNode();

        // Use block 0 as a test case for matrix transformations
        int[][] original = IdentiBaseBlocks.BLOCKS[0];

        ObjectNode transformNode = mapper.createObjectNode();
        transformNode.put("description", "Matrix transformations on block 0");
        transformNode.set("original", matrixToArrayNode(original));
        transformNode.put("original_hash", computeMatrixHash(original));

        int[][] cloned = IdentiColorHelper.clone(original);
        transformNode.set("cloned", matrixToArrayNode(cloned));
        transformNode.put("cloned_hash", computeMatrixHash(cloned));

        int[][] mirrorV = IdentiColorHelper.mirrorVertical(original);
        transformNode.set("mirror_vertical", matrixToArrayNode(mirrorV));
        transformNode.put("mirror_vertical_hash", computeMatrixHash(mirrorV));

        int[][] mirrorH = IdentiColorHelper.mirrorHorizontal(original);
        transformNode.set("mirror_horizontal", matrixToArrayNode(mirrorH));
        transformNode.put("mirror_horizontal_hash", computeMatrixHash(mirrorH));

        int[][] mirrorHV = IdentiColorHelper.mirrorHplusV(original);
        transformNode.set("mirror_h_plus_v", matrixToArrayNode(mirrorHV));
        transformNode.put("mirror_h_plus_v_hash", computeMatrixHash(mirrorHV));

        // Test merge4Square with 4 copies of block 0
        int[][] merged = IdentiColorHelper.merge4Square(
            IdentiColorHelper.clone(original),
            IdentiColorHelper.mirrorVertical(original),
            IdentiColorHelper.mirrorHplusV(original),
            IdentiColorHelper.mirrorHorizontal(original)
        );
        transformNode.set("merged_4_square", matrixToArrayNode(merged));
        transformNode.put("merged_4_square_hash", computeMatrixHash(merged));

        transformArray.add(transformNode);

        return transformArray;
    }

    private ArrayNode generateBlock32Vectors() {
        ArrayNode block32Array = mapper.createArrayNode();

        for (String hex : BLOCK_32_HEX_VECTORS) {
            ObjectNode blockNode = mapper.createObjectNode();
            blockNode.put("input_hex", hex);

            int[][] block32 = IdentiColorHelper.get32by32SquareBlock(hex);
            blockNode.set("matrix", matrixToArrayNode(block32));
            blockNode.put("matrix_hash", computeMatrixHash(block32));
            blockNode.put("size", 32);

            block32Array.add(blockNode);
        }

        return block32Array;
    }

    private ArrayNode generateIdenticonVectors() {
        ArrayNode identiconArray = mapper.createArrayNode();

        // Generate from test addresses
        for (String address : TEST_ADDRESSES) {
            try {
                ObjectNode identiconNode = mapper.createObjectNode();
                identiconNode.put("input_type", "address");
                identiconNode.put("input_address", address);

                // Compute SHA3-256 hash
                String hash256Hex = TkmSignUtils.Hash256ToHex(address);
                identiconNode.put("hash_256_hex", hash256Hex);

                // Generate 256x256 matrix
                int[][] matrix256 = IdentiColorHelper.get256by256SquareBlockHIRND(hash256Hex.substring(0, 60));
                identiconNode.put("matrix_256_hash", computeMatrixHash(matrix256));

                // Extract colors
                String colorHex = hash256Hex.substring(58, 64);
                Color fg = IdentiColorHelper.fgColorFromHex(colorHex);
                Color bg = IdentiColorHelper.bgColorFromHex(colorHex);

                ObjectNode colorsNode = mapper.createObjectNode();
                colorsNode.put("color_hex", colorHex);
                colorsNode.put("fg_r", fg.getRed());
                colorsNode.put("fg_g", fg.getGreen());
                colorsNode.put("fg_b", fg.getBlue());
                colorsNode.put("bg_r", bg.getRed());
                colorsNode.put("bg_g", bg.getGreen());
                colorsNode.put("bg_b", bg.getBlue());
                identiconNode.set("colors", colorsNode);

                // Generate PNG and compute hash
                BufferedImage image = IdentiColorHelper.getAvatarByString256(address);
                String pngHash = computeImageHash(image);
                identiconNode.put("png_sha256", pngHash);
                identiconNode.put("png_hash_note",
                        "Java-reference-only (platform-specific encoding)");

                identiconArray.add(identiconNode);

            } catch (IdenticonException | HashEncodeException |
                     HashAlgorithmNotFoundException | HashProviderNotFoundException e) {
                log.error("Error generating identicon for: " + address, e);
            }
        }

        // Generate from raw hex strings
        for (String hex : FULL_HEX_VECTORS) {
            try {
                ObjectNode identiconNode = mapper.createObjectNode();
                identiconNode.put("input_type", "hex");
                identiconNode.put("input_hex", hex);

                // Generate 256x256 matrix
                int[][] matrix256 = IdentiColorHelper.get256by256SquareBlockHIRND(hex.substring(0, 60));
                identiconNode.put("matrix_256_hash", computeMatrixHash(matrix256));

                // Extract colors
                String colorHex = hex.substring(58, 64);
                Color fg = IdentiColorHelper.fgColorFromHex(colorHex);
                Color bg = IdentiColorHelper.bgColorFromHex(colorHex);

                ObjectNode colorsNode = mapper.createObjectNode();
                colorsNode.put("color_hex", colorHex);
                colorsNode.put("fg_r", fg.getRed());
                colorsNode.put("fg_g", fg.getGreen());
                colorsNode.put("fg_b", fg.getBlue());
                colorsNode.put("bg_r", bg.getRed());
                colorsNode.put("bg_g", bg.getGreen());
                colorsNode.put("bg_b", bg.getBlue());
                identiconNode.set("colors", colorsNode);

                // Generate PNG and compute hash
                BufferedImage image = IdentiColorHelper.getAvatarByHex(hex);
                String pngHash = computeImageHash(image);
                identiconNode.put("png_sha256", pngHash);
                identiconNode.put("png_hash_note",
                        "Java-reference-only (platform-specific encoding)");

                identiconArray.add(identiconNode);

            } catch (IdenticonException e) {
                log.error("Error generating identicon for hex: " + hex, e);
            }
        }

        return identiconArray;
    }

    private ArrayNode generateAddressUtilityVectors() {
        ArrayNode addressArray = mapper.createArrayNode();

        for (String address : TEST_ADDRESSES) {
            try {
                ObjectNode addressNode = mapper.createObjectNode();
                addressNode.put("input_address", address);
                addressNode.put("address_length", address.length());

                CompactAddressBean cab = TkmAddressUtils.toCompactAddress(address);
                addressNode.put("type", cab.getType().name());
                addressNode.put("original", cab.getOriginal());
                if (cab.getDefaultShort() != null) {
                    addressNode.put("default_short", cab.getDefaultShort());
                }

                addressArray.add(addressNode);

            } catch (AddressNotRecognizedException | AddressTooLongException e) {
                log.error("Error processing address: " + address, e);
            }
        }

        // Add specific test cases for address type detection
        ObjectNode ed25519Test = mapper.createObjectNode();
        ed25519Test.put("description", "Ed25519 address detection (44 chars, 32 bytes decoded)");
        ed25519Test.put("expected_length", 44);
        ed25519Test.put("expected_decoded_bytes", 32);
        ed25519Test.put("expected_type", "ed25519");
        addressArray.add(ed25519Test);

        ObjectNode qteslaTest = mapper.createObjectNode();
        qteslaTest.put("description", "QTESLA address detection (19840 chars, 14880 bytes decoded)");
        qteslaTest.put("expected_length", 19840);
        qteslaTest.put("expected_decoded_bytes", 14880);
        qteslaTest.put("expected_type", "qTesla");
        addressArray.add(qteslaTest);

        return addressArray;
    }

    private ArrayNode matrixToArrayNode(int[][] matrix) {
        ArrayNode matrixArray = mapper.createArrayNode();
        for (int[] row : matrix) {
            ArrayNode rowArray = mapper.createArrayNode();
            for (int value : row) {
                rowArray.add(value);
            }
            matrixArray.add(rowArray);
        }
        return matrixArray;
    }

    private String computeMatrixHash(int[][] matrix) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder sb = new StringBuilder();
            for (int[] row : matrix) {
                for (int value : row) {
                    sb.append(value).append(",");
                }
                sb.append("\n");
            }
            byte[] hash = digest.digest(sb.toString().getBytes());
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            log.error("SHA-256 not available", e);
            return null;
        }
    }

    private String computeImageHash(BufferedImage image) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(imageBytes);
            return bytesToHex(hash);
        } catch (IOException | NoSuchAlgorithmException e) {
            log.error("Error computing image hash", e);
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
