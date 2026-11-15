#!/usr/bin/env python3
"""Loopback-only host for Infinite Studio Laboratory.

No API request is possible unless OPENAI_API_KEY and ALLOW_PAID_API=1 are both set.
The server intentionally has no API-key-setting route, persistent logs or upload archive.
"""
from __future__ import annotations

import base64
import binascii
import json
import os
import re
import secrets
import struct
import threading
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import urlsplit

ROOT = Path(__file__).resolve().parent
HOST = '127.0.0.1'
PORT = int(os.environ.get('STUDIO_PORT', '8765'))
OPENAI_URL = 'https://api.openai.com/v1/images/edits'
MODELS = {'gpt-image-2.6-flare', 'gpt-image-2.6-sunburst'}
MAX_JSON = 28 * 1024 * 1024
MAX_REQUESTS = max(0, min(int(os.environ.get('STUDIO_MAX_PAID_CALLS', '3')), 100))
ALLOW = os.environ.get('ALLOW_PAID_API') == '1'
KEY = os.environ.get('OPENAI_API_KEY', '').strip()
COUNTER_LOCK = threading.Lock()
COUNTER = {'calls': 0}


def png_details(raw: bytes):
    if len(raw) < 33 or raw[:8] != b'\x89PNG\r\n\x1a\n' or raw[12:16] != b'IHDR':
        raise ValueError('A valid PNG image is required')
    width, height = struct.unpack('>II', raw[16:24])
    if not (1 <= width <= 2048 and 1 <= height <= 2048):
        raise ValueError('PNG dimensions must be between 1 and 2048 pixels')
    return width, height, raw[25]


def decode_data_url(value, *, mask=False):
    if not isinstance(value, str) or not value.startswith('data:image/png;base64,'):
        raise ValueError('Expected an image/png base64 data URL')
    try:
        raw = base64.b64decode(value.partition(',')[2], validate=True)
    except (ValueError,binascii.Error) as exc:
        raise ValueError('Invalid base64 image') from exc
    if len(raw) > (4 if mask else 18) * 1024 * 1024:
        raise ValueError('Image exceeds its upload size limit')
    details = png_details(raw)
    if mask and details[2] not in (4, 6):
        raise ValueError('Edit mask needs an alpha channel')
    return raw, details


def multipart(fields, files):
    marker = '----Studio' + secrets.token_hex(15)
    chunks = []
    for key, val in fields.items():
        chunks.append((f'--{marker}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{val}\r\n').encode('utf-8'))
    for key, (filename, content) in files.items():
        chunks.extend([
            (f'--{marker}\r\nContent-Disposition: form-data; name="{key}"; filename="{filename}"\r\nContent-Type: image/png\r\n\r\n').encode('ascii'),
            content, b'\r\n'
        ])
    chunks.append(f'--{marker}--\r\n'.encode('ascii'))
    return b''.join(chunks), f'multipart/form-data; boundary={marker}'


class Handler(BaseHTTPRequestHandler):
    server_version = 'StudioLab/0.1'

    def log_message(self, fmt, *args):
        # Exclude request bodies, user prompts and API credentials from logs.
        if self.path.startswith('/api/edits'):
            print('Image edit request attempted')
        else:
            super().log_message(fmt, *args)

    def _reply(self, code, data):
        body = json.dumps(data, separators=(',', ':')).encode('utf-8')
        self.send_response(code)
        self.send_header('Content-Type', 'application/json; charset=utf-8')
        self.send_header('Content-Length', str(len(body)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.end_headers()
        self.wfile.write(body)

    def _same_origin(self):
        origin = self.headers.get('Origin')
        if not origin:
            return True
        try:
            parsed = urlsplit(origin)
            return parsed.scheme == 'http' and parsed.hostname in ('localhost', '127.0.0.1') and parsed.port == PORT
        except ValueError:
            return False

    def do_GET(self):
        path = urlsplit(self.path).path
        if path == '/api/health':
            return self._reply(200, {'provider': 'openai', 'can_generate': bool(ALLOW and KEY and MAX_REQUESTS > COUNTER['calls']),
                                     'paid_generation_enabled': ALLOW and bool(KEY),
                                     'remaining_call_limit': max(0, MAX_REQUESTS - COUNTER['calls'])})
        if path not in ('/', '/index.html'):
            return self._reply(404, {'error': 'Not found'})
        html = (ROOT / 'index.html').read_bytes()
        self.send_response(200)
        self.send_header('Content-Type', 'text/html; charset=utf-8')
        self.send_header('Content-Length', str(len(html)))
        self.send_header('Cache-Control', 'no-store')
        self.send_header('X-Content-Type-Options', 'nosniff')
        self.send_header('Referrer-Policy', 'no-referrer')
        self.send_header('X-Frame-Options', 'DENY')
        self.end_headers()
        self.wfile.write(html)

    def do_POST(self):
        if urlsplit(self.path).path != '/api/edits':
            return self._reply(404, {'error': 'Not found'})
        if not self._same_origin():
            return self._reply(403, {'error': 'Invalid request origin'})
        # Enforce the cost gate before reading potentially large request bodies.
        if not (ALLOW and KEY):
            return self._reply(403, {'error': 'Paid API requests are disabled on this server'})
        content_len = self.headers.get('Content-Length', '')
        try:
            size = int(content_len)
        except (ValueError, TypeError):
            return self._reply(411, {'error': 'Content-Length required'})
        if self.headers.get('Content-Type', '').split(';')[0] != 'application/json' or size < 2 or size > MAX_JSON:
            return self._reply(413, {'error': 'Request must be bounded JSON'})
        try:
            data = json.loads(self.rfile.read(size))
            model = data.get('model')
            prompt = data.get('prompt')
            if model not in MODELS or not isinstance(prompt, str) or not 1 <= len(prompt) <= 2500:
                raise ValueError('Invalid model or prompt')
            image_raw, info = decode_data_url(data.get('image'))
            mask_raw = None
            if data.get('mask') is not None:
                mask_raw, mask_info = decode_data_url(data['mask'], mask=True)
                if mask_info[:2] != info[:2]:
                    raise ValueError('Mask and image dimensions differ')
        except (ValueError, TypeError, KeyError, json.JSONDecodeError) as exc:
            return self._reply(400, {'error': str(exc)[:200]})

        with COUNTER_LOCK:
            if COUNTER['calls'] >= MAX_REQUESTS:
                return self._reply(429, {'error': 'Paid-call safety cap reached; restart with deliberate new configuration if intended'})
            COUNTER['calls'] += 1  # reserve a slot *before* external request

        fields = {'model': model, 'prompt': prompt, 'quality': 'medium', 'complexity': 'medium', 'output_format': 'png'}
        files = {'image[]': ('canvas.png', image_raw)}
        if mask_raw is not None:
            files['mask'] = ('mask.png', mask_raw)
        body, mime = multipart(fields, files)
        req = urllib.request.Request(OPENAI_URL, data=body, method='POST', headers={
            'Authorization': 'Bearer '+KEY, 'Content-Type': mime, 'Accept': 'application/json'
        })
        try:
            with urllib.request.urlopen(req, timeout=160) as res:
                payload = json.loads(res.read(34 * 1024 * 1024))
            base64_png = payload['data'][0]['b64_json']
            if not isinstance(base64_png, str):
                raise ValueError('API returned an invalid image')
            return self._reply(200, {'image': 'data:image/png;base64,'+base64_png, 'model': model})
        except urllib.error.HTTPError as exc:
            # Never log or echo prompts, base64 data, headers, or credentials.
            return self._reply(502, {'error': f'OpenAI API rejected the edit (HTTP {exc.code})'})
        except Exception:
            return self._reply(502, {'error': 'Model request failed; the paid-call safety slot remains consumed'})


def main():
    server = ThreadingHTTPServer((HOST, PORT), Handler)
    print(f'Studio Laboratory: http://{HOST}:{PORT}/')
    print('Paid requests:', 'ENABLED with finite cap' if ALLOW and KEY else 'DISABLED (safe default)')
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()


if __name__ == '__main__':
    main()
