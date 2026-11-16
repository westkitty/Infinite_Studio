import base64
import json
import struct
import threading
import time
import unittest
import urllib.error
import urllib.request
import zlib
from http.server import ThreadingHTTPServer

from companion.server import CompanionState, PNG_SIGNATURE, _encode_png, make_handler


def _make_sample_png_b64() -> str:
    # 4x4 bright sky-blue RGBA PNG
    row = b"\x00" + bytes((56, 189, 248, 255)) * 4
    png = _encode_png(4, 4, 6, zlib.compress(row * 4))
    return base64.b64encode(png).decode("ascii")


class CompanionServerTest(unittest.TestCase):
    def setUp(self) -> None:
        self.state = CompanionState(
            {
                "host": "127.0.0.1",
                "port": 0,
                "mode": "MOCK_DETERMINISTIC",
                "auth_token": "test-secret-token",
                "max_payload_bytes": 1024 * 1024,
                "job_ttl_seconds": 60,
                "openai_api_key": "",
            }
        )
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), make_handler(self.state))
        self.port = self.server.server_address[1]
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()

    def _request(self, method: str, path: str, body=None, token="test-secret-token"):
        url = f"http://127.0.0.1:{self.port}{path}"
        headers = {}
        data = None
        if token is not None:
            headers["Authorization"] = f"Bearer {token}"
        if body is not None:
            headers["Content-Type"] = "application/json"
            data = json.dumps(body).encode("utf-8")
        req = urllib.request.Request(url, data=data, headers=headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=5) as resp:
                return resp.status, json.loads(resp.read().decode("utf-8"))
        except urllib.error.HTTPError as err:
            return err.code, json.loads(err.read().decode("utf-8"))

    def test_auth_required_and_health_capabilities(self) -> None:
        code, _ = self._request("GET", "/health", token=None)
        self.assertEqual(code, 401)

        code, health = self._request("GET", "/health")
        self.assertEqual(code, 200)
        self.assertEqual(health["providerId"], "loopback-companion")
        self.assertTrue(health["offlineSafe"])

        code, caps = self._request("GET", "/capabilities")
        self.assertEqual(code, 200)
        self.assertTrue(caps["supportsCancel"])
        self.assertTrue(caps["requiresExplicitConsentForRemote"])

    def test_submit_idempotency_status_cancel_delete(self) -> None:
        sample_b64 = _make_sample_png_b64()
        payload = {
            "idempotencyKey": "idem-001",
            "documentId": "doc_test",
            "baseRevision": 3,
            "branchLabel": "Blue Hour",
            "directionSuffix": "nocturnal blue",
            "prompt": "Explore cool lighting",
            "cropPngBase64": sample_b64,
        }
        code, job1 = self._request("POST", "/jobs", body=payload)
        self.assertEqual(code, 200)
        self.assertEqual(job1["status"], "SUCCEEDED")
        out_png = base64.b64decode(job1["resultPngBase64"])
        self.assertTrue(out_png.startswith(PNG_SIGNATURE))
        self.assertNotEqual(job1["resultPngBase64"], sample_b64)

        # Idempotent retry returns same jobId
        code, job2 = self._request("POST", "/jobs", body=payload)
        self.assertEqual(code, 200)
        self.assertEqual(job1["jobId"], job2["jobId"])

        # Fetch by ID
        code, fetched = self._request("GET", f"/jobs/{job1['jobId']}")
        self.assertEqual(code, 200)
        self.assertEqual(fetched["idempotencyKey"], "idem-001")

        # Cancel transitions status cleanly
        code, canceled = self._request("POST", f"/jobs/{job1['jobId']}/cancel", body={})
        self.assertEqual(code, 200)
        self.assertEqual(canceled["status"], "CANCELED")

        # Explicit retention delete removes job
        code, deleted = self._request("DELETE", f"/jobs/{job1['jobId']}")
        self.assertEqual(code, 200)
        self.assertTrue(deleted["deleted"])

        code, missing = self._request("GET", f"/jobs/{job1['jobId']}")
        self.assertEqual(code, 404)

    def test_validation_stale_revision_bad_mime_and_consent_gate(self) -> None:
        # Bad non-PNG data rejected with 415
        code, err = self._request(
            "POST",
            "/jobs",
            body={
                "idempotencyKey": "idem-bad",
                "documentId": "doc_test",
                "baseRevision": 1,
                "branchLabel": "Salt Print",
                "prompt": "test",
                "cropPngBase64": base64.b64encode(b"not-a-png-file-at-all-0000").decode("ascii"),
            },
        )
        self.assertEqual(code, 415)
        self.assertEqual(err["errorCode"], "INVALID_RESPONSE")

        # Stale revision rejected with 409
        code, stale = self._request(
            "POST",
            "/jobs",
            body={
                "idempotencyKey": "idem-stale",
                "documentId": "doc_test",
                "baseRevision": 1,
                "expectedRevision": 2,
                "branchLabel": "Salt Print",
                "prompt": "test",
                "cropPngBase64": _make_sample_png_b64(),
            },
        )
        self.assertEqual(code, 409)
        self.assertEqual(stale["errorCode"], "STALE_DOCUMENT_REVISION")

        # Remote mode without consent blocked with 403
        self.state.config["mode"] = "REMOTE_OPENAI"
        code, consent_err = self._request(
            "POST",
            "/jobs",
            body={
                "idempotencyKey": "idem-consent",
                "documentId": "doc_test",
                "baseRevision": 1,
                "branchLabel": "Remote",
                "prompt": "test",
                "cropPngBase64": _make_sample_png_b64(),
                "userConfirmedPaidEgress": False,
            },
        )
        self.assertEqual(code, 403)
        self.assertEqual(consent_err["errorCode"], "CONSENT_REQUIRED")


if __name__ == "__main__":
    unittest.main()
