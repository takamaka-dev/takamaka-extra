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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.takamaka.wallet.InstanceWalletKeyStoreBCED25519;
import io.takamaka.wallet.utils.TkmSignUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

/**
 * One-shot generator for the chat-web-gui SKIN reference materials (P1 rows
 * #1-#4 of {@code mockups/REFERENCE-MATERIALS.md}). Runs the Java reference
 * tools (reference owns crypto + colour + identicon) and writes static vectors
 * to {@code OUT_DIR} for the file-served mockups to embed.
 *
 * <p>Invoke explicitly: {@code mvn -o test -Dtest=SkinReferenceMaterialsGenerator}.</p>
 *
 * @author Giovanni Antino giovanni.antino@takamaka.io
 */
@Slf4j
public class SkinReferenceMaterialsGenerator {

    private static final ObjectMapper M = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Staging dir; copied into the skin worktree by the driver after the run. */
    private static final Path OUT_DIR = Paths.get("/tmp/skin-reference-out");

    // ---- #1 canonical fixed test addresses (plain strings) ----
    private static final String ADDR_CANON_1 = "yzrhYG_yVL_Cswdg6tiTEx0nTKSPwcfd75J4BP2n0C4.";
    private static final String ADDR_CANON_2 = "WsJLuVzgJuTzZ8qGLSXiGZGKqX_-YiKvz3kXm1N0L2o.";
    private static final String ADDR_CANON_3 = "abcdefABCDEF0123456789_-abcdefABCDEF0123456.";
    private static final String ADDR_SEEDED  = "4mfAa-hIJBU8_iU7IUDIgQpDZCpBVNp7oyCsMED6Y4A.";

    // ---- #2 H-019 byte-exact anchors (address -> bucket/hue/hex/tintHex) ----
    private static final class Anchor {
        final int bucket; final int hue; final String hex; final String tintHex;
        Anchor(int bucket, int hue, String hex, String tintHex) {
            this.bucket = bucket; this.hue = hue; this.hex = hex; this.tintHex = tintHex;
        }
    }
    private static final Map<String, Anchor> ANCHORS = new LinkedHashMap<>();
    static {
        ANCHORS.put(ADDR_CANON_1, new Anchor(18, 270, "#8c45d3", "#46236a"));
        ANCHORS.put(ADDR_CANON_2, new Anchor(15, 225, "#4569d3", "#23356a"));
        ANCHORS.put(ADDR_CANON_3, new Anchor(14, 210, "#458cd3", "#23466a"));
    }

    @Test
    public void generate() throws Exception {
        Files.createDirectories(OUT_DIR);
        Files.createDirectories(OUT_DIR.resolve("identicons"));

        // ===== #1 test addresses (incl. a fresh disposable real wallet) =====
        // Fresh ephemeral Ed25519 wallet -> address index 0, 1, 2 are REAL keys
        // (random per run, disposable; for mockup multi-identity/index tabs).
        InstanceWalletKeyStoreBCED25519 iwk =
                new InstanceWalletKeyStoreBCED25519("skin_ref_disposable_" + System.nanoTime(), 64, -1);
        String walletAddr0 = iwk.getPublicKeyAtIndexURL64(0);
        String walletIdx1  = iwk.getPublicKeyAtIndexURL64(1);
        String walletIdx2  = iwk.getPublicKeyAtIndexURL64(2);

        ObjectNode addrRoot = M.createObjectNode();
        addrRoot.put("note", "STATIC reference test addresses for skin mockups. The fixed "
                + "canonical + seeded entries are constants; the wallet group is a FRESH "
                + "disposable Ed25519 wallet (random per generation, derived via wallet-core "
                + "InstanceWalletKeyStoreBCED25519) so multi-identity/index tabs are real keys.");
        addrRoot.put("generator", "SkinReferenceMaterialsGenerator.java (takamaka-extra)");
        addrRoot.put("cipher", "Ed25519BC");

        ArrayNode canon = M.createArrayNode();
        canon.add(addrEntry("canonical-1", ADDR_CANON_1, "canonical fixed (G-5/H-016)"));
        canon.add(addrEntry("canonical-2", ADDR_CANON_2, "canonical fixed (G-5/H-016)"));
        canon.add(addrEntry("canonical-3", ADDR_CANON_3, "canonical fixed (G-5/H-016)"));
        canon.add(addrEntry("seeded-identity", ADDR_SEEDED, "seeded identity (S-13)"));
        addrRoot.set("canonical_addresses", canon);

        ObjectNode walletGroup = M.createObjectNode();
        walletGroup.put("note", "ONE real disposable Ed25519 wallet, three addresses. "
                + "address-0 is the wallet/identity-defining address; index-1 / index-2 are "
                + "additional derived indexes of the SAME wallet (multi-identity tabs).");
        walletGroup.put("source", "wallet-core InstanceWalletKeyStoreBCED25519 (ephemeral, disposable)");
        walletGroup.set("address_0", addrEntry("address-0", walletAddr0, "wallet root / identity (index 0)"));
        walletGroup.set("index_1", addrEntry("index-1", walletIdx1, "same wallet, derived index 1"));
        walletGroup.set("index_2", addrEntry("index-2", walletIdx2, "same wallet, derived index 2"));
        addrRoot.set("wallet_group", walletGroup);

        write("test-addresses.json", addrRoot);

        // All addresses that feed #2 / #3.
        String[] allAddrs = {
            ADDR_CANON_1, ADDR_CANON_2, ADDR_CANON_3, ADDR_SEEDED,
            walletAddr0, walletIdx1, walletIdx2
        };
        String[] allLabels = {
            "canonical-1", "canonical-2", "canonical-3", "seeded-identity",
            "wallet:address-0", "wallet:index-1", "wallet:index-2"
        };

        // ===== #2 colour scheme v2.0 vectors =====
        // Assert published constants first.
        assertEquals(24, IdentiColorSchemeV2.HUE_BUCKETS, "HUE_BUCKETS");
        assertEquals(8, IdentiColorSchemeV2.HUE_SEED_HEX_LEN, "HUE_SEED_HEX_LEN");
        assertEquals(0.62d, IdentiColorSchemeV2.SATURATION, 0.0d, "SATURATION");
        assertEquals(0.55d, IdentiColorSchemeV2.LIGHTNESS, 0.0d, "LIGHTNESS");

        ObjectNode colorRoot = M.createObjectNode();
        colorRoot.put("scheme_version", IdentiColorSchemeV2.SCHEME_VERSION);
        colorRoot.put("note", "Colour Scheme v2.0 vectors generated by takamaka-extra "
                + "IdentiColorSchemeV2 (feature/color-scheme-v2). seed=SHA3-256(address) hex; "
                + "hue=first 8 hex chars mod 24 scaled to [0,360); HSL S/L fixed; "
                + "tint = composeOverBlack(walletColor, 0.5).");
        ObjectNode consts = M.createObjectNode();
        consts.put("HUE_BUCKETS", IdentiColorSchemeV2.HUE_BUCKETS);
        consts.put("HUE_SEED_HEX_LEN", IdentiColorSchemeV2.HUE_SEED_HEX_LEN);
        consts.put("SATURATION", IdentiColorSchemeV2.SATURATION);
        consts.put("LIGHTNESS", IdentiColorSchemeV2.LIGHTNESS);
        colorRoot.set("constants", consts);

        ArrayNode vectors = M.createArrayNode();
        for (int i = 0; i < allAddrs.length; i++) {
            vectors.add(colorVector(allLabels[i], allAddrs[i]));
        }
        colorRoot.set("color_scheme_v2_vectors", vectors);
        write("color_scheme_v2_vectors.json", colorRoot);

        // ===== #2 anchor self-check (byte-exact against H-019) =====
        for (Map.Entry<String, Anchor> e : ANCHORS.entrySet()) {
            String addr = e.getKey();
            Anchor a = e.getValue();
            String hash = TkmSignUtils.Hash256ToHex(addr);
            int bucket = IdentiColorSchemeV2.hueBucketFromHash(hash);
            int hue = IdentiColorSchemeV2.hueFromHash(hash);
            Color wc = IdentiColorSchemeV2.walletColor(hash);
            String hex = IdentiColorSchemeV2.toHexRGB(wc);
            String tintHex = IdentiColorSchemeV2.toHexRGB(IdentiColorSchemeV2.composeOverBlack(wc, 0.5d));
            log.info("ANCHOR {} -> bucket={} hue={} hex={} tint={}", addr, bucket, hue, hex, tintHex);
            assertEquals(a.bucket, bucket, "bucket " + addr);
            assertEquals(a.hue, hue, "hue " + addr);
            assertEquals(a.hex, hex, "hex " + addr);
            assertEquals(a.tintHex, tintHex, "tint " + addr);
        }
        log.info("H-019 anchor self-check PASSED (byte-exact) for all 3 anchors");

        // ===== #3 identicon images (PNG via reference) =====
        // NOTE: IdentiColorHelper.getAvatarByHex paints TYPE_INT_RGB (opaque).
        // The reference does NOT emit a transparent background; honoured as-is.
        for (int i = 0; i < allAddrs.length; i++) {
            BufferedImage img = IdentiColorHelper.getAvatarByString256(allAddrs[i]);
            String file = "identicons/" + allLabels[i].replace(':', '_') + ".png";
            ImageIO.write(img, "PNG", OUT_DIR.resolve(file).toFile());
            log.info("identicon {} -> {} ({}x{})", allLabels[i], file, img.getWidth(), img.getHeight());
        }

        // ===== #4 user-options manifest sample =====
        write("useroptionsmanifest.sample.json", buildOptionsManifest());

        log.info("Skin reference materials written to {}", OUT_DIR.toAbsolutePath());
    }

    private ObjectNode addrEntry(String role, String address, String desc) {
        ObjectNode n = M.createObjectNode();
        n.put("role", role);
        n.put("address", address);
        n.put("length", address.length());
        n.put("description", desc);
        return n;
    }

    private ObjectNode colorVector(String label, String address) throws Exception {
        String hash = TkmSignUtils.Hash256ToHex(address);
        int bucket = IdentiColorSchemeV2.hueBucketFromHash(hash);
        int hue = IdentiColorSchemeV2.hueFromHash(hash);
        Color wc = IdentiColorSchemeV2.walletColor(hash);
        Color tint = IdentiColorSchemeV2.composeOverBlack(wc, 0.5d);

        ObjectNode n = M.createObjectNode();
        n.put("label", label);
        n.put("address", address);
        n.put("hash256Hex", hash);
        n.put("bucket", bucket);
        n.put("hue", hue);
        n.set("walletColor", rgb(wc));
        n.put("hex", IdentiColorSchemeV2.toHexRGB(wc));
        ObjectNode tn = M.createObjectNode();
        tn.put("recipe", "composeOverBlack@0.5");
        tn.setAll(rgb(tint));
        tn.put("hex", IdentiColorSchemeV2.toHexRGB(tint));
        n.set("tint", tn);
        return n;
    }

    private ObjectNode rgb(Color c) {
        ObjectNode n = M.createObjectNode();
        n.put("r", c.getRed());
        n.put("g", c.getGreen());
        n.put("b", c.getBlue());
        return n;
    }

    private ObjectNode buildOptionsManifest() {
        ObjectNode root = M.createObjectNode();
        root.put("manifest", "useroptionsmanifest");
        root.put("sample", true);
        root.put("note", "SAMPLE representative manifest (USER_OPTIONS_DESIGN + H-014 canonical "
                + "option list). Not a live server payload. Visibility: Protected | MembersOnly | Public.");

        ObjectNode app = M.createObjectNode();
        ArrayNode appOpts = M.createArrayNode();
        appOpts.add(opt("theme", "Theme", "UI colour theme (light/dark/system).", "Protected", "enum"));
        appOpts.add(opt("debug", "Debug mode", "Enable verbose diagnostic logging.", "Protected", "boolean"));
        appOpts.add(opt("auto-lock", "Auto-lock", "Lock the wallet after an idle timeout.", "Protected", "duration"));
        appOpts.add(opt("deep-lock", "Deep lock", "Require full re-authentication, clearing cached keys.", "Protected", "boolean"));
        appOpts.add(opt("network", "Network", "Active network profile (local/test/prod).", "Protected", "enum"));
        appOpts.add(opt("change-master-password", "Change master password", "Re-encrypt the keystore under a new master password.", "Protected", "action"));
        app.set("options", appOpts);

        ObjectNode wallet = M.createObjectNode();
        ArrayNode walletOpts = M.createArrayNode();
        walletOpts.add(opt("add", "Add wallet", "Create a new wallet/identity.", "Protected", "action"));
        walletOpts.add(opt("import", "Import wallet", "Import a wallet from mnemonic words.", "Protected", "action"));
        ObjectNode exportWords = opt("export-words", "Export words", "Reveal the mnemonic words (heavy confirmation required).", "Protected", "action");
        exportWords.put("heavyConfirm", true);
        walletOpts.add(exportWords);
        walletOpts.add(opt("remove", "Remove wallet", "Delete a wallet/identity from the keystore.", "Protected", "action"));
        wallet.set("options", walletOpts);

        ObjectNode identity = M.createObjectNode();
        ArrayNode identityOpts = M.createArrayNode();
        identityOpts.add(opt("READ_NOTIFICATIONS", "Read notifications", "Allow this identity to receive/read notifications.", "MembersOnly", "boolean"));
        identity.set("options", identityOpts);

        ObjectNode groups = M.createObjectNode();
        groups.set("app", app);
        groups.set("wallet", wallet);
        groups.set("identity", identity);
        root.set("groups", groups);
        return root;
    }

    private ObjectNode opt(String key, String label, String desc, String visibility, String valueType) {
        ObjectNode n = M.createObjectNode();
        n.put("key", key);
        n.put("label", label);
        n.put("description", desc);
        n.put("visibility", visibility);
        n.put("valueType", valueType);
        return n;
    }

    private void write(String name, ObjectNode node) throws IOException {
        M.writeValue(OUT_DIR.resolve(name).toFile(), node);
        log.info("wrote {}", name);
    }
}
