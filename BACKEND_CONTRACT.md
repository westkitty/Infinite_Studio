# Infinite Studio Backend & Provider Router Contract

Infinite Studio is strictly **offline-first** and holds **zero AI provider API keys** inside the Android client APK. External model inference is isolated behind `com.example.services.ProviderRouter` and `ProviderAdapter`, with remote dispatch disabled by default and routed **only** through a separately hosted companion service (`companion/server.py`).

- **Machine-Readable OpenAPI Spec:** [`schema/openapi.json`](schema/openapi.json)
- **Deterministic Fixtures:** [`schema/sample-job-request.json`](schema/sample-job-request.json), [`schema/sample-job-response.json`](schema/sample-job-response.json)
- **Companion Source:** [`companion/server.py`](companion/server.py) (`127.0.0.1:8787` loopback-only)

---

## 1. Client Provider Modes (`ProviderMode`)

The Android client exposes three modes — zero direct third-party AI endpoints or credentials exist in client code:

| Mode | Network Egress | Credential in APK | Behavior |
|---|---|---|---|
| `OFFLINE_LOCAL_ONLY` *(default)* | **Zero** | **None** | Blocks all external jobs (`OFFLINE_MODE_ACTIVE`). Futures Table computes 3 local CPU color studies (`Salt Print`, `Blue Hour`, `Copper Light`). |
| `OFFLINE_MOCK_PROVIDER` | **Zero** | **None** | Exercises full async `ProviderAdapter` contract in-process (`OfflineMockProviderAdapter`), enforcing idempotency keys, stale-revision rejection, and cancellation. |
| `LOOPBACK_COMPANION` | Loopback only (`127.0.0.1:8787`) | **None** *(optional local bearer token if configured on host)* | Calls the separately hosted local companion service (`LoopbackCompanionAdapter`) via HTTP/JSON. Requires explicit per-request confirmation (`userConfirmedPaidEgress == true`) and enforces `MAX_COMPANION_CALLS_PER_SESSION = 3`. |

> **Auto-Commit Prohibition:** Job responses returned by any `ProviderAdapter` populate preview candidates inside `FuturesTableSessionV3`. No provider response is ever auto-committed to document layers without explicit user acceptance.

---

## 2. REST Wire Endpoints (`companion/server.py` & `openapi.json`)

### `GET /health`
Returns `ProviderHealth`:
```json
{
  "providerId": "loopback-companion",
  "status": "healthy",
  "offlineSafe": true,
  "detail": "Loopback companion active in deterministic local mock mode (zero paid calls)."
}
```

### `GET /capabilities`
Returns `ProviderCapabilities`:
```json
{
  "providerId": "loopback-companion",
  "supportsImageEdit": true,
  "supportsOutpaint": true,
  "maxResolutionPx": 2048,
  "supportsCancel": true,
  "requiresExplicitConsentForRemote": true,
  "maxPayloadBytes": 8388608,
  "retentionTtlSeconds": 1800
}
```

### `POST /jobs`
Accepts `ProviderJobRequest` (`application/json`):
- Required fields: `idempotencyKey`, `documentId`, `baseRevision`, `branchLabel`, `prompt`, `cropPngBase64`
- Optional fields: `expectedRevision`, `directionSuffix`, `modelId`, `maskPngBase64`, `userConfirmedPaidEgress`
- Validation rules enforced by both `ProviderRouter` and `companion/server.py`:
  1. Payload size capped (`8 MiB` default on companion; `413` on overflow).
  2. `cropPngBase64` and `maskPngBase64` must decode to valid base64 with PNG magic bytes (`415` on invalid MIME/header).
  3. `idempotencyKey` replays return the original `jobId` without re-execution.
  4. `baseRevision != expectedRevision` (or `!= currentDocumentRevision`) fails cleanly with `STALE_DOCUMENT_REVISION` (`409 Conflict`).
  5. Optional host-side upstream remote execution on `companion/server.py` (when the companion host operator configures `INFINITE_COMPANION_UPSTREAM_MODE=REMOTE_OPENAI`) requires `userConfirmedPaidEgress == true` (`403 CONSENT_REQUIRED`) and host-side `OPENAI_API_KEY` (`503 MISSING_API_KEY` defined exclusively in `companion/.env.example`, never on Android).

### `GET /jobs/{id}`, `POST /jobs/{id}/cancel`, `DELETE /jobs/{id}`
- `GET /jobs/{id}` returns `ProviderJobRecord` including `metadata.modelVersion`, `metadata.sourceHash` (SHA-256 of input crop), and TTL expiry timestamps. Expired jobs are purged automatically.
- `POST /jobs/{id}/cancel` transitions the job status to `CANCELED`.
- `DELETE /jobs/{id}` immediately deletes stored job inputs/outputs before TTL expiry.

---

## 3. Environment & Security Boundary

- **Zero Client Secrets:** `secrets-gradle-plugin` and root `.env.example` have been completely removed from the Android project. Neither `BuildConfig`, resources, `AndroidManifest.xml`, assets, nor `app-debug.apk` contain `OPENAI_API_KEY`, `api.openai.com`, or `sk-` tokens (verified automatically by `CredentialBoundaryAndBuildArtifactTest`).
- **Companion Exposure:** `companion/server.py` refuses to start if configured with a non-loopback bind host unless explicitly altered for an authenticated TLS reverse proxy / SSH tunnel.
- **Environment Verification Note:** The Python companion unit test suite executes standalone via `python3 -m unittest`; it does not execute inside the Android emulator during AI Studio Android builds. Live paid upstream execution has **not** been run in this build.
