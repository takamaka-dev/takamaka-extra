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
package io.takamaka.extra.identicon;

import java.awt.Color;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Unit + determinism tests for Color Scheme v2.0.
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public class IdentiColorSchemeV2Test {

    @Test
    public void hslPrimaries_matchKnownValues() {
        assertArrayEquals(new int[]{255, 0, 0}, IdentiColorSchemeV2.hslToRgb(0, 1.0, 0.5), "pure red");
        assertArrayEquals(new int[]{0, 255, 0}, IdentiColorSchemeV2.hslToRgb(120, 1.0, 0.5), "pure green");
        assertArrayEquals(new int[]{0, 0, 255}, IdentiColorSchemeV2.hslToRgb(240, 1.0, 0.5), "pure blue");
    }

    @Test
    public void hslExtremes_blackAndWhite() {
        assertArrayEquals(new int[]{0, 0, 0}, IdentiColorSchemeV2.hslToRgb(0, 0.0, 0.0), "black");
        assertArrayEquals(new int[]{255, 255, 255}, IdentiColorSchemeV2.hslToRgb(0, 0.0, 1.0), "white");
    }

    @Test
    public void composeOverBlack_halvesChannels() {
        // round(255 * 0.5) = 128
        Color out = IdentiColorSchemeV2.composeOverBlack(new Color(255, 0, 0), 0.5);
        assertEquals(new Color(128, 0, 0), out);
        // alpha 0 collapses to black, alpha 1 is identity
        assertEquals(Color.BLACK, IdentiColorSchemeV2.composeOverBlack(new Color(10, 20, 30), 0.0));
        assertEquals(new Color(10, 20, 30), IdentiColorSchemeV2.composeOverBlack(new Color(10, 20, 30), 1.0));
    }

    @Test
    public void hue_isQuantisedAndInRange() {
        String hash = "a5907fa2fc0fcb336d648805be46da11ab850e5c44934279c921a855ed50825a";
        int bucket = IdentiColorSchemeV2.hueBucketFromHash(hash);
        int hue = IdentiColorSchemeV2.hueFromHash(hash);
        assertTrue(bucket >= 0 && bucket < IdentiColorSchemeV2.HUE_BUCKETS, "bucket in range");
        assertTrue(hue >= 0 && hue < 360, "hue in range");
        assertEquals(0, hue % (360 / IdentiColorSchemeV2.HUE_BUCKETS), "hue snapped to a bucket stop");
    }

    @Test
    public void walletColor_isDeterministic() {
        String hash = "123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef0";
        assertEquals(IdentiColorSchemeV2.walletColor(hash), IdentiColorSchemeV2.walletColor(hash),
                "same hash → identical colour");
    }

    @Test
    public void shortHash_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> IdentiColorSchemeV2.hueBucketFromHash("abc"));
        assertThrows(IllegalArgumentException.class, () -> IdentiColorSchemeV2.hueBucketFromHash(null));
    }

    @Test
    public void toHexRGB_formatsLowerCase() {
        assertEquals("#ff0000", IdentiColorSchemeV2.toHexRGB(new Color(255, 0, 0)));
        assertEquals("#0a141e", IdentiColorSchemeV2.toHexRGB(new Color(10, 20, 30)));
    }
}
