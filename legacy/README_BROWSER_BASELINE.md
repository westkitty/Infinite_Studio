# Infinite Studio — The Futures Table

A pressure-aware, layered canvas for the Samsung Galaxy Tab S9 Ultra and other browsers, paired with a reversible image-iteration table and an optional server-side OpenAI image-edit connector.

The default workflow is intentionally free of API calls. Draw, choose a rectangle or lasso, open **Futures**, inspect three distinct **local color studies**, peel between the original and a candidate, then accept a candidate as a new editable layer or discard it. The studies are CPU-produced visual previews; they are *not* AI generations. When you deliberately enable the API connector and confirm an individual call, the selected study slot can instead display a GPT Image result.

## Files

- `index.html` — runnable, self-contained browser editor (no runtime CDN, no font files)
- `server.py` — optional loopback-only HTTP host and guarded server-side Image API edit proxy
- `original_source.html` — byte-for-byte copy of the supplied editor (SHA-256 `0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76`)
- `laboratory.js`, `laboratory.css`, `laboratory.ui.html` — isolated extension sources
- `build.py` — repeatable source assembly (requires locally installed Tailwind CSS 4 and Font Awesome Free SVG npm packages)
- `tests/` — browser journey and backend regression checks, with simulated upstream responses
- `AWE_BRIEF.md` — three challenger mechanisms and the selected signature experience
- `QA_REPORT.md` — evidence and remaining gaps

## Start locally

Use the browser-openable `index.html` alone for drawing, local color studies, and downloadable editable projects. This mode has no live AI backend.

For same-machine serving, from this folder run:

```sh
python3 server.py
```

Open `http://127.0.0.1:8765/` in that machine's browser. Without explicit opt-in, server-side OpenAI image calls remain disabled.

For *deliberately authorized* paid model calls, configure a secret `OPENAI_API_KEY` in the server process environment, set `ALLOW_PAID_API=1`, and set an intentionally small `STUDIO_MAX_PAID_CALLS` (default maximum: 3 per server process). Restart the server. Confirm every individual request in the browser before the request is made. **Do not put an API key in HTML, browser settings, a Git repository, or the project ZIP.** API requests can incur charges. A call-count cap is not a monetary cap; because image costs vary, keep provider billing limits and available credit checks in place separately. This local prototype does not fetch real-time account balances.

The backend intentionally binds to `127.0.0.1`. To operate from a Samsung tablet while the backend runs on the Big Mac, a separately designed authenticated HTTPS/private-network path is required. Do not expose this development server directly to a LAN, a Tailscale interface, or the public internet: it has no account-based authentication, CSRF token, or TLS.

## Core interaction

1. Paint using the existing pens, brushes, symmetry or shape guides.
2. Select an area using **AI Box** or **AI Lasso**; alternatively use **AI Canvas** for the whole piece.
3. Tap **Futures** (or the former **Render** action). Three local color studies appear immediately. Select each and drag the comparison slider to inspect the change.
4. To apply it, choose **Accept as a new layer**. Nothing on the original layers is flattened. To cancel, choose **Discard without changes**.
5. To preserve the entire editable drawing, choose **Project → Save editable project**. Reopen a `.iastudio` file through **Project → Open a project**.
6. When supported, the browser also stores an asynchronous local recovery draft; this is not a reliable backup and does not replace saving a file.

PNG/JPG export and image import from the original editor remain available. Undo/redo now uses bounded compressed snapshots of the complete document, so layer creation and future acceptance can be undone without orphaning the layers. History is limited to nine snapshots and roughly 42 MB of encoded images to manage memory; very large documents may still exceed these limits.

## Technical boundaries

- The image-edit mask uses a PNG alpha channel. Fully transparent mask pixels indicate the region the model may modify; the surrounding context is passed as a cropped image. The result is independently clipped to the original polygon/rectangle **during commit**. Model compliance with the mask is approximate, but the local compositing boundary is exact.
- The canvas remains a **bounded raster canvas**, currently at most 8,000 pixels in either dimension. It is not a sparse tiled renderer; very large layers can still be expensive in memory.
- Imported raster images are already downscaled by the original editor. The futures table also limits the model input image to a 1,536-pixel longest side; an accepted result is scaled back to the selection's world coordinates.
- Real OpenAI image quality, organization/model eligibility, provider billing, account credit availability, mobile S-Pen pressure, IndexedDB behavior under Android Chrome, long-session memory growth, and network transport from the actual tablet were **not** validated in this container.
- No images, drawings, or project content are uploaded by the local-study path. With API enabled, the deliberately selected crop and mask are sent to OpenAI per confirmed call.
- Save a project file before experimenting with unusually large scenes. The local recovery draft is best-effort only.

## Testing and proof

```sh
python3 -m unittest discover -s tests -p 'test_*.py' -v
python3 tests/run_browser_smoke.py
python3 tests/run_extra_browser_checks.py
```

Browser tests use an isolated Chromium document-injection harness because this evaluation container has an administrator-wide URL-navigation block. They verify UI behavior, not real network navigation or production mobile hardware. Backend unit tests include a fake upstream that records the assembled multipart request, proves mask upload and call-count enforcement, and makes **no OpenAI request**. See `QA_REPORT.md` for exact states.

## Design and licensing

The bundled HTML includes its own compiled Tailwind CSS utilities and inline SVG data for icons, so it does not ship font files or depend on a CDN while running. Tailwind CSS is MIT-licensed. Font Awesome Free SVG icons used by the editor are licensed under CC BY 4.0; attribution: Font Awesome by Fonticons, Inc. The original user-supplied drawing application remains in `original_source.html`.

Official OpenAI reference: https://developers.openai.com/api/docs/guides/image-generation
