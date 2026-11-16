# Samsung Galaxy Tab S9 Ultra — Physical Device QA Script

> **CRITICAL EXECUTION NOTICE:** Physical Samsung Galaxy Tab S9 Ultra hardware tests are **NOT RUN (`NOT_RUN_NO_DEVICE`)** in the AI Studio cloud sandbox. Robolectric SDK 35 unit tests verify `MotionEvent` routing logic synthetically, **not** physical S-Pen digitizer latency, palm rejection ergonomics, display color accuracy, or 120 Hz frame pacing.

---

## Target Device Profile
- **Hardware:** Samsung Galaxy Tab S9 Ultra (SM-X910 / SM-X916B), Dynamic AMOLED 2X 14.6" (2960 × 1848), 120 Hz adaptive refresh, Wacom EMR S-Pen
- **OS:** Android 14 / 15 (One UI 6.x / 7.x), `minSdk = 26`, `targetSdk = 36`

---

## Pre-Test Setup (Physical Device via USB)
1. Build and install debug APK:
   ```bash
   gradle assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Verify offline operation first: enable **Airplane Mode** on tablet before starting tests `D01`–`D08`.

---

## Physical Hardware Test Cases

| ID | Test Case | Expected Behavior | Status | Measured Result |
|---|---|---|---|---|
| `D01` | **S-Pen Pressure Curve** | Draw slow-to-fast taper strokes with light (10%) to heavy (100%) S-Pen tip force using `Pencil`, `Ink`, and `Watercolor`. Stroke width and alpha vary smoothly without stair-stepping; 5-tap Brand Badge Diagnostics shows `intent=STYLUS` and updating `pressure` (`0.05..1.0`). | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D02` | **Air Hover Cursor & Tool Preview** | Hover S-Pen 5–12 mm above AMOLED glass without touching. Hover crosshair/radius preview tracks tip cleanly (`ACTION_HOVER_MOVE`) with zero unintended pixels committed. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D03` | **S-Pen Side Button / Flip Eraser** | Test hardware eraser / `Eraser` tool switch (`TOOL_TYPE_ERASER` or top dock). Existing strokes erase cleanly while leaving other layers untouched. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D04` | **Resting Palm + Finger Concurrency** | Toggle `S-Pen Guard` ON in Top Bar. Rest palm/wrist firmly on glass while drawing continuous S-Pen curves. Palm/single-finger touches pan only; S-Pen strokes draw continuously with zero stray blobs. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D05` | **One-Handed Two/Three-Finger Gestures** | Two-finger pinch/drag zooms (`0.25x..12x`) and pans smoothly; two-finger tap triggers Undo; three-finger tap triggers Redo; OS system-gesture `ACTION_CANCEL` rolls back provisional buffer with zero orphan ink. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D06` | **120 Hz Frame Pacing & Ink Latency** | Record `adb shell dumpsys gfxinfo com.aistudio.infinitestudio.kqvxzm framestats` during 60 seconds of rapid cross-hatching at 4x zoom. Target 95th percentile frame time `< 8.33 ms` (120 Hz) / `< 16.6 ms` (60 Hz) without jank spikes. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D07` | **Rotation, Multi-Window & Dark/Light Transitions** | Rotate Landscape ↔ Portrait and resize to Samsung Multi-Window 50/50 split while `Futures Table` or `Studio Hub` is open. Floating docks stay clear of system cutouts; canvas state and active layer survive intact. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D08` | **20-Minute Thermal, Memory & Battery Endurance** | Load Offline Sample (`Project Vault -> Load Offline Sample`), run Recipes, Forensics, Time Canvas 12-frame playback, and export Foundry ZIP. Verify `Debug.getNativeHeapAllocatedSize()` stabilizes below cache budget (`48 MB` LRU) without OOM or thermal throttling. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
| `D09` | **USB Loopback Companion Pairing** | Run `python3 companion/server.py` on host, execute `adb reverse tcp:8787 tcp:8787`, switch `ProviderRouter` in `Studio Hub -> Constraints & Router` to `LOOPBACK_COMPANION`, trigger Futures candidate, inspect provenance badge. | `NOT_RUN_NO_DEVICE` | *(pending hardware)* |
