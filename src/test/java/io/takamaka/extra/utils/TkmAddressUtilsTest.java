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

import com.fasterxml.jackson.core.JsonProcessingException;
import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.beans.EncMessageBean;
import io.takamaka.extra.identicon.exceptions.AddressDecodeException;
import io.takamaka.extra.identicon.exceptions.AddressEncodeException;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressNullException;
import io.takamaka.extra.identicon.exceptions.AddressFunctionUnsupportedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
import io.takamaka.extra.seed.DeterministicSeedGenerator;
import io.takamaka.extra.seed.DeterministicSeedGeneratorInterface;
import static io.takamaka.extra.utils.TestEnvObjects.REF_TRX_QTESLA;
import io.takamaka.wallet.exceptions.WalletException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class TkmAddressUtilsTest {

    public TkmAddressUtilsTest() {
        Configurator.setLevel("io.takamaka.extra.utils.AddressUtilsTest", Level.DEBUG);
        Configurator.setLevel("io.takamaka.extra.utils.TkmAddressUtilsTest", Level.DEBUG);
        log.info("test constructor");
    }

    @BeforeAll
    public static void setUpClass() throws IOException {
        log.info("test BeforeClass");
        log.info("load qtesla transactions");
        //REF_TRX_QTESLA
        for (Map.Entry<String, String> trx : REF_TRX_QTESLA.entrySet()) {
            String key = trx.getKey();
            //String val = trx.getValue();
            InputStream appProp = Thread.currentThread().getContextClassLoader().getResourceAsStream(key + ".json");
            REF_TRX_QTESLA.put(key, new String(appProp.readAllBytes(), StandardCharsets.UTF_8));
            log.info("loaded " + trx.getKey());
        }
        log.info("all loaded");
    }

    @AfterAll
    public static void tearDownClass() {
        log.info("test AfterClass");
        Configurator.setLevel("io.takamaka.extra.utils.AddressUtilsTest", Level.INFO);
    }

    @BeforeEach
    public void setUp() {
        log.info("test Before");
    }

    @AfterEach
    public void tearDown() {
        log.info("test After");
    }

    /**
     * Test of toCompactAddress method, of class AddressUtils.
     */
    @Test
    public void testToCompactAddress() throws AddressNullException, AddressNotRecognizedException, AddressFunctionUnsupportedException, AddressTooLongException {
        log.info("toCompactAddress");
        for (String addr : TestEnvObjects.REF_ADDR_ARRAY) {
            CompactAddressBean result = TkmAddressUtils.toCompactAddress(addr);
            assertEquals(addr, result.getOriginal());
            switch (result.getType()) {
                case ed25519:
                    assertNull(result.getDefaultShort());
                    break;
                case qTesla:
                    assertNotNull(result.getDefaultShort());
                    assertEquals(TestEnvObjects.DEFAULT_SHORT.get(addr), result.getDefaultShort());
                    assertEquals(TestEnvObjects.DEFAULT_SHORT_HEX.get(addr), TkmAddressUtils.getBookmarkAddress(result));
                    break;
                case undefined:
                    assertNotNull(result.getDefaultShort());
                    break;
                default:
                    throw new AssertionError();
            }
        }
    }

    @Test
    public void testToHexBookmarksAddress() throws AddressNotRecognizedException, AddressFunctionUnsupportedException, AddressTooLongException {
        for (String originalAddr : TestEnvObjects.REF_ADDR_ARRAY) {
            if (originalAddr.length() == 19840) {
                assertEquals(TestEnvObjects.DEFAULT_SHORT_HEX.get(originalAddr), TkmAddressUtils.getBookmarkAddress(TkmAddressUtils.toCompactAddress(originalAddr)));
            }

        }
    }

    @Test
    public void testNullAddress() {
        for (String originalAddr : TestEnvObjects.REF_ADDR_ARRAY_EMPTY) {
            AddressNotRecognizedException assertThrows = assertThrows("null or zero char", AddressNotRecognizedException.class, () -> {
                TkmAddressUtils.toCompactAddress(originalAddr);
            });
            assertEquals("null or zero char", assertThrows.getMessage());
        }
    }

    @Test
    public void testTooLongAddress() {
        AddressTooLongException assertThrows = assertThrows("expected 19840 or less, found 19862", AddressTooLongException.class, () -> {
            TkmAddressUtils.toCompactAddress(TestEnvObjects.REF_ADDR01_TOO_LONG);
        });
        assertEquals("expected 19840 or less, found 19862", assertThrows.getMessage());

    }

    @Test
    public void testUndefinedAddress() throws AddressNotRecognizedException, AddressFunctionUnsupportedException, AddressTooLongException {
        for (String originalAddr : TestEnvObjects.REF_ADDR_ARRAY_LOREM) {
            CompactAddressBean compactAddress = TkmAddressUtils.toCompactAddress(originalAddr);
            assertEquals(TkmAddressUtils.TypeOfAddress.undefined, compactAddress.getType());
            assertEquals(TestEnvObjects.DEFAULT_UNDEFINED_SHORT.get(originalAddr), compactAddress.getDefaultShort());
            //bookmark
            String bookmarkAddress = TkmAddressUtils.getBookmarkAddress(compactAddress);
            assertEquals(bookmarkAddress.length(), 96);
            assertEquals(TestEnvObjects.DEFAULT_UNDEFINED_SHORT_HEX.get(originalAddr), bookmarkAddress);
        }
    }

    @Test
    public void testSibhRetro() throws AddressNotRecognizedException, AddressFunctionUnsupportedException, AddressTooLongException, AddressDecodeException, AddressEncodeException {
        //Configurator.setLevel("io.takamaka.extra.utils.AddressUtilsTest", Level.DEBUG);
        String hexBH = "69b9619ea29021e5dee7978e789e6cb6369432e6e356176c906d40dfab1dd045aee023e3489946d7293ec7dd6689cd766d6a67b1d9af5a10045ab97133be0c9b";
        testAddr(hexBH);
    }

    @Test
    public void getDeterministicSeedtext() throws WalletException {
        DeterministicSeedGeneratorInterface dsi = new DeterministicSeedGenerator("the_test_seed");
        String seedB64Url = dsi.getSeedB64Url("test", 0, 32);
        String seedB64Url1 = dsi.getSeedB64Url("test", 1, 32);
        String seedB64Url_eq1 = dsi.getSeedB64Url("test", 0, 32);
        //System.err.println(dsi.getSeedB64Url("test", 0, 32));
        assertEquals(seedB64Url, seedB64Url_eq1);
        assertNotEquals(seedB64Url, seedB64Url1);
        assertNotEquals(seedB64Url_eq1, seedB64Url1);
    }

    private void testAddr(String hexBH) throws AddressEncodeException, AddressDecodeException {
        byte[] byteBH = TkmSignUtils.fromHexToByteArray(hexBH);
        log.info("reference: " + Arrays.toString(byteBH));
        String b64urlBH = TkmSignUtils.fromByteArrayToB64URL(byteBH);
        log.info("byte to url: " + b64urlBH);
        String fromB64UrlToHEX = TkmSignUtils.fromB64UrlToHEX(b64urlBH);
        log.info("url to hex : " + fromB64UrlToHEX);
        byte[] b64uToByte = TkmSignUtils.fromB64URLToByteArray(b64urlBH);
        log.info("conv 1   : " + Arrays.toString(b64uToByte));
        String hexBHfromByteBH = TkmSignUtils.fromByteArrayToHexString(b64uToByte);
        log.info("byte to hex: " + fromB64UrlToHEX);
        byte[] byFromHexTobyteToB64ToHex = TkmSignUtils.fromHexToByteArray(hexBHfromByteBH);
        log.info("conv 1   : " + Arrays.toString(byFromHexTobyteToB64ToHex));
        assertArrayEquals(byteBH, b64uToByte);
        assertArrayEquals(byteBH, byFromHexTobyteToB64ToHex);
        assertEquals(hexBH, hexBHfromByteBH);
        assertEquals(hexBH, fromB64UrlToHEX);
        log.info("address utils");
        String fromHexToB64URL = TkmAddressUtils.fromHexToB64URL(hexBH);
        log.info("b64url: " + fromHexToB64URL);
        assertEquals(b64urlBH, fromHexToB64URL);
        String fromB64URLToHex = TkmAddressUtils.fromB64URLToHex(fromHexToB64URL);
        log.info("hex : " + fromB64URLToHex);
        assertEquals(hexBH, fromB64URLToHex);
        //69b9619ea29021e5dee7978e789e6cb6369432e6e356176c906d40dfab1dd045aee023e3489946d7293ec7dd6689cd766d6a67b1d9af5a10045ab97133be0c9b
        //ablhnqKQIeXe55eOeJ5stjaUMubjVhdskG1A36sd0EWu4CPjSJlG1yk-x91mic12bWpnsdmvWhAEWrlxM74Mmw..
    }

    @Test
    public void testEncryption() throws WalletException, JsonProcessingException {
        int ei = 0;
        for (String password : TestEnvObjects.REF_ADDR_ARRAY_LOREM) {
            for (String message : TestEnvObjects.REF_ADDR_ARRAY_LOREM) {
                for (String scope : TestEnvObjects.REF_ADDR_ARRAY_LOREM) {
                    for (EncryptionContext ec : EncryptionContext.values()) {
                        log.info("testing encryption for %s %s %s %s ", password, message, scope, ec.name());
                        log.info(message);
                        EncMessageBean passwordEncryptedContent = TkmEncryptionUtils.toPasswordEncryptedContent(password, message, scope, ec.name());
                        log.info(passwordEncryptedContent.toString());
                        String jsonEMB = SerializerUtils.getJson(passwordEncryptedContent);
                        log.info(jsonEMB);
                        EncMessageBean encMessageBeanFromJson = SerializerUtils.getEncMessageBeanFromJson(jsonEMB);
                        log.info(encMessageBeanFromJson.toString());
                        String decodedMessage = TkmEncryptionUtils.fromPasswordEncryptedContent(password, scope, encMessageBeanFromJson);
                        log.info(decodedMessage);
                        assertEquals(message, decodedMessage);
                    }
                }
            }
        }
    }

}
