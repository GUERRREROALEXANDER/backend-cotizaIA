# Spec 01 - Encryption at rest

## Goal
A dump of the database reveals no agency or client content. The API behaves exactly as before.

## Judge (tests already written, do not edit them)
- `src/test/java/com/cotizaia/crypto/FieldCipherTests.java`
- `src/test/java/com/cotizaia/crypto/BlindIndexTests.java`
- `src/test/java/com/cotizaia/crypto/CryptoConfigurationTests.java`
- `src/test/java/com/cotizaia/crypto/EncryptionAtRestTests.java` (raw JDBC scan of every table)
- **and the full suite**: `./mvnw -B test` must end with 0 failures, 0 errors.

## Package `com.cotizaia.crypto` (new)
| Type | Contract |
|---|---|
| `KeyProvider` (interface) | `String activeKeyId()`, `javax.crypto.SecretKey key(String keyId)`; unknown id -> `CryptoException` |
| `StaticKeyProvider` | ctor `(Map<String, byte[]> keys, String activeKeyId)`. Every key exactly 32 bytes, active id present, ids match `[A-Za-z0-9_-]+` (no `:`), else `IllegalArgumentException`. Defensive copies. |
| `CryptoException` | unchecked |
| `FieldCipher` | ctor `(KeyProvider)`. AES/GCM/NoPadding, 12-byte random IV per call (`SecureRandom`), 128-bit tag. `String encrypt(String)` -> `"v1:" + keyId + ":" + base64(iv ‖ ciphertext‖tag)`; `String decrypt(String)` parses that format, any other input or failed auth -> `CryptoException` (never pass plaintext through). `byte[] encrypt(byte[])` / `byte[] decrypt(byte[])`: binary format `0x01 ‖ keyIdLength(1 byte) ‖ keyId(UTF-8) ‖ iv(12) ‖ ciphertext‖tag`. `null` in -> `null` out for all four. Encrypt always uses the active key; decrypt uses the key id stored in the value. |
| `BlindIndex` | ctor `(byte[] key)`. `String hash(String)`: HMAC-SHA256 over `value.strip().toLowerCase(Locale.ROOT)` normalized NFC, lowercase hex (64 chars). `null` -> `null`. |
| `CryptoProperties` | `@ConfigurationProperties("crypto")`: `activeKeyId`, `Map<String,String> keys` (base64), `blindIndexKey` (base64) |
| `CryptoConfiguration` | `@Configuration` + `@EnableConfigurationProperties(CryptoProperties.class)`; beans `KeyProvider`, `FieldCipher`, `BlindIndex`. Missing/blank/short (≠32 bytes) key or missing blind-index key -> exception at startup. **No default keys anywhere.** |
| `EncryptedStringConverter`, `EncryptedBytesConverter` | JPA `AttributeConverter`s (`@Converter`, not autoApply) that are Spring `@Component`s with the cipher injected by constructor (Spring Boot wires Hibernate's bean container). |

## Columns to encrypt (`@Convert(converter = ...)` on the entity field)
- `agencies.name`
- `users.full_name`, `users.email`, `users.login_email`
- `clients.name`, `clients.email`, `clients.company`
- `briefs.raw_text`, `briefs.raw_payload`
- `brief_attachments.file_name`, `brief_attachments.content` (bytes)
- `brief_questions.question`, `brief_questions.answer`
- `extracted_requirements.description`
- `agent_steps.input_summary`, `agent_steps.output_summary`
- `proposals.terms`, `proposals.schedule`, `proposals.extras`
- `proposal_documents.content` (bytes)
- `notifications.payload`

Plaintext on purpose (document in README "Data classification"): ids, FKs, enums/status, dates, hours, amounts
(SQL aggregation for analytics and pricing), catalog/role/category names (agency configuration, looked up by name),
contract clause templates (same text for every client), file sizes, content types.

## Lookups on encrypted columns: blind index
- New columns `users.login_email_hash` (unique), `users.email_hash`, `clients.email_hash`, all `VARCHAR(64)`.
- Services (`AuthService`, `ClientService`, anything that sets an email) compute the hash with the `BlindIndex`
  bean and set it on the entity next to the email. Entities never call Spring beans.
- Repositories: replace `findByLoginEmail`, `existsByLoginEmail`, `findByAgencyIdAndEmail` (users and clients)
  with `...Hash` variants. Login is case-insensitive through the hash; duplicate registration with another
  case -> 409 (the existing conflict path).

## Migration `V21__encrypt_content_columns.sql` (must run on H2 test DB and on PostgreSQL)
- Widen every encrypted `VARCHAR(n)` above to `TEXT` with `ALTER TABLE t ALTER COLUMN c SET DATA TYPE TEXT`
  (portable syntax for both engines). BYTEA columns stay.
- Add the three hash columns; drop `uq_users_login_email`, add `uq_users_login_email_hash`.
- No backfill: existing deployments start from an empty database (demo data only). Say so in a SQL comment.

## Configuration
- `application.yml`: `crypto.active-key-id: ${DATA_ENCRYPTION_KEY_ID:k1}`, `crypto.keys.k1: ${DATA_ENCRYPTION_KEY}`,
  `crypto.blind-index-key: ${BLIND_INDEX_KEY}` (no defaults: the app refuses to start without them).
- `.env.example` + `render.yaml` (`sync: false`) gain `DATA_ENCRYPTION_KEY`, `BLIND_INDEX_KEY`
  with the hint `openssl rand -base64 32`. `src/test/resources/application-test.yml` already has test keys.
- README: one paragraph under a new "Security" heading + the data classification lists above.

## Out of scope
Cookie auth, rate limiting, KMS, key-rotation tooling, anything in `agent/`, the frontend.
Do not edit any file under `src/test/`.
