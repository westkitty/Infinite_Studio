#!/usr/bin/env python3
"""
Infinite Studio Companion Service (Loopback-Only Reference Runtime)

Implements the wire protocol consumed by com.example.services.LoopbackCompanionAdapter:
  - GET  /health
  - GET  /capabilities
  - POST /jobs
  - GET  /jobs/{jobId}
  - POST /jobs/{jobId}/cancel
  - DELETE /jobs/{jobId}

Defaults strictly to 127.0.0.1:8787 and MOCK_DETERMINISTIC (zero network/paid egress).
"""

from __future__ import annotations

import base64
import hashlib
import hmac
import json
import os
import struct
import threading
import time
import urllib.error
import urllib.request
import uuid
import zlib
from dataclasses import dataclass, asdict
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any, Dict, Optional, Tuple


PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
DEFAULT_MAX_PAYLOAD_BYTES = 8 * 1024 * 1024
DEFAULT_JOB_TTL_SECONDS = 1800


def _read_config() -> Dict[str, Any]:
    return {
        "host": os.environ.get("INFINITE_COMPANION_HOST", "127.0.0.1").strip() or "127.0.0.1",
        "port": int(os.environ.get("INFINITE_COMPANION_PORT", "8787")),
        "mode": os.environ.get("INFINITE_COMPANION_MODE", "MOCK_DETERMINISTIC").strip().upper(),
        "auth_token": os.environ.get("INFINITE_COMPANION_AUTH_TOKEN", "").strip(),
        "max_payload_bytes": int(os.environ.get("INFINITE_COMPANION_MAX_PAYLOAD_BYTES", str(DEFAULT_MAX_PAYLOAD_BYTES))),
        "job_ttl_seconds": int(os.environ.get("INFINITE_COMPANION_JOB_TTL_SECONDS", str(DEFAULT_JOB_TTL_SECONDS))),
        "openai_api_key": os.environ.get("OPENAI_API_KEY", "").strip(),
        "paid_image_edit_enabled": os.environ.get("INFINITE_COMPANION_ENABLE_PAID_IMAGE_EDIT", "").strip().lower() == "yes",
        "max_paid_requests": min(3, max(0, int(os.environ.get("INFINITE_COMPANION_MAX_PAID_REQUESTS", "1")))),
    }


def _recolor_png_deterministic(png_bytes: bytes, direction_suffix: str) -> bytes:
    """
    Pure-stdlib PNG tint transformer for 8-bit RGB (color_type=2) or RGBA (color_type=6)
    non-interlaced PNGs. Produces visibly distinct deterministic local study bytes.
    Falls back to a valid synthetic 16x16 RGBA PNG if input uses an unsupported PNG color type.
    """
    if len(png_bytes) < 24 or png_bytes[:8] != PNG_SIGNATURE:
        raise ValueError("Invalid PNG signature")

    offset = 8
    width = height = bit_depth = color_type = None
    idat_chunks = []

    while offset + 8 <= len(png_bytes):
        length = struct.unpack(">I", png_bytes[offset:offset + 4])[0]
        ctype = png_bytes[offset + 4:offset + 8]
        data_start = offset + 8
        data_end = data_start + length
        if data_end + 4 > len(png_bytes):
            break
        cdata = png_bytes[data_start:data_end]
        if ctype == b"IHDR" and len(cdata) >= 13:
            width, height, bit_depth, color_type, comp, flt, interlace = struct.unpack(">IIBBBBB", cdata[:13])
            if comp != 0 or flt != 0 or interlace != 0:
                width = None
                break
        elif ctype == b"IDAT":
            idat_chunks.append(cdata)
        elif ctype == b"IEND":
            break
        offset = data_end + 4

    lower = direction_suffix.lower()
    if width is None or height is None or bit_depth != 8 or color_type not in (2, 6) or not idat_chunks:
        return _synthesize_badge_png(lower)

    channels = 4 if color_type == 6 else 3
    stride = width * channels
    raw = zlib.decompress(b"".join(idat_chunks))
    if len(raw) != height * (stride + 1):
        return _synthesize_badge_png(lower)

    recon = bytearray(height * stride)
    for y in range(height):
        src_row = y * (stride + 1)
        ftype = raw[src_row]
        dst_row = y * stride
        for i in range(stride):
            x = raw[src_row + 1 + i]
            a = recon[dst_row + i - channels] if i >= channels else 0
            b = recon[dst_row - stride + i] if y > 0 else 0
            c = recon[dst_row - stride + i - channels] if (y > 0 and i >= channels) else 0
            if ftype == 0:
                val = x
            elif ftype == 1:
                val = (x + a) & 0xFF
            elif ftype == 2:
                val = (x + b) & 0xFF
            elif ftype == 3:
                val = (x + ((a + b) >> 1)) & 0xFF
            elif ftype == 4:
                p = a + b - c
                pa, pb, pc = abs(p - a), abs(p - b), abs(p - c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                val = (x + pr) & 0xFF
            else:
                return _synthesize_badge_png(lower)
            recon[dst_row + i] = val

    # Deterministic color grading matching Android OfflineMockProviderAdapter
    out_raw = bytearray()
    for y in range(height):
        out_raw.append(0)  # filter type 0
        row_off = y * stride
        for x in range(width):
            idx = row_off + x * channels
            r, g, b = recon[idx], recon[idx + 1], recon[idx + 2]
            lum = 0.29 * r + 0.59 * g + 0.12 * b
            if "nocturnal" in lower or "blue" in lower:
                nr = max(0, min(255, int(round(18 + lum * 0.42))))
                ng = max(0, min(255, int(round(32 + lum * 0.68))))
                nb = max(0, min(255, int(round(60 + lum * 0.92))))
            elif "copper" in lower or "amber" in lower:
                nr = max(0, min(255, int(round(46 + lum * 0.92))))
                ng = max(0, min(255, int(round(20 + lum * 0.72))))
                nb = max(0, min(255, int(round(12 + lum * 0.45))))
            else:
                nr = max(0, min(255, int(round((lum - 110.0) * 1.45 + 132.0))))
                ng = max(0, min(255, int(round((lum - 110.0) * 1.38 + 128.0))))
                nb = max(0, min(255, int(round((lum - 110.0) * 1.25 + 122.0))))
            out_raw.extend((nr, ng, nb))
            if channels == 4:
                out_raw.append(recon[idx + 3])

    return _encode_png(width, height, color_type, zlib.compress(bytes(out_raw), level=6))


def _synthesize_badge_png(lower_dir: str) -> bytes:
    width, height = 16, 16
    if "nocturnal" in lower_dir or "blue" in lower_dir:
        rgba = (42, 92, 188, 255)
    elif "copper" in lower_dir or "amber" in lower_dir:
        rgba = (212, 118, 62, 255)
    else:
        rgba = (210, 206, 198, 255)
    row = b"\x00" + bytes(rgba) * width
    return _encode_png(width, height, 6, zlib.compress(row * height, level=6))


def _chunk(ctype: bytes, data: bytes) -> bytes:
    crc = zlib.crc32(ctype + data) & 0xFFFFFFFF
    return struct.pack(">I", len(data)) + ctype + data + struct.pack(">I", crc)


def _encode_png(width: int, height: int, color_type: int, idat: bytes) -> bytes:
    ihdr = struct.pack(">IIBBBBB", width, height, 8, color_type, 0, 0, 0)
    return PNG_SIGNATURE + _chunk(b"IHDR", ihdr) + _chunk(b"IDAT", idat) + _chunk(b"IEND", b"")


@dataclass
class JobEntry:
    jobId: str
    idempotencyKey: str
    providerId: str
    documentId: str
    baseRevision: int
    status: str
    processingMode: str
    resultPngBase64: Optional[str]
    message: str
    errorCode: str
    createdAtEpoch: float
    expiresAtEpoch: float
    sourceHash: str
    modelVersion: str

    def to_wire(self) -> Dict[str, Any]:
        return {
            "jobId": self.jobId,
            "idempotencyKey": self.idempotencyKey,
            "providerId": self.providerId,
            "documentId": self.documentId,
            "baseRevision": self.baseRevision,
            "status": self.status,
            "processingMode": self.processingMode,
            "resultPngBase64": self.resultPngBase64,
            "message": self.message,
            "errorCode": self.errorCode,
            "metadata": {
                "modelVersion": self.modelVersion,
                "sourceHash": self.sourceHash,
                "createdAtEpoch": int(self.createdAtEpoch),
                "expiresAtEpoch": int(self.expiresAtEpoch),
            },
        }


class CompanionState:
    def __init__(self, config: Optional[Dict[str, Any]] = None) -> None:
        self.config = config or _read_config()
        self._lock = threading.Lock()
        self._jobs: Dict[str, JobEntry] = {}
        self._idempotency: Dict[str, str] = {}
        self._paid_attempts = 0

    def purge_expired(self, now: Optional[float] = None) -> int:
        ts = now if now is not None else time.time()
        removed = 0
        with self._lock:
            expired_ids = [jid for jid, job in self._jobs.items() if job.expiresAtEpoch <= ts]
            for jid in expired_ids:
                job = self._jobs.pop(jid)
                self._idempotency.pop(job.idempotencyKey, None)
                removed += 1
        return removed

    def health_payload(self) -> Dict[str, Any]:
        remote_ready = self.config["mode"] == "REMOTE_OPENAI" and bool(self.config.get("openai_api_key")) and bool(self.config.get("paid_image_edit_enabled", False))
        return {
            "providerId": "loopback-companion",
            "status": "healthy",
            "offlineSafe": not remote_ready,
            "detail": (
                "Loopback companion active in deterministic local mock mode (zero paid calls)."
                if not remote_ready
                else "Loopback companion armed with host OpenAI key; explicit per-request consent still required."
            ),
        }

    def capabilities_payload(self) -> Dict[str, Any]:
        return {
            "providerId": "loopback-companion",
            "supportsImageEdit": True,
            "supportsOutpaint": True,
            "maxResolutionPx": 2048,
            "supportsCancel": True,
            "requiresExplicitConsentForRemote": True,
            "maxPayloadBytes": self.config["max_payload_bytes"],
            "retentionTtlSeconds": self.config["job_ttl_seconds"],
        }

    def submit_job(self, payload: Dict[str, Any]) -> Tuple[int, Dict[str, Any]]:
        self.purge_expired()
        required = ("idempotencyKey", "documentId", "baseRevision", "branchLabel", "prompt", "cropPngBase64")
        for field in required:
            if field not in payload:
                return HTTPStatus.BAD_REQUEST, self._error_record(
                    payload, "INVALID_RESPONSE", f"Missing required field: {field}"
                )

        idemp = str(payload["idempotencyKey"]).strip()
        if not idemp:
            return HTTPStatus.BAD_REQUEST, self._error_record(
                payload, "INVALID_RESPONSE", "idempotencyKey must be non-empty"
            )

        with self._lock:
            existing_id = self._idempotency.get(idemp)
            if existing_id and existing_id in self._jobs:
                return HTTPStatus.OK, self._jobs[existing_id].to_wire()

        expected_rev = payload.get("expectedRevision")
        base_rev = int(payload["baseRevision"])
        if expected_rev is not None and int(expected_rev) != base_rev:
            return HTTPStatus.CONFLICT, self._error_record(
                payload,
                "STALE_DOCUMENT_REVISION",
                f"Stale document revision {base_rev}; expected {expected_rev}",
            )

        try:
            crop_bytes = base64.b64decode(str(payload["cropPngBase64"]), validate=True)
        except Exception:
            return HTTPStatus.BAD_REQUEST, self._error_record(
                payload, "INVALID_RESPONSE", "cropPngBase64 is not valid base64"
            )

        if len(crop_bytes) < 24 or crop_bytes[:8] != PNG_SIGNATURE:
            return HTTPStatus.UNSUPPORTED_MEDIA_TYPE, self._error_record(
                payload, "INVALID_RESPONSE", "cropPngBase64 must decode to a valid image/png"
            )

        if payload.get("maskPngBase64"):
            try:
                mask_bytes = base64.b64decode(str(payload["maskPngBase64"]), validate=True)
                if len(mask_bytes) < 24 or mask_bytes[:8] != PNG_SIGNATURE:
                    raise ValueError("Invalid mask PNG header")
            except Exception:
                return HTTPStatus.UNSUPPORTED_MEDIA_TYPE, self._error_record(
                    payload, "INVALID_RESPONSE", "maskPngBase64 must decode to a valid image/png"
                )

        source_hash = hashlib.sha256(crop_bytes).hexdigest()
        now = time.time()
        ttl = max(30, int(self.config["job_ttl_seconds"]))
        direction = str(payload.get("directionSuffix", ""))
        mode = self.config["mode"]

        if mode == "REMOTE_OPENAI":
            if not bool(payload.get("userConfirmedPaidEgress", False)):
                return HTTPStatus.FORBIDDEN, self._error_record(
                    payload, "CONSENT_REQUIRED", "Explicit per-request remote-inference consent required."
                )
            if not self.config.get("paid_image_edit_enabled", False):
                return HTTPStatus.SERVICE_UNAVAILABLE, self._error_record(
                    payload, "PAID_EGRESS_DISABLED", "Host image-edit enable switch is off."
                )
            if not self.config.get("openai_api_key", ""):
                return HTTPStatus.SERVICE_UNAVAILABLE, self._error_record(
                    payload, "MISSING_API_KEY", "Host OpenAI credential has not been configured."
                )
            from remote_openai import ImageEditError, edit_png, ALLOWED_MODELS
            model = str(payload.get("modelId") or "gpt-image-2.6-flare")
            if model not in ALLOWED_MODELS:
                return HTTPStatus.BAD_REQUEST, self._error_record(
                    payload, "INVALID_RESPONSE", "Unsupported or unapproved image model."
                )
            with self._lock:
                if self._paid_attempts >= int(self.config.get("max_paid_requests", 1)):
                    return HTTPStatus.TOO_MANY_REQUESTS, self._error_record(
                        payload, "QUOTA_CAP_REACHED", "Host paid-request budget exhausted."
                    )
                self._paid_attempts += 1
            try:
                out_png = edit_png(
                    source_png=crop_bytes,
                    mask_png=mask_bytes if payload.get("maskPngBase64") else None,
                    prompt=str(payload["prompt"]),
                    model=model,
                    api_key=self.config["openai_api_key"],
                )
            except ImageEditError as error:
                return HTTPStatus.BAD_GATEWAY, self._error_record(
                    payload, "NETWORK_ERROR", str(error)
                )
            processing_mode = "REMOTE_OPENAI_IMAGE_EDIT"
            model_version = model
            response_detail = "Image edit returned by host provider; requires explicit local artist acceptance."
        else:
            out_png = _recolor_png_deterministic(crop_bytes, direction)
            processing_mode = "LOOPBACK_COMPANION_DETERMINISTIC"
            model_version = "companion-local-study-v1"
            response_detail = f"Loopback deterministic study completed for {payload['branchLabel']}. Not remote AI."

        out_b64 = base64.b64encode(out_png).decode("ascii")
        job_id = f"cmp_{uuid.uuid4().hex[:10]}"
        entry = JobEntry(
            jobId=job_id,
            idempotencyKey=idemp,
            providerId="loopback-companion",
            documentId=str(payload["documentId"]),
            baseRevision=base_rev,
            status="SUCCEEDED",
            processingMode=processing_mode,
            resultPngBase64=out_b64,
            message=response_detail,
            errorCode="NONE",
            createdAtEpoch=now,
            expiresAtEpoch=now + ttl,
            sourceHash=source_hash,
            modelVersion=model_version,
        )
        with self._lock:
            self._jobs[job_id] = entry
            self._idempotency[idemp] = job_id
        return HTTPStatus.OK, entry.to_wire()

    def get_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        self.purge_expired()
        with self._lock:
            job = self._jobs.get(job_id)
            return job.to_wire() if job else None

    def cancel_job(self, job_id: str) -> Optional[Dict[str, Any]]:
        self.purge_expired()
        with self._lock:
            job = self._jobs.get(job_id)
            if not job:
                return None
            job.status = "CANCELED"
            job.message = "Job canceled by client request."
            return job.to_wire()

    def delete_job(self, job_id: str) -> bool:
        with self._lock:
            job = self._jobs.pop(job_id, None)
            if not job:
                return False
            self._idempotency.pop(job.idempotencyKey, None)
            return True

    def _error_record(self, payload: Dict[str, Any], code: str, message: str) -> Dict[str, Any]:
        return {
            "jobId": f"err_{uuid.uuid4().hex[:8]}",
            "idempotencyKey": str(payload.get("idempotencyKey", "")),
            "providerId": "loopback-companion",
            "documentId": str(payload.get("documentId", "")),
            "baseRevision": int(payload.get("baseRevision", 0) or 0),
            "status": "FAILED",
            "processingMode": "LOOPBACK_COMPANION",
            "resultPngBase64": None,
            "message": message,
            "errorCode": code,
        }


def make_handler(state: CompanionState):
    class Handler(BaseHTTPRequestHandler):
        server_version = "InfiniteStudioCompanion/1.0"

        def log_message(self, format: str, *args: Any) -> None:
            return  # quiet by default for clean test output

        def _check_auth(self) -> bool:
            token = state.config["auth_token"]
            if not token:
                return True
            header = self.headers.get("Authorization", "")
            expected = f"Bearer {token}"
            return hmac.compare_digest(header, expected)

        def _send_json(self, status: int, body: Any) -> None:
            raw = json.dumps(body).encode("utf-8")
            self.send_response(status)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(raw)))
            self.send_header("Cache-Control", "no-store")
            self.end_headers()
            self.wfile.write(raw)

        def _read_json_body(self) -> Tuple[Optional[Dict[str, Any]], Optional[Tuple[int, str]]]:
            ctype = self.headers.get("Content-Type", "")
            if "application/json" not in ctype.lower():
                return None, (HTTPStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type must be application/json")
            try:
                length = int(self.headers.get("Content-Length", "0"))
            except ValueError:
                return None, (HTTPStatus.BAD_REQUEST, "Invalid Content-Length")
            max_len = state.config["max_payload_bytes"]
            if length <= 0 or length > max_len:
                return None, (HTTPStatus.REQUEST_ENTITY_TOO_LARGE, f"Payload size must be 1..{max_len} bytes")
            raw = self.rfile.read(length)
            try:
                parsed = json.loads(raw.decode("utf-8"))
                if not isinstance(parsed, dict):
                    raise ValueError("JSON root must be object")
                return parsed, None
            except Exception as exc:
                return None, (HTTPStatus.BAD_REQUEST, f"Malformed JSON body: {exc}")

        def do_GET(self) -> None:
            if not self._check_auth():
                self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "Unauthorized"})
                return
            if self.path == "/health":
                self._send_json(HTTPStatus.OK, state.health_payload())
            elif self.path == "/capabilities":
                self._send_json(HTTPStatus.OK, state.capabilities_payload())
            elif self.path.startswith("/jobs/"):
                job_id = self.path[len("/jobs/"):].strip("/")
                job = state.get_job(job_id)
                if job is None:
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "Job not found"})
                else:
                    self._send_json(HTTPStatus.OK, job)
            else:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown route"})

        def do_POST(self) -> None:
            if not self._check_auth():
                self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "Unauthorized"})
                return
            if self.path == "/jobs":
                body, err = self._read_json_body()
                if err:
                    self._send_json(err[0], {"error": err[1], "errorCode": "INVALID_RESPONSE"})
                    return
                assert body is not None
                status, resp = state.submit_job(body)
                self._send_json(status, resp)
            elif self.path.startswith("/jobs/") and self.path.endswith("/cancel"):
                job_id = self.path[len("/jobs/"):-len("/cancel")].strip("/")
                canceled = state.cancel_job(job_id)
                if canceled is None:
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "Job not found"})
                else:
                    self._send_json(HTTPStatus.OK, canceled)
            else:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown route"})

        def do_DELETE(self) -> None:
            if not self._check_auth():
                self._send_json(HTTPStatus.UNAUTHORIZED, {"error": "Unauthorized"})
                return
            if self.path.startswith("/jobs/"):
                job_id = self.path[len("/jobs/"):].strip("/")
                deleted = state.delete_job(job_id)
                if deleted:
                    self._send_json(HTTPStatus.OK, {"deleted": True, "jobId": job_id})
                else:
                    self._send_json(HTTPStatus.NOT_FOUND, {"error": "Job not found"})
            else:
                self._send_json(HTTPStatus.NOT_FOUND, {"error": "Unknown route"})

    return Handler


def main() -> None:
    cfg = _read_config()
    if cfg["host"] not in ("127.0.0.1", "localhost", "::1"):
        raise SystemExit(
            f"Refusing insecure non-loopback bind host '{cfg['host']}'. "
            "Companion binds strictly to loopback by default."
        )
    state = CompanionState(cfg)
    server = ThreadingHTTPServer((cfg["host"], cfg["port"]), make_handler(state))
    print(f"Infinite Studio Companion listening on http://{cfg['host']}:{cfg['port']} (mode={cfg['mode']})")
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == "__main__":
    main()
