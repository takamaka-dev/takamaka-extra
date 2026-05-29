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

import io.takamaka.wallet.exceptions.HashAlgorithmNotFoundException;
import io.takamaka.wallet.exceptions.HashEncodeException;
import io.takamaka.wallet.exceptions.HashProviderNotFoundException;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.awt.Color;

/**
 * Color Scheme v2.0 — deterministic wallet/identity colour derivation.
 *
 * <p>This is the <b>formalised, versioned successor</b> to the "basic" colour
 * provider {@link IdentiColorHelper#fgColorFromHex(java.lang.String)}, which maps
 * six hex characters directly to an sRGB triplet ({@code new Color(r,g,b)}). That
 * direct mapping yields uncontrolled luminance/saturation and perceptually adjacent
 * colours for unrelated wallets. v2.0 instead routes the address through
 * <b>HSL with fixed saturation and lightness and a quantised hue</b>, so every
 * derived colour is vivid, contrast-stable and visually separable.</p>
 *
 * <h2>Determinism contract (cross-platform)</h2>
 * <ul>
 *   <li><b>Seed:</b> {@code SHA3-256(address)} as lower-case hex
 *       (same hash {@link IdentiColorHelper} already uses), via
 *       {@link TkmSignUtils#Hash256ToHex(java.lang.String)}.</li>
 *   <li><b>Hue:</b> the first {@link #HUE_SEED_HEX_LEN} hex characters of that hash,
 *       parsed as an unsigned integer, reduced modulo {@link #HUE_BUCKETS}, and scaled
 *       to a hue in {@code [0,360)} (one bucket every {@code 360/HUE_BUCKETS} degrees).</li>
 *   <li><b>Saturation / Lightness:</b> fixed ({@link #SATURATION} / {@link #LIGHTNESS}).</li>
 *   <li><b>HSL→sRGB:</b> the exact conversion in {@link #hslToRgb(double, double, double)},
 *       with channels rounded by {@link Math#round(double)} (round half up).</li>
 * </ul>
 *
 * <p>The Dart/Flutter port MUST reproduce these values to the integer. Parity is
 * anchored by test vectors (see {@code TestVectorGenerator}); never by PNG bytes.</p>
 *
 * <p><b>This class does not change v1 identicon output.</b> It only adds a colour
 * derivation used by the new visual-identity chrome (wallet-coloured tile/title
 * borders). The canonical specification is
 * {@code chat-web-gui/docs/architecture/COLOR_SCHEME_V2.md}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
public final class IdentiColorSchemeV2 {

    /** Scheme version tag, embedded in test vectors and persisted metadata. */
    public static final String SCHEME_VERSION = "2.0";

    /** Number of hue buckets. 24 → one stop every 15°, well within human discrimination. */
    public static final int HUE_BUCKETS = 24;

    /** Number of leading hex chars of the SHA3-256 hash used to pick the hue bucket. */
    public static final int HUE_SEED_HEX_LEN = 8;

    /** Fixed saturation (0..1). Chosen for vivid-but-not-garish, theme-stable colours. */
    public static final double SATURATION = 0.62d;

    /** Fixed lightness (0..1). Chosen for adequate contrast against a dark tile. */
    public static final double LIGHTNESS = 0.55d;

    private IdentiColorSchemeV2() {
        // utility class
    }

    /**
     * Hue bucket index in {@code [0, HUE_BUCKETS)} for a SHA3-256 hex hash.
     *
     * @param hash256Hex lower-case hex of {@code SHA3-256(address)}; must be at least
     *                   {@link #HUE_SEED_HEX_LEN} characters long
     * @return the deterministic bucket index
     */
    public static int hueBucketFromHash(String hash256Hex) {
        if (hash256Hex == null || hash256Hex.length() < HUE_SEED_HEX_LEN) {
            throw new IllegalArgumentException(
                    "hash256Hex must be at least " + HUE_SEED_HEX_LEN + " hex chars");
        }
        // First 8 hex chars = 32 bits; parse as long to stay unsigned-safe in Java 11.
        long seed = Long.parseLong(hash256Hex.substring(0, HUE_SEED_HEX_LEN), 16);
        return (int) (seed % HUE_BUCKETS);
    }

    /**
     * Hue in degrees {@code [0,360)} for a SHA3-256 hex hash.
     *
     * @param hash256Hex lower-case hex of {@code SHA3-256(address)}
     * @return hue in degrees, quantised to {@link #HUE_BUCKETS} stops
     */
    public static int hueFromHash(String hash256Hex) {
        return hueBucketFromHash(hash256Hex) * 360 / HUE_BUCKETS;
    }

    /**
     * Wallet colour for a SHA3-256 hex hash (HSL with fixed S/L, quantised hue).
     *
     * @param hash256Hex lower-case hex of {@code SHA3-256(address)}
     * @return the derived sRGB colour
     */
    public static Color walletColor(String hash256Hex) {
        int[] rgb = hslToRgb(hueFromHash(hash256Hex), SATURATION, LIGHTNESS);
        return new Color(rgb[0], rgb[1], rgb[2]);
    }

    /**
     * Convenience: derive the wallet colour directly from an address by hashing it
     * with the same SHA3-256 step the identicon uses.
     *
     * @param address the address (typically address index 0 of the wallet/keyring)
     * @return the derived sRGB colour
     * @throws HashEncodeException           on hashing failure
     * @throws HashAlgorithmNotFoundException on hashing failure
     * @throws HashProviderNotFoundException  on hashing failure
     */
    public static Color walletColorFromAddress(String address)
            throws HashEncodeException, HashAlgorithmNotFoundException, HashProviderNotFoundException {
        return walletColor(TkmSignUtils.Hash256ToHex(address));
    }

    /**
     * Exact HSL → sRGB conversion shared by Java and the Dart port.
     *
     * @param h hue in degrees {@code [0,360)}
     * @param s saturation {@code [0,1]}
     * @param l lightness {@code [0,1]}
     * @return {@code int[3]} of {@code {r,g,b}}, each {@code [0,255]}
     */
    public static int[] hslToRgb(double h, double s, double l) {
        double c = (1.0d - Math.abs(2.0d * l - 1.0d)) * s;
        double hp = h / 60.0d;
        double x = c * (1.0d - Math.abs((hp % 2.0d) - 1.0d));
        double m = l - c / 2.0d;

        double r1, g1, b1;
        if (hp < 1.0d) {
            r1 = c; g1 = x; b1 = 0.0d;
        } else if (hp < 2.0d) {
            r1 = x; g1 = c; b1 = 0.0d;
        } else if (hp < 3.0d) {
            r1 = 0.0d; g1 = c; b1 = x;
        } else if (hp < 4.0d) {
            r1 = 0.0d; g1 = x; b1 = c;
        } else if (hp < 5.0d) {
            r1 = x; g1 = 0.0d; b1 = c;
        } else {
            r1 = c; g1 = 0.0d; b1 = x;
        }

        int r = (int) Math.round((r1 + m) * 255.0d);
        int g = (int) Math.round((g1 + m) * 255.0d);
        int b = (int) Math.round((b1 + m) * 255.0d);
        return new int[]{clamp8(r), clamp8(g), clamp8(b)};
    }

    /**
     * Composite an opaque colour over a black background at a given alpha — the
     * deterministic equivalent of painting {@code colour @ alpha} on black. Used for
     * the tile-background tint recipe (alpha 0.5 over black).
     *
     * @param c     source colour
     * @param alpha alpha {@code [0,1]}
     * @return the resulting opaque colour
     */
    public static Color composeOverBlack(Color c, double alpha) {
        int r = clamp8((int) Math.round(c.getRed() * alpha));
        int g = clamp8((int) Math.round(c.getGreen() * alpha));
        int b = clamp8((int) Math.round(c.getBlue() * alpha));
        return new Color(r, g, b);
    }

    /**
     * Render a colour as a CSS/web hex string {@code #rrggbb}.
     *
     * @param c the colour
     * @return lower-case {@code #rrggbb}
     */
    public static String toHexRGB(Color c) {
        return String.format("#%02x%02x%02x", c.getRed(), c.getGreen(), c.getBlue());
    }

    private static int clamp8(int v) {
        return v < 0 ? 0 : (v > 255 ? 255 : v);
    }
}
