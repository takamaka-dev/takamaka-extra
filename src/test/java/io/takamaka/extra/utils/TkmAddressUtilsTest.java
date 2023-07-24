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

import io.takamaka.extra.beans.CompactAddressBean;
import io.takamaka.extra.identicon.exceptions.AddressNotRecognizedException;
import io.takamaka.extra.identicon.exceptions.AddressNullException;
import io.takamaka.extra.identicon.exceptions.AddressFunctionUnsupportedException;
import io.takamaka.extra.identicon.exceptions.AddressTooLongException;
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
        log.info("test constructor");
    }

    @BeforeAll
    public static void setUpClass() {
        log.info("test BeforeClass");
    }

    @AfterAll
    public static void tearDownClass() {
        log.info("test AfterClass");
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

}
