# Repair Queue — Infinite Studio Native Android (Living Workshop Uplift)

Ranked status of closed Living Workshop uplift tasks, remaining environment-blocked hardware/emulator verifications, and incremental future work for `HANDOFF_FOR_CHATGPT.md`.

---

## Closed Priorities (Verified in Uplift RC2)

- **`RQ-SEC-01` Client Credential Boundary Fix (`RESOLVED`):**
  - Deleted root `.env.example`, removed `secrets-gradle-plugin` and `REMOTE_OPT_IN_OPENAI`, and verified `CredentialBoundaryAndBuildArtifactTest` (`3/3 PASSED`) scanning source, Gradle, `BuildConfig`, and `app-debug.apk`.
- **`RQ-P1-01` Expose Existing Engine Helpers in `StudioWorkspaceSheet` (`F10`, `P04`, `P07`) (`RESOLVED`):**
  - Wired multi-filter Vault search + empty/no-results states (`F10`), full Character Studio editor + manual `userApprovedCanon` switch (`P04`), and Creative Branches diff/cherry-pick controls in both `FuturesLoomDialog` and Tab 4 (`P07`).
- **`RQ-P1-02` Interactive Direct-Manipulation & Deeper Control Panels (`F01`, `F02`, `F04`, `F06`, `F09`, `P06`, `P10`) (`RESOLVED`):**
  - `F01` Object Surgery: on-canvas drag/scale/rotate gizmo (`TRANSFORM_*`), Snap/Cancel/Apply dock, `+Mask`/`-Mask` refine.
  - `F02` Spatial Stage: Yaw + Pitch + Zoom sliders, Stage Guides switch, Camera Reset button.
  - `F04` Creative Recipes: interactive visual Bezier DAG editor (`RecipeGraphEditor`) with drag, tap-to-link cycle rejection, `CACHED`/`INVALIDATED`/`ERROR` badges, param editing/validation, and preset save/select.
  - `F06` Material Intelligence: added elevation, emissive `#Hex` + intensity, mask-bound preview (`renderMaterialLitPreview`), defaults reset, and non-destructive `MaterialRegion` save.
  - `F09` Vector & Typography: `VECTOR_EDIT` on-canvas move/resize/Bezier anchor & handle drag, balloon `tailTip` drag, snap crosshairs, property editor.
  - `P06` Outpainting: custom L/R/T/B tile expansion + context preview with `"DETERMINISTIC LOCAL EDGE STUDY"` disclosure.
  - `P10` Secure Provider Router: `/health` ping button, active job cancel, 1-tap offline local fallback, privacy disclosure.
- **`RQ-P2-01` Replace Small Modal Checkerboard Loops (`ExportDialog`, `FuturesTableDialog`) (`RESOLVED`):**
  - Replaced both ~200-rect Compose `Canvas` loops with cached `ImageBitmap` `ShaderBrush` tiles in `CheckerboardBox.kt` (`CheckerboardBackground`).

---

## P0 — Hardware & Live Emulator Verification (Environment-Blocked Here)

1. **`RQ-P0-01` Physical Galaxy Tab S9 Ultra S-Pen & Live Emulator `framestats` QA**
   - **Area:** `input/StylusInputRouter.kt`, `ui/canvas/InfiniteCanvasView.kt`, `viewmodel/StudioViewModel.kt`
   - **Cause / Gap:** AI Studio cloud sandbox provides no ADB bridge or interactive emulator shell access (`TAB_S9_ULTRA_DEVICE_QA.md`). Diagnostics now record `lastFrameDrawMicros`, `emaFrameDrawMicros`, and `hitRatioPercent`, and all 32 synthetic/Robolectric unit + Compose UI tests pass, but live Android runtime UI interaction and `adb shell dumpsys gfxinfo framestats` (separating cold-start ART verification from sustained 120 Hz canvas drawing) cannot execute in this sandbox.
   - **Next Action:** Install `app/build/outputs/apk/debug/app-debug.apk` on emulator and physical SM-X910/SM-X916B via USB and execute `D01`–`D08` with `adb shell dumpsys gfxinfo com.aistudio.infinitestudio.kqvxzm framestats`.

2. **`RQ-P0-02` Live Loopback Companion USB Tunneling (`adb reverse`)**
   - **Area:** `services/ProviderRouter.kt`, `companion/server.py`
   - **Cause / Gap:** Companion server Python suite passed (`3/3`), and Android OkHttp client passed real localhost socket `/health` & job calls against a JVM test server, while physical USB `adb reverse tcp:8787 tcp:8787` from tablet to workstation Python could not run without ADB.
   - **Next Action:** Run `python3 companion/server.py`, execute `adb reverse tcp:8787 tcp:8787`, switch `ProviderRouter` to `LOOPBACK_COMPANION`, ping health, confirm dispatch in `FuturesTableDialog`, and record provenance (`D09`).

---

## P1 — Intentional Headless Codec & Branch Hygiene Boundaries

3. **`RQ-P1-04` Native `MediaCodec` MP4 Video Export (`F05` Time Canvas)**
   - **Gap:** Time Canvas now has lifecycle-safe real-time looping playback (`playbackJob`), duplicate/delete/loop range, dual onion-skin, keyframe editing, and verified horizontal PNG sprite sheet export. Native hardware `MediaCodec` + `MediaMuxer` MP4 export remains unimplemented/excluded for deterministic headless portability.
   - **Next Action:** Implement `MediaCodec` + `MediaMuxer` surface encoder on physical device targets when MP4 video export is required, gating behind API capability checks.

4. **`RQ-P1-03` Legacy Repository File Reconciliation (Git Branch Hygiene)**
   - **Gap:** Migration commit `db230c8` removed ten web prototype files from GitHub `main`; commit `32c6db3` restored all ten original Git blobs unchanged on recovery branch `reconcile/restore-original-and-native` (protected `original_source.html` SHA-256 `0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76`).
   - **Next Action:** Push/merge this native Android deliverable into a dedicated native branch or `android/` directory without overwriting `main`.

---

## P2 — Incremental Enhancements

5. **`RQ-P2-02` Disk Spillover Tier for Large History Stacks (`document/TransactionEngine.kt`)**
   - **Detail:** Adding temporary PNG spillover to `cacheDir/history/` would extend deep undo beyond the current in-memory 48 MB / 24-step cap on 16k×16k canvases.
6. **`RQ-P2-03` AndroidX Ink (`androidx.ink`) Low-Latency Front-Buffer Adoption (`ui/canvas/InfiniteCanvasView.kt`)**
   - **Detail:** Evaluating `CanvasFrontBufferedRenderer` on physical Tab S9 Ultra One UI hardware after `D01`–`D08` baseline validation.


## 2026-11-16 engine repair closure

- **G03 critical provisional alignment/eraser preview**: patched (`InfiniteCanvasView.kt`), five-case regression suite includes live preview alignment, eraser restoration and cancellation; GitHub Actions run `43820160709` passed. World-aligned bounded preview raster is built on input, not re-generated inside every `onDraw`. Physical S-Pen latency remains untested.
- **F04 critical hash-only/synchronous recipe engine**: patched (`RecipePixelKernels.kt`, `RecipePixelOperators.kt`, `TimeAndRecipeEngine.kt`, `StudioViewModel.kt`). Five local deterministic node operations now modify real sparse-tile pixels, with cancellation, 24 MiB artifact cache, off-main-thread execution, stale-version guard and new accepted layer. The first cloud run caught a cache-invalidation mismatch; subsequent run `43820160709` passed.
- Outstanding outside this bounded correction: real-device QA, deeper undo spillover, large-bounds smudge/fill OOM protections, native MP4, device-to-companion bridge, and ultimate merge decision. `main` has not been changed by these repairs.
