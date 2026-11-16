# Requirement Traceability Matrix — Infinite Studio (Living Workshop Uplift)

Maps every architectural invariant, end-to-end journey (`J01`–`J08`), and creative system (`F01`–`F10`, `P01`–`P10`) plus Stage B–F Living Workshop features to verified source symbols, automated tests, and honest verification boundaries.

---

## 1. End-to-End Test Journeys (`J01`–`J08` & Living Workshop Stage Tests)

| Journey / Suite | Requirement | Implementation Path | Automated Test Proof | Result |
|---|---|---|---|---|
| **J01** | Create -> S-Pen brush/color -> layers -> viewport transform -> undo/redo -> atomic save -> reopen -> export PNG | `TileBrushEngine.commitStrokeToDocument` -> `TransactionEngine` -> `ProjectStorageManager.saveLocalDraftAtomic` / `loadLocalDraft` -> `renderExportPreview` | `EndToEndJourneysTest.J01 paint save reopen and deterministic flattened export` | `PASS` (Robolectric SDK 35 NATIVE) |
| **J02** | Object Surgery: extract -> interactive transform / mask refine -> speculative BG repair -> revert -> original unchanged | `ObjectSurgeryAndMaterialEngine.extractObjectFromSelection`, `refineObjectMaskAndReextract`, `applyTransformToLayerOrObject`, `revertObjectExtraction` | `EndToEndJourneysTest.J02...`, `LivingWorkshopTest.F01 F02 and F09...` | `PASS` |
| **J03** | Protected mask -> 3 local Futures -> accept onto new layer -> original hashes invariant -> **0 pixels outside mask** -> Forensics heatmap | `FuturesAndConstraintsEngine.buildFuturesSession`, `acceptBranchToNewLayer`, `countNonTransparentPixelsOutsideMask`, `computeForensicsReport` | `EndToEndJourneysTest.J03...` & `UiFlowsRobolectricTest` | `PASS` |
| **J04** | Exact vector `<text>`, Bezier/balloon handles, masked Material lighting, Time frames duplicate/delete/loop/dual-onion & sprite sheet, Foundry ZIP roundtrip | `VectorAndFoundryEngine`, `ObjectSurgeryAndMaterialEngine`, `TimeAndRecipeEngine` | `EndToEndJourneysTest.J04...` & `LivingWorkshopTest` | `PASS` |
| **J05** | Visual Recipe DAG -> add/connect/cycle-reject -> downstream param edit & validation error -> selective descendant recompute + presets | `TimeAndRecipeEngine`, `RecipeGraphEditor.kt` | `EndToEndJourneysTest.J05...` & `LivingWorkshopTest.F04 Recipe DAG...` | `PASS` |
| **J06** | Vault multi-filter search + explicit `userApproved` + full Character Studio manual `userApprovedCanon` + local Art Director | `VaultBranchOrchardEngine` -> `StudioWorkspaceTabsExt.kt` | `EndToEndJourneysTest.J06...` & `LivingWorkshopTest` | `PASS` |
| **J07** | Offline egress block + companion consent gate + mock idempotency/cancel/stale rev + real OkHttp `/health` & cancel/fallback UI | `ProviderRouter`, `LoopbackCompanionAdapter`, `companion/server.py` | `ProviderAndBranchesTest.J07...`, `WorkspaceAdvancedSystemsTest`, `LivingWorkshopTest`, `companion/test_companion.py` | `PASS` |
| **J08** | Corrupt archive / zip-slip / future schema / canceled stroke / process-death draft recovery + Sample & Living Workshop journey roundtrips | `DocumentArchiveCodec`, `StylusInputRouter`, `ProjectStorageManager`, `SampleProjectFactory` | `DocumentAndArchiveTests`, `StylusAndTileRendererTests`, `EndToEndJourneysTest.J08`, `LivingWorkshopTest` | `PASS` |
| **Stage B–F UI** | Futures Loom diff/cherry-pick, Command Ring radial/accessible/reduced-motion/custom slots, WorkspaceMode switch + Back to Canvas, Recipe DAG UI, Vault no-results, Render Timing Diagnostics | `FuturesLoomDialog.kt`, `CreativeCommandRing.kt`, `TopControlBar.kt`, `RecipeGraphEditor.kt`, `StudioWorkspaceTabsExt.kt`, `SparseTileRenderer.kt` | `LivingWorkshopTest.Living Workshop Compose UI CUJs...` & `P07 and Futures Loom...` | `PASS` |

---

## 2. Core Non-Negotiable Machine Checks

| Machine Check | Enforcing Function / Class | Verified By |
|---|---|---|
| Pre-existing layer pixel hash equality after Futures accept / Object extract & mask refine | `DocumentLayer.pixelContentHash()`, `FuturesAndConstraintsEngine`, `ObjectSurgeryAndMaterialEngine` | `EndToEndJourneysTest.J03`, `LivingWorkshopTest.F01 F02 and F09...` |
| Zero committed pixels outside allowed mask | `FuturesAndConstraintsEngine.countNonTransparentPixelsOutsideMask` | `EndToEndJourneysTest.J03` (`assertEquals(0, outsidePixels)`) |
| Undo/redo, mode switching & branch isolation (zero mutable aliasing) | `TransactionEngine`, `VaultBranchOrchardEngine.forkBranch`, `StudioViewModel.setWorkspaceMode` | `DocumentAndArchiveTests`, `ProviderAndBranchesTest`, `LivingWorkshopTest` |
| Lossless `.iastudio` v2 migration & v3 roundtrip (including branch thumbnails & ancestry) | `DocumentArchiveCodec` | `DocumentAndArchiveTests`, `LivingWorkshopTest` |
| Exact SVG `<text>` lettering & sprite sheet dimensions | `VectorAndFoundryEngine.exportDocumentToSvg`, `TimeAndRecipeEngine.exportSpriteSheet` | `EndToEndJourneysTest.J04`, `LivingWorkshopTest` |
| DAG cycle rejection & parameter validation error reporting | `TimeAndRecipeEngine.connectRecipeNodes`, `executeRecipeGraph`, `RecipeGraphEditor` | `LivingWorkshopTest.F04 Recipe DAG...` & Compose UI CUJ |
| Zip-slip, corrupt PNG, future-version & payload bounds guards | `DocumentArchiveCodec`, `companion/server.py` | `DocumentAndArchiveTests`, `companion/test_companion.py` |
| Zero client secrets / zero OpenAI endpoints in APK + default offline gating | `ProviderRouter` (3 modes), removed root `.env.example` & `secrets-gradle-plugin` | `CredentialBoundaryAndBuildArtifactTest` (`3/3` scanning rebuilt `app-debug.apk`) |

---

## 3. Feature System Source & Audit Scope (`F01`–`F10`, `P01`–`P10`)

- **F01 Object Surgery (`VERIFIED`):** `ObjectSurgeryAndMaterialEngine`, `InfiniteCanvasView` (`TRANSFORM_*`), `BottomToolDock` (`transform_dock`), `StudioWorkspaceSheet` Tab 0 (`EndToEndJourneysTest.J02`, `LivingWorkshopTest`).
- **F02 Spatial Stage (`VERIFIED`):** `SpatialStageState`, `ObjectSurgeryAndMaterialEngine.resetSpatialCamera` -> `StudioWorkspaceSheet` Tab 0 (`WorkspaceAdvancedSystemsTest`, `LivingWorkshopTest`).
- **F03 Living Preview (`VERIFIED`):** `LivingPreviewEngine` -> `StudioWorkspaceSheet` Tab 3 (`WorkspaceAdvancedSystemsTest`).
- **F04 Creative Recipes (`VERIFIED`):** `TimeAndRecipeEngine` -> `RecipeGraphEditor.kt` -> Tab 3 (`EndToEndJourneysTest.J05`, `LivingWorkshopTest`).
- **F05 Time Canvas (`PARTIAL` — Playback & Sprite Sheet `VERIFIED`; `MediaCodec` MP4 Headless Limitation):** `TimeAndRecipeEngine`, `SparseTileRenderer` dual onion-skin, `StudioViewModel.playbackJob`, `BottomToolDock` (`animate_transport_bar`), `StudioWorkspaceTabsExt` Tab 2 (`EndToEndJourneysTest.J04`, `LivingWorkshopTest`).
- **F06 Material Intelligence (`VERIFIED`):** `MaterialRegion`, `renderMaterialLitPreview`, `upsertMaterial` -> `StudioWorkspaceTabsExt` Tab 2 (`EndToEndJourneysTest.J04`, `LivingWorkshopTest`).
- **F07 Visual Forensics (`VERIFIED`):** `ForensicsReport`, `computeForensicsReport` -> Tab 3 & `FuturesLoomDialog` (`EndToEndJourneysTest.J03`).
- **F08 Production Foundry (`VERIFIED`):** `FoundryManifest`, `exportFoundryBundleZip`, `verifyFoundryBundleZip` -> Tab 1 (`EndToEndJourneysTest.J04`, `LivingWorkshopTest`).
- **F09 Vector & Typography (`VERIFIED`):** `VectorAndFoundryEngine`, `InfiniteCanvasView` (`VECTOR_EDIT`), `BottomToolDock` (`vector_edit_dock`) -> Tab 1 (`EndToEndJourneysTest.J04`, `LivingWorkshopTest`).
- **F10 Visual Memory Vault (`VERIFIED`):** `searchVaultAssets` multi-filter query & clear/no-results UI -> `StudioWorkspaceTabsExt` Tab 4 (`EndToEndJourneysTest.J06`, `LivingWorkshopTest`).
- **P01 Futures Table (`VERIFIED` Local Studies + Masked Accept):** `FuturesTableDialog.kt` & `FuturesLoomDialog.kt` (`EndToEndJourneysTest.J03`, `UiFlowsRobolectricTest`).
- **P02 AI Brush (`VERIFIED` Local Guidance):** `AiGuidanceMark`, `StudioTool.AI_GUIDANCE` (`WorkspaceAdvancedSystemsTest`).
- **P03 Constraint Constellation (`VERIFIED`):** `ConstraintRecord`, `evaluateConstraintConflicts` -> Tab 5 (`EndToEndJourneysTest.J03`).
- **P04 Character Studio (`VERIFIED`):** `CharacterSheet`, `updateCharacterSheetFull`, `setCharacterCanonApproval` (manual switch defaulting `false`), 8-Head Ruler -> Tab 4 (`WorkspaceAdvancedSystemsTest`, `UiFlowsRobolectricTest`, `LivingWorkshopTest`).
- **P05 Art Director (`VERIFIED` Local Grammar):** `executeArtDirectorCommand` -> Tab 4 (`EndToEndJourneysTest.J06`).
- **P06 Outpainting (`PARTIAL` — Local Edge Study `VERIFIED`; Generative Seam Behind P10):** `generateCustomLocalOutpaint`, `buildOutpaintContextPreview` (`"DETERMINISTIC LOCAL EDGE STUDY"`) -> Tab 5 (`ProviderAndBranchesTest`, `LivingWorkshopTest`).
- **P07 Creative Branches (`VERIFIED`):** `BranchNode` (`thumbnailPng`, `ancestryPath`), `buildBranchComparisonReport`, `previewCherryPickLayer`, `cherryPickLayerWithResolution` -> `FuturesLoomDialog.kt` & Tab 4 (`ProviderAndBranchesTest`, `LivingWorkshopTest`).
- **P08 World Orchard (`VERIFIED`):** `navigateToOrchardNode` -> Tab 4 & `FuturesLoomDialog` (`WorkspaceAdvancedSystemsTest`, `UiFlowsRobolectricTest`).
- **P09 Asset Workshop (`VERIFIED`):** `WORKSHOP_TEMPLATES`, `applyWorkshopTemplate` -> Tab 1 (`WorkspaceAdvancedSystemsTest`).
- **P10 Secure Provider Router (`PARTIAL` — Offline/Mock/Loopback Socket/Health/Cancel `VERIFIED`; USB `adb reverse` Unrun):** `ProviderRouter`, `LoopbackCompanionAdapter`, `companion/server.py` -> Tab 5 & `FuturesTableDialog` (`ProviderAndBranchesTest.J07`, `WorkspaceAdvancedSystemsTest`, `LivingWorkshopTest`, `CredentialBoundaryAndBuildArtifactTest`, `companion/test_companion.py`).
