# Infinite Studio Companion Service

Minimal, standard-library Python companion service for **Infinite Studio — The Futures Table** (`LoopbackCompanionAdapter`).

## Security & Network Defaults
- **Loopback only by default:** binds to `127.0.0.1:8787`. Never bind to `0.0.0.0` or public interfaces without authenticated TLS/tunneling (e.g., WireGuard/SSH port forward + bearer token).
- **Zero paid API calls by default:** runs in `MOCK_DETERMINISTIC` mode unless explicitly started with `INFINITE_COMPANION_MODE=REMOTE_OPENAI`, `OPENAI_API_KEY` in the host environment, and `userConfirmedPaidEgress: true` per request.
- **Bounded payloads & retention:** rejects bodies larger than `INFINITE_COMPANION_MAX_PAYLOAD_BYTES` (default 8 MiB), validates base64 PNG headers, enforces idempotency keys, and expires completed jobs after `INFINITE_COMPANION_JOB_TTL_SECONDS` (default 1800s).

## Endpoints
- `GET /health` — Provider health (`ProviderHealth`)
- `GET /capabilities` — Provider capabilities & resolution/consent rules (`ProviderCapabilities`)
- `POST /jobs` — Submit edit/outpaint job (`ProviderJobRequest` -> `ProviderJobRecord`)
- `GET /jobs/{id}` — Inspect job status, provenance metadata, and result
- `POST /jobs/{id}/cancel` — Cancel an active/queued job
- `DELETE /jobs/{id}` — Delete stored job payload immediately

## Running Locally
```bash
python3 companion/server.py
```

On an Android tablet connected via USB debugging, forward loopback if desired:
```bash
adb reverse tcp:8787 tcp:8787
```

## Running Tests
```bash
python3 -m unittest discover -s companion -p "test_*.py" -v
```
