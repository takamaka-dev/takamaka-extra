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
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
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
public class AddressUtilsTest {
    
    public AddressUtilsTest() {
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
    public void testToCompactAddress() {
        log.info("toCompactAddress");
        String address = "";
        CompactAddressBean expResult = null;
        CompactAddressBean result = AddressUtils.toCompactAddress(address);
        assertEquals(expResult, result);
        // TODO review the generated test code and remove the default call to fail.
        //fail("The test case is a prototype.");
        assert(true);
    }
    
}
