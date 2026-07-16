# takamaka-extra

Java utility module for the Takamaka blockchain ecosystem: **message/stream
encryption**, **file metadata extraction**, **address utilities**, deterministic
**identicon** primitives, and **block/time/transaction** helpers that sit above
the wallet core.

- **Maven coordinate:** `io.takamaka.extra:takamaka-extra`
- **Version:** `0.5.0-SNAPSHOT`
- **Java:** 11
- **License:** Apache License 2.0
- **Main dependencies:** `io.takamaka.wallet:takamaka-core-wallet` (0.10.0-SNAPSHOT),
  Apache Tika (`tika-core`), Jackson (`jackson-databind`), Apache Commons Text,
  Lombok.

## Position in the build chain

`takamaka-extra` builds on top of `wallet-core` and is consumed by `Messages`:

```
tkmsecurityround1  →  wallet-core  →  takamaka-extra  →  Messages  →  rsclient / rschat
```

Build (bottom-up): `wallet-core` must be installed to the local Maven repo
first, then:

```bash
cd takamaka-extra && mvn clean install
```

## Encryption utilities

`io.takamaka.extra.utils.TkmEncryptionUtils` provides the encryption primitives
used by the chat protocol. Parameters are driven by the `EncryptionContext`
enum so both encrypt and decrypt paths (and the Flutter port) stay in lockstep:

| Context | Cipher | KDF | Iterations | Key size | Used for |
|---------|--------|-----|-----------:|----------|----------|
| `v0_1_a` | `AES/CBC/PKCS5Padding` | `PBKDF2WithHmacSHA512` | 20000 | 256-bit | conversation creation (title + key bean), text messages |
| `v0_2_a_stream_gcm` | `AES/GCM/NoPadding` | `PBKDF2WithHmacSHA512` | 20000 | 256-bit | streamed attachments/files (128-bit tag, 12-byte nonce, SHA3-256 content digest) |

Both contexts use **20000** iterations. (Historic note: a stale `999997`
*label* that once appeared only in test/vector surfaces was corrected to
`20000` on 2026-06-11; no key or ciphertext was ever derived with the old
value.)

Key methods:

- `toPasswordEncryptedContent(...)` / `fromPasswordEncryptedContent(...)` —
  password-based AES-CBC encrypt/decrypt of message content
  (`EncMessageBean`).
- `streamPasswordEncrypt(...)` / `streamPasswordDecrypt(...)` /
  `streamCalcHash(...)` — AES-GCM streaming encrypt/decrypt for attachments
  (`StreamEncryptedDescriptor`).
- `encryptRSAAES(...)` / `decryptRSAAES(...)` / `decryptSecretKey(...)` —
  hybrid RSA-4096 + AES envelope (`CombinedRSAAESBean`).
- `getRandomSaltWithScope256bitB64(...)`, `generateIv(...)`,
  `getHMACDigestUTF8(...)` — supporting crypto helpers.

## Other utilities

- **`TkmAddressUtils`** — address type detection and compact-address handling:
  `toCompactAddress()` (Ed25519 vs QTESLA classification → `CompactAddressBean`),
  `getBookmarkAddress()`, `extractAddressesFromTransaction()`,
  `fromHexToB64URL()` / `fromB64URLToHex()`, `get5ZeroPaddedNumberWithPrefix()`.
- **`TkmTimeUtils`** — epoch/slot arithmetic: `getAbsoluteBlockNumber()` and a
  family of `isInRange*()` predicates over (epoch, slot) ranges.
- **`TkmBlockUtils`** — block decoding and canonical hashing
  (`decodeBlock()`, `getBlockHash()`, `getInternalBlockHash()`,
  `getTransactionsHash()`, `getForwardKeysHash()`, `getRewardListHash()`,
  address collection over a `BlockBox`).
- **`TkmTransactionUtils`**, **`TransactionGeneratorUtils`** — transaction /
  blob-transaction construction helpers.
- **`TkmRewardUtils`**, **`TkmForwardKeys`**, **`TkmArrayUtils`**,
  **`TkmRandomGeneratorUtils`**, **`TkmErrorUtils`**, **`SerializerUtils`** —
  supporting utilities.
- **`files/MetadataUtils`** — file metadata extraction via Apache Tika
  (`collectMetadata()`, `extractMetadatatUsingParser()`,
  `fromFileToB64String()`, `getOsIdentifyingString()`) producing `TkmMetadata`.
- **`identicon/`** — deterministic identicon primitives (`IdenticonManager`,
  `IdentiBaseBlocks`, `IdentiColorHelper`, `IdentiColorSchemeV2`) that are the
  reference for the Flutter `wallet-extra-flutter` identicon port.
- **`beans/`** — data beans including `EncMessageBean`, `CombinedRSAAESBean`,
  `StreamEncryptedDescriptor`, `CompactAddressBean`, `TkmMetadata`, and block
  request/response beans.

## Reference implementation

Java is the reference implementation for the Takamaka ecosystem. Downstream
Dart/Flutter ports (`wallet-extra-flutter`, `rsclient-flutter`) must reproduce
this module's output exactly — same encrypted content from the same
plaintext + key + parameters, and the same identicons and address
classification from the same inputs.

## Testing

```bash
mvn test                     # all tests (JUnit 5)
mvn test -Dtest=ClassName    # a specific test class
```
