# Baseline verification - 2026-11-15

Source verification:
- 14 text files matched the archived Drive source snapshot and original Futures Table ZIP byte-for-byte.
- 4 preview PNG files copied unchanged from the Futures Table ZIP.
- Original user source SHA-256 verified against the governing original hash lock.

Executed in a Linux container with Python 3.13.5 and headless Chromium:
- python3 -m unittest discover -s tests -p 'test_*.py' -v: 5 passed.
- python3 -m py_compile server.py: passed.
- python3 tests/run_browser_smoke.py: completed with zero browser JavaScript errors; selection mask, three distinct candidates, accept, original-layer pixel preservation, undo/redo and editable project save/load passed.
- python3 tests/run_extra_browser_checks.py: viewport 800x1280 and 390x844 passed with no JS errors; transparent region fill and undo passed.

Caveats:
- Headless Chromium uses document injection because managed navigation policy blocks direct browser navigation. Not a real Android or production network test.
- No physical S-Pen tests, no live OpenAI billing, no API key, no deployment.
- GitHub remote verification is separate from local source verification.
