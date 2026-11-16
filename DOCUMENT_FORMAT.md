# Infinite Studio Canonical Document Format (`.iastudio` v3)

Infinite Studio uses a versioned, ZIP-container `.iastudio` v3 format (`infinite-studio-archive`) designed for sparse tile efficiency, forward compatibility, lossless multi-layer migrations, and zero silent data loss.

---

## 1. Archive Container Layout

A saved `.iastudio` v3 file is a standard ZIP archive containing:

```
<project>.iastudio (ZIP)
├── manifest.json
└── tiles/
    ├── <sanitizedLayerId>/
    │   ├── 4_2.png
    │   ├── 4_3.png
    │   └── ...
    └── ...
```

- **Tile Dimensions:** `256 × 256` RGBA PNG (`tileSize = 256`).
- **Sparse Indexing:** `TileKey(tx, ty)` maps to `tiles/<sanitizedLayerId>/<tx>_<ty>.png` relative to `document.bounds` (`minX`, `minY`). Completely transparent tiles are omitted from both `tileKeys` and the ZIP entries.
- **Asset Checksums:** `manifest.json` stores `assetChecksums["tiles/<layer>/<tx>_<ty>.png"] = "<sha256>"`. On load, `DocumentArchiveCodec.readDocument()` recomputes SHA-256 for every tile PNG and rejects mismatches.

---

## 2. Schema Definition (`manifest.json`)

Machine-readable schema: [`schema/iastudio-v3.schema.json`](schema/iastudio-v3.schema.json)  
Sample manifest fixture: [`schema/sample-manifest.json`](schema/sample-manifest.json)

### Required Top-Level Fields
- `format`: `"infinite-studio-archive"`
- `schemaVersion`: `3` (supported range: `1..3`)
- `document`: canonical metadata object (`documentId`, `revision`, `title`, `bounds`, `tileSize`, `paperColorHex`, `layers`)

### Canonical Subsystems Stored Inside `document`
- `layers`: ordered stack of `ArchiveLayerMeta` (`id`, `name`, `visible`, `opacity`, `blendModeId`, `alphaLock`, `transform`, `depthPlaneId`, `tileKeys`)
- `vectorObjects`: editable `PATH` (Bezier handles), `RECT`, `ELLIPSE`, `TEXT_BOX`, `SPEECH_BALLOON` (including `tailTip` and exact `text` strings)
- `masks`: polygon `SelectionMaskRecord` entries
- `semanticObjects`: Object Surgery extractions + `repairLabel` (`LOCAL INFERRED REPAIR (SPECULATIVE)`)
- `constraints`: `PROTECTED_SILHOUETTE`, `FORBIDDEN_REGION`, `MATERIAL_NOTE`, `REQUIRED_TRAIT`
- `materials`: `MaterialRegion` (base color, roughness, metalness, alpha, emissive, relief, light azimuth/elevation)
- `spatialStage`: 2.5D depth planes, vanishing point, camera yaw/pitch/zoom, stage light
- `timeline`: frames, durations (ms), onion skin toggle, layer keyframes
- `recipes`: serializable DAG `RecipeGraph` definitions + cached `RecipeStageResult` hashes
- `vaultAssets`: user-selected references (`sourceUri`, `sourceId`, `tags`, `dominantColorHexes`, `rightsNote`, `userApproved`)
- `characterSheets`: character reference IDs, palette, proportion notes, ruler flag, explicit `userApprovedCanon`
- `branches` / `activeBranchId`: immutable revision DAG snapshots + `sourceHash`
- `revisions`: append-only provenance entries (`revision`, `summary`, `sourceHash`, `processingMode`, `providerId`)
- `artDirectorHistory`: instruction log bound to target layer & selection
- `unknownFields`: forward-compatible unknown top-level JSON string fields preserved across load/save cycles

---

## 3. Legacy `.iastudio` v2 Migration

When `DocumentArchiveCodec.readDocument()` detects a non-ZIP JSON payload with:
- `"format": "infinite-studio-project"` and `"version": 2`

It performs a **lossless layer-by-layer migration** (`readLegacyOrJsonDocument`):
1. Validates document bounds (`1..16384` per side) and layer count (`1..40`).
2. Decodes each layer's base64 `data:image/png;base64,...` payload independently, validating the PNG magic signature and dimensions.
3. Slices each layer's bitmap into sparse 256×256 PNG tiles (`sliceBitmapToSparseTiles`) with per-tile SHA-256 hashes.
4. Preserves layer `id`, `name`, `visible`, `opacity`, `blendMode`, and `alphaLock` — **never flattening layers**.
5. Appends a `MIGRATION_V2_TO_V3` provenance entry.

---

## 4. Validation & Failure Safety

`DocumentArchiveCodec` enforces strict defensive checks before committing any loaded document:
- **Zip-slip path traversal guard:** rejects entries containing `..`, `\0`, or leading `/` / `\\`.
- **Archive bomb bounds:** caps entry count (`MAX_ARCHIVE_ENTRIES = 8192`) and total uncompressed bytes (`MAX_ARCHIVE_UNCOMPRESSED_BYTES = 192 MiB`).
- **PNG signature check:** verifies the 8-byte PNG header (`89 50 4E 47 0D 0A 1A 0A`) on every tile asset.
- **Forward-version safety:** rejects `schemaVersion > 3` (`MAX_SUPPORTED_SCHEMA_VERSION`) with an explicit `Unsupported mandatory document schema version` error rather than silently corrupting newer documents.
- **Atomic recovery:** `ProjectStorageManager.saveLocalDraftAtomic` writes to temporary `.tmp` files and commits via `recovery_journal.json` only after complete flush, leaving the last known good project untouched on failure or cancellation.
