# Infinite Studio — Native Android Architecture (Living Workshop)

Infinite Studio is built as a single-module, single-Activity (`com.example.MainActivity`), landscape-first Jetpack Compose application organized into strict layered packages so domain, document, geometry, rendering, and serialization logic never depend on Composables or `Activity`.

---

## 1. Package Boundaries & Dependency Rule

```
ui/ ──────────────▶ viewmodel/ ──────────▶ features/ & tools/
                                                │
                       input/ ──────────────────┼──▶ render/ ──▶ document/
                       storage/ ────────────────┤
                       services/ ───────────────┘
```

- **`document/`** (`DocumentModels.kt`, `TransactionEngine.kt`, `DocumentArchiveCodec.kt`, `SampleProjectFactory.kt`)
  - Immutable `CanonicalDocument` (`schemaVersion = 3`) with structural tile sharing.
  - Extended schemas for `BranchNode` (`thumbnailPng`, `ancestryPath`), `MaterialRegion` (elevation, emissive hex/intensity, `maskId`), `TimelineState` (`loopStartFrame`, `loopEndFrame`, `onionSkinBeforeOpacity`, `onionSkinAfterEnabled/Opacity`), `RecipeNode` (visual `(posX, posY)` layout), `VaultAssetRecord`, and `CharacterSheet` (`userApprovedCanon` manual gate).
  - `SampleProjectFactory` provides both `buildSignatureDemo` and `buildLivingWorkshopJourney` for repeatable offline demos across all connected systems.
  - `TransactionEngine` maintains undo/redo stacks bounded by `MAX_HISTORY_STEPS = 24` and `48 MB` estimated tile memory.
- **`input/`** (`StylusInputRouter.kt`)
  - Processes raw Android `MotionEvent` streams (`ACTION_DOWN/MOVE/UP/CANCEL`, `ACTION_HOVER_MOVE`, `ACTION_POINTER_DOWN/UP`, Android 13+ `FLAG_CANCELED`), distinguishing `STYLUS`, `ERASER`, `TOUCH`, and `MOUSE` with `sPenGuard`, 2/3-finger tap undo/redo, and historical sample batching.
- **`render/`** (`SparseTileRenderer.kt`)
  - Manages sparse `256 × 256` PNG tiles, viewport culling, `48 MB` `LruCache`, dual before+after onion-skin compositing, `StaticLayout` wrapped vector/balloon text, and live timing telemetry (`lastFrameDrawMicros`, `emaFrameDrawMicros`, `hitRatioPercent`).
- **`tools/`** (`TileBrushEngine.kt`)
  - Tile-native rendering for `PENCIL`, `INK`, `MARKER`, `WATERCOLOR`, `AIRBRUSH`, `SMUDGE`, `ERASER`, `AI_GUIDANCE`, custom presets, symmetry, shape guides, scanline flood fill, eyedropper, and tile-aligned `expandBoundsIfNeeded`.
- **`features/`** (Connected Creative Engines)
  - `FuturesAndConstraintsEngine.kt` (`P01`, `P02`, `P03`, `P06`, `F07`): masked Futures studies, custom L/R/T/B deterministic edge-study outpainting + context preview, constraints & forensics.
  - `ObjectSurgeryAndMaterialEngine.kt` (`F01`, `F02`, `F06`): extraction, contour mask refine, interactive transforms, 2.5D depth planes & camera reset, masked Blinn-Phong + emission relief material preview.
  - `VectorAndFoundryEngine.kt` (`F08`, `F09`, `P09`): vector hit-testing, snap coordinates, move/resize/Bezier handle/balloon tail editing, SVG `<text>` export, Production Foundry bundle export & verification, Workshop templates.
  - `TimeAndRecipeEngine.kt` (`F03`, `F04`, `F05`): Living Preview debouncing, acyclic Recipe DAG validation/execution/caching/layout, Time Canvas frame ops, loop range & sprite sheet export.
  - `VaultBranchOrchardEngine.kt` (`F10`, `P04`, `P05`, `P07`, `P08`): immutable branch thumbnails/ancestry/diff/cherry-pick conflict preview & resolution, multi-filter Vault search, full Character Studio & canon gate, local Art Director grammar, World Orchard spatial nodes.
- **`services/`** (`ProviderRouter.kt`)
  - Zero-key client boundary (`OFFLINE_LOCAL_ONLY` default, `OFFLINE_MOCK_PROVIDER`, `LOOPBACK_COMPANION`) supporting `/health` ping, job submission, and active job cancellation against host `companion/server.py`.
- **`storage/`** (`ProjectStorageManager.kt`)
  - Atomic draft staging + `recovery_journal.json`, SAF `.iastudio`/PNG/JPG/SVG/Foundry ZIP read/write, and zero-permission Photo Picker import.
- **`viewmodel/`** (`StudioViewModel.kt`)
  - Coordinates transactions, `WorkspaceMode` (`PAINT`, `EDIT`, `EXPLORE`, `ANIMATE`, `PRODUCE`) with non-destructive "Back to Canvas" state preservation, lifecycle-safe `playbackJob` & `activeProviderJob` cleanup in `onCleared()`, `CreativeCommandRingOverlay` slot/accessibility/reduced-motion state, synchronous branch thumbnail capture inside `commitDoc`, and background `Dispatchers.IO`/`Default` heavy bitmap work.
- **`ui/`**
  - `InfiniteCanvasView.kt`: zero-allocation `onDraw()` (`BitmapShader` checkerboard, hoisted `Paint`/`Path`), direct `TRANSFORM_*` gizmo & `VECTOR_EDIT` Bezier/tail handle manipulation, snap crosshairs, and View-local selection marquee runner.
  - Modular Compose UI components kept compact (<500 lines/file): `TopControlBar.kt`, `LeftBrushDock.kt`, `BottomToolDock.kt`, `LayersDrawer.kt`, `ColorStudioPopover.kt`, `FuturesTableDialog.kt`, `FuturesLoomDialog.kt`, `CreativeCommandRing.kt`, `RecipeGraphEditor.kt`, `CheckerboardBox.kt`, `BrushStudioDialog.kt`, `StudioWorkspaceSheet.kt`, `StudioWorkspaceTabsExt.kt`, `DiagnosticsDialog.kt`, `ProjectVaultDialog.kt`, `ExportDialog.kt`.

---

## 2. Lifecycle, Rendering & Threading Model

- **Zero-Allocation Canvas & Modal Checkerboards:** `InfiniteCanvasView` uses a cached `48×48` `BitmapShader` `Paint` in 1 draw call; `ExportDialog` and `FuturesTableDialog` use `CheckerboardBackground` (`CheckerboardBox.kt`) backed by a remembered `ImageBitmap` `ShaderBrush`.
- **Lifecycle-Safe Coroutines:** `StudioViewModel` cancels `playbackJob`, `activeProviderJob`, `livingPreviewEngine`, and `autosaveJob` cleanly when switching away from `ANIMATE`, canceling provider work, or in `onCleared()`.
- **Semantics & Accessibility:** `CreativeCommandRingOverlay` uses a sibling scrim `Box` inside `Dialog` so descendant semantics are never merged, and provides an accessible grid fallback (`command_ring_accessible_grid`) and `reducedMotionEnabled` instant-snap transition toggle.
