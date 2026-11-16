# Handoff for Independent Audit & Repair (`HANDOFF_FOR_CHATGPT.md`)

## 1. Project Identity & Baseline Context
- **Product:** Infinite Studio — The Futures Table (Native Android · Living Workshop Uplift)
- **Package / App ID:** `namespace = "com.example"`, `applicationId = "com.aistudio.infinitestudio.kqvxzm"`
- **Target Device:** Samsung Galaxy Tab S9 Ultra (landscape-first, S-Pen-first)
- **Upstream Repository Baseline:** `https://github.com/westkitty/Infinite_Studio` (Contract reference commit `242534065f7035f57a44bb400cb30bc2cd74387d`)
- **Portable Delivery Artifacts:**
  - Rebuilt Debug APK: `app/build/outputs/apk/debug/app-debug.apk` (`19,741,870` bytes, SHA-256 `e7332d0709a627cb6431d30be05a93af6ced442edd64ba1e56dbc9bf4aec3d1c`)
  - Preserved Baseline Archive (Untouched): `Infinite_Studio_Native_Android_RC.zip` (`766,758` bytes, SHA-256 `94b0a22368e7401c7425c83bac33162de19fe3287016660934b53cc2fe70808d`)
  - Living Workshop Uplift Source Archive: `Infinite_Studio_Native_Android_Uplift_RC2.zip` (96 files matching `SOURCE_MANIFEST.json`, verified via clean extraction build & test in `/tmp/rc2_verify`)

---

## 2. Independent Audit, Uplift & Repair Summary

### 1) Living Workshop Uplift Highlights (Stages B–F)
- **The Futures Loom (`FuturesLoomDialog.kt` & `P07`):** Connected continuum hub linking Futures Table, Creative Branches (with `160×100` PNG thumbnails, ancestry lineage, side-by-side & pixel diff-overlay `compareBranches`, conflict-aware cherry-pick preview with `RENAME_NEW` vs `REPLACE_MATCHING`, persistent across `.iastudio` v3 save/reopen), Object Surgery, Constraints, Forensics, Vector/Typography, Material Intelligence, Time Canvas, and Production Foundry.
- **Direct On-Canvas Manipulation (`F01`, `F02`, `F09`):** `InfiniteCanvasView.kt` adds interactive `TRANSFORM_*` gizmo handles (`transform_dock` Snap/Cancel/Apply), `+Mask`/`-Mask` morphology refine, `F02` Pitch+Yaw+Zoom sliders + Stage Guides toggle + Reset Camera, and `VECTOR_EDIT` move/resize/Bezier anchor & `handleIn`/`handleOut` drag + speech balloon `tailTip` drag + 16px snap crosshairs + exact `<text>` SVG export.
- **Completed UI & Engine Integrations (`F04`, `F05`, `F06`, `F10`, `P04`, `P06`, `P10`):**
  - `F04` Visual Bezier DAG (`RecipeGraphEditor.kt`) with node drag, tap-to-link cycle rejection, `CACHED`/`INVALIDATED`/`ERROR` badges, parameter editing & validation, and preset management.
  - `F05` Time Canvas lifecycle-safe `playbackJob` (`animate_transport_bar`), duplicate/delete/loop range, dual before+after onion-skin, keyframe editing, and horizontal PNG sprite sheet export (`MediaCodec` MP4 omitted in headless builds).
  - `F06` Material elevation, emissive `#Hex` + intensity, selection-mask preview, reset & non-destructive save.
  - `F10` Multi-filter Vault search (`input_vault_search_name`, approval chips, empty/no-results states + filter reset).
  - `P04` Full Character Studio editor with manual `userApprovedCanon` switch defaulting `false` + 8-Head Ruler overlay.
  - `P06` Custom L/R/T/B tile outpainting + context preview banner (`"DETERMINISTIC LOCAL EDGE STUDY"`).
  - `P10` `/health` ping, job cancellation, 1-tap offline local fallback, privacy disclosure.
- **Creative Command Ring & Task-Adaptive Workspaces (Stage E):**
  - `CreativeCommandRingOverlay` 8-slot S-Pen radial menu + accessible grid fallback (`command_ring_accessible_grid`) + `reducedMotionEnabled` toggle + customizable slots, using sibling scrim dismiss semantics inside `Dialog` and zero stroke-stealing on bare canvas strokes.
  - `WorkspaceMode` (`PAINT`, `EDIT`, `EXPLORE`, `ANIMATE`, `PRODUCE`) + 1-tap `Back to Canvas` pill preserving document, selection, revision, history, and active jobs.
- **Guided Sample & Rendering Polish:** `SampleProjectFactory.buildLivingWorkshopJourney`, `CheckerboardBox.kt` cached `ImageBitmap` `ShaderBrush` for modals (closing `RQ-P2-01`), and `lastFrameDrawMicros` / `emaFrameDrawMicros` / `hitRatioPercent` telemetry in `SparseTileRenderer` & redacted `DiagnosticsDialog`.

### 2) Credential Boundary & Security Posture (`VERIFIED`)
- Zero root `.env`/`.env.example`, zero `secrets-gradle-plugin`, zero `OPENAI_API_KEY`, zero `api.openai.com` in source, generated `BuildConfig`, and rebuilt `app/build/outputs/apk/debug/app-debug.apk`.
- Confirmed by `CredentialBoundaryAndBuildArtifactTest` (`3/3 PASSED`) after rebuilding the APK.

### 3) Clean-Build Reproducibility
- Invoke system **`gradle`** (do **not** use `./gradlew`; workspace runs Gradle `9.3.1`, JDK `21`, AGP `9.1.1`, Kotlin `2.2.21`).
- Ensure `ANDROID_SDK_ROOT` is set; never create `local.properties` or root `.env`.
- Because `debug.keystore*` is intentionally excluded from portable source ZIPs, generate a local throwaway debug keystore on a fresh machine before `gradle assembleDebug`:
  ```bash
  keytool -genkey -v -keystore debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
  gradle assembleDebug
  gradle :app:testDebugUnitTest
  python3 -m unittest discover -s companion -p "test_*.py" -v
  ```

### 4) Unverified Real-Device, Hardware Codec & Live-Emulator Boundaries (Honest Disclosure)
The AI Studio cloud build sandbox has **no ADB bridge and no interactive emulator control**, so the following remain **UNVERIFIED on live Android Runtime / Hardware**:
1. Launching and manually interacting with the live UI in a streaming/physical emulator (`adb shell dumpsys gfxinfo framestats`, cold-start vs sustained 120 Hz pacing).
2. Physical Samsung Galaxy Tab S9 Ultra Wacom EMR S-Pen latency, tilt/pressure curves, hover cursor, resting palm ergonomics, and Command Ring S-Pen button shortcut (`TAB_S9_ULTRA_DEVICE_QA.md` `D01`–`D08`).
3. Native hardware `MediaCodec` + `MediaMuxer` MP4 video encoding (`RQ-P1-04`; Time Canvas looping playback and horizontal PNG sprite sheet export are verified).
4. Android SAF / Photo Picker system activity tap-through and physical USB `adb reverse tcp:8787 tcp:8787` loopback pairing with `companion/server.py` (`D09`).

---

## 3. Recommended Independent Verification Order
1. Extract `Infinite_Studio_Native_Android_Uplift_RC2.zip` into a clean directory, verify the 96 entries against `SOURCE_MANIFEST.json`, confirm zero `.env*` or secrets, and run `gradle :app:testDebugUnitTest assembleDebug` (`32/32`) plus `python3 -m unittest discover -s companion -p "test_*.py" -v` (`3/3`).
2. Inspect `LivingWorkshopTest.kt`, `EndToEndJourneysTest.kt`, `UiFlowsRobolectricTest.kt`, and `CredentialBoundaryAndBuildArtifactTest.kt`.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on a physical Samsung Galaxy Tab S9 Ultra and execute `TAB_S9_ULTRA_DEVICE_QA.md` (`D01`–`D09`) with `dumpsys gfxinfo framestats`.


---

## 4. 2026-11-16 source-repair continuation (supersedes native RC2 checkpoint)

**Current branch:** `reconcile/restore-original-and-native` at source commit `3d8420194801b163c6cddb6a9575ae8ced98016c` (prior restoration `32c6db3`). Do not target `main` and do not assume the original RC2 ZIP contains these later changes. Original browser source is restored byte-for-byte on the recovery branch, not main.

**Engine changes:** input-event world-space provisional raster and non-destructive eraser preview in `InfiniteCanvasView.kt`; F04 deterministic sparse-tile operations in `RecipePixelKernels.kt` and `RecipePixelOperators.kt`; cancellable job and revision-aware non-destructive layer acceptance through `TimeAndRecipeEngine` and `StudioViewModel`. New source tests: `EngineRepairRegressionTest.kt` (five tests).

**Independent evidence:** [CI 43820160709](https://github.com/westkitty/Infinite_Studio/actions/runs/43820160709) SUCCESS — Kotlin compilation + 37 Android Robolectric/JVM tests + debug APK compilation + 3 companion tests. Initial run `43819610050` failed 1/37 on the new cache fingerprint; fixed in `3d84201` and rerun green. CI APK was not uploaded, and no physical Tab S9 Ultra or real S-Pen test was executed. Big Mac did run 3/3 Python companion tests; restored browser tests were blocked locally by missing Pillow.

**Remaining engineering:** hardware S-Pen/latency and SAF tests, large-document OOM defenses in non-tiled legacy brush paths, disk-backed undo history, optional MediaCodec video and companion USB transport. No live paid API calls or server deployment.
