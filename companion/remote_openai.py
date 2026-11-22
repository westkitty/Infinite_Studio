"""Optional host-side OpenAI image edits for Infinite Studio.

This module never executes during import. The companion's offline mode remains the default.
A request may incur real charges only after multiple independent host and user gates.
"""
from __future__ import annotations

import base64
import json
import os
import secrets
import struct
import urllib.error
import urllib.request
from typing import Callable, Optional

PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"
API_URL = "https://api.openai.com/v1/images/edits"
ALLOWED_MODELS = frozenset({
    "gpt-image-2.6-flare",
    "gpt-image-2.6-sunburst",
    "gpt-image-2.5-flare",
    "gpt-image-2.5-sunburst",
})


class ImageEditError(Exception):
    """Safe, redacted provider error for caller-side reporting."""


def png_dimensions(blob: bytes) -> tuple[int, int]:
    if len(blob) < 33 or blob[:8] != PNG_SIGNATURE or blob[12:16] != b"IHDR":
        raise ImageEditError("Invalid PNG header")
    w, h = struct.unpack(">II", blob[16:24])
    if not (1 <= w <= 4096 and 1 <= h <= 4096):
        raise ImageEditError("PNG dimensions outside protected range")
    return w, h


def _part(name: str, value: bytes, boundary: str, filename: Optional[str] = None) -> bytes:
    if filename:
        disposition = f'Content-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
        header = disposition + "Content-Type: image/png\r\n\r\n"
    else:
        header = f'Content-Disposition: form-data; name="{name}"\r\n\r\n'
    return f"--{boundary}\r\n".encode() + header.encode() + value + b"\r\n"


def edit_png(
    *, source_png: bytes, mask_png: Optional[bytes], prompt: str, model: str,
    api_key: str, urlopen: Callable = urllib.request.urlopen,
) -> bytes:
    """Send exactly one image edit with fixed URL, bounded data, and no implicit retries."""
    if model not in ALLOWED_MODELS:
        raise ImageEditError("Unsupported image-edit model")
    if not api_key or len(prompt.strip()) < 1 or len(prompt) > 32000:
        raise ImageEditError("Missing credential or invalid prompt")
    source_size = png_dimensions(source_png)
    if len(source_png) >= 50 * 1024 * 1024:
        raise ImageEditError("Source PNG exceeds provider upload limit")
    if mask_png is not None:
        if png_dimensions(mask_png) != source_size:
            raise ImageEditError("Mask dimensions do not match source image")
        if len(mask_png) >= 4 * 1024 * 1024:
            raise ImageEditError("Mask PNG exceeds protected upload limit")
        if mask_png[25] not in (4, 6):
            raise ImageEditError("Edit mask must have an alpha channel")
    boundary = "infinite-studio-" + secrets.token_hex(16)
    payload = b"".join([
        _part("model", model.encode(), boundary),
        _part("prompt", prompt.encode(), boundary),
        _part("n", b"1", boundary),
        _part("output_format", b"png", boundary),
        _part("image", source_png, boundary, "source.png"),
        *([_part("mask", mask_png, boundary, "mask.png")] if mask_png is not None else []),
        f"--{boundary}--\r\n".encode(),
    ])
    request = urllib.request.Request(
        API_URL,
        data=payload,
        headers={
            "Authorization": "Bearer " + api_key,
            "Content-Type": "multipart/form-data; boundary=" + boundary,
            "Accept": "application/json",
        },
        method="POST",
    )
    try:
        with urlopen(request, timeout=90) as response:
            # Maximum 24 MiB decoded JSON; do not leak provider response details or key.
            body = response.read(24 * 1024 * 1024 + 1)
        if len(body) > 24 * 1024 * 1024:
            raise ImageEditError("Provider response exceeded size limit")
        parsed = json.loads(body)
        img_b64 = parsed["data"][0]["b64_json"]
        raw = base64.b64decode(img_b64, validate=True)
        png_dimensions(raw)
        return raw
    except ImageEditError:
        raise
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError):
        raise ImageEditError("Image edit provider unavailable or rejected the request") from None
    except (ValueError, TypeError, KeyError, IndexError):
        raise ImageEditError("Invalid image edit provider response") from None
