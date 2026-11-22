import base64
import io
import json
import struct
import unittest
from remote_openai import ImageEditError, edit_png, png_dimensions


def tiny_png(w=1, h=1, color_type=6):
    # Valid PNG pixels are not needed for the transport-only contract test.
    return b'\x89PNG\r\n\x1a\n' + b'\x00\x00\x00\rIHDR' + struct.pack('>II', w, h) + bytes([8, color_type, 0, 0, 0]) + b'\x00\x00\x00\x00'


class Response:
    def __init__(self, payload): self.payload = payload
    def __enter__(self): return self
    def __exit__(self, *exc): return None
    def read(self, n): return self.payload[:n]


class RemoteEditTests(unittest.TestCase):
    def test_no_request_on_invalid_mask_or_model(self):
        calls = []
        def fake(*a, **k): calls.append(1)
        with self.assertRaises(ImageEditError):
            edit_png(source_png=tiny_png(), mask_png=tiny_png(2,1), prompt='Edit', model='gpt-image-2.6-flare', api_key='fake', urlopen=fake)
        with self.assertRaises(ImageEditError):
            edit_png(source_png=tiny_png(), mask_png=None, prompt='Edit', model='not-approved-model', api_key='fake', urlopen=fake)
        self.assertEqual(calls, [])

    def test_multipart_contract_and_single_request(self):
        calls = []
        def fake(request, timeout):
            calls.append((request, timeout))
            payload = json.dumps({'data':[{'b64_json':base64.b64encode(tiny_png()).decode()}]}).encode()
            return Response(payload)
        out = edit_png(source_png=tiny_png(), mask_png=tiny_png(), prompt='Change color', model='gpt-image-2.6-flare', api_key='fake', urlopen=fake)
        self.assertEqual(png_dimensions(out), (1, 1))
        self.assertEqual(len(calls), 1)
        request, timeout = calls[0]
        self.assertEqual(request.full_url, 'https://api.openai.com/v1/images/edits')
        self.assertIn(b'name="mask"', request.data)
        self.assertIn(b'name="model"', request.data)
        self.assertEqual(timeout, 90)

    def test_alpha_mask_required(self):
        with self.assertRaises(ImageEditError):
            edit_png(source_png=tiny_png(), mask_png=tiny_png(color_type=2), prompt='Edit', model='gpt-image-2.6-sunburst', api_key='fake')


if __name__ == '__main__': unittest.main()
