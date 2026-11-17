# RC3 local verification checkpoint — 2026-11-17

| Check | Status | Evidence |
|---|---|---|
| Repository identity / base branch | `VERIFIED` | `origin` is `westkitty/Infinite_Studio`; fetched `reconcile/restore-original-and-native` resolves to `a8a0c2af2b0ef43a81bc95d0984fff370d3661c4`; working branch is `codex/native-rc3-reproducible`. |
| Original browser source preservation | `VERIFIED` | `original_source.html` SHA-256 is `0e1497037505c011d7837874f5a277c5b65cc030161e36fc33102218f9970b76`; all ten source files from import commit `242534065f7035f57a44bb400cb30bc2cd74387d` have no diff on the recovery branch. |
| Toolchain | `VERIFIED` | JDK 21.0.13, Gradle 9.3.1, Android SDK 36, AGP 9.1.1, Kotlin 2.2.21. Gradle network used the managed HTTPS proxy and system Java trust store with certificate checks enabled. |
| App Kotlin compilation | `VERIFIED` | `:app:compileDebugKotlin` passed after the sparse photo-import repair. |
| Debug APK assembly | `VERIFIED` | `:app:assembleDebug` passed after the import-boundary repairs. APK is 19,587,403 bytes; SHA-256 `9617c9eafd729156f78786a993f77e6341045acff71ecee35c3793908848ca54`. |
| Android test-source compilation | `VERIFIED` | `:app:compileDebugUnitTestKotlin` passed, including the new max-canvas sparse import regression test. |
| Android unit-test execution | `BLOCKED` | `:app:testDebugUnitTest` did not start tests because `nativeruntime-dist-compat:1.0.16` redirected to `gcp.mirrors.maven.org`, which returned HTTP 403 under the current restricted egress policy. No Android test result is claimed for this run. |
| Credential boundary | `VERIFIED` *(manual artifact scan)* | No root `.env` or `.env.example`; source and Gradle credential tokens absent; scanned 145 APK entries and found no `.env`, `api.openai.com`, or `OPENAI_API_KEY`. The automated Robolectric credential-boundary test remains unexecuted with the suite. |
| Companion tests | `VERIFIED` | `python3 -m unittest discover -s companion -p 'test_*.py' -v`: 3/3 passed. |
| Photo/archive import bounds | `IMPLEMENTED_UNVERIFIED` | Photo Picker encoded input is capped at 32 MiB and rasterizes only occupied tiles; `.iastudio` stream input is capped at 224 MiB for SAF/local-draft reads. Cross-tile and byte-boundary test sources compile but runtime execution is blocked with the Android suite. |
| RC3 source ZIP integrity | `VERIFIED` | 128 unique entries; all 127 source-manifest entries matched their recorded size and SHA-256. The extracted companion suite passed 3/3. |
| Fresh-extraction Android compile/build and tests | `CANCELED / NOT_VERIFIED` | The extracted Gradle build was canceled by the user before completion. Android tests were not run in that extraction. |
| Device-specific checks | `NOT_STARTED` | No physical tablet, interactive emulator, or ADB bridge is exposed. |

The prior successful GitHub Actions run `43820160709` remains historical evidence for the recovery branch and does not replace these RC3 checks.

# QA Evidence — Infinite Studio Native Android (Living Workshop Uplift)

**Date:** 2026-11-15  
**Environment:** Cloud Linux Android Build Sandbox (JDK 21, AGP 9.1.1, Kotlin 2.2.21, Gradle 9.3.1, `compileSdk = 36`, `minSdk = 26`, `targetSdk = 36`)  
**No-Spend / No-Deploy / Zero-Client-Secret Scope:** Zero API keys or `.env` files in the Android client/APK; zero live paid OpenAI calls; zero server deployments; zero upstream `main` branch modifications.

---

## 1. Executed Build, Artifact & Test Checks

- **Android Compilation (`compile_applet` & `gradle assembleDebug`):** `PASSED`
  - Produced Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
  - Measured Size: `19,741,870` bytes (`18.83 MiB`)
  - Measured SHA-256: `e7332d0709a627cb6431d30be05a93af6ced442edd64ba1e56dbc9bf4aec3d1c`
  - Preserved Prior Baseline Archive: `Infinite_Studio_Native_Android_RC.zip` (`766,758` bytes, SHA-256 `94b0a22368e7401c7425c83bac33162de19fe3287016660934b53cc2fe70808d`, verified untouched)
  - APK Credential Audit (`CredentialBoundaryAndBuildArtifactTest` scanning rebuilt `app-debug.apk`): `0` `.env` entries, `0` `OPENAI_API_KEY` occurrences, `0` `api.openai.com` occurrences across DEX/assets/resources.
- **Robolectric + Compose UI + Security Test Execution:** `gradle :app:testDebugUnitTest --rerun-tasks` -> `BUILD SUCCESSFUL` (**32 tests completed, 0 failed, 0 skipped**)
- **Python Companion Test Suite:** `python3 -m unittest discover -s companion -p "test_*.py" -v` -> **`Ran 3 tests in 1.527s — OK`**

> **Runtime Note:** Android JVM tests run under **Robolectric 4.14.1** at `@Config(sdk = [35])` with `@GraphicsMode(GraphicsMode.Mode.NATIVE)` (real Skia/libhwui pixel rasterization, `Bitmap.compress`, `BitmapFactory`, `Canvas.clipPath`, Jetpack Compose `createAndroidComposeRule<ComponentActivity>()`, and synthetic `MotionEvent` sequences). `UiFlowsRobolectricTest` and `LivingWorkshopTest` use `qualifiers = "w1280dp-h800dp-land"` to validate tablet landscape Compose layout sizing and interactions.

---

## 2. Exact Executed Android Tests (`gradle :app:testDebugUnitTest`, 32/32 Passed)

### `com.example.CredentialBoundaryAndBuildArtifactTest` (3/3 Passed)
1. `BuildConfig and ProviderMode contain zero OpenAI credentials or client remote endpoints`
2. `Android source resources Gradle config and generated BuildConfig exclude OpenAI keys and secrets plugin`
3. `Offline startup painting undo and redacted diagnostics operate with zero network or API keys`

### `com.example.DocumentAndArchiveTests` (4/4 Passed)
4. `transaction engine supports undo redo redo-clearing and structural tile sharing`
5. `zip-slip traversal truncated archive and unsupported future version fail safely`
6. `v3 archive write and read roundtrip preserves multi-layer tiles checksums and unknown fields`
7. `legacy v2 json project migrates losslessly without flattening layers`

### `com.example.EndToEndJourneysTest` (7/7 Passed)
8. `J01 paint save reopen and deterministic flattened export`
9. `J02 object surgery extracts semantic object repairs hole speculatively and reverts cleanly`
10. `J03 futures produces 3 honest local studies and zero pixels mutate outside mask`
11. `J04 vector exact SVG material preview time frames and foundry bundle roundtrip`
12. `J05 creative recipe DAG selective descendant recompute skips unchanged upstream nodes`
13. `J06 vault tag and RGB color similarity search plus explicit approval and local Art Director`
14. `J08 corrupt input bad checksum and process death recovery preserve last good project and sample project roundtrips`

### `com.example.LivingWorkshopTest` (6/6 Passed)
15. `P07 and Futures Loom fork thumbnails compare diff overlay and conflict-aware cherry pick persist across v3 archive`
16. `F01 F02 and F09 direct manipulation mask refine camera reset Bezier handles and SVG export`
17. `F04 Recipe DAG editing cycle rejection invalidation validation error and presets`
18. `F05 Time Canvas F06 Material F10 Vault P04 Character P06 Outpaint P10 Health`
19. `SampleProjectFactory buildLivingWorkshopJourney and Foundry export pass all invariants`
20. `Living Workshop Compose UI CUJs Loom Command Ring Modes Recipe DAG Vault filters and Diagnostics`

### `com.example.ProviderAndBranchesTest` (2/2 Passed)
21. `J07 offline mode blocks external egress and mock provider honors idempotency cancellation and stale revision`
22. `creative branches fork diff rollback and cherry pick have zero mutable aliasing`

### `com.example.StylusAndTileRendererTests` (3/3 Passed)
23. `stylus input router distinguishes stylus touch eraser and consumes historical batched samples`
24. `sPenGuard routes single touch to pan instead of stroke`
25. `sparse tile renderer lazily allocates only touched tiles and culls viewport cleanly`

### `com.example.UiFlowsRobolectricTest` (1/1 Passed)
26. `UI CUJ opens Workspace Hub tabs toggles ruler jumps Orchard node loads Sample and accepts Futures study`

### `com.example.WorkspaceAdvancedSystemsTest` (6/6 Passed)
27. `F02 spatial stage assigns depth planes renders distinct parallax composites and roundtrips v3`
28. `F03 living preview coalesces rapid revisions and honors low power battery pause`
29. `P04 character studio ruler toggle and notes persist cleanly across v3 archive`
30. `P08 world orchard and P09 asset workshop integrate with StudioViewModel and Foundry`
31. `P10 loopback companion adapter executes real OkHttp HTTP calls against loopback server`
32. `StudioViewModel core CUJ paint undo redo layers AI guidance preset and redacted diagnostics`

---

## 3. Executed Companion Python Tests (`companion/test_companion.py`, 3/3 Passed)

1. `test_auth_required_and_health_capabilities` (`401` without bearer token, `200` `/health` & `/capabilities`)
2. `test_submit_idempotency_status_cancel_delete` (distinct PNG transform, idempotency replay, `GET`, `POST /cancel`, `DELETE`)
3. `test_validation_stale_revision_bad_mime_and_consent_gate` (`415` non-PNG, `409` stale revision, `403 CONSENT_REQUIRED`)

---

## 4. Rendering & Modal Checkerboard Polish + Diagnostics

1. **Main Canvas (`InfiniteCanvasView.kt`):**
   - Cached `48×48` two-tone `BitmapShader` `Paint` (drawn in 1 `drawRect` call), hoisted `Paint`/`Path`/`DashPathEffect` instances, View-local selection marquee runner (`postInvalidateOnAnimation()`), and direct gizmo/Bezier handle manipulation with zero `onDraw()` allocations.
2. **Modal Checkerboard Cleanup (`CheckerboardBox.kt`):**
   - Closed `RQ-P2-01` by replacing ~200-rect Compose `Canvas` loops inside `ExportDialog.kt` and `FuturesTableDialog.kt` with cached `ImageBitmap` `ShaderBrush` (`CheckerboardBackground`).
3. **Renderer Telemetry (`SparseTileRenderer.kt` & `DiagnosticsDialog.kt`):**
   - Tracks `lastFrameDrawMicros`, `emaFrameDrawMicros`, and `hitRatioPercent`, surfaced in redacted `StudioViewModel.buildRedactedDiagnosticsReport()`.

---

## 5. Explicitly NOT EXECUTED / Environment-Blocked

| Item | Reason | Reference |
|---|---|---|
| Live emulator launch, UI click-through on streaming emulator, & `adb shell dumpsys gfxinfo framestats` | AI Studio agent execution sandbox provides **no ADB bridge and no interactive emulator shell access**; live Android runtime frame pacing cannot be measured here. | `TAB_S9_ULTRA_DEVICE_QA.md` (`D01`–`D08`) |
| Physical Galaxy Tab S9 Ultra S-Pen latency, hover height, palm ergonomics, 120 Hz `framestats` | No physical SM-X910/SM-X916B device / ADB in sandbox | `TAB_S9_ULTRA_DEVICE_QA.md` (`D01`–`D08`) |
| Native hardware `MediaCodec` MP4 video encoding (`F05`) | Explicitly omitted / unverified in headless JVM/Robolectric environment (`F05` verified for looping playback & PNG sprite sheet export). | `REPAIR_QUEUE.md` (`RQ-P1-04`) |
| Android ↔ Python Companion live loopback over USB `adb reverse tcp:8787 tcp:8787` | No physical device / ADB available here (Android OkHttp `/health` + job calls verified against JVM loopback servers). | `TAB_S9_ULTRA_DEVICE_QA.md` (`D09`) |
| Live remote host upstream OpenAI image-edit execution | Forbidden by no-paid-API rule & zero client credential policy | `BACKEND_CONTRACT.md` |


---

## 6. Protected recovery branch — independent engine repair validation (2026-11-16)

- Source branch: `reconcile/restore-original-and-native`; original recovery commit `32c6db3`, implementation `2038f6b`, cache-key correction `3d84201`, CI setup correction `73c69b1`.
- **[GitHub Actions 43820160709](https://github.com/westkitty/Infinite_Studio/actions/runs/43820160709): SUCCESS.** Kotlin application/test compilation, `:app:testDebugUnitTest` (37 tests including five added `EngineRepairRegressionTest` cases), `:app:assembleDebug`, and Python companion suite (3 tests) all passed. Source verified with Android SDK 36, JDK 21, Gradle 9.3.1 in isolated Linux CI.
- The preceding [run 43819610050](https://github.com/westkitty/Infinite_Studio/actions/runs/43819610050) compiled and passed 36/37 tests; its single failure exposed a mismatch between the new image-cache fingerprint and `computeInvalidatedNodeIds`. Commit `3d84201` fixed that logic; the clean subsequent run passed.
- Verified bounds: provisional stroke pixel location and `ACTION_CANCEL`, eraser restoration, real deterministic raster recipe output, original-layer preservation, cache reuse, changed-parameter invalidation, and cancellation. Physical S-Pen testing, sustained FPS, system picker and hardware MP4 were **not** executed.
- This repairs the checked-out development branch only. GitHub `main` remains `db230c8`, which lacks ten historical browser files; original files are preserved byte-for-byte on the recovery branch. The old RC2 ZIP / APK hashes elsewhere in this document describe **pre-repair** artifacts, not this CI compilation. The CI debug APK is ephemeral and was not uploaded as a downloadable artifact.
