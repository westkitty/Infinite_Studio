# Infinite Studio — The Futures Table (Native Android)

**One Mark, Three Futures, Zero Sacrificed Originals.**

Native Kotlin + Jetpack Compose + Material 3 creative studio engineered for **Samsung Galaxy Tab S9 Ultra** (landscape-first, S-Pen responsive, offline-first, zero embedded credentials).

---

## Key Capabilities
- **S-Pen-First Canvas & Sparse Tile Rendering:** `256 × 256` sparse PNG tile architecture (`SparseTileRenderer`), lazy allocation within configurable bounds, viewport culling, `48 MB` LRU bitmap cache, cached `BitmapShader` checkerboard & preallocated `Paint` instances in `InfiniteCanvasView`, hardware-stylus vs touch/mouse discrimination, historical batched `MotionEvent` samples, hover crosshair, tilt/pressure curves, S-Pen Guard (resting palm protection), and `ACTION_CANCEL` rollback (`StylusInputRouter`).
- **Real Brush Engine & Brush Studio:** Pressure-responsive `Pencil`, `Technical Ink`, `Marker`, `Watercolor`, `Airbrush`, `Smudge`, `Eraser`, `AI Brush Guidance`, symmetry modes (`None`, `Vertical`, `Horizontal`, `Quadrant`, `Kaleidoscope`), shape guides, scanline flood fill, eyedropper, and editable presets (`TileBrushEngine`, `BrushStudioDialog`).
- **The Futures Table (`P01`) & Constraints (`P03`):** Paint or import artwork -> mark a region -> compare 3 deterministic **local** color studies (`Salt Print`, `Blue Hour`, `Copper Light`) side-by-side or with an interactive peel scrubber -> accept onto a brand-new layer strictly clipped to the selection polygon and outside protected constraints (**0 outside-mask pixels**, original layer hashes unchanged).
- **Integrated 20-System Creative Hub (`StudioWorkspaceSheet`):**
  - `F01` Object Surgery · `F02` Spatial Stage · `F03` Living Preview · `F04` Creative Recipes · `F05` Time Canvas
  - `F06` Material Intelligence · `F07` Visual Forensics · `F08` Production Foundry · `F09` Vector & Typography Studio · `F10` Visual Memory Vault
  - `P02` AI Brush · `P04` Character Studio · `P05` Local Art Director · `P06` Local Outpainting · `P07` Immutable Branches · `P08` World Orchard · `P09` Asset Workshop · `P10` Zero-Credential Provider Router *(See `BUILD_STATUS.md` for honest `VERIFIED` vs `PARTIAL` feature audit.)*
- **Portable `.iastudio` v3 Archives & Lossless v2 Migration:** ZIP-based manifest + per-tile PNG + SHA-256 checksums, zip-slip / decompression bomb guards, atomic local drafts with recovery journal, Android SAF open/save, and zero-permission Photo Picker import.
- **Hidden Developer Diagnostics:** 5-tap the `INFINITE` brand badge in the top bar to inspect document revision, tile cache, history heap usage, S-Pen telemetry, zero-secret boundary status, and redacted error logs.

---

## Setup, Build & Test

### Android App
```bash
# Compile debug APK cleanly (always use system gradle; do not use ./gradlew)
gradle assembleDebug

# Execute local Robolectric SDK 35 + Native Graphics unit, UI, & credential-boundary suite (26/26)
gradle :app:testDebugUnitTest
```

### Offline Sample Project
Open **Project Vault** (folder icon in top bar) -> tap **Load Offline Sample (One Mark, Three Futures)** -> confirm to inspect a full multi-layer canvas with Futures study, protected constraint, vector speech balloon, material pass, branch fork, recipe results, and timeline frames.

### Optional Loopback Companion (`companion/`)
```bash
python3 companion/server.py
python3 -m unittest discover -s companion -p "test_*.py" -v
```
Binds strictly to `127.0.0.1:8787` in `MOCK_DETERMINISTIC` mode by default. All upstream API credentials (if optionally enabled by a host operator) live exclusively on the host companion environment (`companion/.env.example`) — **zero credentials exist in the Android client or APK**.

---

## Known Environment & Scope Limitations
- **Physical Tab S9 Ultra Hardware & Interactive Emulator `framestats`:** Real Wacom EMR pressure curves, air hover, resting-palm ergonomics, and 120 Hz frame pacing (`dumpsys gfxinfo framestats`) could not be executed in the AI Studio cloud build sandbox (which exposes no ADB shell or interactive device control; `TAB_S9_ULTRA_DEVICE_QA.md` marks `D01`–`D09` as `NOT_RUN_NO_DEVICE`).
- **Paid Cloud Inference:** `ProviderMode.OFFLINE_LOCAL_ONLY` is the default, and `OPENAI_API_KEY` has been completely eliminated from the Android client. External provider workflows are verified offline through `OfflineMockProviderAdapter`, JVM loopback socket tests, and `companion/test_companion.py`.
- **Launcher / Repository Notes:** This workspace contains the rewritten single-module native Android project, companion service (`companion/`), schemas/fixtures (`schema/`), and contract documentation. Upstream web baseline `original_source.html` remains preserved on GitHub `main` (`westkitty/Infinite_Studio`).
