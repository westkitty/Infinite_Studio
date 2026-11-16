# Security & Privacy Architecture — Infinite Studio

## 1. Offline-First Guarantee

- **Default Mode:** `ProviderMode.OFFLINE_LOCAL_ONLY`. In this mode, `ProviderRouter` refuses all outbound inference network calls (`ProviderErrorCode.OFFLINE_MODE_ACTIVE`), enabling airplane-mode painting, Futures Table local color studies, vector lettering, material previews, recipes, timelines, and exports.
- **No Hidden Accounts or Telemetry:** Zero Firebase, zero Google Sign-In gates, zero analytics SDKs, zero advertising SDKs, and zero background network scanners.

---

## 2. Android Permissions Model (Least Privilege)

Declared in `app/src/main/AndroidManifest.xml`:
- `android.permission.INTERNET` (install-time normal permission; strictly gated behind `ProviderRouter` opt-in `LOOPBACK_COMPANION` mode).

Explicitly **omitted**:
- No `READ_EXTERNAL_STORAGE`, `WRITE_EXTERNAL_STORAGE`, `READ_MEDIA_IMAGES`, or `READ_MEDIA_VIDEO`. Media selection uses the zero-permission Android Photo Picker (`ActivityResultContracts.PickVisualMedia`), and `.iastudio` / SVG / PNG / JPG / ZIP files use the Android Storage Access Framework (`CreateDocument` / `OpenDocument`).
- Vault references (`VaultAssetRecord`) import only assets explicitly selected by the user; Infinite Studio never scans `MediaStore`, Drive, GitHub, or local shared folders.

---

## 3. Archive & File-System Trust Boundaries

`DocumentArchiveCodec` enforces:
1. **Zip-Slip Prevention:** Rejects any entry starting with `/` or `\`, containing `..`, or containing NUL bytes (`\u0000`).
2. **Resource Exhaustion Guards:** Caps archive entry count (`8192`) and uncompressed bytes (`192 MiB`), plus document side bounds (`1..16384 px`).
3. **Asset Type & Integrity Checks:** Verifies the 8-byte PNG header signature on every decoded tile and checks SHA-256 checksums (`assetChecksums`) when present.
4. **Atomic Draft Replacement:** `ProjectStorageManager.saveLocalDraftAtomic` writes to temporary `.tmp` files and updates `recovery_journal.json` after stream flush, preserving the last valid local draft if interrupted or out of space.
5. **Scoped File Sharing:** Exports shared via `Intent.ACTION_SEND` use `androidx.core.content.FileProvider` (`exported="false"`, `grantUriPermissions="true"`) scoped to `cacheDir/exports`.

---

## 4. External Provider & Secret Boundary

- **Zero Secrets in Source, Gradle, `BuildConfig`, or APK:**
  - Root `.env` / `.env.example` and `secrets-gradle-plugin` are completely removed from the Android project.
  - `ProviderRouter` has no direct OpenAI endpoint or API key injection path; remote inference is disabled by default (`OFFLINE_LOCAL_ONLY`).
  - `CredentialBoundaryAndBuildArtifactTest` automatically verifies via reflection, workspace source scan, generated `BuildConfig` scan, and compiled APK (`app-debug.apk`) inspection that zero `OPENAI_API_KEY`, `api.openai.com`, `sk-`, or `.env` artifacts exist in client builds.
- **Explicit Companion Dispatch Consent:** When a user intentionally switches `ProviderRouter` to `LOOPBACK_COMPANION`, external companion dispatch requires `request.userConfirmedPaidEgress == true` (triggered only after an explicit confirmation dialog in `FuturesTableDialog`) and enforces a hard `MAX_COMPANION_CALLS_PER_SESSION = 3` cap.
- **Host-Only Credentials & Loopback Binding:** Any upstream provider key (such as an optional `OPENAI_API_KEY` defined in `companion/.env.example`) resides **exclusively on the separately hosted workstation companion (`companion/server.py`)**, which enforces `host in ("127.0.0.1", "localhost", "::1")`, bearer token auth (`INFINITE_COMPANION_AUTH_TOKEN`), max payload caps (`8 MiB`), and automatic TTL purge (`1800s`).
- **Redacted Diagnostics:** `StudioViewModel.buildRedactedDiagnosticsReport()` excludes all secrets, bearer tokens, private URIs, and raw artwork pixels, reporting `Client Secrets: NONE (zero API credentials in APK or BuildConfig)`.
